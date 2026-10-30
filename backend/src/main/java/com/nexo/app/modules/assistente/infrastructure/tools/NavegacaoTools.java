package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.dto.AcaoInterface;
import com.nexo.app.modules.assistente.application.navigation.NavigationActionCatalog;
import com.nexo.app.modules.assistente.application.navigation.NavigationActionProposal;
import com.nexo.app.modules.assistente.application.navigation.NavigationActionResolver;
import com.nexo.app.modules.assistente.application.navigation.PainelFiltroResolver;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.*;

/**
 * Propostas de navegacao (somente botoes): o agente leva o usuario a tela certa do Nexo, sem executar escrita.
 * Reaproveita o catalogo/validador de navegacao existente.
 */
@Configuration(proxyBeanMethods = false)
public class NavegacaoTools {

    @Bean
    AgentTool listarAcoesNavegacaoTool(NavigationActionCatalog catalog, ResultFormatter formatter) {
        return new BaseTool("listar_acoes_navegacao", "Listar telas do Nexo",
                "Lista destinos de navegacao do Nexo autorizados ao usuario (abrir, cadastrar, lancar, acompanhar algo). "
                        + "Nao use para perguntas analiticas.",
                JsonSchemas.object().build(), ToolKind.NAVIGATION, Set.of(), Duration.ofSeconds(10)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return !catalog.listAuthorized(context.security()).isEmpty();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                return "Procurando a tela certa no Nexo";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                List<Map<String, Object>> acoes = catalog.listAuthorized(context.security()).stream().map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("chaveDestino", entry.key());
                    item.put("descricao", entry.modelDescription());
                    item.put("parametros", entry.parameters().stream().map(parameter -> Map.of(
                            "nome", parameter.name(), "tipo", parameter.type().name(),
                            "obrigatorio", parameter.required(), "valoresPermitidos", parameter.allowedValues())).toList());
                    return item;
                }).toList();
                return ToolResult.of(formatter.json(Map.of("versao", catalog.version(), "acoes", acoes)),
                        acoes.size() + " telas disponiveis");
            }
        };
    }

    @Bean
    AgentTool proporAcaoNavegacaoTool(NavigationActionResolver resolver, ObjectMapper objectMapper) {
        return new BaseTool("propor_acao_navegacao", "Oferecer atalho de tela",
                "Oferece ao usuario um botao para abrir uma tela do Nexo usando somente chaveDestino e parametros "
                        + "retornados por listar_acoes_navegacao. Nao executa nenhuma operacao; nunca envie URL.",
                JsonSchemas.object()
                        .required("chaveDestino", JsonSchemas.string(null))
                        .prop("parametros", JsonSchemas.object().build())
                        .build(),
                ToolKind.NAVIGATION, Set.of(), Duration.ofSeconds(10)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                return "Preparando atalho para o Nexo";
            }

            @Override
            @SuppressWarnings("unchecked")
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String chave = ToolArgs.requiredText(arguments, "chaveDestino");
                JsonNode parametrosNode = ToolArgs.object(arguments, "parametros");
                Map<String, Object> parametros = parametrosNode == null ? Map.of()
                        : objectMapper.convertValue(parametrosNode, Map.class);
                var resolution = resolver.resolve(new NavigationActionProposal("NAVIGATE", chave, parametros),
                        context.security());
                if (resolution.accepted()) {
                    AcaoInterface acao = resolution.action().orElseThrow();
                    if (context.run().navegacao().record(acao)) {
                        context.run().emit(Packet.of(context.placement(), Packet.Types.NAVIGATION, "acao", acao));
                    }
                    return ToolResult.of("{\"resultado\":\"ACEITA\",\"orientacao\":\"O botao foi oferecido ao usuario. "
                            + "Explique que nenhuma operacao foi executada.\"}", "Atalho oferecido");
                }
                String orientacao = resolution.rejection() == NavigationActionResolver.Rejection.MISSING_PARAMETERS
                        ? "Falta parametro obrigatorio. Peca o dado ou ofereca a listagem."
                        : "A navegacao nao esta disponivel. Nao revele chave, rota ou permissao.";
                throw new ToolException(orientacao);
            }
        };
    }
    @Bean
    AgentTool proporFiltroPainelTool(PainelFiltroResolver resolver, ObjectMapper objectMapper) {
        JsonNode filtroSchema = JsonSchemas.object()
                .required("campo", JsonSchemas.string("Campo declarado na tabela do painel"))
                .required("operador", JsonSchemas.enumString("Operador permitido para o campo",
                        List.of("eq", "ne", "lt", "lte", "gt", "gte", "contains")))
                .required("valor", JsonSchemas.anyValue("Valor no tipo da coluna (numero, texto ou booleano)"))
                .build();
        return new BaseTool("propor_filtro_painel", "Sugerir filtro na tabela",
                "Oferece ao usuario um botao que aplica filtros numa tabela do painel aberto (ex.: inspetores abaixo de "
                        + "80% da meta). Use somente o id de tabela, os campos e os operadores listados em 'Tabelas "
                        + "filtraveis' do contexto do painel. Nao aplica nada sozinho: o usuario decide.",
                JsonSchemas.object()
                        .required("alvo", JsonSchemas.string("Id da tabela filtravel do painel"))
                        .required("rotulo", JsonSchemas.string("Texto curto do botao, ex.: 'Mostrar inspetores abaixo de 80% da meta'"))
                        .required("filtros", JsonSchemas.array("De 1 a 5 filtros combinados (E)", filtroSchema))
                        .build(),
                ToolKind.NAVIGATION, Set.of(), Duration.ofSeconds(10)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                var contexto = context.contexto();
                return contexto != null && contexto.painel() != null && !contexto.painel().tabelas().isEmpty();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                return "Preparando filtro para a tabela";
            }

            @Override
            @SuppressWarnings("unchecked")
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String alvo = ToolArgs.requiredText(arguments, "alvo");
                String rotulo = ToolArgs.requiredText(arguments, "rotulo");
                JsonNode filtrosNode = arguments == null ? null : arguments.get("filtros");
                List<Map<String, Object>> filtros = filtrosNode == null || !filtrosNode.isArray() ? List.of()
                        : objectMapper.convertValue(filtrosNode, List.class);
                var contexto = context.run().contexto();
                var resolution = resolver.resolve(contexto == null ? null : contexto.painel(), alvo, rotulo, filtros);
                if (resolution.accepted()) {
                    AcaoInterface acao = resolution.action().orElseThrow();
                    if (context.run().navegacao().record(acao)) {
                        context.run().emit(Packet.of(context.placement(), Packet.Types.NAVIGATION, "acao", acao));
                    }
                    return ToolResult.of("{\"resultado\":\"ACEITA\",\"orientacao\":\"O botao de filtro foi oferecido. "
                            + "Explique que o filtro so sera aplicado se o usuario clicar.\"}", "Filtro sugerido");
                }
                throw new ToolException("Filtro recusado (" + resolution.rejection().name().toLowerCase(java.util.Locale.ROOT)
                        + "). Use apenas a tabela, os campos e os operadores declarados no contexto do painel.");
            }
        };
    }
}
