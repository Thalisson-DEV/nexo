package com.nexo.app.modules.estoque.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ces.estoque.solicitacoes-tecnicas.enabled=true")
class SolicitacaoTecnicaOpenApiContractTest {

    private static final Map<OperacaoApi, String> OPERACOES_TECNICAS = operacoesTecnicas();

    private static final Set<String> RESPOSTAS_DE_PROBLEMA = Set.of("400", "403", "404", "409", "422");

    @LocalServerPort
    private int porta;

    @Autowired
    private ObjectMapper objectMapper;


    @Test
    void publicaContratoTecnicoCompletoSemReferenciasQuebradas() throws Exception {
        HttpResponse<String> resposta = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/v3/api-docs"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode openApi = objectMapper.readTree(resposta.body());
        JsonNode paths = openApi.path("paths");

        OPERACOES_TECNICAS.forEach((contrato, autoridade) -> {
            JsonNode operacao = paths.path(contrato.path()).path(contrato.metodo());
            assertThat(operacao.isMissingNode())
                    .as("operacao OpenAPI %s %s", contrato.metodo(), contrato.path())
                    .isFalse();
            assertThat(operacao.path("responses").has(contrato.sucesso()))
                    .as("status de sucesso de %s %s", contrato.metodo(), contrato.path())
                    .isTrue();
            assertThat(operacao.path("description").asText()).contains(autoridade);
            assertThat(operacao.path("security").toString()).contains("bearerAuth");
            validarProblemas(operacao, contrato);
        });

        for (OperacaoApi contrato : OPERACOES_TECNICAS.keySet().stream()
                .filter(item -> item.idempotenciaObrigatoria() != null).toList()) {
            validarParametro(paths.path(contrato.path()).path(contrato.metodo()),
                    "Idempotency-Key", "header", contrato.idempotenciaObrigatoria());
        }

        validarParametros(paths.path("/v1/solicitacoes-material/{idSolicitacao}/historico").path("get"),
                "inicio", "fim", "tipoTecnico", "tipoObra", "ocorrencia", "tipoEvento",
                "idAutor", "origem", "idItem", "page", "size", "sort");
        validarParametros(paths.path("/v1/solicitacoes-material").path("get"),
                "contexto", "tipoTecnico", "tipoObra", "idObra", "ocorrencia",
                "idEquipe", "idAlmoxarifado", "numeroReserva", "idAgenteReserva", "status",
                "statuses", "statusSeparacao", "etapa", "dataInicio", "dataFim", "referencia", "numSerie",
                "buscaGlobal", "page", "size", "sort");
        validarParametros(paths.path("/v1/movimentacoes-estoque").path("get"),
                "idEstoque", "idBaseOrigem", "idBaseDestino", "idMaterial", "condicao", "tipo",
                "dataInicio", "dataFim", "referencia", "idUsuarioOperador", "numSerie", "contexto",
                "idSolicitacao", "tipoTecnico", "tipoObra", "idObra", "ocorrencia", "idEquipe",
                "idReserva", "numeroReserva", "idAgenteReserva", "statusSolicitacao", "statusSeparacao",
                "buscaGlobal", "page", "size", "sort");

        JsonNode schemas = openApi.path("components").path("schemas");
        assertThat(schemas.has("CriarSolicitacaoTecnicaRequest")).isTrue();
        assertThat(schemas.has("PlanejarSolicitacaoTecnicaRequest")).isTrue();
        assertThat(schemas.has("ProblemaHttpResponse")).isTrue();
        JsonNode problema = schemas.path("ProblemaHttpResponse").path("properties");
        assertThat(problema.has("erros")).isTrue();
        assertThat(problema.has("recurso")).isTrue();
        assertThat(problema.has("versaoEsperada")).isTrue();
        assertThat(problema.has("versaoAtual")).isTrue();
        assertThat(problema.path("erros").path("additionalProperties").path("type").asText())
                .isEqualTo("string");
        String referenciaErroCampo = problema.path("fieldErrors").path("additionalProperties")
                .path("$ref").asText();
        assertThat(referenciaErroCampo).isNotBlank();
        JsonNode propriedadesErroCampo = openApi.at(referenciaErroCampo.substring(1)).path("properties");
        assertThat(propriedadesErroCampo.has("code")).isTrue();
        assertThat(propriedadesErroCampo.has("message")).isTrue();
        validarCamposSchema(schemas, "MovimentacaoEstoqueResponse",
                "contexto", "idSolicitacao", "tipoTecnico", "tipoObra", "ocorrencia",
                "idReserva", "idOperacao", "idItemOperacao", "idObra", "idEquipe");
        validarCamposSchema(schemas, "MovimentacaoOperacaoSummaryResponse",
                "contexto", "idSolicitacao", "tipoTecnico", "tipoObra", "ocorrencia",
                "idReserva", "idOperacao", "idObra", "idEquipe");
        validarCamposSchema(schemas, "MovimentacaoOperacaoResponse",
                "contexto", "idSolicitacao", "tipoTecnico", "tipoObra", "ocorrencia",
                "idReserva", "idOperacao", "idObra", "idEquipe", "itens");
        validarCamposSchema(schemas, "MovimentacaoItemResponse",
                "id", "material", "condicao", "quantidade", "numSerie", "idItemOperacao", "idItemSolicitacao");
        validarCamposSchema(schemas, "CriacaoSolicitacaoTecnicaResponse",
                "solicitacoesCriadas");
        validarCamposSchema(schemas, "SolicitacaoSummaryResponse", "contexto", "statusSeparacao", "etapaOperacional");
        assertThat(schemas.path("SolicitacaoSummaryResponse").path("properties")
                .path("etapaOperacional").path("enum").toString())
                .contains("PENDENTE_ENTREGA", "CONCLUIDA");
        JsonNode etapaParametro = parametro(paths.path("/v1/solicitacoes-material").path("get"), "etapa");
        assertThat(etapaParametro.path("schema").path("enum").toString())
                .contains("AGUARDANDO_PLANEJAMENTO", "EM_SEPARACAO", "PENDENTE_ENTREGA");
        validarCamposSchema(schemas, "PlanejamentoSolicitacaoTecnicaResponse",
                "solicitacaoAtualizada");
        assertThat(paths.path("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/planejamento")
                .path("put").path("responses").path("200").path("content")
                .path("application/json").path("schema").path("$ref").asText())
                .endsWith("/PlanejamentoSolicitacaoTecnicaResponse");
        assertThat(resposta.body())
                .contains("Idempotency-Key")
                .contains("fieldErrors")
                .contains("assinaturaAlmox")
                .contains("quantidadeEntregue")
                .contains("ADICAO_COMUM")
                .contains("CORRETIVA")
                .contains("tipoMovimentacao");

        List<String> referencias = new ArrayList<>();
        coletarReferencias(openApi, referencias);
        assertThat(referencias).allSatisfy(referencia -> {
            if (referencia.startsWith("#/")) {
                assertThat(openApi.at(referencia.substring(1)).isMissingNode())
                        .as("referencia OpenAPI %s", referencia)
                        .isFalse();
            }
        });
    }

