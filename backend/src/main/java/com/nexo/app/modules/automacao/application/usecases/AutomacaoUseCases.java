package com.nexo.app.modules.automacao.application.usecases;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.assistente.CopilotoApi;
import com.nexo.app.modules.automacao.application.dto.AprovacaoResponse;
import com.nexo.app.modules.automacao.application.dto.ArquivoExecucaoResponse;
import com.nexo.app.modules.automacao.application.dto.AutomacaoDetalheResponse;
import com.nexo.app.modules.automacao.application.dto.AutomacaoFilter;
import com.nexo.app.modules.automacao.application.dto.AutomacaoRequest;
import com.nexo.app.modules.automacao.application.dto.AutomacaoResumoResponse;
import com.nexo.app.modules.automacao.application.dto.EtapaResponse;
import com.nexo.app.modules.automacao.application.dto.ExecucaoDetalheResponse;
import com.nexo.app.modules.automacao.application.dto.ExecucaoResumoResponse;
import com.nexo.app.modules.automacao.application.dto.ExecutarRequest;
import com.nexo.app.modules.automacao.application.dto.OpcaoResponse;
import com.nexo.app.modules.automacao.application.dto.ProblemaResponse;
import com.nexo.app.modules.automacao.application.dto.RespostaAprovacaoRequest;
import com.nexo.app.modules.automacao.application.dto.UltimaExecucaoResponse;
import com.nexo.app.modules.automacao.application.dto.ValidacaoRequest;
import com.nexo.app.modules.automacao.application.dto.ValidacaoResponse;
import com.nexo.app.modules.automacao.application.dto.VersaoResponse;
import com.nexo.app.modules.automacao.application.motor.Catalogo;
import com.nexo.app.modules.automacao.application.motor.Catalogo.EspecNo;
import com.nexo.app.modules.automacao.application.motor.Recorrencia;
import com.nexo.app.modules.automacao.application.motor.Verificador;
import com.nexo.app.modules.automacao.domain.AprovacaoAutomacao;
import com.nexo.app.modules.automacao.domain.ArquivoAutomacao;
import com.nexo.app.modules.automacao.domain.Automacao;
import com.nexo.app.modules.automacao.domain.ExecucaoAutomacao;
import com.nexo.app.modules.automacao.domain.ModoExecucao;
import com.nexo.app.modules.automacao.domain.OrigemAutomacao;
import com.nexo.app.modules.automacao.domain.StatusAutomacao;
import com.nexo.app.modules.automacao.domain.StatusExecucao;
import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import com.nexo.app.modules.automacao.domain.VersaoAutomacao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.modules.automacao.infrastructure.persistence.AprovacaoAutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.ArquivoAutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.AutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.EtapaExecucaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.ExecucaoAutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.VersaoAutomacaoRepository;
import com.nexo.app.modules.iam.ContatoUsuario;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;

/**
 * Casos de uso das automações: cadastro com versões, verificação, ativação, execução
 * manual e de teste, histórico, aprovações e os seletores do designer.
 */
@Service
public class AutomacaoUseCases {

    private final AutomacaoRepository automacoes;
    private final VersaoAutomacaoRepository versoes;
    private final ExecucaoAutomacaoRepository execucoes;
    private final EtapaExecucaoRepository etapas;
    private final AprovacaoAutomacaoRepository aprovacoes;
    private final ArquivoAutomacaoRepository arquivos;
    private final Catalogo catalogo;
    private final Verificador verificador;
    private final CatalogoAutomacaoService visaoCatalogo;
    private final ExecucaoAutomacoesService fila;
    private final EnriquecedorEntidades entidades;
    private final UsuarioApi usuarios;
    private final ColaboradorApi colaboradores;
    private final BaseOperacionalApi bases;
    private final CopilotoApi copiloto;

    public AutomacaoUseCases(AutomacaoRepository automacoes, VersaoAutomacaoRepository versoes,
            ExecucaoAutomacaoRepository execucoes, EtapaExecucaoRepository etapas, AprovacaoAutomacaoRepository aprovacoes,
            ArquivoAutomacaoRepository arquivos, Catalogo catalogo, Verificador verificador,
            CatalogoAutomacaoService visaoCatalogo, ExecucaoAutomacoesService fila, EnriquecedorEntidades entidades,
            UsuarioApi usuarios, ColaboradorApi colaboradores, BaseOperacionalApi bases, CopilotoApi copiloto) {
        this.automacoes = automacoes;
        this.versoes = versoes;
        this.execucoes = execucoes;
        this.etapas = etapas;
        this.aprovacoes = aprovacoes;
        this.arquivos = arquivos;
        this.catalogo = catalogo;
        this.verificador = verificador;
        this.visaoCatalogo = visaoCatalogo;
        this.fila = fila;
        this.entidades = entidades;
        this.usuarios = usuarios;
        this.colaboradores = colaboradores;
        this.bases = bases;
        this.copiloto = copiloto;
    }

