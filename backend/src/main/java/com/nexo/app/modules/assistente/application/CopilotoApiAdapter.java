package com.nexo.app.modules.assistente.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.nexo.app.modules.assistente.CopilotoApi;
import com.nexo.app.modules.assistente.application.agent.AgentRun;
import com.nexo.app.modules.assistente.application.agent.AgentRunner;
import com.nexo.app.modules.assistente.application.dto.EnviarMensagemRequest;
import com.nexo.app.modules.assistente.application.files.ArquivoService;
import com.nexo.app.modules.assistente.application.llm.LlmMessage;
import com.nexo.app.modules.assistente.application.llm.LlmProvider;
import com.nexo.app.modules.assistente.application.llm.LlmRequest;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.application.semantic.SemanticCatalog;
import com.nexo.app.modules.assistente.application.semantic.ViewQuery;
import com.nexo.app.modules.assistente.application.semantic.ViewQueryCompiler;
import com.nexo.app.modules.assistente.domain.ArquivoAssistente;
import com.nexo.app.modules.assistente.domain.ConversaAssistente;
import com.nexo.app.modules.assistente.domain.MensagemAssistente;
import com.nexo.app.modules.assistente.domain.StatusMensagemAssistente;
import com.nexo.app.modules.assistente.infrastructure.persistence.MensagemAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.semantic.SemanticSqlExecutor;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;

/** Implementa a {@link CopilotoApi} sobre o runtime do agente. */
@Service
public class CopilotoApiAdapter implements CopilotoApi {

    private static final int ANALISE_MAX_CICLOS = 40;
    private static final int ANALISE_MAX_CHAMADAS = 120;

    private static final Logger LOGGER = LoggerFactory.getLogger(CopilotoApiAdapter.class);

    private final AssistenteProperties properties;
    private final LlmProvider llm;
    private final AgentRunner runner;
    private final ConversaPersistencia persistencia;
    private final MensagemAssistenteRepository mensagens;
    private final ArquivoService arquivos;
    private final SemanticCatalog catalogo;
    private final ViewQueryCompiler compilador;
    private final SemanticSqlExecutor executor;

    public CopilotoApiAdapter(AssistenteProperties properties, LlmProvider llm, AgentRunner runner,
            ConversaPersistencia persistencia, MensagemAssistenteRepository mensagens, ArquivoService arquivos,
            SemanticCatalog catalogo, ViewQueryCompiler compilador, SemanticSqlExecutor executor) {
        this.properties = properties;
        this.llm = llm;
        this.runner = runner;
        this.persistencia = persistencia;
        this.mensagens = mensagens;
        this.arquivos = arquivos;
        this.catalogo = catalogo;
        this.compilador = compilador;
        this.executor = executor;
    }

    @Override
    public boolean disponivel() {
        return properties.isEnabled() && llm.isConfigured();
    }

    @Override
    public String completar(String sistema, String usuario, double temperatura, int limiteSegundos) {
        if (!disponivel()) throw new IllegalStateException("O provedor de IA não está configurado neste ambiente.");
        List<LlmMessage> conversa = new ArrayList<>();
        if (sistema != null && !sistema.isBlank()) conversa.add(new LlmMessage.System(sistema));
        conversa.add(new LlmMessage.User(usuario));
        return llm.complete(new LlmRequest(properties.getLlm().getModel(), conversa, List.of(),
                LlmRequest.ToolChoice.NONE, temperatura, 8192, false, null)).content();
    }

    @Override
    public boolean visaoDisponivel() {
        return disponivel() && modeloVisao() != null;
    }

    @Override
    public String modeloVisao() {
        String modelo = properties.getLlm().getVisionModel();
        return modelo == null || modelo.isBlank() ? null : modelo;
    }

    @Override
    public String completarComImagens(String sistema, String usuario, List<Imagem> imagens, double temperatura, int limiteTokens) {
        if (!visaoDisponivel()) throw new IllegalStateException("Não há modelo com visão configurado neste ambiente.");
        List<LlmMessage> conversa = new ArrayList<>();
        if (sistema != null && !sistema.isBlank()) conversa.add(new LlmMessage.System(sistema));
        conversa.add(new LlmMessage.User(usuario, imagens.stream()
                .map(imagem -> new LlmMessage.Image(imagem.tipoConteudo(), imagem.base64()))
                .toList()));
        return llm.complete(new LlmRequest(modeloVisao(), conversa, List.of(),
                LlmRequest.ToolChoice.NONE, temperatura, limiteTokens, false, null)).content();
    }