    private static Map<OperacaoApi, String> operacoesTecnicas() {
        Map<OperacaoApi, String> operacoes = new LinkedHashMap<>();
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/importacoes/modelo", "get", "200", null),
                "estoque:solicitacao:import");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/importacoes/validacao", "post", "200", null),
                "estoque:solicitacao:import");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/importacoes/confirmacao", "post", "200", true),
                "estoque:solicitacao:import");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/tecnicas", "post", "201", false),
                "estoque:solicitacao:create");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/planejamento", "put", "200", null),
                "estoque:solicitacao:plan");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/reserva", "put", "200", true),
                "estoque:solicitacao:reserve");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/separacao/iniciar", "post", "200", true),
                "estoque:solicitacao:separate");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/separacao/concluir", "post", "200", true),
                "estoque:solicitacao:separate");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/entregas", "post", "200", true),
                "estoque:solicitacao:deliver");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/devolucoes", "post", "200", true),
                "estoque:solicitacao:return");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/estornos", "post", "200", true),
                "estoque:solicitacao:reverse");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/tecnicas/{idSolicitacao}/encerramento", "post", "204", true),
                "estoque:solicitacao:close");
        operacoes.put(new OperacaoApi("/v1/solicitacoes-material/{idSolicitacao}/historico", "get", "200", null),
                "estoque:solicitacao:audit:read");
        return Map.copyOf(operacoes);
    }

    private void validarProblemas(JsonNode operacao, OperacaoApi contrato) {
        JsonNode respostas = operacao.path("responses");
        assertThat(RESPOSTAS_DE_PROBLEMA).allSatisfy(status -> {
            assertThat(respostas.has(status))
                    .as("status %s de %s %s", status, contrato.metodo(), contrato.path())
                    .isTrue();
            JsonNode conteudo = respostas.path(status).path("content")
                    .path("application/problem+json").path("schema");
            assertThat(conteudo.path("$ref").asText())
                    .isEqualTo("#/components/schemas/ProblemaHttpResponse");
        });
    }

    private void validarParametros(JsonNode operacao, String... nomes) {
        for (String nome : nomes) {
            validarParametro(operacao, nome, "query", null);
        }
    }

    private void validarParametro(JsonNode operacao, String nome, String local, Boolean obrigatorio) {
        JsonNode parametro = parametro(operacao, nome, local);
        assertThat(parametro)
                .as("parametro %s em %s", nome, local)
                .isNotNull();
        if (obrigatorio != null) {
            assertThat(parametro.path("required").asBoolean()).isEqualTo(obrigatorio);
        }
    }

    private JsonNode parametro(JsonNode operacao, String nome) {
        return parametro(operacao, nome, "query");
    }

    private JsonNode parametro(JsonNode operacao, String nome, String local) {
        JsonNode parametro = null;
        for (JsonNode candidato : operacao.path("parameters")) {
            if (nome.equals(candidato.path("name").asText())
                    && local.equals(candidato.path("in").asText())) {
                parametro = candidato;
                break;
            }
        }
        return parametro;
    }

    private void validarCamposSchema(JsonNode schemas, String nomeSchema, String... campos) {
        JsonNode propriedades = schemas.path(nomeSchema).path("properties");
        assertThat(propriedades.isMissingNode()).as("schema OpenAPI %s", nomeSchema).isFalse();
        assertThat(List.of(campos)).allSatisfy(campo ->
                assertThat(propriedades.has(campo))
                        .as("campo %s em %s", campo, nomeSchema)
                        .isTrue());
    }

    private void coletarReferencias(JsonNode node, List<String> referencias) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> campos = node.fields();
            while (campos.hasNext()) {
                Map.Entry<String, JsonNode> campo = campos.next();
                if (campo.getKey().equals("$ref") && campo.getValue().isTextual()) {
                    referencias.add(campo.getValue().asText());
                } else {
                    coletarReferencias(campo.getValue(), referencias);
                }
            }
        } else if (node.isArray()) {
            node.forEach(item -> coletarReferencias(item, referencias));
        }
    }

    private record OperacaoApi(
            String path,
            String metodo,
            String sucesso,
            Boolean idempotenciaObrigatoria
    ) {
    }
}
