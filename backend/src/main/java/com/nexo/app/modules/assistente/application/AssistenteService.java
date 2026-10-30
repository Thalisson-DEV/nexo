package com.nexo.app.modules.assistente.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.artifacts.ArtifactService;
import com.nexo.app.modules.assistente.application.dto.*;
import com.nexo.app.modules.assistente.application.files.ArquivoService;
import com.nexo.app.modules.assistente.application.llm.LlmProvider;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.application.skills.SkillCatalog;
import com.nexo.app.modules.assistente.domain.ArquivoAssistente;
import com.nexo.app.modules.assistente.domain.ConversaAssistente;
import com.nexo.app.modules.assistente.domain.MensagemAssistente;
import com.nexo.app.modules.assistente.domain.OrigemConversa;
import com.nexo.app.modules.assistente.domain.PapelMensagem;
import com.nexo.app.modules.assistente.infrastructure.persistence.ConversaAssistenteRepository;
import com.nexo.app.modules.assistente.infrastructure.persistence.MensagemAssistenteRepository;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Casos de uso do workspace do Copiloto: conversas (listar, buscar, criar, renomear, excluir), envio de
 * mensagens com streaming, interrupcao/retomada, anexos, artefatos, resultados e capacidades. Toda operacao
 * e restrita ao dono da conversa.
 */
@Service
public class AssistenteService {

    private static final TypeReference<List<AcaoInterface>> ACOES = new TypeReference<>() { };
    private static final TypeReference<List<Packet>> PACOTES = new TypeReference<>() { };
    private static final TypeReference<List<UUID>> IDS = new TypeReference<>() { };

    private final ConversaAssistenteRepository conversas;
    private final MensagemAssistenteRepository mensagens;
    private final ConversaPersistencia persistencia;
    private final AgentRunner runner;
    private final AssistantSecurityContextFactory securityFactory;
    private final ArquivoService arquivos;
    private final ArtifactService artefatos;
    private final ResultStore resultados;
    private final ToolRegistry ferramentas;
    private final SkillCatalog skills;
    private final LlmProvider llm;
    private final AssistenteProperties properties;
    private final ObjectMapper objectMapper;

    public AssistenteService(ConversaAssistenteRepository conversas, MensagemAssistenteRepository mensagens,
            ConversaPersistencia persistencia, AgentRunner runner, AssistantSecurityContextFactory securityFactory,
            ArquivoService arquivos, ArtifactService artefatos, ResultStore resultados, ToolRegistry ferramentas,
            SkillCatalog skills, LlmProvider llm, AssistenteProperties properties, ObjectMapper objectMapper) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.persistencia = persistencia;
        this.runner = runner;
        this.securityFactory = securityFactory;
        this.arquivos = arquivos;
        this.artefatos = artefatos;
        this.resultados = resultados;
        this.ferramentas = ferramentas;
        this.skills = skills;
        this.llm = llm;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------------------------ conversas

    @Transactional(readOnly = true)
    public PageResponse<ConversaResumoResponse> listar(int pagina, int tamanho, String busca) {
        return listar(pagina, tamanho, busca, null);
    }

    /** Lista as conversas do usuario; {@code painelId} restringe as iniciadas naquele painel (dashboard). */
    @Transactional(readOnly = true)
    public PageResponse<ConversaResumoResponse> listar(int pagina, int tamanho, String busca, String painelId) {
        UUID usuarioId = usuario();
        PageRequest page = PageRequest.of(Math.max(0, pagina), Math.min(100, Math.max(1, tamanho)),
                Sort.by(Sort.Direction.DESC, "ultimaAtividadeEm"));
        Page<ConversaAssistente> resultado;
        if (painelId != null && !painelId.isBlank()) {
            resultado = conversas.findByUsuarioIdAndAtivoTrueAndOrigemAndOrigemId(usuarioId, OrigemConversa.DASHBOARD,
                    painelId.strip(), page);
        } else {
            resultado = busca == null || busca.isBlank()
                    ? conversas.findByUsuarioIdAndAtivoTrueAndOrigemNot(usuarioId, OrigemConversa.MCP, page)
                    : conversas.buscarDoUsuario(usuarioId, termoBusca(busca), page);
        }
        return PageResponse.fromPage(resultado.map(this::resumo));
    }

