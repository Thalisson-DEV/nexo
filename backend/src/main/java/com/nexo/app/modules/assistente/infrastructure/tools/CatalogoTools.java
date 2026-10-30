package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.SemanticDatasetRegistry;
import com.nexo.app.modules.assistente.application.SemanticResourceCountRegistry;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import com.nexo.app.modules.assistente.application.semantic.SemanticCatalog;
import com.nexo.app.shared.application.analytics.SemanticDatasetDescriptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.*;

/**
 * Ferramentas de descoberta da camada semantica: o agente aprende o Nexo sob demanda (fontes, colunas,
 * enums, relacionamentos e regras de negocio) em vez de receber o banco inteiro no prompt.
 */
@Configuration(proxyBeanMethods = false)
public class CatalogoTools {

    @Bean
    AgentTool listarFontesDadosTool(SemanticCatalog catalog, SemanticDatasetRegistry datasets,
            SemanticResourceCountRegistry recursos, ResultFormatter formatter) {
        return new BaseTool("listar_fontes_dados", "Explorar fontes de dados",
                "Lista as fontes de dados do Nexo que o usuario pode consultar, agrupadas por dominio (almoxarifado, obras, "
                        + "RH, execucao, contratos, descontos, financeiro, SST, chamados, comunicacao, acessos, "
                        + "sincronizacao), mais datasets semanticos oficiais e cadastros contaveis. Use quando nao souber "
                        + "qual fonte responde a pergunta. Filtre por dominio ou termo.",
                JsonSchemas.object()
                        .prop("dominio", JsonSchemas.string("Id do dominio (opcional), ex.: financeiro, almoxarifado, sst"))
                        .prop("busca", JsonSchemas.string("Termo para filtrar fontes (opcional), ex.: desconto, reserva, compra"))
                        .build(),
                ToolKind.CATALOG, Set.of(), Duration.ofSeconds(20)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                return "Explorando as fontes de dados do Nexo";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String dominio = ToolArgs.text(arguments, "dominio");
                String busca = ToolArgs.text(arguments, "busca");
                List<SemanticCatalog.Fonte> fontes = busca == null
                        ? catalog.autorizadas(context.security())
                        : catalog.buscarFontes(busca, context.security());
                Map<String, List<Map<String, Object>>> porDominio = new LinkedHashMap<>();
                for (SemanticCatalog.Fonte fonte : fontes) {
                    if (dominio != null && !dominio.equalsIgnoreCase(fonte.dominio())) continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("fonte", fonte.id());
                    item.put("nome", fonte.nome());
                    item.put("descricao", fonte.descricao());
                    if (!"todos".equals(fonte.escopo())) item.put("escopo", fonte.escopo());
                    porDominio.computeIfAbsent(fonte.dominio(), ignored -> new ArrayList<>()).add(item);
                }
                Map<String, Object> resposta = new LinkedHashMap<>();
                List<Map<String, Object>> dominios = new ArrayList<>();
                for (SemanticCatalog.Dominio item : catalog.dominios()) {
                    List<Map<String, Object>> doDominio = porDominio.get(item.id());
                    if (doDominio == null) continue;
                    dominios.add(Map.of("dominio", item.id(), "nome", item.nome(), "fontes", doDominio));
                }
                resposta.put("fontes_sql", dominios);
                resposta.put("sql_disponivel", catalog.schemaDisponivel());
                List<Map<String, Object>> oficiais = datasets.listarAutorizados(context.security()).stream()
                        .map(descriptor -> Map.<String, Object>of("dataset", descriptor.id(), "nome", descriptor.nome(),
                                "descricao", descriptor.descricao()))
                        .toList();
                if (!oficiais.isEmpty()) resposta.put("datasets_semanticos_oficiais", oficiais);
                List<String> contaveis = recursos.listarAutorizados(context.security()).stream()
                        .map(item -> item.id()).toList();
                if (!contaveis.isEmpty()) resposta.put("cadastros_contaveis", contaveis);
                resposta.put("proximo_passo", "Use descrever_fonte_dados para ver colunas e valores; depois consulte "
                        + "com as ferramentas consultar_* ou consultar_sql.");
                int total = dominios.stream().mapToInt(item -> ((List<?>) item.get("fontes")).size()).sum();
                return ToolResult.of(formatter.json(resposta), total + " fontes disponiveis");
            }
        };
    }

    @Bean
    AgentTool descreverFonteDadosTool(SemanticCatalog catalog, SemanticDatasetRegistry datasets,
            ResultFormatter formatter) {
        return new BaseTool("descrever_fonte_dados", "Entender uma fonte de dados",
                "Descreve uma fonte de dados do Nexo: colunas com tipo e significado, valores validos de campos "
                        + "categoricos, relacionamentos com outras fontes, coluna de data e exemplos de perguntas. "
                        + "Use antes de escrever SQL ou montar consultas com colunas que voce ainda nao conhece.",
                JsonSchemas.object()
                        .required("fonte", JsonSchemas.string("Id da fonte (view SQL) ou do dataset semantico oficial"))
                        .build(),
                ToolKind.CATALOG, Set.of(), Duration.ofSeconds(20)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                String fonte = ToolArgs.text(arguments, "fonte");
                if (fonte == null) return "Lendo a estrutura de uma fonte de dados";
                return "Lendo a estrutura de " + catalog.fonte(fonte).map(item -> item.nome().toLowerCase(Locale.ROOT)).orElse(fonte);
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String id = ToolArgs.requiredText(arguments, "fonte");
                Optional<SemanticCatalog.Fonte> fonte = catalog.fonte(id);
                if (fonte.isPresent()) {
                    SemanticCatalog.Fonte item = fonte.get();
                    if (!catalog.autorizado(item, context.security())) {
                        throw new ToolException("Seu perfil nao tem acesso a esta fonte.");
                    }
                    Map<String, Object> resposta = new LinkedHashMap<>();
                    resposta.put("fonte", item.id());
                    resposta.put("sql", item.nomeQualificado());
                    resposta.put("nome", item.nome());
                    resposta.put("descricao", item.descricao());
                    if (item.colunaData() != null) resposta.put("coluna_data_principal", item.colunaData());
                    if (!"todos".equals(item.escopo())) {
                        resposta.put("escopo", "A view ja filtra os registros visiveis ao usuario (" + item.escopo() + ").");
                    }
                    List<Map<String, Object>> colunas = new ArrayList<>();
                    for (SemanticCatalog.ColunaBanco coluna : catalog.colunasDoBanco(item.id())) {
                        Map<String, Object> descricao = new LinkedHashMap<>();
                        descricao.put("coluna", coluna.nome());
                        descricao.put("tipo", coluna.tipo());
                        SemanticCatalog.ColunaSemantica semantica = item.colunas().get(coluna.nome());
                        if (semantica != null) {
                            if (semantica.descricao() != null) descricao.put("significado", semantica.descricao());
                            if (!semantica.valores().isEmpty()) descricao.put("valores", semantica.valores());
                        }
                        colunas.add(descricao);
                    }
                    if (colunas.isEmpty()) {
                        item.colunas().forEach((nome, semantica) -> colunas.add(Map.of("coluna", nome,
                                "significado", Objects.toString(semantica.descricao(), ""))));
                    }
                    resposta.put("colunas", colunas);
                    if (!item.relacionamentos().isEmpty()) resposta.put("relacionamentos", item.relacionamentos());
                    if (!item.exemplos().isEmpty()) resposta.put("exemplos_perguntas", item.exemplos());
                    List<SemanticCatalog.TermoGlossario> regras = catalog.buscarGlossario("").stream()
                            .filter(termo -> termo.fontes().contains(item.id())).toList();
                    if (!regras.isEmpty()) {
                        resposta.put("regras_de_negocio", regras.stream()
                                .map(termo -> termo.termo() + ": " + termo.definicao()).toList());
                    }
                    return ToolResult.of(formatter.json(resposta), colunas.size() + " colunas descritas");
                }
                SemanticDatasetDescriptor descriptor;
                try {
                    descriptor = datasets.descrever(id, context.security());
                } catch (IllegalArgumentException exception) {
                    throw new ToolException("Fonte '" + id + "' nao existe. Use listar_fontes_dados.");
                } catch (org.springframework.security.access.AccessDeniedException exception) {
                    throw new ToolException("Seu perfil nao tem acesso a esta fonte.");
                }
                return ToolResult.of(formatter.json(descriptor), descriptor.dimensoes().size() + " dimensoes e "
                        + descriptor.medidas().size() + " medidas");
            }
        };
    }

    @Bean
    AgentTool buscarConceitoTool(SemanticCatalog catalog, ResultFormatter formatter) {
        return new BaseTool("buscar_conceito", "Consultar regras de negócio",
                "Busca no glossario do Nexo o significado de termos e regras de negocio (SLA de reservas, tipo tecnico "
                        + "221/921, ciclo do desconto, aprovacao financeira, SLA de chamados, obra atrasada...) e as fontes "
                        + "de dados relacionadas. Use quando a pergunta envolver um conceito que voce nao tem certeza.",
                JsonSchemas.object().required("termo", JsonSchemas.string("Termo ou conceito, ex.: SLA, sucata, baixa")).build(),
                ToolKind.CATALOG, Set.of(), Duration.ofSeconds(10)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                return "Consultando regras de negócio";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String termo = ToolArgs.requiredText(arguments, "termo");
                List<Map<String, Object>> conceitos = catalog.buscarGlossario(termo).stream()
                        .map(item -> Map.<String, Object>of("termo", item.termo(), "definicao", item.definicao(),
                                "fontes", item.fontes()))
                        .toList();
                List<Map<String, Object>> fontes = catalog.buscarFontes(termo, context.security()).stream().limit(8)
                        .map(item -> Map.<String, Object>of("fonte", item.id(), "nome", item.nome()))
                        .toList();
                Map<String, Object> resposta = new LinkedHashMap<>();
                resposta.put("conceitos", conceitos);
                resposta.put("fontes_relacionadas", fontes);
                if (conceitos.isEmpty() && fontes.isEmpty()) {
                    resposta.put("observacao", "Nenhum conceito encontrado. Tente listar_fontes_dados.");
                }
                return ToolResult.of(formatter.json(resposta), conceitos.size() + " conceitos encontrados");
            }
        };
    }
}