    private static UUID usuarioLogado() {
        UUID id = SecurityUtils.getUsuarioLogadoId();
        if (id == null) throw new InvarianteVioladaException("Usuário não autenticado.");
        return id;
    }

    // ------------------------------------------------------------------------------ catálogo

    @Transactional(readOnly = true)
    public Map<String, Object> catalogo() {
        List<Map<String, Object>> lista = automacoes.findAll(Sort.by("nome")).stream()
                .filter(Automacao::isAtivo)
                .map(a -> Map.<String, Object>of("id", a.getId().toString(), "nome", a.getNome()))
                .toList();
        return visaoCatalogo.visao(lista);
    }

    // ------------------------------------------------------------------------------ listagem

    @Transactional(readOnly = true)
    public PageResponse<AutomacaoResumoResponse> listar(AutomacaoFilter filtro, Pageable pageable) {
        Specification<Automacao> spec = (root, query, cb) -> cb.isTrue(root.get("ativo"));
        if (filtro != null && filtro.tipo() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tipo"), filtro.tipo()));
        }
        if (filtro != null && filtro.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filtro.status()));
        }
        if (filtro != null && filtro.busca() != null && !filtro.busca().isBlank()) {
            String termo = "%" + filtro.busca().strip().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("nome")), termo),
                    cb.like(cb.lower(cb.coalesce(root.get("descricao"), "")), termo)));
        }
        Pageable pagina = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "dataUltimaAlteracao"));
        Page<Automacao> resultado = automacoes.findAll(spec, pagina);
        List<UUID> ids = resultado.getContent().stream().map(Automacao::getId).toList();
        Map<UUID, List<ExecucaoAutomacao>> recentes = ids.isEmpty() ? Map.of()
                : execucoes.buscarRecentes(ids, OffsetDateTime.now().minusDays(30)).stream()
                .collect(Collectors.groupingBy(ExecucaoAutomacao::getIdAutomacao));
        Map<UUID, String> nomes = nomes(resultado.getContent().stream().map(Automacao::getIdDono).collect(Collectors.toSet()));
        return PageResponse.fromPage(resultado.map(a -> resumo(a, recentes.getOrDefault(a.getId(), List.of()), nomes)));
    }

    private Map<UUID, String> nomes(Set<UUID> ids) {
        Map<UUID, String> nomes = new HashMap<>();
        for (Map<String, Object> p : entidades.pessoas(new ArrayList<>(ids))) {
            nomes.put(UUID.fromString(Valores.texto(p.get("usuario_id"))), Valores.texto(p.get("nome")));
        }
        return nomes;
    }

    private DefinicaoAutomacao definicaoAtual(Automacao a) {
        return versoes.findByIdAutomacaoAndNumero(a.getId(), a.getNumeroVersao())
                .map(v -> DefinicaoAutomacao.ler(v.getDefinicao()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Versão da automação não encontrada."));
    }

    private AutomacaoResumoResponse resumo(Automacao a, List<ExecucaoAutomacao> recentes, Map<UUID, String> nomes) {
        Map<String, Object> params = Map.of();
        try {
            params = definicaoAtual(a).gatilho().params();
        } catch (RuntimeException ignorada) {
            // versão ilegível: mostra o resto
        }
        OffsetDateTime proxima = null;
        if (a.isAtiva() && a.getTipoGatilho().equals("trigger.schedule")) {
            try {
                proxima = Recorrencia.proximo(params, OffsetDateTime.now());
            } catch (IllegalArgumentException ignorada) {
                proxima = null;
            }
        }
        List<ExecucaoAutomacao> ordenadas = recentes.stream()
                .sorted(Comparator.comparing(ExecucaoAutomacao::getCriadaEm).reversed()).toList();
        ExecucaoAutomacao ultima = ordenadas.stream().filter(e -> e.getModo() != ModoExecucao.TESTE).findFirst()
                .orElse(ordenadas.isEmpty() ? null : ordenadas.get(0));
        int falhas = (int) ordenadas.stream().filter(e -> e.getStatus() == StatusExecucao.FALHA || e.getStatus() == StatusExecucao.TEMPO_ESGOTADO).count();
        return new AutomacaoResumoResponse(a.getId(), a.getNome(), a.getDescricao(), a.getTipo(), a.getStatus(), a.getOrigem(),
                a.getTipoGatilho(), visaoCatalogo.rotuloGatilho(a.getTipoGatilho()), visaoCatalogo.resumoGatilho(a.getTipoGatilho(), params),
                proxima, ultima == null ? null : new UltimaExecucaoResponse(ultima.getId(), ultima.getStatus(), ultima.getCriadaEm(), ultima.getErro()),
                ordenadas.size(), falhas, a.getIdDono(), nomes.get(a.getIdDono()), a.getNumeroVersao(), a.getDataUltimaAlteracao());
    }

    // ------------------------------------------------------------------------------ detalhe e edição

    private Automacao exigir(UUID id) {
        return automacoes.findById(id).filter(Automacao::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Automação não encontrada."));
    }

    @Transactional(readOnly = true)
    public AutomacaoDetalheResponse buscar(UUID id) {
        Automacao a = exigir(id);
        return detalhe(a);
    }

    private AutomacaoDetalheResponse detalhe(Automacao a) {
        List<VersaoAutomacao> lista = versoes.findByIdAutomacaoOrderByNumeroDesc(a.getId());
        VersaoAutomacao atual = lista.stream().filter(v -> v.getNumero() == a.getNumeroVersao()).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Versão da automação não encontrada."));
        DefinicaoAutomacao definicao = DefinicaoAutomacao.ler(atual.getDefinicao());
        List<ExecucaoAutomacao> recentes = execucoes.buscarRecentes(List.of(a.getId()), OffsetDateTime.now().minusDays(30));
        List<VersaoResponse> vs = lista.stream().limit(30)
                .map(v -> new VersaoResponse(v.getNumero(), v.getDataCriacao(), v.getUsuarioCriacao(), v.getNota())).toList();
        return new AutomacaoDetalheResponse(resumo(a, recentes, nomes(Set.of(a.getIdDono()))), atual.getDefinicao(), vs,
                verificador.problemasDeAtivacao(definicao, a.getTipo()));
    }

    /** Estrutura e catálogo: nó desconhecido não é salvo. */
    private DefinicaoAutomacao lerDefinicao(Map<String, Object> bruta) {
        DefinicaoAutomacao definicao = DefinicaoAutomacao.ler(bruta == null ? branca() : bruta);
        EspecNo gatilho = catalogo.buscar(definicao.gatilho().tipo());
        if (gatilho == null || !gatilho.gatilho()) {
            throw new InvarianteVioladaException("Gatilho desconhecido: " + definicao.gatilho().tipo());
        }
        for (DefinicaoAutomacao.No no : definicao.todosOsNos()) {
            if (catalogo.buscar(no.tipo()) == null) throw new InvarianteVioladaException("Tipo de passo desconhecido no passo '" + no.id() + "': " + no.tipo());
        }
        return definicao;
    }

    public static Map<String, Object> branca() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("schema", 3);
        d.put("trigger", new LinkedHashMap<>(Map.of("type", "trigger.manual", "params", new LinkedHashMap<>())));
        d.put("variables", new ArrayList<>());
        d.put("steps", new ArrayList<>());
        d.put("settings", new LinkedHashMap<>(Map.of("notify_on_failure", new ArrayList<>(), "timeout_hours", 168)));
        d.put("layout", new LinkedHashMap<>());
        return d;
    }

    private String chaveEvento(DefinicaoAutomacao definicao) {
        EspecNo gatilho = catalogo.buscar(definicao.gatilho().tipo());
        return gatilho == null ? null : gatilho.chaveEvento();
    }

    @Transactional
    public AutomacaoDetalheResponse criar(AutomacaoRequest request) {
        return detalhe(criarInterno(request, OrigemAutomacao.USUARIO, usuarioLogado(), null));
    }

    /** Também usado pelo rascunho do Copiloto e pela conversão de regras. */
    @Transactional
    public Automacao criarInterno(AutomacaoRequest request, OrigemAutomacao origem, UUID dono, UUID idRegraOrigem) {
        DefinicaoAutomacao definicao = lerDefinicao(request.definicao());
        TipoAutomacao tipo = request.tipo() == null ? verificador.sugerirTipo(definicao) : request.tipo();
        Automacao a = automacoes.save(new Automacao(request.nome(), request.descricao(), tipo, origem, dono,
                definicao.gatilho().tipo(), chaveEvento(definicao), idRegraOrigem));
        versoes.save(new VersaoAutomacao(a.getId(), 1, definicao.paraMapa(), request.nota()));
        return a;
    }

    /** Nova versão gravada pelo próprio Nexo (atualização de automação que ele criou). */
    @Transactional
    public void novaVersaoInterna(UUID id, Map<String, Object> definicao, String nota) {
        Automacao a = exigir(id);
        DefinicaoAutomacao nova = lerDefinicao(definicao);
        int numero = a.getNumeroVersao() + 1;
        versoes.save(new VersaoAutomacao(a.getId(), numero, nova.paraMapa(), nota));
        a.registrarVersao(numero, nova, chaveEvento(nova));
        if (a.isAtiva() && !verificador.problemasDeAtivacao(nova, a.getTipo()).isEmpty()) a.pausar();
        automacoes.save(a);
    }

    @Transactional
    public AutomacaoDetalheResponse salvar(UUID id, AutomacaoRequest request) {
        Automacao a = exigir(id);
        a.renomear(request.nome(), request.descricao(), request.tipo() == null ? a.getTipo() : request.tipo());
        if (request.definicao() != null) {
            DefinicaoAutomacao nova = lerDefinicao(request.definicao());
            Map<String, Object> mapa = nova.paraMapa();
            VersaoAutomacao atual = versoes.findByIdAutomacaoAndNumero(a.getId(), a.getNumeroVersao()).orElse(null);
            if (atual == null || !Objects.equals(Valores.comoJson(atual.getDefinicao()), Valores.comoJson(mapa))) {
                int numero = a.getNumeroVersao() + 1;
                versoes.save(new VersaoAutomacao(a.getId(), numero, mapa, request.nota()));
                a.registrarVersao(numero, nova, chaveEvento(nova));
                if (a.isAtiva()) {
                    List<String> problemas = verificador.problemasDeAtivacao(nova, a.getTipo());
                    if (!problemas.isEmpty()) a.pausar();
                }
            }
        }
        automacoes.save(a);
        return detalhe(a);
    }

    @Transactional
    public AutomacaoDetalheResponse alterarStatus(UUID id, StatusAutomacao status) {
        Automacao a = exigir(id);
        if (status == StatusAutomacao.ATIVA) {
            a.ativar(verificador.problemasDeAtivacao(definicaoAtual(a), a.getTipo()), OffsetDateTime.now());
        } else if (status == StatusAutomacao.PAUSADA) {
            a.pausar();
        } else {
            throw new InvarianteVioladaException("Use ATIVA ou PAUSADA.");
        }
        automacoes.save(a);
        return detalhe(a);
    }

    @Transactional
    public void arquivar(UUID id) {
        Automacao a = exigir(id);
        a.arquivar();
        automacoes.save(a);
    }

    @Transactional
    public AutomacaoDetalheResponse duplicar(UUID id) {
        Automacao a = exigir(id);
        DefinicaoAutomacao definicao = definicaoAtual(a);
        String nome = (a.getNome() + " (cópia)");
        if (nome.length() > 120) nome = nome.substring(0, 120);
        return detalhe(criarInterno(new AutomacaoRequest(nome, a.getDescricao(), a.getTipo(), definicao.paraMapa(),
                "Cópia de \"" + a.getNome() + "\""), OrigemAutomacao.USUARIO, usuarioLogado(), null));
    }

    @Transactional
    public AutomacaoDetalheResponse transferir(UUID id, UUID novoDono) {
        Automacao a = exigir(id);
        if (usuarios.buscarPermissoes(novoDono).isEmpty()) throw new InvarianteVioladaException("O novo responsável precisa ser um usuário ativo.");
        a.transferirPara(novoDono);
        automacoes.save(a);
        return detalhe(a);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> versao(UUID id, int numero) {
        exigir(id);
        return versoes.findByIdAutomacaoAndNumero(id, numero).map(VersaoAutomacao::getDefinicao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Versão não encontrada."));
    }

    // ------------------------------------------------------------------------------ verificação

    public ValidacaoResponse validar(ValidacaoRequest request) {
        DefinicaoAutomacao definicao;
        try {
            definicao = DefinicaoAutomacao.ler(request.definicao() == null ? branca() : request.definicao());
        } catch (InvarianteVioladaException e) {
            return new ValidacaoResponse(List.of(), List.of(e.getMessage()), e.getMessage(), null);
        }
        List<ProblemaResponse> problemas = verificador.verificar(definicao).stream()
                .map(p -> new ProblemaResponse(p.severidade(), p.mensagem(), p.noId(), p.param())).toList();
        TipoAutomacao tipo = request.tipo() == null ? verificador.sugerirTipo(definicao) : request.tipo();
        return new ValidacaoResponse(problemas, verificador.problemasDeAtivacao(definicao, tipo), null, verificador.sugerirTipo(definicao));
    }

    // ------------------------------------------------------------------------------ execução

    @Transactional
    public ExecucaoResumoResponse executar(UUID id, ExecutarRequest request) {
        Automacao a = exigir(id);
        if (a.getStatus() == StatusAutomacao.ARQUIVADA) throw new InvarianteVioladaException("Automação arquivada.");
        DefinicaoAutomacao definicao = definicaoAtual(a);
        List<String> erros = verificador.verificar(definicao).stream().filter(p -> p.severidade().equals("error"))
                .map(Verificador.Problema::mensagem).toList();
        if (!erros.isEmpty()) throw new InvarianteVioladaException("Corrija antes de executar: " + String.join("; ", erros.subList(0, Math.min(3, erros.size()))));
        UUID usuario = usuarioLogado();
        boolean teste = request != null && request.teste();
        if (!teste && !definicao.gatilho().tipo().equals("trigger.manual") && a.getStatus() != StatusAutomacao.ATIVA) {
            throw new InvarianteVioladaException("Para executar de verdade uma automação com este gatilho, ative-a ou use 'Testar'.");
        }
        String emailTestador = null;
        if (teste) {
            ContatoUsuario contato = usuarios.buscarContatos(List.of(usuario)).get(usuario);
            emailTestador = contato == null ? null : contato.email();
        }
        Map<String, Object> inputs = request == null || request.inputs() == null ? Map.of() : request.inputs();
        Map<String, Object> carga = cargaDeTeste(definicao, inputs, usuario);
        UUID idExecucao = fila.disparar(new ExecucaoAutomacoesService.Disparo(a, teste ? ModoExecucao.TESTE : ModoExecucao.MANUAL,
                        (teste ? "teste:" : "manual:") + UUID.randomUUID(), carga, null, usuario, emailTestador, null))
                .orElseThrow(() -> new InvarianteVioladaException("O filtro do gatilho não deixou a execução começar com estes dados."));
        return resumoExecucao(execucoes.findById(idExecucao).orElseThrow(), Map.of());
    }

    /** O que o gatilho recebe numa execução manual ou de teste. */
    private Map<String, Object> cargaDeTeste(DefinicaoAutomacao definicao, Map<String, Object> inputs, UUID usuario) {
        String tipo = definicao.gatilho().tipo();
        Map<String, Object> carga = new HashMap<>();
        carga.put("usuario_id", usuario.toString());
        if (tipo.equals("trigger.manual")) {
            carga.put("inputs", inputs);
        } else if (tipo.equals("trigger.schedule")) {
            carga.put("agendado_para", OffsetDateTime.now().toString());
        } else if (tipo.startsWith("threshold.")) {
            Object limiar = definicao.gatilho().params().getOrDefault("limiar", 1);
            carga.put("total", inputs.getOrDefault("total", limiar));
        } else if (tipo.startsWith("event.")) {
            EspecNo spec = catalogo.buscar(tipo);
            Map<String, Object> contexto = new HashMap<>();
            Map<String, Object> papeis = new HashMap<>();
            inputs.forEach((k, v) -> {
                if (!k.startsWith("papel:")) contexto.put(k, v);
                else papeis.put(k.substring(6), List.of(Valores.texto(v)));
            });
            if (spec != null) {
                for (var saida : spec.saidas()) {
                    if ("object".equals(saida.tipo()) && !saida.chave().equals("colaborador") && !saida.chave().equals("papeis")
                            && !papeis.containsKey(saida.chave())) {
                        papeis.put(saida.chave(), List.of(usuario.toString()));
                    }
                }
            }
            carga.put("contexto", contexto);
            carga.put("papeis", papeis);
            carga.put("id_referencia", Valores.texto(inputs.getOrDefault("id_referencia", UUID.randomUUID().toString())));
        } else if (tipo.equals(ExecucaoAutomacoesService.GATILHO_FALHA)) {
            carga.put("automacao_nome", "Automação de exemplo");
            carga.put("erro", "Falha simulada no teste");
            carga.put("link", "");
        }
        return carga;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecucaoResumoResponse> execucoes(UUID id, Pageable pageable) {
        exigir(id);
        Page<ExecucaoAutomacao> pagina = execucoes.findByIdAutomacaoOrderByCriadaEmDesc(id,
                PageRequest.of(pageable.getPageNumber(), Math.min(100, pageable.getPageSize())));
        Set<UUID> pessoas = pagina.getContent().stream().map(ExecucaoAutomacao::getIniciadaPor).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> nomes = nomes(pessoas);
        Map<UUID, Integer> numeros = numerosDeVersao(id);
        return PageResponse.fromPage(pagina.map(e -> resumoExecucao(e, nomes, numeros)));
    }

    private Map<UUID, Integer> numerosDeVersao(UUID automacao) {
        Map<UUID, Integer> numeros = new HashMap<>();
        for (VersaoAutomacao v : versoes.findByIdAutomacaoOrderByNumeroDesc(automacao)) numeros.put(v.getId(), v.getNumero());
        return numeros;
    }

    private ExecucaoResumoResponse resumoExecucao(ExecucaoAutomacao e, Map<UUID, String> nomes) {
        return resumoExecucao(e, nomes, numerosDeVersao(e.getIdAutomacao()));
    }

    private ExecucaoResumoResponse resumoExecucao(ExecucaoAutomacao e, Map<UUID, String> nomes, Map<UUID, Integer> numeros) {
        Long duracao = e.getIniciadaEm() == null ? null
                : Duration.between(e.getIniciadaEm(), e.getFinalizadaEm() == null ? OffsetDateTime.now() : e.getFinalizadaEm()).toMillis();
        return new ExecucaoResumoResponse(e.getId(), e.getIdAutomacao(), e.getStatus(), e.getModo(), numeros.getOrDefault(e.getIdVersao(), 0),
                e.getCriadaEm(), e.getIniciadaEm(), e.getFinalizadaEm(), duracao, e.getErro(), e.getMensagem(), e.getAguardando(),
                e.getRetomarEm(), e.getIniciadaPor() == null ? null : nomes.get(e.getIniciadaPor()), e.getPassosExecutados());
    }

    @Transactional(readOnly = true)
    public ExecucaoDetalheResponse execucao(UUID id, UUID execucaoId) {
        Automacao a = exigir(id);
        ExecucaoAutomacao e = execucoes.findById(execucaoId).filter(x -> x.getIdAutomacao().equals(id))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução não encontrada."));
        Map<String, Object> definicao = versoes.findById(e.getIdVersao()).map(VersaoAutomacao::getDefinicao).orElse(branca());
        List<EtapaResponse> lista = etapas.findByIdExecucao(execucaoId).stream()
                .sorted(Comparator.comparing(x -> x.getInicio() == null ? OffsetDateTime.MAX : x.getInicio()))
                .map(x -> new EtapaResponse(x.getNoId(), x.getIteracao(), x.getTipo(), x.getStatus(), x.getTentativa(), x.getEntradas(),
                        x.getSaidas(), x.getErro(), x.getInicio(), x.getFim(), x.getProximaEm()))
                .toList();
        UUID usuario = usuarioLogado();
        List<AprovacaoResponse> aps = aprovacoes.findByIdExecucao(execucaoId).stream().map(ap -> aprovacao(ap, a.getNome(), usuario)).toList();
        List<ArquivoExecucaoResponse> arqs = arquivos.listarDaExecucao(execucaoId).stream()
                .map(r -> new ArquivoExecucaoResponse(r.getId(), r.getNome(), r.getTipoConteudo(), r.getTamanhoBytes())).toList();
        Map<UUID, String> nomes = nomes(e.getIniciadaPor() == null ? Set.of() : Set.of(e.getIniciadaPor()));
        return new ExecucaoDetalheResponse(resumoExecucao(e, nomes), a.getNome(), definicao, e.getSaidaGatilho(), lista, aps, arqs);
    }

    public void cancelarExecucao(UUID id, UUID execucaoId) {
        exigirExecucao(id, execucaoId);
        fila.cancelar(execucaoId);
    }

    @Transactional
    public ExecucaoResumoResponse reenviar(UUID id, UUID execucaoId) {
        Automacao a = exigir(id);
        ExecucaoAutomacao e = exigirExecucao(id, execucaoId);
        UUID nova = fila.reenviar(e, a, usuarioLogado());
        return resumoExecucao(execucoes.findById(nova).orElseThrow(), Map.of());
    }

    private ExecucaoAutomacao exigirExecucao(UUID id, UUID execucaoId) {
        return execucoes.findById(execucaoId).filter(x -> x.getIdAutomacao().equals(id))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução não encontrada."));
    }

    @Transactional(readOnly = true)
    public ArquivoAutomacao arquivo(UUID arquivoId) {
        ArquivoAutomacao arquivo = arquivos.findById(arquivoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo não encontrado."));
        arquivo.getConteudo();
        return arquivo;
    }

    // ------------------------------------------------------------------------------ aprovações

    private AprovacaoResponse aprovacao(AprovacaoAutomacao ap, String automacaoNome, UUID usuario) {
        String quem = ap.getRespondidaPor() == null ? null : nomes(Set.of(ap.getRespondidaPor())).get(ap.getRespondidaPor());
        return new AprovacaoResponse(ap.getId(), ap.getIdAutomacao(), automacaoNome, ap.getIdExecucao(), ap.getTitulo(), ap.getDetalhes(),
                ap.getOpcoes(), ap.getStatus(), ap.getResposta(), ap.getComentario(), quem, ap.getRespondidaEm(), ap.getExpiraEm(),
                ap.getCriadaEm(), AprovacaoAutomacao.PENDENTE.equals(ap.getStatus()) && ap.podeResponder(usuario));
    }

    @Transactional(readOnly = true)
    public List<AprovacaoResponse> minhasAprovacoes() {
        UUID usuario = usuarioLogado();
        List<AprovacaoAutomacao> lista = aprovacoes.buscarPendentesDe(usuario.toString());
        Map<UUID, String> nomes = new HashMap<>();
        for (AprovacaoAutomacao ap : lista) {
            nomes.computeIfAbsent(ap.getIdAutomacao(), k -> automacoes.findById(k).map(Automacao::getNome).orElse("Automação"));
        }
        return lista.stream().map(ap -> aprovacao(ap, nomes.get(ap.getIdAutomacao()), usuario)).toList();
    }

    @Transactional(readOnly = true)
    public AprovacaoResponse buscarAprovacao(UUID aprovacaoId) {
        AprovacaoAutomacao ap = aprovacoes.findById(aprovacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aprovação não encontrada."));
        UUID usuario = usuarioLogado();
        if (!ap.podeResponder(usuario) && !usuarios.buscarPermissoes(usuario).contains("automacao:read")) {
            throw new RecursoNaoEncontradoException("Aprovação não encontrada.");
        }
        return aprovacao(ap, automacoes.findById(ap.getIdAutomacao()).map(Automacao::getNome).orElse("Automação"), usuario);
    }

    @Transactional
    public AprovacaoResponse responder(UUID aprovacaoId, RespostaAprovacaoRequest request) {
        AprovacaoAutomacao ap = aprovacoes.findById(aprovacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aprovação não encontrada."));
        UUID usuario = usuarioLogado();
        ap.responder(usuario, request.opcao(), request.comentario(), OffsetDateTime.now());
        aprovacoes.save(ap);
        fila.submeterDepoisDoCommit(ap.getIdExecucao());
        return aprovacao(ap, automacoes.findById(ap.getIdAutomacao()).map(Automacao::getNome).orElse("Automação"), usuario);
    }

    // ------------------------------------------------------------------------------ seletores

    @Transactional(readOnly = true)
    public List<OpcaoResponse> opcoes(String tipo, String busca) {
        String termo = busca == null ? "" : busca.strip().toLowerCase();
        return switch (tipo == null ? "" : tipo) {
            case "usuario" -> {
                if (termo.length() < 2) yield List.of();
                Map<UUID, String> encontrados = new LinkedHashMap<>();
                usuarios.buscarAtivosPorTrechoDoLogin(termo, 15).forEach(r -> encontrados.put(r.id(), r.login()));
                List<UUID> porNome = colaboradores.buscarIdsPorTrechoDoNome(termo);
                for (UUID idColaborador : porNome.subList(0, Math.min(15, porNome.size()))) {
                    colaboradores.buscarPorIdResponse(idColaborador)
                            .filter(c -> c.idUsuario() != null && c.ativo())
                            .ifPresent(c -> encontrados.putIfAbsent(c.idUsuario(), c.nomeCompleto()));
                }
                List<OpcaoResponse> lista = new ArrayList<>();
                for (Map<String, Object> p : entidades.pessoas(new ArrayList<>(encontrados.keySet()))) {
                    lista.add(new OpcaoResponse(Valores.texto(p.get("usuario_id")), Valores.texto(p.get("nome")),
                            detalhePessoa(p)));
                    if (lista.size() >= 20) break;
                }
                yield lista;
            }
            case "colaborador" -> {
                if (termo.length() < 2) yield List.of();
                List<UUID> ids = colaboradores.buscarIdsPorTrechoDoNome(termo);
                Map<UUID, ResumoColaborador> resumos = colaboradores.buscarResumosPorIds(ids.subList(0, Math.min(20, ids.size())));
                yield resumos.values().stream().filter(ResumoColaborador::ativo)
                        .map(r -> new OpcaoResponse(r.id().toString(), r.nomeCompleto(), juntar(r.funcao(), r.baseOperacional()))).toList();
            }
            case "perfil" -> usuarios.listarPerfisAtivos().stream()
                    .filter(o -> termo.isEmpty() || o.label().toLowerCase().contains(termo))
                    .map(o -> new OpcaoResponse(o.value(), o.label(), null)).toList();
            case "permissao" -> usuarios.listarPermissoes().stream()
                    .filter(p -> termo.isEmpty() || p.contains(termo)).limit(60)
                    .map(p -> new OpcaoResponse(p, p, null)).toList();
            case "base" -> bases.listarOpcoesAtivas().stream()
                    .filter(o -> termo.isEmpty() || o.label().toLowerCase().contains(termo))
                    .map(o -> new OpcaoResponse(o.label(), o.label(), null)).toList();
            case "fonte" -> copiloto.fontes(usuarios.buscarPermissoes(usuarioLogado())).stream()
                    .filter(f -> termo.isEmpty() || (f.nome() + " " + f.id()).toLowerCase().contains(termo))
                    .map(f -> new OpcaoResponse(f.id(), f.nome(), f.descricao())).toList();
            default -> throw new InvarianteVioladaException("Tipo de opção desconhecido: " + tipo);
        };
    }

    /** Nomes para exibir valores já escolhidos (chips do designer). */
    @Transactional(readOnly = true)
    public List<OpcaoResponse> rotulos(String tipo, List<String> valores) {
        List<UUID> ids = new ArrayList<>();
        for (String v : valores == null ? List.<String>of() : valores) {
            try {
                ids.add(UUID.fromString(v));
            } catch (IllegalArgumentException ignorada) {
                // não é id
            }
        }
        if (ids.isEmpty()) return List.of();
        return switch (tipo) {
            case "usuario" -> entidades.pessoas(ids).stream()
                    .map(p -> new OpcaoResponse(Valores.texto(p.get("usuario_id")), Valores.texto(p.get("nome")), detalhePessoa(p))).toList();
            case "colaborador" -> colaboradores.buscarResumosPorIds(ids).values().stream()
                    .map(r -> new OpcaoResponse(r.id().toString(), r.nomeCompleto(), juntar(r.funcao(), r.baseOperacional()))).toList();
            case "perfil" -> {
                Set<String> procurados = new HashSet<>(valores);
                yield usuarios.listarPerfisAtivos().stream().filter(o -> procurados.contains(o.value()))
                        .map(o -> new OpcaoResponse(o.value(), o.label(), null)).toList();
            }
            default -> List.of();
        };
    }

    private static String detalhePessoa(Map<String, Object> p) {
        return juntar(Valores.texto(p.get("funcao")), Valores.texto(p.get("base")), Valores.texto(p.get("email")));
    }

    private static String juntar(String... partes) {
        List<String> lista = new ArrayList<>();
        for (String p : partes) if (p != null && !p.isBlank()) lista.add(p);
        return lista.isEmpty() ? null : String.join(" · ", lista);
    }
}
