package com.nexo.app.modules.assistente.application.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.agent.AgentTool;
import com.nexo.app.modules.assistente.application.agent.RiskLevel;
import com.nexo.app.modules.assistente.application.conexoes.ConexaoIaService;
import com.nexo.app.modules.assistente.application.semantic.SemanticCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor MCP do Nexo (JSON-RPC 2.0 sobre Streamable HTTP, sem estado entre requisicoes). Expoe as
 * ferramentas do Copiloto que o usuario da conexao pode usar, um recurso com as fontes de dados e
 * alguns prompts prontos.
 */
@Component
public class McpServidor {

    public static final List<String> VERSOES = List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");
    static final String RECURSO_FONTES = "ces://fontes-de-dados";

    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;
    static final int LIMITE_EXCEDIDO = -32000;

    private final McpCatalogo catalogo;
    private final McpExecutor executor;
    private final ConexaoIaService conexoes;
    private final SemanticCatalog fontes;
    private final ObjectMapper objectMapper;
    private final int chamadasPorMinuto;
    private final Map<UUID, Deque<Instant>> janelas = new ConcurrentHashMap<>();

    public McpServidor(McpCatalogo catalogo, McpExecutor executor, ConexaoIaService conexoes, SemanticCatalog fontes,
            ObjectMapper objectMapper, @Value("${ces.conexoes-ia.chamadas-por-minuto:120}") int chamadasPorMinuto) {
        this.catalogo = catalogo;
        this.executor = executor;
        this.conexoes = conexoes;
        this.fontes = fontes;
        this.objectMapper = objectMapper;
        this.chamadasPorMinuto = chamadasPorMinuto;
    }

    /** Trata uma mensagem JSON-RPC. Vazio para notificacoes e respostas (nao ha o que devolver). */
    public Optional<ObjectNode> processar(JsonNode mensagem, SessaoMcp sessao) {
        if (mensagem == null || !mensagem.isObject() || !"2.0".equals(mensagem.path("jsonrpc").asText())) {
            return Optional.of(erro(JsonNodeFactory.instance.nullNode(), INVALID_REQUEST, "Mensagem JSON-RPC 2.0 invalida."));
        }
        JsonNode id = mensagem.get("id");
        String metodo = mensagem.path("method").asText(null);
        if (metodo == null) return Optional.empty();
        if (id == null || id.isNull()) {
            return Optional.empty();
        }
        JsonNode params = mensagem.path("params");
        try {
            ObjectNode resultado = switch (metodo) {
                case "initialize" -> inicializar(params, sessao);
                case "ping", "logging/setLevel" -> objectMapper.createObjectNode();
                case "tools/list" -> listarFerramentas(sessao);
                case "tools/call" -> chamarFerramenta(params, sessao);
                case "resources/list" -> listarRecursos();
                case "resources/templates/list" -> objectMapper.createObjectNode().set("resourceTemplates", objectMapper.createArrayNode());
                case "resources/read" -> lerRecurso(params, sessao);
                case "prompts/list" -> McpPrompts.listar(objectMapper, sessao.permiteEscrita());
                case "prompts/get" -> McpPrompts.obter(objectMapper, params);
                case "completion/complete" -> completarVazio();
                default -> throw new ErroMcp(METHOD_NOT_FOUND, "Metodo nao suportado: " + metodo);
            };
            ObjectNode resposta = objectMapper.createObjectNode();
            resposta.put("jsonrpc", "2.0");
            resposta.set("id", id);
            resposta.set("result", resultado);
            return Optional.of(resposta);
        } catch (ErroMcp e) {
            return Optional.of(erro(id, e.codigo, e.getMessage()));
        }
    }

    public ObjectNode erro(JsonNode id, int codigo, String mensagem) {
        ObjectNode resposta = objectMapper.createObjectNode();
        resposta.put("jsonrpc", "2.0");
        resposta.set("id", id == null ? JsonNodeFactory.instance.nullNode() : id);
        ObjectNode erro = resposta.putObject("error");
        erro.put("code", codigo);
        erro.put("message", mensagem);
        return resposta;
    }