    @Override
    public Analise analisar(PedidoAnalise pedido) {
        long inicio = System.nanoTime();
        if (!disponivel()) {
            return new Analise("", null, List.of(), false,
                    "O Copiloto está indisponível: o provedor de IA não está configurado.", 0);
        }
        SemanticQueryContext seguranca = new SemanticQueryContext(pedido.usuarioId(), Set.copyOf(pedido.permissoes()));
        ConversaAssistente conversa = persistencia.criar(pedido.usuarioId(), pedido.titulo(), null);
        // Relatório de automação: várias consultas e um PDF; o orçamento do chat (16 ciclos) não basta.
        AgentRunner.Orcamento orcamento = new AgentRunner.Orcamento(ANALISE_MAX_CICLOS, ANALISE_MAX_CHAMADAS,
                Duration.ofSeconds(Math.max(120, pedido.limiteSegundos())));
        AgentRunner.Inicio partida = runner.iniciar(conversa.getId(), seguranca, pedido.nomeUsuario(),
                new EnviarMensagemRequest(UUID.randomUUID(), pedido.pergunta(), List.of(), null), orcamento);
        UUID mensagemId;
        if (partida.execucao() != null) {
            AgentRun run = partida.execucao();
            mensagemId = run.mensagemAssistenteId();
            aguardar(run, Duration.ofSeconds(Math.max(60, pedido.limiteSegundos())));
            if (!run.isFinished()) {
                runner.parar(conversa.getId(), pedido.usuarioId());
                aguardar(run, Duration.ofSeconds(20));
            }
        } else {
            mensagemId = partida.concluida().getId();
        }
        long duracao = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
        MensagemAssistente mensagem = mensagens.findById(mensagemId).orElse(null);
        if (mensagem == null) {
            return new Analise("", conversa.getId(), List.of(), false, "A resposta do Copiloto não foi encontrada.", duracao);
        }
        List<Arquivo> anexos = new ArrayList<>();
        for (ArquivoAssistente arquivo : arquivos.listarDaConversa(conversa.getId())) {
            if (arquivo.getOrigem() != ArquivoAssistente.Origem.GERADO) continue;
            try {
                anexos.add(new Arquivo(arquivo.getNome(), arquivo.getTipoConteudo(), arquivos.conteudo(arquivo)));
            } catch (RuntimeException e) {
                LOGGER.warn("Arquivo {} gerado pelo Copiloto não pôde ser lido: {}", arquivo.getId(), e.getMessage());
            }
        }
        boolean concluida = mensagem.getStatus() == StatusMensagemAssistente.CONCLUIDA;
        String erro = concluida ? null : (mensagem.getErro() == null ? "O Copiloto não concluiu a análise." : mensagem.getErro());
        return new Analise(mensagem.getConteudo() == null ? "" : mensagem.getConteudo(), conversa.getId(), anexos,
                concluida, erro, duracao);
    }

    private static void aguardar(AgentRun run, Duration limite) {
        long fim = System.nanoTime() + limite.toNanos();
        try {
            while (!run.isFinished() && System.nanoTime() < fim) {
                run.readFrom(Integer.MAX_VALUE - 1, 2_000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<FonteDados> fontes(Set<String> permissoes) {
        SemanticQueryContext seguranca = new SemanticQueryContext(null, Set.copyOf(permissoes));
        List<FonteDados> lista = new ArrayList<>();
        for (SemanticCatalog.Fonte fonte : catalogo.autorizadas(seguranca)) {
            Map<String, String> colunas = new LinkedHashMap<>();
            if (fonte.colunas() != null) fonte.colunas().forEach((nome, coluna) -> colunas.put(nome, coluna.descricao()));
            lista.add(new FonteDados(fonte.id(), fonte.nome(), fonte.descricao(), fonte.dominio(), fonte.colunaData(), colunas));
        }
        return lista;
    }

    @Override
    public ResultadoConsulta consultar(UUID usuarioId, Set<String> permissoes, Consulta consulta) {
        SemanticQueryContext seguranca = new SemanticQueryContext(usuarioId, Set.copyOf(permissoes));
        List<ViewQuery.Metrica> metricas = consulta.metricas() == null ? List.of()
                : consulta.metricas().stream().map(m -> new ViewQuery.Metrica(m.funcao(), m.coluna(), m.alias())).toList();
        List<ViewQuery.Filtro> filtros = consulta.filtros() == null ? List.of()
                : consulta.filtros().stream().map(f -> new ViewQuery.Filtro(f.coluna(), f.operador(), f.valores())).toList();
        ViewQuery.Periodo periodo = consulta.colunaPeriodo() == null || consulta.colunaPeriodo().isBlank()
                || (consulta.inicio() == null && consulta.fim() == null)
                ? null : new ViewQuery.Periodo(consulta.colunaPeriodo(), consulta.inicio(), consulta.fim());
        List<ViewQuery.Ordem> ordem = consulta.ordenarPor() == null || consulta.ordenarPor().isBlank() ? List.of()
                : List.of(new ViewQuery.Ordem(consulta.ordenarPor(), consulta.decrescente()));
        ViewQuery query = new ViewQuery(consulta.fonte(), consulta.colunas(), consulta.agruparPor(), metricas, filtros,
                periodo, ordem, consulta.limite());
        ViewQueryCompiler.CompiledQuery compilada = compilador.compilar(query, seguranca);
        TabularData dados = executor.executar(compilada.sql(), compilada.parametros(), seguranca, compilada.limite());
        List<String> colunas = dados.columns().stream().map(TabularData.Column::name).toList();
        return new ResultadoConsulta(colunas, dados.asMaps(compilada.limite()), dados.totalRows(), dados.truncated());
    }
}