    public ConversaResumoResponse criar(CriarConversaRequest request) {
        ConversaAssistente conversa = persistencia.criar(usuario(), request == null ? null : request.titulo(),
                request == null ? null : request.contexto());
        return resumo(conversa);
    }

    @Transactional(readOnly = true)
    public ConversaDetalheResponse buscar(UUID conversaId) {
        UUID usuarioId = usuario();
        ConversaAssistente conversa = exigir(conversaId, usuarioId);
        List<MensagemAssistente> lista = mensagens.findByConversaIdAndAtivoTrueOrderByDataCriacaoAsc(conversaId);
        List<ArquivoAssistente> arquivosConversa = arquivos.listarDaConversa(conversaId);
        Map<UUID, ArquivoAssistente> arquivosPorId = new HashMap<>();
        arquivosConversa.forEach(arquivo -> arquivosPorId.put(arquivo.getId(), arquivo));
        List<MensagemResponse> respostas = lista.stream().map(mensagem -> mensagem(mensagem, arquivosPorId)).toList();
        return new ConversaDetalheResponse(conversa.getId(), conversa.getTitulo(), conversa.getDataCriacao(),
                conversa.getUltimaAtividadeEm(), persistencia.contexto(conversa), respostas, artefatos.listar(conversaId),
                arquivosConversa.stream().map(arquivos::toResponse).toList(), runner.mensagemEmAndamento(conversaId).orElse(null),
                conversa.getOrigem(), conversa.getOrigemId());
    }

    @Transactional
    public ConversaResumoResponse renomear(UUID conversaId, RenomearConversaRequest request) {
        ConversaAssistente conversa = exigir(conversaId, usuario());
        conversa.renomear(request.titulo());
        return resumo(conversas.save(conversa));
    }

    @Transactional
    public void excluir(UUID conversaId) {
        UUID usuarioId = usuario();
        ConversaAssistente conversa = exigir(conversaId, usuarioId);
        runner.parar(conversaId, usuarioId);
        conversa.desativar();
        conversas.save(conversa);
    }

    // ------------------------------------------------------------------------------------ mensagens

    public AgentRunner.Inicio enviar(UUID conversaId, EnviarMensagemRequest request) {
        UUID usuarioId = usuario();
        SemanticQueryContext security = securityFactory.current(usuarioId);
        return runner.iniciar(conversaId, security, securityFactory.nomeUsuario(), request);
    }

    public boolean parar(UUID conversaId) {
        UUID usuarioId = usuario();
        exigirSemTransacao(conversaId, usuarioId);
        return runner.parar(conversaId, usuarioId);
    }

    public Optional<AgentRun> execucao(UUID conversaId) {
        UUID usuarioId = usuario();
        exigirSemTransacao(conversaId, usuarioId);
        return runner.execucao(conversaId, usuarioId);
    }

