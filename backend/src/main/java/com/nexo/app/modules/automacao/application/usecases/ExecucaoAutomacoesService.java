package com.nexo.app.modules.automacao.application.usecases;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexo.app.modules.automacao.application.motor.ArmazemExecucao;
import com.nexo.app.modules.automacao.application.motor.Catalogo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.ContextoGatilho;
import com.nexo.app.modules.automacao.application.motor.Interpretador;
import com.nexo.app.modules.automacao.application.motor.RegistroEtapa;
import com.nexo.app.modules.automacao.domain.AprovacaoAutomacao;
import com.nexo.app.modules.automacao.domain.Automacao;
import com.nexo.app.modules.automacao.domain.EtapaExecucao;
import com.nexo.app.modules.automacao.domain.ExecucaoAutomacao;
import com.nexo.app.modules.automacao.domain.ModoExecucao;
import com.nexo.app.modules.automacao.domain.StatusExecucao;
import com.nexo.app.modules.automacao.domain.VersaoAutomacao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.modules.automacao.infrastructure.persistence.AprovacaoAutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.AutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.EtapaExecucaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.ExecucaoAutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.VersaoAutomacaoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.AvisoAutomacao;
import com.nexo.app.modules.notificacao.NotificacaoApi;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.email.EmailProperties;
import com.nexo.app.shared.infrastructure.email.MensagemEmail;
import com.nexo.app.shared.infrastructure.email.ServicoEmail;

import jakarta.annotation.PreDestroy;

/**
 * A fila de execuções.
 *
 * <p>Disparar grava uma execução {@code NA_FILA} (idempotente pela chave do disparo) e,
 * depois do commit, a entrega a um executor de virtual threads. Executar toma a execução
 * com um <em>lease</em> atômico, roda o interpretador até o fim ou até uma suspensão
 * (espera, aprovação, repetição com atraso) e grava o desfecho. O tick devolve à fila o
 * que venceu ou foi abandonado por um servidor que caiu.</p>
 */
