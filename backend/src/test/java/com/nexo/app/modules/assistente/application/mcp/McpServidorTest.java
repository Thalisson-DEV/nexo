package com.nexo.app.modules.assistente.application.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.agent.AgentTool;
import com.nexo.app.modules.assistente.application.agent.RiskLevel;
import com.nexo.app.modules.assistente.application.agent.ToolKind;
import com.nexo.app.modules.assistente.application.conexoes.ConexaoIaService;
import com.nexo.app.modules.assistente.application.semantic.SemanticCatalog;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.infrastructure.security.AcessoExterno;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Protocolo JSON-RPC do servidor MCP: negociacao, lista filtrada, chamadas, erros e limite por conexao. */
class McpServidorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final McpCatalogo catalogo = mock(McpCatalogo.class);
    private final McpExecutor executor = mock(McpExecutor.class);
    private final ConexaoIaService conexoes = mock(ConexaoIaService.class);
    private final AgentTool leitura = ferramenta("consultar_estoque", ToolKind.DATA, RiskLevel.READ_ONLY);
    private final AgentTool escrita = ferramenta("cadastrar_motivo_desconto", ToolKind.WRITE, RiskLevel.WRITE_LOW);
    private final AgentTool apaga = ferramenta("excluir_algo", ToolKind.WRITE, RiskLevel.WRITE_HIGH);

    private McpServidor servidor(int limite) {
        return new McpServidor(catalogo, executor, conexoes, mock(SemanticCatalog.class), json, limite);
    }

    private static SessaoMcp sessao(boolean escrita) {
        UUID usuario = UUID.randomUUID();
        return new SessaoMcp(new AcessoExterno(usuario, UUID.randomUUID(), escrita),
                new SemanticQueryContext(usuario, Set.of("assistente:conexao:use")), "Fulano");
    }

    private ObjectNode chamar(McpServidor servidor, SessaoMcp sessao, String corpo) throws Exception {
        return servidor.processar(json.readTree(corpo), sessao).orElseThrow();
    }

    @Test
    void mensagemQueNaoEJsonRpc2ERecusada() throws Exception {
        ObjectNode resposta = chamar(servidor(10), sessao(false), "{\"id\":1,\"method\":\"ping\"}");
        assertThat(resposta.path("error").path("code").asInt()).isEqualTo(-32600);
    }

    @Test
    void notificacaoNaoTemResposta() throws Exception {
        assertThat(servidor(10).processar(json.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"),
                sessao(false))).isEmpty();
    }

    @Test
    void initializeNegociaAVersaoERegistraOAplicativo() throws Exception {
        SessaoMcp sessao = sessao(false);
        ObjectNode conhecida = chamar(servidor(10), sessao, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
                 "clientInfo":{"name":"claude-ai","version":"0.1"}}}""");
        ObjectNode futura = chamar(servidor(10), sessao, """
                {"jsonrpc":"2.0","id":2,"method":"initialize","params":{"protocolVersion":"2099-01-01"}}""");

        assertThat(conhecida.path("result").path("protocolVersion").asText()).isEqualTo("2025-06-18");
        assertThat(conhecida.path("result").path("serverInfo").path("name").asText()).isEqualTo("ces");
        assertThat(conhecida.path("result").path("instructions").asText()).contains("Fulano", "somente de consulta");
        assertThat(futura.path("result").path("protocolVersion").asText()).isEqualTo(McpServidor.VERSOES.getFirst());
        verify(conexoes).registrarAplicativo(sessao.acesso().conexaoId(), "claude-ai", "0.1");
    }

    @Test
    void listaTrazAsAnotacoesDeLeituraEDeEscrita() throws Exception {
        SessaoMcp sessao = sessao(true);
        when(catalogo.disponiveis(sessao.seguranca(), true)).thenReturn(List.of(leitura, escrita, apaga));

        JsonNode ferramentas = chamar(servidor(10), sessao, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")
                .path("result").path("tools");

        assertThat(ferramentas).hasSize(3);
        assertThat(ferramentas.get(0).path("annotations").path("readOnlyHint").asBoolean()).isTrue();
        assertThat(ferramentas.get(1).path("annotations").path("readOnlyHint").asBoolean()).isFalse();
        assertThat(ferramentas.get(1).path("annotations").path("destructiveHint").asBoolean()).isFalse();
        assertThat(ferramentas.get(2).path("annotations").path("destructiveHint").asBoolean()).isTrue();
        assertThat(ferramentas.get(0).path("inputSchema").path("type").asText()).isEqualTo("object");
    }

    @Test
    void chamadaExecutaEDevolveOTexto() throws Exception {
        SessaoMcp sessao = sessao(false);
        when(catalogo.buscar("consultar_estoque", sessao.seguranca(), false)).thenReturn(Optional.of(leitura));
        when(executor.executar(eq(leitura), any(), eq(sessao))).thenReturn(new McpExecutor.Resposta("{\"total\":3}", false));

        JsonNode resultado = chamar(servidor(10), sessao, """
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"consultar_estoque"}}""").path("result");

        assertThat(resultado.path("isError").asBoolean()).isFalse();
        assertThat(resultado.path("content").get(0).path("text").asText()).isEqualTo("{\"total\":3}");
    }

    @Test
    void escritaEmConexaoSoDeLeituraExplicaSemExecutar() throws Exception {
        SessaoMcp sessao = sessao(false);
        when(catalogo.buscar("cadastrar_motivo_desconto", sessao.seguranca(), false)).thenReturn(Optional.empty());
        when(catalogo.buscar("cadastrar_motivo_desconto", sessao.seguranca(), true)).thenReturn(Optional.of(escrita));
        when(catalogo.existe("cadastrar_motivo_desconto")).thenReturn(true);

        JsonNode resultado = chamar(servidor(10), sessao, """
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"cadastrar_motivo_desconto","arguments":{}}}""")
                .path("result");

        assertThat(resultado.path("isError").asBoolean()).isTrue();
        assertThat(resultado.path("content").get(0).path("text").asText()).contains("somente de consulta");
        verify(executor, never()).executar(any(), any(), any());
    }

    @Test
    void ferramentaSemPermissaoDoPerfilNaoExecuta() throws Exception {
        SessaoMcp sessao = sessao(true);
        when(catalogo.buscar(eq("consultar_pagamentos"), any(), anyBoolean())).thenReturn(Optional.empty());
        when(catalogo.existe("consultar_pagamentos")).thenReturn(true);

        JsonNode resultado = chamar(servidor(10), sessao, """
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"consultar_pagamentos"}}""").path("result");

        assertThat(resultado.path("content").get(0).path("text").asText()).contains("nao tem permissao");
        verify(executor, never()).executar(any(), any(), any());
    }

    @Test
    void ferramentaDesconhecidaEArgumentosInvalidosSaoErroDeParametro() throws Exception {
        SessaoMcp sessao = sessao(false);
        ObjectNode desconhecida = chamar(servidor(10), sessao, """
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"nao_existe"}}""");
        ObjectNode argumentos = chamar(servidor(10), sessao, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"consultar_estoque","arguments":[1]}}""");
        ObjectNode metodo = chamar(servidor(10), sessao, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"sampling/createMessage\"}");

        assertThat(desconhecida.path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(argumentos.path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(metodo.path("error").path("code").asInt()).isEqualTo(-32601);
        assertThat(metodo.path("id").asInt()).isEqualTo(3);
    }

    @Test
    void limiteDeChamadasPorMinutoValePorConexao() throws Exception {
        McpServidor servidor = servidor(2);
        SessaoMcp sessao = sessao(false);
        SessaoMcp outra = sessao(false);
        when(catalogo.buscar(eq("consultar_estoque"), any(), anyBoolean())).thenReturn(Optional.of(leitura));
        when(executor.executar(any(), any(), any())).thenReturn(new McpExecutor.Resposta("ok", false));
        String corpo = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"consultar_estoque\"}}";

        chamar(servidor, sessao, corpo);
        chamar(servidor, sessao, corpo);
        JsonNode terceira = chamar(servidor, sessao, corpo).path("result");
        JsonNode deOutraConexao = chamar(servidor, outra, corpo).path("result");

        assertThat(terceira.path("isError").asBoolean()).isTrue();
        assertThat(terceira.path("content").get(0).path("text").asText()).contains("Limite de 2 chamadas");
        assertThat(deOutraConexao.path("isError").asBoolean()).isFalse();
    }

    @Test
    void promptDeCadastroSoApareceComEscrita() throws Exception {
        String corpo = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"prompts/list\"}";
        JsonNode soLeitura = chamar(servidor(10), sessao(false), corpo).path("result").path("prompts");
        JsonNode comEscrita = chamar(servidor(10), sessao(true), corpo).path("result").path("prompts");

        assertThat(comEscrita.size()).isEqualTo(soLeitura.size() + 1);
        assertThat(comEscrita.toString()).contains("cadastrar_no_ces");
        assertThat(soLeitura.toString()).doesNotContain("cadastrar_no_ces");
    }

    private AgentTool ferramenta(String nome, ToolKind tipo, RiskLevel risco) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(nome);
        when(tool.displayName()).thenReturn(nome);
        when(tool.description()).thenReturn("Descricao de " + nome);
        when(tool.kind()).thenReturn(tipo);
        when(tool.riskLevel()).thenReturn(risco);
        when(tool.inputSchema()).thenReturn(json.createObjectNode().put("type", "object"));
        return tool;
    }
}