    @Transactional(readOnly = true)
    public MensagemResponse mensagem(UUID conversaId, UUID mensagemId) {
        exigir(conversaId, usuario());
        MensagemAssistente mensagem = mensagens.findById(mensagemId)
                .filter(item -> item.isAtivo() && item.getConversa().getId().equals(conversaId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mensagem não encontrada"));
        Map<UUID, ArquivoAssistente> arquivosPorId = new HashMap<>();
        arquivos.listarDaConversa(conversaId).forEach(arquivo -> arquivosPorId.put(arquivo.getId(), arquivo));
        return mensagem(mensagem, arquivosPorId);
    }

    // ------------------------------------------------------------------------------------ arquivos, artefatos, resultados

    public ArquivoAssistenteResponse enviarArquivo(String nome, String tipoConteudo, byte[] conteudo) {
        return arquivos.enviar(usuario(), nome, tipoConteudo, conteudo);
    }

    @Transactional(readOnly = true)
    public ArquivoBaixado baixarArquivo(UUID arquivoId) {
        ArquivoAssistente arquivo = arquivos.exigirDoUsuario(arquivoId, usuario());
        return new ArquivoBaixado(arquivo.getNome(), arquivo.getTipoConteudo(), arquivos.conteudo(arquivo));
    }

    public void removerArquivo(UUID arquivoId) {
        arquivos.remover(arquivoId, usuario());
    }

    @Transactional(readOnly = true)
    public List<ArtifactService.ArtefatoView> artefatos(UUID conversaId) {
        exigir(conversaId, usuario());
        return artefatos.listar(conversaId);
    }

    @Transactional(readOnly = true)
    public ArtifactService.ArtefatoView artefato(UUID conversaId, String codigo) {
        exigir(conversaId, usuario());
        return artefatos.buscar(conversaId, codigo);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resultado(UUID conversaId, String codigo, int limite) {
        exigir(conversaId, usuario());
        ResultStore.StoredResult resultado = resultados.carregar(conversaId, codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Resultado não encontrado"));
        TabularData data = resultado.data();
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("codigo", resultado.codigo());
        resposta.put("titulo", resultado.titulo());
        resposta.put("natureza", resultado.natureza().name());
        resposta.put("origem", resultado.origem());
        resposta.put("consulta", resultado.consulta());
        resposta.put("colunas", data.columns().stream()
                .map(coluna -> Map.of("nome", coluna.name(), "tipo", coluna.type().name().toLowerCase(Locale.ROOT))).toList());
        resposta.put("linhas", data.rows().stream().limit(Math.max(1, Math.min(1_000, limite))).toList());
        resposta.put("total", data.totalRows());
        resposta.put("truncado", data.truncated() || data.rows().size() < data.totalRows());
        return resposta;
    }

    // ------------------------------------------------------------------------------------ capacidades

    public CapacidadesResponse capacidades() {
        UUID usuarioId = usuario();
        SemanticQueryContext security = securityFactory.current(usuarioId);
        boolean disponivel = properties.isEnabled() && llm.isConfigured();
        List<AgentTool> autorizadas = ferramentas.autorizadas(security);
        boolean python = operacional(autorizadas, "executar_python");
        boolean web = operacional(autorizadas, "pesquisar_web");
        boolean leitura = operacional(autorizadas, "abrir_url");
        List<CapacidadesResponse.Ferramenta> lista = autorizadas.stream()
                .filter(tool -> !List.of("executar_python", "pesquisar_web", "abrir_url").contains(tool.name())
                        || (tool.name().equals("executar_python") ? python : tool.name().equals("pesquisar_web") ? web : leitura))
                .map(tool -> new CapacidadesResponse.Ferramenta(tool.name(), tool.displayName(), tool.kind().name()))
                .toList();
        List<CapacidadesResponse.Competencia> competencias = skills.disponiveis(security).stream()
                .map(skill -> new CapacidadesResponse.Competencia(skill.nome(), skill.titulo(), skill.descricao())).toList();
        return new CapacidadesResponse(disponivel,
                disponivel ? null : "O provedor de IA não está configurado neste ambiente.",
                disponivel ? properties.getLlm().getModel() : null, python, web, leitura, lista, competencias,
                sugestoes(security, python, web),
                new CapacidadesResponse.Limites(properties.getArquivos().getMaxBytes(), 10,
                        List.of("pdf", "xlsx", "xls", "csv", "json", "txt", "docx", "png", "jpg")));
    }

    private static boolean operacional(List<AgentTool> autorizadas, String nome) {
        return autorizadas.stream().filter(tool -> tool.name().equals(nome)).anyMatch(tool -> {
            try {
                return tool.isAvailable(null);
            } catch (RuntimeException exception) {
                return false;
            }
        });
    }

    private static List<CapacidadesResponse.Sugestao> sugestoes(SemanticQueryContext security, boolean python, boolean web) {
        List<CapacidadesResponse.Sugestao> sugestoes = new ArrayList<>();
        if (security.possuiAutoridade("estoque:movimentacao:read")) {
            sugestoes.add(new CapacidadesResponse.Sugestao("Consumo de materiais",
                    "Tendência, maiores consumidores e comparação com o mês anterior",
                    "Analise o consumo de materiais deste mês por obra e por material, compare com o mês anterior e monte um dashboard com os destaques.",
                    "pi pi-box"));
        }
        if (security.possuiAutoridade("estoque:saldo:read")) {
            sugestoes.add(new CapacidadesResponse.Sugestao("Estoque crítico",
                    "Saldos baixos cruzados com reservas e solicitações",
                    "Quais materiais estão com saldo disponível baixo ou zerado e ainda têm reservas ou solicitações pendentes?",
                    "pi pi-exclamation-triangle"));
        }
        if (security.possuiAutoridade("estoque:solicitacao:read")) {
            sugestoes.add(new CapacidadesResponse.Sugestao("Solicitações em aberto",
                    "Pendências, retiradas atrasadas e tempo de atendimento",
                    "Mostre as solicitações de material pendentes ou com retirada atrasada, por status, e o tempo médio até a entrega neste mês.",
                    "pi pi-inbox"));
        }
        if (security.possuiAutoridade("desconto:read")) {
            sugestoes.add(new CapacidadesResponse.Sugestao("Descontos em folha",
                    "Valores por motivo, status e pendências de aprovação",
                    "Resuma os descontos deste mês por motivo e status e destaque o que ainda aguarda aprovação.",
                    "pi pi-percentage"));
        }
        if (security.possuiAutoridade("financeiro:pagamento:read") || security.possuiAutoridade("financeiro:compra:read")) {
            sugestoes.add(new CapacidadesResponse.Sugestao("Compras e pagamentos",
                    "Valores por status e concentração por fornecedor",
                    "Quanto temos em solicitações de compra e de pagamento por status neste mês e quais fornecedores concentram mais valor?",
                    "pi pi-wallet"));
        }
        if (security.possuiAutoridade("sst:inspecao:read") || security.possuiAutoridade("sst:dashboard:read")) {
            sugestoes.add(new CapacidadesResponse.Sugestao("Segurança do trabalho",
                    "Desvios por tipo e equipe em relatório PDF",
                    "Analise os desvios de SST dos últimos 3 meses por tipo e por equipe e gere um relatório PDF com recomendações.",
                    "pi pi-shield"));
        }
        if (security.possuiAutoridade("chamado:read") || security.possuiAutoridade("chamado:manage")
                || security.possuiAutoridade("chamado:dashboard:read")) {
            sugestoes.add(new CapacidadesResponse.Sugestao("Fila de chamados",
                    "Abertos por prioridade, antiguidade e tempo de resolução",
                    "Como está a fila de chamados: quantos abertos por prioridade, o tempo médio de resolução e os mais antigos?",
                    "pi pi-ticket"));
        }
        if (security.possuiAutoridade("obras:obra:read")) {
            sugestoes.add(new CapacidadesResponse.Sugestao("Obras",
                    "Status, atrasos e materiais pendentes",
                    "Quais obras estão atrasadas ou com materiais pendentes? Monte uma tabela com os principais pontos de atenção.",
                    "pi pi-building"));
        }
        if (python && security.possuiAutoridade("estoque:movimentacao:read")) {
            sugestoes.add(new CapacidadesResponse.Sugestao("Previsão de demanda",
                    "Projeção de consumo e cobertura do estoque",
                    "Projete o consumo dos 10 materiais mais usados para as próximas 4 semanas e calcule a cobertura do estoque atual.",
                    "pi pi-chart-line"));
        }
        sugestoes.add(new CapacidadesResponse.Sugestao("Conciliar planilha",
                "Compare um arquivo com os dados do Nexo",
                "Vou anexar uma planilha: concilie-a com os dados do Nexo e aponte as divergências linha a linha.",
                "pi pi-file-excel"));
        if (web) {
            sugestoes.add(new CapacidadesResponse.Sugestao("Normas e mercado",
                    "Pesquisa externa com fontes citadas",
                    "Pesquise os requisitos atuais da NR-10 para trabalhos em linha viva e resuma os pontos principais com as fontes.",
                    "pi pi-globe"));
        }
        return sugestoes.size() > 8 ? sugestoes.subList(0, 8) : sugestoes;
    }

    // ------------------------------------------------------------------------------------ mapeamento

    private ConversaResumoResponse resumo(ConversaAssistente conversa) {
        return new ConversaResumoResponse(conversa.getId(), conversa.getTitulo(), conversa.getUltimaAtividadeEm(),
                conversa.getDataCriacao(), runner.emAndamento(conversa.getId()), conversa.getOrigem(), conversa.getOrigemId());
    }

    private MensagemResponse mensagem(MensagemAssistente mensagem, Map<UUID, ArquivoAssistente> arquivosPorId) {
        List<Packet> pacotes = List.of();
        if (mensagem.getPacotesJson() != null && !mensagem.getPacotesJson().isBlank()) {
            try {
                pacotes = objectMapper.readValue(mensagem.getPacotesJson(), PACOTES);
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                pacotes = List.of();
            }
        }
        List<AcaoInterface> acoes = List.of();
        if (mensagem.getAcoesJson() != null && !mensagem.getAcoesJson().isBlank()) {
            try {
                acoes = objectMapper.readValue(mensagem.getAcoesJson(), ACOES);
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                acoes = List.of();
            }
        }
        List<ArquivoAssistenteResponse> anexos = List.of();
        if (mensagem.getPapel() == PapelMensagem.USUARIO && mensagem.getArquivosJson() != null) {
            try {
                anexos = objectMapper.readValue(mensagem.getArquivosJson(), IDS).stream()
                        .map(arquivosPorId::get).filter(Objects::nonNull).map(arquivos::toResponse).toList();
            } catch (JsonProcessingException exception) {
                anexos = List.of();
            }
        }
        MensagemResponse.Metricas metricas = mensagem.getPapel() == PapelMensagem.ASSISTENTE && mensagem.getDuracaoMs() != null
                ? new MensagemResponse.Metricas(mensagem.getModelo(), mensagem.getTokensEntrada(), mensagem.getTokensSaida(),
                mensagem.getDuracaoMs(), mensagem.getCiclos(), mensagem.getChamadasFerramenta())
                : null;
        return new MensagemResponse(mensagem.getId(), mensagem.getClientMessageId(), mensagem.getPapel(), mensagem.getStatus(),
                mensagem.getConteudo(), pacotes, acoes, anexos, mensagem.getDataCriacao(), metricas,
                mensagem.getPapel() == PapelMensagem.USUARIO ? persistencia.contextoDe(mensagem.getContextoJson()) : null);
    }

    private ConversaAssistente exigir(UUID conversaId, UUID usuarioId) {
        return conversas.findByIdAndUsuarioIdAndAtivoTrue(conversaId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa não encontrada"));
    }

    private void exigirSemTransacao(UUID conversaId, UUID usuarioId) {
        exigir(conversaId, usuarioId);
    }

    private static String termoBusca(String busca) {
        String limpo = busca.strip().toLowerCase(Locale.ROOT);
        if (limpo.length() > 100) limpo = limpo.substring(0, 100);
        return "%" + limpo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static UUID usuario() {
        UUID usuarioId = SecurityUtils.getUsuarioLogadoId();
        if (usuarioId == null) throw new IllegalStateException("Usuário não autenticado");
        return usuarioId;
    }

    public record ArquivoBaixado(String nome, String tipoConteudo, byte[] conteudo) {
    }
}