@Service
public class ExecucaoAutomacoesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecucaoAutomacoesService.class);
    private static final Duration LEASE = Duration.ofMinutes(10);
    public static final String GATILHO_FALHA = "trigger.automation_failed";

    private final AutomacaoRepository automacoes;
    private final VersaoAutomacaoRepository versoes;
    private final ExecucaoAutomacaoRepository execucoes;
    private final EtapaExecucaoRepository etapas;
    private final AprovacaoAutomacaoRepository aprovacoes;
    private final Catalogo catalogo;
    private final ResolvedorDestinatarios destinatarios;
    private final UsuarioApi usuarios;
    private final NotificacaoApi notificacoes;
    private final ServicoEmail email;
    private final EmailProperties emailProperties;
    private final TransactionTemplate transacao;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore vagas = new Semaphore(6);

    public ExecucaoAutomacoesService(AutomacaoRepository automacoes, VersaoAutomacaoRepository versoes,
            ExecucaoAutomacaoRepository execucoes, EtapaExecucaoRepository etapas, AprovacaoAutomacaoRepository aprovacoes,
            Catalogo catalogo, ResolvedorDestinatarios destinatarios, UsuarioApi usuarios, NotificacaoApi notificacoes,
            ServicoEmail email, EmailProperties emailProperties, TransactionTemplate transacao) {
        this.automacoes = automacoes;
        this.versoes = versoes;
        this.execucoes = execucoes;
        this.etapas = etapas;
        this.aprovacoes = aprovacoes;
        this.catalogo = catalogo;
        this.destinatarios = destinatarios;
        this.usuarios = usuarios;
        this.notificacoes = notificacoes;
        this.email = email;
        this.emailProperties = emailProperties;
        this.transacao = transacao;
    }

    /** Desligando: dá um tempo para as execuções em andamento chegarem ao fim antes de soltar a fila. */
    @PreDestroy
    void encerrar() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.warn("Execuções de automação ainda em andamento no desligamento; serão retomadas quando o lease vencer");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Pedido de disparo. {@code carga} é o que o gatilho recebe para montar a sua saída. */
    public record Disparo(Automacao automacao, ModoExecucao modo, String chave, Map<String, Object> carga, UUID idReferencia,
                          UUID iniciadaPor, String emailTestador, Integer numeroVersao) {
    }

    /**
     * Cria a execução e a põe na fila. Vazio quando o gatilho decidiu não executar (filtro
     * do gatilho) ou quando o mesmo disparo já existe.
     */
    public Optional<UUID> disparar(Disparo d) {
        Automacao a = d.automacao();
        VersaoAutomacao versao = versoes.findByIdAutomacaoAndNumero(a.getId(),
                        d.numeroVersao() == null ? a.getNumeroVersao() : d.numeroVersao())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Versão da automação não encontrada."));
        if (execucoes.existsByIdAutomacaoAndChaveDisparo(a.getId(), d.chave())) return Optional.empty();
        OffsetDateTime agora = OffsetDateTime.now();
        DefinicaoAutomacao definicao = DefinicaoAutomacao.ler(versao.getDefinicao());
        EspecNo gatilho = catalogo.buscar(definicao.gatilho().tipo());
        Map<String, Object> saida;
        String falha = null;
        try {
            if (gatilho == null || gatilho.preparador() == null) throw new IllegalStateException("Gatilho desconhecido: " + definicao.gatilho().tipo());
            saida = gatilho.preparador().preparar(new ContextoGatilho(a.getId(), a.getNome(), a.getIdDono(), agora),
                    definicao.gatilho().params(), d.carga() == null ? Map.of() : d.carga());
            if (saida == null) return Optional.empty();
        } catch (RuntimeException e) {
            saida = Map.of();
            falha = "O gatilho não conseguiu preparar os dados: " + e.getMessage();
        }
        ExecucaoAutomacao execucao = new ExecucaoAutomacao(a.getId(), versao.getId(), d.modo(), d.chave(),
                saida, d.idReferencia(), d.iniciadaPor(), d.emailTestador(), agora);
        if (falha != null) execucao.falharAntesDeComecar(falha, agora);
        try {
            execucoes.saveAndFlush(execucao);
        } catch (DataIntegrityViolationException duplicada) {
            return Optional.empty();
        }
        if (falha == null) submeterDepoisDoCommit(execucao.getId());
        return Optional.of(execucao.getId());
    }

    /**
     * Roda de novo com a mesma saída congelada do gatilho, na versão atual da automação
     * (útil depois de corrigir um passo que falhou).
     */
    public UUID reenviar(ExecucaoAutomacao original, Automacao automacao, UUID usuario) {
        VersaoAutomacao versao = versoes.findByIdAutomacaoAndNumero(automacao.getId(), automacao.getNumeroVersao())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Versão da automação não encontrada."));
        ExecucaoAutomacao nova = new ExecucaoAutomacao(automacao.getId(), versao.getId(), ModoExecucao.REENVIO,
                "reenvio:" + UUID.randomUUID(), original.getSaidaGatilho(), original.getIdReferencia(), usuario, null,
                OffsetDateTime.now());
        execucoes.saveAndFlush(nova);
        submeterDepoisDoCommit(nova.getId());
        return nova.getId();
    }

    public void submeterDepoisDoCommit(UUID id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submeter(id);
                }
            });
        } else {
            submeter(id);
        }
    }

    public void submeter(UUID id) {
        executor.submit(() -> {
            boolean entrou = false;
            try {
                vagas.acquire();
                entrou = true;
                executar(id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                LOGGER.error("Execução de automação {} falhou fora do interpretador", id, e);
                liberar(id);
            } finally {
                if (entrou) vagas.release();
            }
        });
    }

    /** Solta o lease para o próximo tick retomar a execução (os passos já registrados não se repetem). */
    private void liberar(UUID id) {
        try {
            execucoes.liberar(id, OffsetDateTime.now());
        } catch (RuntimeException e) {
            LOGGER.warn("Não foi possível liberar a execução {}; ela será retomada quando o lease vencer", id, e);
        }
    }

    /** Executa (ou retoma) uma execução. Sem efeito se outro servidor já a tomou. */
    public void executar(UUID id) {
        OffsetDateTime agora = OffsetDateTime.now();
        if (execucoes.tomar(id, agora, agora.plus(LEASE)) == 0) return;
        ExecucaoAutomacao execucao = execucoes.findById(id).orElse(null);
        if (execucao == null) return;
        Automacao automacao = automacoes.findById(execucao.getIdAutomacao()).orElse(null);
        VersaoAutomacao versao = versoes.findById(execucao.getIdVersao()).orElse(null);
        if (automacao == null || versao == null) {
            finalizar(execucao, new Interpretador.Desfecho(StatusExecucao.FALHA, null, "Automação ou versão não encontrada", null, null), 0);
            return;
        }
        if (execucao.isCancelamentoPedido()) {
            execucao.cancelarAgora(OffsetDateTime.now());
            execucoes.save(execucao);
            encerrarAprovacoes(execucao.getId());
            return;
        }
        Set<String> permissoes = usuarios.buscarPermissoes(automacao.getIdDono());
        if (permissoes.isEmpty()) {
            finalizar(execucao, new Interpretador.Desfecho(StatusExecucao.FALHA, null,
                    "O responsável pela automação está inativo ou sem perfil; nada foi executado. Transfira a automação para outra pessoa.",
                    null, null), 0);
            return;
        }
        DefinicaoAutomacao definicao = DefinicaoAutomacao.ler(versao.getDefinicao());
        Map<String, Object> infoExecucao = new LinkedHashMap<>();
        infoExecucao.put("id", execucao.getId().toString());
        infoExecucao.put("uuid", execucao.getId());
        infoExecucao.put("modo", execucao.getModo().name());
        infoExecucao.put("link", emailProperties.resolverUrl(linkExecucao(automacao.getId(), execucao.getId())));
        infoExecucao.put("criada_em", execucao.getCriadaEm().toString());
        Map<String, Object> infoAutomacao = new LinkedHashMap<>();
        infoAutomacao.put("id", automacao.getId().toString());
        infoAutomacao.put("uuid", automacao.getId());
        infoAutomacao.put("name", automacao.getNome());
        infoAutomacao.put("nome", automacao.getNome());
        infoAutomacao.put("link", emailProperties.resolverUrl("/notificacoes/automacoes/" + automacao.getId()));
        ArmazemJpa armazem = new ArmazemJpa(execucao.getId(), automacao.getId());
        Interpretador interpretador = new Interpretador(definicao, catalogo, armazem, execucao.getSaidaGatilho(), infoExecucao,
                infoAutomacao, execucao.getModo() == ModoExecucao.TESTE ? "TESTE" : execucao.getModo().name(),
                OffsetDateTime::now, ExecucaoAutomacoesService::dormir,
                new Interpretador.Identidade(automacao.getIdDono(), permissoes, execucao.getEmailTestador()),
                destinatarios, false);
        Interpretador.Desfecho desfecho;
        try {
            desfecho = interpretador.executar();
        } catch (RuntimeException e) {
            LOGGER.error("Interpretador falhou na execução {}", id, e);
            desfecho = new Interpretador.Desfecho(StatusExecucao.FALHA, null, "Falha inesperada do motor: " + e.getMessage(), null, null);
        }
        ExecucaoAutomacao atual = execucoes.findById(id).orElse(execucao);
        finalizar(atual, desfecho, interpretador.executados());
        if (desfecho.status() == StatusExecucao.FALHA && atual.getModo() != ModoExecucao.TESTE) {
            avisarFalha(automacao, definicao, atual, desfecho.erro());
        }
    }

    private void finalizar(ExecucaoAutomacao execucao, Interpretador.Desfecho desfecho, int passos) {
        OffsetDateTime agora = OffsetDateTime.now();
        OffsetDateTime retomar = desfecho.retomarEm();
        execucao.concluir(desfecho.status(), retomar, desfecho.aguardando(), desfecho.erro(), desfecho.mensagem(), passos, agora);
        execucoes.save(execucao);
        if (desfecho.status().isFinal()) encerrarAprovacoes(execucao.getId());
        if (desfecho.status() == StatusExecucao.AGUARDANDO && retomar != null && Duration.between(agora, retomar).toSeconds() < 50) {
            // Repetição curta: retoma já, sem esperar o tick.
            executor.submit(() -> {
                dormir(Math.max(0, Duration.between(OffsetDateTime.now(), retomar).toMillis()) + 200);
                submeter(execucao.getId());
            });
        }
    }

    private void encerrarAprovacoes(UUID execucaoId) {
        for (AprovacaoAutomacao a : aprovacoes.findByIdExecucao(execucaoId)) {
            if (AprovacaoAutomacao.PENDENTE.equals(a.getStatus())) {
                a.encerrar("CANCELADA");
                aprovacoes.save(a);
            }
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String linkExecucao(UUID automacao, UUID execucao) {
        return "/notificacoes/automacoes/" + automacao + "/execucoes/" + execucao;
    }

    // ------------------------------------------------------------------------------ falhas

    private void avisarFalha(Automacao automacao, DefinicaoAutomacao definicao, ExecucaoAutomacao execucao, String erro) {
        String link = emailProperties.resolverUrl(linkExecucao(automacao.getId(), execucao.getId()));
        List<String> avisar = definicao.configuracoes().avisarFalha();
        if (!avisar.isEmpty() && email.ativo()) {
            try {
                String texto = "A automação \"" + automacao.getNome() + "\" falhou.\n\nMotivo: " + erro + "\n\nVeja a execução: " + link;
                String html = "<p>A automação <strong>" + escapar(automacao.getNome()) + "</strong> falhou.</p><p>Motivo: "
                        + escapar(erro) + "</p><p><a href=\"" + escapar(link) + "\">Abrir a execução</a></p>";
                email.enviar(MensagemEmail.builder().destinatarios(Set.copyOf(avisar))
                        .assunto("Automação com falha: " + automacao.getNome()).html(html).texto(texto).build());
            } catch (RuntimeException e) {
                LOGGER.warn("Aviso de falha da automação {} não enviado: {}", automacao.getId(), e.getMessage());
            }
        }
        // Outras automações podem reagir à falha desta.
        for (Automacao ouvinte : automacoes.buscarAtivasPorGatilho(GATILHO_FALHA)) {
            if (ouvinte.getId().equals(automacao.getId())) continue;
            Map<String, Object> carga = new HashMap<>();
            carga.put("automacao_id", automacao.getId().toString());
            carga.put("automacao_nome", automacao.getNome());
            carga.put("erro", erro);
            carga.put("execucao_id", execucao.getId().toString());
            carga.put("link", link);
            try {
                transacao.executeWithoutResult(s -> disparar(new Disparo(ouvinte, ModoExecucao.AUTOMATICA,
                        "falha:" + execucao.getId(), carga, execucao.getId(), null, null, null)));
            } catch (RuntimeException e) {
                LOGGER.warn("Automação {} não pôde reagir à falha: {}", ouvinte.getId(), e.getMessage());
            }
        }
    }

    private static String escapar(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------------------ ações humanas

    public void cancelar(UUID execucaoId) {
        ExecucaoAutomacao execucao = execucoes.findById(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução não encontrada."));
        execucao.pedirCancelamento();
        execucoes.save(execucao);
        if (execucao.getStatus() != StatusExecucao.EXECUTANDO) submeter(execucaoId);
    }

    /** Uma decisão registrada: a execução retoma na hora. */
    public void retomarAposAprovacao(UUID execucaoId) {
        submeter(execucaoId);
    }

    // ------------------------------------------------------------------------------ armazém

    /** Grava cada etapa no banco assim que muda (é o ponto de retomada). */
    private final class ArmazemJpa implements ArmazemExecucao {

        private final UUID execucaoId;
        private final UUID automacaoId;
        private final Map<String, EtapaExecucao> entidades = new HashMap<>();
        private long ultimoSinal = System.currentTimeMillis();

        ArmazemJpa(UUID execucaoId, UUID automacaoId) {
            this.execucaoId = execucaoId;
            this.automacaoId = automacaoId;
        }

        @Override
        public Map<String, RegistroEtapa> carregar() {
            Map<String, RegistroEtapa> registros = new HashMap<>();
            for (EtapaExecucao e : etapas.findByIdExecucao(execucaoId)) {
                entidades.put(e.getNoId() + "|" + e.getIteracao(), e);
                RegistroEtapa r = new RegistroEtapa(e.getNoId(), e.getIteracao(), e.getTipo(), e.getStatus(), e.getInicio());
                r.tentativa = e.getTentativa();
                r.entradas = e.getEntradas();
                r.saidas = e.getSaidas();
                r.erro = e.getErro();
                r.fim = e.getFim();
                r.proximaEm = e.getProximaEm();
                registros.put(r.chave(), r);
            }
            return registros;
        }

        @Override
        public void salvar(RegistroEtapa r) {
            EtapaExecucao e = entidades.computeIfAbsent(r.chave(), k -> new EtapaExecucao(execucaoId, r.noId, r.iteracao));
            e.atualizar(r.tipo, r.status, r.tentativa, r.entradas, r.saidas, r.erro, r.inicio, r.fim, r.proximaEm);
            entidades.put(r.chave(), etapas.save(e));
        }

        @Override
        public boolean cancelada() {
            return execucoes.findById(execucaoId).map(ExecucaoAutomacao::isCancelamentoPedido).orElse(true);
        }

        @Override
        public void sinalVital() {
            long agora = System.currentTimeMillis();
            if (agora - ultimoSinal < 60_000) return;
            ultimoSinal = agora;
            OffsetDateTime t = OffsetDateTime.now();
            execucoes.tomar(execucaoId, t.plusYears(10), t.plus(LEASE));
        }

        @Override
        public UUID abrirAprovacao(RegistroEtapa registro, PedidoAprovacao pedido) {
            AprovacaoAutomacao aprovacao = aprovacoes.save(new AprovacaoAutomacao(execucaoId, automacaoId, pedido.noId(),
                    pedido.iteracao(), pedido.titulo(), pedido.detalhes(), pedido.opcoes(), pedido.aprovadores(),
                    pedido.expiraEm(), OffsetDateTime.now()));
            try {
                notificacoes.publicarAviso(new AvisoAutomacao(Set.copyOf(pedido.aprovadores()), automacaoId, aprovacao.getId(),
                        "APROVACAO", "ALTA", "Aprovação pedida: " + pedido.titulo(),
                        pedido.detalhes() == null || pedido.detalhes().isBlank() ? "Uma automação aguarda a sua decisão." : pedido.detalhes(),
                        "/notificacoes/automacoes/aprovacoes/" + aprovacao.getId(), Map.of("idAprovacao", aprovacao.getId().toString()),
                        "aprovacao:" + aprovacao.getId(), true, null));
            } catch (RuntimeException e) {
                LOGGER.warn("Aviso da aprovação {} não publicado: {}", aprovacao.getId(), e.getMessage());
            }
            return aprovacao.getId();
        }

        @Override
        public Map<String, Object> resultadoAprovacao(UUID aprovacaoId) {
            AprovacaoAutomacao a = aprovacoes.findById(aprovacaoId).orElse(null);
            if (a == null || !AprovacaoAutomacao.DECIDIDA.equals(a.getStatus())) return null;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("outcome", a.getResposta());
            r.put("approved", a.getResposta() != null && a.getOpcoes().indexOf(a.getResposta()) == 0);
            Map<String, Object> pessoa = a.getRespondidaPor() == null ? null
                    : destinatarios.descrever(List.of(a.getRespondidaPor())).stream().findFirst().orElse(null);
            r.put("responder", pessoa == null ? null : Valores.texto(pessoa.get("login")));
            r.put("responder_email", pessoa == null ? null : pessoa.get("email"));
            r.put("comment", a.getComentario());
            r.put("decided_at", a.getRespondidaEm() == null ? null : a.getRespondidaEm().toString());
            return r;
        }

        @Override
        public void encerrarAprovacao(UUID aprovacaoId, String status) {
            aprovacoes.findById(aprovacaoId).ifPresent(a -> {
                a.encerrar(status);
                aprovacoes.save(a);
            });
        }
    }

    /** Usado pelo caso de uso de respostas: garante que a execução exista e espere aprovação. */
    public void exigirAguardando(ExecucaoAutomacao execucao) {
        if (execucao.getStatus().isFinal()) throw new InvarianteVioladaException("A execução já terminou.");
    }
}
