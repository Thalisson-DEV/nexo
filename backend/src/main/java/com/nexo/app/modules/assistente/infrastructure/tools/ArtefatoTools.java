package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.artifacts.ArtifactResolver;
import com.nexo.app.modules.assistente.application.artifacts.ArtifactService;
import com.nexo.app.modules.assistente.application.files.ArquivoService;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularExport;
import com.nexo.app.modules.assistente.domain.ArquivoAssistente;
import com.nexo.app.modules.assistente.domain.ArtefatoAssistente;
import com.nexo.app.modules.assistente.infrastructure.report.PdfReportRenderer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Artefatos visuais e entregaveis: dashboards/graficos/tabelas/documentos declarativos, relatorios PDF e
 * planilhas exportadas. Todos referenciam resultados rN; os valores sao resolvidos no backend.
 */
@Configuration(proxyBeanMethods = false)
public class ArtefatoTools {

    private static final String EXEMPLO_COMPONENTES = "Exemplo: [{\"tipo\":\"kpi\",\"titulo\":\"Saidas no mes\",\"resultado\":\"r1\","
            + "\"coluna\":\"quantidade\",\"agregacao\":\"soma\"},{\"tipo\":\"grafico\",\"grafico\":\"barra\",\"titulo\":\"Saidas por obra\","
            + "\"resultado\":\"r1\",\"x\":\"obra\",\"y\":[\"quantidade\"],\"agregacao\":\"soma\",\"limite\":10},{\"tipo\":\"tabela\","
            + "\"resultado\":\"r1\",\"colunas\":[\"obra\",\"material\",\"quantidade\"],\"ordenar_por\":\"quantidade\"}]";

    static ObjectNode componenteSchema() {
        ObjectNode filtro = JsonSchemas.object()
                .prop("coluna", JsonSchemas.string(null))
                .prop("valor", JsonSchemas.anyValue(null))
                .build();
        ObjectNode comparacao = JsonSchemas.object()
                .description("Somente kpi: valor de referencia para variacao (outro resultado ou filtro)")
                .prop("resultado", JsonSchemas.string("rN da referencia; omita para usar o mesmo resultado"))
                .prop("coluna", JsonSchemas.string(null))
                .prop("agregacao", JsonSchemas.enumString(null, ArtifactResolver.AGREGACOES))
                .prop("filtro", filtro)
                .prop("rotulo", JsonSchemas.string("Ex.: vs. agosto"))
                .build();
        return JsonSchemas.object()
                .required("tipo", JsonSchemas.enumString("kpi, grafico, tabela ou texto", ArtifactResolver.TIPOS_COMPONENTE))
                .prop("titulo", JsonSchemas.string(null))
                .prop("descricao", JsonSchemas.string(null))
                .prop("resultado", JsonSchemas.string("Codigo rN dos dados (obrigatorio exceto em texto)"))
                .prop("grafico", JsonSchemas.enumString("Somente grafico", ArtifactResolver.TIPOS_GRAFICO))
                .prop("x", JsonSchemas.string("Grafico: coluna de categorias ou datas"))
                .prop("y", JsonSchemas.arrayOfStrings("Grafico: colunas numericas; vazio = contagem de linhas"))
                .prop("serie", JsonSchemas.string("Grafico: coluna que separa series (use com um unico y)"))
                .prop("agregacao", JsonSchemas.enumString("Padrao soma (ou contagem sem y)", ArtifactResolver.AGREGACOES))
                .prop("granularidade", JsonSchemas.enumString("Grafico com datas", ArtifactResolver.GRANULARIDADES))
                .prop("ordenar", JsonSchemas.enumString("Grafico", ArtifactResolver.ORDENACOES))
                .prop("limite", JsonSchemas.integer("Grafico: maximo de categorias; tabela: maximo de linhas", 1, 500))
                .prop("formato", JsonSchemas.enumString("percentual espera fracao (0.125 = 12,5%)", ArtifactResolver.FORMATOS))
                .prop("titulo_eixo_x", JsonSchemas.string(null))
                .prop("titulo_eixo_y", JsonSchemas.string(null))
                .prop("colunas", JsonSchemas.arrayOfStrings("Tabela: colunas exibidas, na ordem"))
                .prop("ordenar_por", JsonSchemas.string("Tabela: coluna de ordenacao"))
                .prop("decrescente", JsonSchemas.bool("Tabela: padrao true"))
                .prop("coluna", JsonSchemas.string("KPI: coluna agregada (omita com agregacao contagem)"))
                .prop("filtro", filtro)
                .prop("comparar_com", comparacao)
                .prop("melhor_quando", JsonSchemas.enumString("KPI: sentido favoravel da variacao", List.of("maior", "menor")))
                .prop("conteudo", JsonSchemas.string("Texto: markdown"))
                .prop("largura", JsonSchemas.enumString("Dashboard: ocupacao do componente", List.of("inteira", "metade")))
                .build();
    }