    private ObjectNode inicializar(JsonNode params, SessaoMcp sessao) {
        String pedida = params.path("protocolVersion").asText("");
        JsonNode cliente = params.path("clientInfo");
        conexoes.registrarAplicativo(sessao.acesso().conexaoId(), cliente.path("title").asText(cliente.path("name").asText(null)),
                cliente.path("version").asText(null));
        ObjectNode resultado = objectMapper.createObjectNode();
        resultado.put("protocolVersion", VERSOES.contains(pedida) ? pedida : VERSOES.getFirst());
        ObjectNode capacidades = resultado.putObject("capabilities");
        capacidades.putObject("tools").put("listChanged", false);
        capacidades.putObject("resources").put("listChanged", false).put("subscribe", false);
        capacidades.putObject("prompts").put("listChanged", false);
        capacidades.putObject("logging");
        ObjectNode servidor = resultado.putObject("serverInfo");
        servidor.put("name", "ces");
        servidor.put("title", "Nexo — Gestão Operacional");
        servidor.put("version", "1.0.0");
        resultado.put("instructions", instrucoes(sessao));
        return resultado;
    }

    private String instrucoes(SessaoMcp sessao) {
        String quem = sessao.nomeUsuario() == null ? "o usuario" : sessao.nomeUsuario();
        return """
                Voce esta conectado ao Nexo (Gestao Operacional), o sistema de gestao da empresa (servicos para a \
                Concessionária): estoque e solicitacoes de material, execucao e faturamento, equipes e colaboradores, \
                descontos em folha, SST (inspecoes, desvios, eventos), frota, chamados, financeiro e obras.
                Voce age em nome de %s e enxerga somente o que essa pessoa enxerga no Nexo.
                - Para perguntas sobre dados, prefira as consultas de dominio e os indicadores; para algo especifico, \
                use listar_fontes_dados e descrever_fonte_dados antes de consultar_fonte ou consultar_sql.
                - Consultas devolvem um codigo de resultado (r1, r2...). As ferramentas de analise reutilizam esse codigo \
                sem repetir a consulta. Nunca invente numeros: use so o que as ferramentas devolverem.
                - %s
                - Responda em portugues do Brasil, em linguagem de negocio, sem expor nomes internos de tabelas ou colunas.
                """.formatted(quem, sessao.permiteEscrita()
                ? "Esta conexao pode cadastrar e alterar registros. Antes de gravar, mostre a pessoa o que sera feito e "
                        + "peca confirmacao; depois informe o que foi gravado (com o identificador devolvido)."
                : "Esta conexao e somente de consulta: nao ha ferramentas de cadastro. Se a pessoa pedir para gravar algo, "
                        + "explique que ela pode permitir cadastros reconectando o Nexo ou fazer pela tela do Nexo.");
    }

    private ObjectNode listarFerramentas(SessaoMcp sessao) {
        ArrayNode lista = objectMapper.createArrayNode();
        for (AgentTool tool : catalogo.disponiveis(sessao.seguranca(), sessao.permiteEscrita())) {
            lista.add(definicao(tool));
        }
        ObjectNode resultado = objectMapper.createObjectNode();
        resultado.set("tools", lista);
        return resultado;
    }

    ObjectNode definicao(AgentTool tool) {
        boolean escreve = McpCatalogo.escreve(tool);
        ObjectNode definicao = objectMapper.createObjectNode();
        definicao.put("name", tool.name());
        definicao.put("title", tool.displayName());
        definicao.put("description", tool.description());
        JsonNode schema = tool.inputSchema();
        definicao.set("inputSchema", schema != null && schema.isObject() ? schema
                : objectMapper.createObjectNode().put("type", "object"));
        ObjectNode anotacoes = definicao.putObject("annotations");
        anotacoes.put("title", tool.displayName());
        anotacoes.put("readOnlyHint", !escreve);
        anotacoes.put("destructiveHint", escreve && tool.riskLevel() == RiskLevel.WRITE_HIGH);
        anotacoes.put("idempotentHint", !escreve);
        anotacoes.put("openWorldHint", false);
        return definicao;
    }

    private ObjectNode chamarFerramenta(JsonNode params, SessaoMcp sessao) {
        String nome = params.path("name").asText(null);
        if (nome == null || nome.isBlank()) throw new ErroMcp(INVALID_PARAMS, "Informe o nome da ferramenta.");
        JsonNode argumentos = params.path("arguments");
        if (argumentos.isMissingNode() || argumentos.isNull()) argumentos = objectMapper.createObjectNode();
        if (!argumentos.isObject()) throw new ErroMcp(INVALID_PARAMS, "arguments deve ser um objeto.");
        Optional<AgentTool> tool = catalogo.buscar(nome, sessao.seguranca(), sessao.permiteEscrita());
        if (tool.isEmpty()) {
            if (!catalogo.existe(nome)) throw new ErroMcp(INVALID_PARAMS, "Ferramenta desconhecida: " + nome);
            return conteudo(sessao.permiteEscrita() || !catalogo.buscar(nome, sessao.seguranca(), true).isPresent()
                    ? "O perfil desta pessoa no Nexo nao tem permissao para esta ferramenta."
                    : "Esta conexao e somente de consulta. Para cadastrar ou alterar, a pessoa precisa reconectar o Nexo "
                            + "permitindo cadastros.", true);
        }
        if (!dentroDoLimite(sessao.acesso().conexaoId())) {
            return conteudo("Limite de " + chamadasPorMinuto + " chamadas por minuto desta conexao atingido. Aguarde um "
                    + "minuto e tente de novo.", true);
        }
        McpExecutor.Resposta resposta = executor.executar(tool.get(), argumentos, sessao);
        return conteudo(resposta.texto(), resposta.erro());
    }

    private ObjectNode conteudo(String texto, boolean erro) {
        ObjectNode resultado = objectMapper.createObjectNode();
        resultado.putArray("content").addObject().put("type", "text").put("text", texto);
        resultado.put("isError", erro);
        return resultado;
    }

    private ObjectNode listarRecursos() {
        ObjectNode resultado = objectMapper.createObjectNode();
        ObjectNode recurso = resultado.putArray("resources").addObject();
        recurso.put("uri", RECURSO_FONTES);
        recurso.put("name", "fontes-de-dados");
        recurso.put("title", "Fontes de dados do Nexo");
        recurso.put("description", "Fontes que esta pessoa pode consultar, com o que cada uma contem e suas colunas.");
        recurso.put("mimeType", "text/markdown");
        return resultado;
    }

    private ObjectNode lerRecurso(JsonNode params, SessaoMcp sessao) {
        String uri = params.path("uri").asText("");
        if (!RECURSO_FONTES.equals(uri)) throw new ErroMcp(INVALID_PARAMS, "Recurso desconhecido: " + uri);
        StringBuilder texto = new StringBuilder("# Fontes de dados do Nexo\n\n");
        for (SemanticCatalog.Fonte fonte : fontes.autorizadas(sessao.seguranca())) {
            texto.append("## ").append(fonte.nome()).append(" (`").append(fonte.id()).append("`)\n");
            if (fonte.descricao() != null) texto.append(fonte.descricao().strip()).append("\n");
            if (fonte.colunaData() != null) texto.append("\nData de referencia: `").append(fonte.colunaData()).append("`\n");
            if (fonte.colunas() != null && !fonte.colunas().isEmpty()) {
                texto.append("\nColunas:\n");
                fonte.colunas().forEach((nome, coluna) -> texto.append("- `").append(nome).append("`")
                        .append(coluna.descricao() == null ? "" : ": " + coluna.descricao()).append("\n"));
            }
            texto.append("\n");
        }
        ObjectNode resultado = objectMapper.createObjectNode();
        resultado.putArray("contents").addObject()
                .put("uri", RECURSO_FONTES)
                .put("mimeType", "text/markdown")
                .put("text", texto.toString());
        return resultado;
    }

    private ObjectNode completarVazio() {
        ObjectNode resultado = objectMapper.createObjectNode();
        ObjectNode completion = resultado.putObject("completion");
        completion.putArray("values");
        completion.put("hasMore", false);
        return resultado;
    }

    private boolean dentroDoLimite(UUID conexaoId) {
        Deque<Instant> janela = janelas.computeIfAbsent(conexaoId, ignored -> new ArrayDeque<>());
        Instant agora = Instant.now();
        synchronized (janela) {
            while (!janela.isEmpty() && janela.peekFirst().isBefore(agora.minus(Duration.ofMinutes(1)))) janela.pollFirst();
            if (janela.size() >= chamadasPorMinuto) return false;
            janela.addLast(agora);
            return true;
        }
    }

    /** Erro de protocolo (JSON-RPC), diferente de uma ferramenta que executou e falhou. */
    static final class ErroMcp extends RuntimeException {
        final int codigo;

        ErroMcp(int codigo, String mensagem) {
            super(mensagem);
            this.codigo = codigo;
        }
    }
}