    @Bean
    AgentTool criarArtefatoTool(ArtifactResolver resolver, ArtifactService artefatos, ResultFormatter formatter) {
        return new BaseTool("criar_artefato", "Criar artefato",
                "Cria um artefato visual no painel lateral da conversa: dashboard (varios KPIs, graficos e tabelas), "
                        + "grafico, tabela ou documento (markdown com componentes). Os dados vem SEMPRE de resultados rN ja "
                        + "obtidos; voce informa colunas e agregacoes e o Nexo calcula os valores (nao digite numeros). "
                        + "Para novas versoes, crie outro artefato. " + EXEMPLO_COMPONENTES,
                JsonSchemas.object()
                        .required("tipo", JsonSchemas.enumString(null, List.of("dashboard", "grafico", "tabela", "documento")))
                        .required("titulo", JsonSchemas.string("Titulo em portugues"))
                        .prop("descricao", JsonSchemas.string("Uma frase sobre o que o artefato mostra"))
                        .required("componentes", JsonSchemas.array("Componentes na ordem de exibicao", componenteSchema()))
                        .build(),
                ToolKind.ARTIFACT, Set.of(), Duration.ofSeconds(30)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                String titulo = ToolArgs.text(arguments, "titulo");
                return titulo == null ? "Montando artefato" : "Montando \"" + titulo + "\"";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String tipo = Objects.toString(ToolArgs.text(arguments, "tipo"), "dashboard").toLowerCase(Locale.ROOT);
                ArtefatoAssistente.Tipo tipoArtefato = switch (tipo) {
                    case "grafico" -> ArtefatoAssistente.Tipo.GRAFICO;
                    case "tabela" -> ArtefatoAssistente.Tipo.TABELA;
                    case "documento" -> ArtefatoAssistente.Tipo.DOCUMENTO;
                    case "dashboard" -> ArtefatoAssistente.Tipo.DASHBOARD;
                    default -> throw new ToolException("Tipo invalido: use dashboard, grafico, tabela ou documento.");
                };
                String titulo = ToolArgs.requiredText(arguments, "titulo");
                ArtifactResolver.Procedencia procedencia = new ArtifactResolver.Procedencia();
                List<Map<String, Object>> componentes = resolver.componentes(context.conversaId(),
                        ToolArgs.array(arguments, "componentes"), procedencia);
                Map<String, Object> spec = new LinkedHashMap<>();
                spec.put("tipo", tipo);
                spec.put("componentes", componentes);
                spec.put("procedencia", procedencia.resumo());
                ArtifactService.ArtefatoView view = artefatos.criar(context.run(), tipoArtefato, titulo,
                        ToolArgs.text(arguments, "descricao"), spec, null);
                context.emit(Packet.Types.ARTIFACT, "artefato", view);
                Map<String, Object> resposta = new LinkedHashMap<>();
                resposta.put("artefato", view.codigo());
                resposta.put("tipo", tipo);
                resposta.put("titulo", titulo);
                resposta.put("componentes", componentes.stream().map(ArtefatoTools::resumoComponente).toList());
                resposta.put("orientacao", "O artefato ja esta visivel para o usuario no painel. Na resposta, comente os "
                        + "destaques usando os valores acima (calculados pelo Nexo) e mencione o artefato pelo titulo.");
                return ToolResult.of(formatter.json(resposta), view.codigo() + " · " + titulo);
            }
        };
    }

    @Bean
    AgentTool gerarRelatorioPdfTool(ArtifactResolver resolver, ArtifactService artefatos, ArquivoService arquivos,
            PdfReportRenderer renderer, ResultFormatter formatter) {
        ObjectNode secao = JsonSchemas.object()
                .required("titulo", JsonSchemas.string(null))
                .prop("texto", JsonSchemas.string("Markdown: analise, interpretacao, contexto"))
                .prop("componentes", JsonSchemas.array("KPIs, graficos, tabelas e textos da secao", componenteSchema()))
                .build();
        return new BaseTool("gerar_relatorio_pdf", "Gerar relatório PDF",
                "Gera um relatorio PDF profissional com a identidade do Nexo (capa, resumo executivo, secoes com KPIs, "
                        + "graficos vetoriais e tabelas, conclusoes e apendice automatico de procedencia dos dados). Use "
                        + "somente depois de obter os dados (rN) e analisar; os numeros dos componentes sao calculados pelo "
                        + "Nexo a partir dos resultados. Textos em portugues, objetivos, com interpretacao e recomendacoes. "
                        + "O arquivo fica disponivel para download na conversa.",
                JsonSchemas.object()
                        .required("titulo", JsonSchemas.string(null))
                        .prop("subtitulo", JsonSchemas.string(null))
                        .prop("periodo", JsonSchemas.string("Periodo coberto, ex.: 01/09/2026 a 25/09/2026"))
                        .prop("resumo", JsonSchemas.string("Resumo executivo em markdown (3 a 6 frases)"))
                        .required("secoes", JsonSchemas.array(null, secao))
                        .prop("conclusoes", JsonSchemas.string("Conclusoes e recomendacoes em markdown"))
                        .prop("nome_arquivo", JsonSchemas.string("Nome do arquivo sem extensao"))
                        .build(),
                ToolKind.REPORT, Set.of(), Duration.ofSeconds(90)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                return "Gerando relatório PDF";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                String titulo = ToolArgs.requiredText(arguments, "titulo");
                JsonNode secoesJson = ToolArgs.array(arguments, "secoes");
                if (secoesJson == null || secoesJson.isEmpty()) throw new ToolException("Informe ao menos uma secao em 'secoes'.");
                if (secoesJson.size() > 20) throw new ToolException("No maximo 20 secoes por relatorio.");
                ArtifactResolver.Procedencia procedencia = new ArtifactResolver.Procedencia();
                List<PdfReportRenderer.Secao> secoes = new ArrayList<>();
                List<Map<String, Object>> secoesSpec = new ArrayList<>();
                int indice = 0;
                for (JsonNode secaoJson : secoesJson) {
                    indice++;
                    JsonNode componentesJson = ToolArgs.array(secaoJson, "componentes");
                    List<Map<String, Object>> componentes;
                    try {
                        componentes = componentesJson == null || componentesJson.isEmpty() ? List.of()
                                : resolver.componentes(context.conversaId(), componentesJson, procedencia);
                    } catch (ToolException exception) {
                        throw new ToolException("Secao " + indice + ": " + exception.getMessage());
                    }
                    String tituloSecao = Objects.toString(ToolArgs.text(secaoJson, "titulo"), "Seção " + indice);
                    String texto = ToolArgs.text(secaoJson, "texto");
                    secoes.add(new PdfReportRenderer.Secao(tituloSecao, texto, componentes));
                    Map<String, Object> secaoSpec = new LinkedHashMap<>();
                    secaoSpec.put("titulo", tituloSecao);
                    if (texto != null) secaoSpec.put("texto", texto);
                    secaoSpec.put("componentes", componentes);
                    secoesSpec.add(secaoSpec);
                }
                String subtitulo = ToolArgs.text(arguments, "subtitulo");
                String periodo = ToolArgs.text(arguments, "periodo");
                String resumo = ToolArgs.text(arguments, "resumo");
                String conclusoes = ToolArgs.text(arguments, "conclusoes");
                byte[] pdf = renderer.renderizar(new PdfReportRenderer.Relatorio(titulo, subtitulo, periodo,
                        context.run().nomeUsuario(), OffsetDateTime.now(), resumo, secoes, conclusoes, procedencia.resumo()));
                String nome = nomeArquivo(ToolArgs.text(arguments, "nome_arquivo"), titulo, "pdf");
                ArquivoAssistente arquivo = arquivos.salvarGerado(context.run(), nome, "application/pdf", pdf);
                Map<String, Object> arquivoDescricao = descreverArquivo(arquivo);
                context.emit(Packet.Types.FILE, "arquivo", arquivoDescricao);

                Map<String, Object> spec = new LinkedHashMap<>();
                spec.put("tipo", "relatorio");
                if (subtitulo != null) spec.put("subtitulo", subtitulo);
                if (periodo != null) spec.put("periodo", periodo);
                if (resumo != null) spec.put("resumo", resumo);
                spec.put("secoes", secoesSpec);
                if (conclusoes != null) spec.put("conclusoes", conclusoes);
                spec.put("procedencia", procedencia.resumo());
                spec.put("arquivo", arquivoDescricao);
                ArtifactService.ArtefatoView view = artefatos.criar(context.run(), ArtefatoAssistente.Tipo.RELATORIO, titulo,
                        subtitulo, spec, arquivo.getId());
                context.emit(Packet.Types.ARTIFACT, "artefato", view);

                Map<String, Object> resposta = new LinkedHashMap<>();
                resposta.put("artefato", view.codigo());
                resposta.put("arquivo", arquivo.getCodigo());
                resposta.put("nome", arquivo.getNome());
                resposta.put("tamanho_kb", Math.max(1, arquivo.getTamanhoBytes() / 1024));
                resposta.put("resultados_usados", procedencia.codigos());
                resposta.put("secoes", secoesSpec.stream().map(secaoSpec -> Map.of("titulo", secaoSpec.get("titulo"),
                        "componentes", ((List<?>) secaoSpec.get("componentes")).stream()
                                .map(componente -> resumoComponente(castMap(componente))).toList())).toList());
                resposta.put("orientacao", "O PDF ja esta disponivel para download na conversa. Resuma em poucas linhas o "
                        + "que o relatorio contem; nao escreva links.");
                return ToolResult.of(formatter.json(resposta), arquivo.getNome())
                        .withFiles(List.of(arquivoDescricao));
            }
        };
    }

    @Bean
    AgentTool exportarPlanilhaTool(ResultStore store, ArtifactService artefatos, ArquivoService arquivos,
            ResultFormatter formatter) {
        return new BaseTool("exportar_planilha", "Exportar planilha",
                "Exporta um ou mais resultados rN para arquivo XLSX (uma aba por resultado, tipos preservados, filtros e "
                        + "aba 'Sobre' com a procedencia) ou CSV (um resultado; separador ';' para Excel em portugues). O "
                        + "arquivo fica disponivel para download na conversa.",
                JsonSchemas.object()
                        .required("resultados", JsonSchemas.arrayOfStrings("Codigos rN a exportar"))
                        .prop("formato", JsonSchemas.enumString("Padrao xlsx", List.of("xlsx", "csv")))
                        .prop("titulo", JsonSchemas.string("Titulo da exportacao"))
                        .prop("nomes_abas", JsonSchemas.arrayOfStrings("Nome de cada aba, na ordem dos resultados"))
                        .prop("nome_arquivo", JsonSchemas.string("Nome do arquivo sem extensao"))
                        .build(),
                ToolKind.EXPORT, Set.of(), Duration.ofSeconds(60)) {
            @Override
            public String progressLabel(JsonNode arguments) {
                return "Exportando planilha";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                List<String> codigos = ToolArgs.texts(arguments, "resultados");
                if (codigos.isEmpty()) throw new ToolException("Informe ao menos um resultado em 'resultados'.");
                if (codigos.size() > 12) throw new ToolException("No maximo 12 resultados por planilha.");
                String formato = Objects.toString(ToolArgs.text(arguments, "formato"), "xlsx").toLowerCase(Locale.ROOT);
                if (!formato.equals("xlsx") && !formato.equals("csv")) throw new ToolException("Formato invalido: use xlsx ou csv.");
                if (formato.equals("csv") && codigos.size() > 1) {
                    throw new ToolException("CSV aceita um unico resultado. Use formato xlsx para varios resultados.");
                }
                List<String> nomesAbas = ToolArgs.texts(arguments, "nomes_abas");
                List<ResultStore.StoredResult> resultados = codigos.stream().map(codigo -> store.exigir(context.conversaId(), codigo)).toList();
                String titulo = Objects.toString(ToolArgs.text(arguments, "titulo"),
                        resultados.size() == 1 ? resultados.getFirst().titulo() : "Exportação do Copiloto");
                List<String> avisos = new ArrayList<>();
                List<TabularExport.Aba> abas = new ArrayList<>();
                List<Map<String, Object>> abasSpec = new ArrayList<>();
                for (int i = 0; i < resultados.size(); i++) {
                    ResultStore.StoredResult resultado = resultados.get(i);
                    String nomeAba = i < nomesAbas.size() ? nomesAbas.get(i) : resultado.titulo();
                    abas.add(new TabularExport.Aba(nomeAba, resultado.data(), resultado.codigo() + " · " + resultado.natureza().name()
                            + (resultado.origem() == null ? "" : " · " + resultado.origem())));
                    if (resultado.data().rows().size() < resultado.data().totalRows()) {
                        avisos.add(resultado.codigo() + ": exportadas " + resultado.data().rows().size() + " de "
                                + resultado.data().totalRows() + " linhas (limite do resultado no assistente). Para a base "
                                + "completa, use a exportação do módulo de origem ou refine a consulta.");
                    }
                    abasSpec.add(Map.of("nome", nomeAba, "resultado", resultado.codigo(),
                            "linhas", resultado.data().rows().size(), "colunas", resultado.data().columns().size()));
                }
                byte[] conteudo = formato.equals("csv") ? TabularExport.csvExcel(resultados.getFirst().data())
                        : TabularExport.xlsx(abas, titulo, avisos);
                String nome = nomeArquivo(ToolArgs.text(arguments, "nome_arquivo"), titulo, formato);
                String tipoConteudo = formato.equals("csv") ? "text/csv"
                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                ArquivoAssistente arquivo = arquivos.salvarGerado(context.run(), nome, tipoConteudo, conteudo);
                Map<String, Object> arquivoDescricao = descreverArquivo(arquivo);
                context.emit(Packet.Types.FILE, "arquivo", arquivoDescricao);
                Map<String, Object> spec = new LinkedHashMap<>();
                spec.put("tipo", "planilha");
                spec.put("formato", formato);
                spec.put("abas", abasSpec);
                spec.put("arquivo", arquivoDescricao);
                if (!avisos.isEmpty()) spec.put("avisos", avisos);
                ArtifactService.ArtefatoView view = artefatos.criar(context.run(), ArtefatoAssistente.Tipo.PLANILHA, titulo,
                        null, spec, arquivo.getId());
                context.emit(Packet.Types.ARTIFACT, "artefato", view);
                Map<String, Object> resposta = new LinkedHashMap<>();
                resposta.put("artefato", view.codigo());
                resposta.put("arquivo", arquivo.getCodigo());
                resposta.put("nome", arquivo.getNome());
                resposta.put("abas", abasSpec);
                if (!avisos.isEmpty()) resposta.put("avisos", avisos);
                resposta.put("orientacao", "O arquivo ja esta disponivel para download na conversa; nao escreva links.");
                return ToolResult.of(formatter.json(resposta), arquivo.getNome()).withFiles(List.of(arquivoDescricao));
            }
        };
    }

    // ------------------------------------------------------------------------------------ utilitarios

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    /** Resumo compacto de um componente resolvido para o modelo narrar com os valores calculados. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> resumoComponente(Map<String, Object> componente) {
        Map<String, Object> resumo = new LinkedHashMap<>();
        String tipo = Objects.toString(componente.get("tipo"), "");
        resumo.put("tipo", tipo);
        if (componente.get("titulo") != null) resumo.put("titulo", componente.get("titulo"));
        switch (tipo) {
            case "kpi" -> {
                resumo.put("valor", componente.get("valor"));
                resumo.put("formato", componente.get("formato"));
                if (componente.get("comparacao") != null) resumo.put("comparacao", componente.get("comparacao"));
            }
            case "grafico" -> {
                List<Object> rotulos = (List<Object>) componente.getOrDefault("rotulos", List.of());
                List<Map<String, Object>> series = (List<Map<String, Object>>) componente.getOrDefault("series", List.of());
                resumo.put("grafico", componente.get("grafico"));
                resumo.put("categorias", rotulos.size());
                List<Map<String, Object>> amostra = new ArrayList<>();
                for (Map<String, Object> serie : series) {
                    List<Object> valores = (List<Object>) serie.getOrDefault("valores", List.of());
                    List<List<Object>> pares = new ArrayList<>();
                    for (int i = 0; i < Math.min(12, rotulos.size()); i++) {
                        pares.add(Arrays.asList(rotulos.get(i), i < valores.size() ? valores.get(i) : null));
                    }
                    amostra.add(Map.of("serie", serie.getOrDefault("nome", ""), "valores", pares));
                }
                resumo.put("series", amostra);
                if (componente.get("pontos") instanceof List<?> pontos) resumo.put("pontos", pontos.size());
                if (componente.get("observacao") != null) resumo.put("observacao", componente.get("observacao"));
            }
            case "tabela" -> {
                resumo.put("linhas_exibidas", componente.get("exibidas"));
                resumo.put("total", componente.get("total"));
            }
            default -> {
                // texto: nada a resumir
            }
        }
        return resumo;
    }

    static Map<String, Object> descreverArquivo(ArquivoAssistente arquivo) {
        Map<String, Object> descricao = new LinkedHashMap<>();
        descricao.put("id", arquivo.getId().toString());
        descricao.put("codigo", arquivo.getCodigo());
        descricao.put("nome", arquivo.getNome());
        descricao.put("tipoConteudo", arquivo.getTipoConteudo());
        descricao.put("categoria", arquivo.getCategoria().name());
        descricao.put("tamanhoBytes", arquivo.getTamanhoBytes());
        descricao.put("origem", arquivo.getOrigem().name());
        return descricao;
    }

    static String nomeArquivo(String informado, String titulo, String extensao) {
        String base = informado != null && !informado.isBlank() ? informado : titulo;
        String slug = Normalizer.normalize(Objects.toString(base, "arquivo"), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\.(pdf|xlsx|csv)$", "").replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) slug = "arquivo";
        if (slug.length() > 80) slug = slug.substring(0, 80).replaceAll("-+$", "");
        if (informado == null || informado.isBlank()) slug = slug + "-" + LocalDate.now();
        return slug + "." + extensao;
    }
}
