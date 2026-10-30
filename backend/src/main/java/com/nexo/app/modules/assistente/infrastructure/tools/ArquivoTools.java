package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.agent.*;
import com.nexo.app.modules.assistente.application.files.ArquivoExtraido;
import com.nexo.app.modules.assistente.application.files.ArquivoService;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.application.results.TabularOps;
import com.nexo.app.modules.assistente.domain.ArquivoAssistente;
import com.nexo.app.modules.assistente.domain.ResultadoAssistente;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.*;

/**
 * Leitura de anexos da conversa (inspirado no FileReaderTool do Onyx): planilhas viram resultados tabulares
 * reutilizaveis; documentos sao lidos por janelas de caracteres ou por busca, sem despejar o arquivo inteiro.
 */
@Configuration(proxyBeanMethods = false)
public class ArquivoTools {

    private static final int JANELA_PADRAO = 12_000;

    @Bean
    AgentTool lerArquivoTool(ArquivoService arquivos, ResultStore store, ResultFormatter formatter,
            AssistenteProperties properties) {
        return new BaseTool("ler_arquivo", "Ler arquivo anexado",
                "Le um arquivo anexado na conversa (f1, f2...). Planilhas (XLSX, XLS, CSV, JSON em lista): informe a "
                        + "aba (nome ou numero, padrao a primeira) e o conteudo e registrado como resultado rN para "
                        + "analise, conciliacao com o Nexo, graficos e Python. Documentos (PDF, DOCX, TXT): retorna uma "
                        + "janela de texto a partir do caractere 'inicio' (padrao 0, ate 12000 caracteres); leia em partes.",
                JsonSchemas.object()
                        .required("arquivo", JsonSchemas.string("Codigo do arquivo, ex.: f1"))
                        .prop("aba", JsonSchemas.string("Nome ou numero (1..n) da aba da planilha"))
                        .prop("inicio", JsonSchemas.integer("Caractere inicial para documentos", 0, null))
                        .prop("limite", JsonSchemas.integer("Tamanho da janela de texto", 500, 30000))
                        .build(),
                ToolKind.FILE, Set.of(), Duration.ofSeconds(30)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return !arquivos.listarDaConversa(context.conversaId()).isEmpty() || !context.arquivosDaMensagem().isEmpty();
            }

            @Override
            public int maxResultChars() {
                return 40_000;
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                return "Lendo o arquivo " + Objects.toString(ToolArgs.text(arguments, "arquivo"), "");
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                ArquivoAssistente arquivo = exigirArquivo(arquivos, context, ToolArgs.requiredText(arguments, "arquivo"));
                if (arquivo.getStatus() == ArquivoAssistente.Status.FALHOU) {
                    throw new ToolException("Nao foi possivel extrair o conteudo de " + arquivo.getNome() + ": "
                            + arquivo.getErro() + ". Tente executar_python se o formato for suportado por pandas.");
                }
                List<ArquivoExtraido.Planilha> planilhas = arquivos.planilhas(arquivo);
                if (!planilhas.isEmpty()) {
                    ArquivoExtraido.Planilha planilha = escolherAba(planilhas, ToolArgs.text(arguments, "aba"));
                    ResultStore.StoredResult stored = store.registrar(context.run(), name(),
                            ResultadoAssistente.Natureza.ARQUIVO, arquivo.getNome() + " / " + planilha.nome(),
                            planilha.dados(), Map.of("arquivo", arquivo.getCodigo(), "aba", planilha.nome()));
                    Map<String, Object> extra = new LinkedHashMap<>();
                    extra.put("arquivo", arquivo.getCodigo());
                    extra.put("aba", planilha.nome());
                    extra.put("abas_disponiveis", planilhas.stream().map(ArquivoExtraido.Planilha::nome).toList());
                    return formatter.toToolResult(stored, properties.getAgent().getToolResultMaxChars(), extra,
                            arquivo.getNome());
                }
                String texto = arquivo.getTextoExtraido();
                if (texto == null || texto.isBlank()) {
                    Map<String, Object> resumo = arquivos.resumo(arquivo);
                    return ToolResult.of(formatter.json(Map.of("arquivo", arquivo.getCodigo(), "nome", arquivo.getNome(),
                            "categoria", arquivo.getCategoria().name(), "metadados", resumo,
                            "observacao", "Sem texto extraivel. Para imagens ou formatos especiais, use executar_python.")),
                            "Sem texto extraivel");
                }
                int inicio = Math.max(0, Optional.ofNullable(ToolArgs.integer(arguments, "inicio")).orElse(0));
                int limite = Optional.ofNullable(ToolArgs.integer(arguments, "limite")).orElse(JANELA_PADRAO);
                if (inicio >= texto.length()) throw new ToolException("Inicio alem do fim do texto (" + texto.length() + " caracteres).");
                int fim = Math.min(texto.length(), inicio + limite);
                Map<String, Object> resposta = new LinkedHashMap<>();
                resposta.put("arquivo", arquivo.getCodigo());
                resposta.put("nome", arquivo.getNome());
                resposta.put("inicio", inicio);
                resposta.put("fim", fim);
                resposta.put("total_caracteres", texto.length());
                resposta.put("texto", texto.substring(inicio, fim));
                if (fim < texto.length()) resposta.put("continua", "Leia a proxima parte com inicio=" + fim + " ou use buscar_em_arquivo.");
                return ToolResult.of(formatter.json(resposta), "Trecho " + inicio + "-" + fim + " de " + texto.length() + " caracteres");
            }
        };
    }

    @Bean
    AgentTool buscarEmArquivoTool(ArquivoService arquivos, ResultFormatter formatter) {
        return new BaseTool("buscar_em_arquivo", "Buscar em arquivo",
                "Busca um termo (sem diferenciar acentos/maiusculas) dentro de um arquivo anexado e retorna os trechos "
                        + "ou linhas de planilha que o contem, com a posicao para leitura detalhada via ler_arquivo.",
                JsonSchemas.object()
                        .required("arquivo", JsonSchemas.string("Codigo do arquivo, ex.: f1"))
                        .required("termo", JsonSchemas.string("Termo buscado"))
                        .prop("max_trechos", JsonSchemas.integer(null, 1, 50))
                        .build(),
                ToolKind.FILE, Set.of(), Duration.ofSeconds(30)) {
            @Override
            public boolean isAvailable(AgentRunContext context) {
                return !arquivos.listarDaConversa(context.conversaId()).isEmpty() || !context.arquivosDaMensagem().isEmpty();
            }

            @Override
            public String progressLabel(JsonNode arguments) {
                return "Procurando \"" + Objects.toString(ToolArgs.text(arguments, "termo"), "") + "\" no arquivo";
            }

            @Override
            public ToolResult execute(JsonNode arguments, ToolContext context) {
                ArquivoAssistente arquivo = exigirArquivo(arquivos, context, ToolArgs.requiredText(arguments, "arquivo"));
                String termo = TabularOps.normalizar(ToolArgs.requiredText(arguments, "termo"));
                int maximo = Optional.ofNullable(ToolArgs.integer(arguments, "max_trechos")).orElse(15);
                List<Map<String, Object>> achados = new ArrayList<>();
                for (ArquivoExtraido.Planilha planilha : arquivos.planilhas(arquivo)) {
                    TabularData dados = planilha.dados();
                    for (int i = 0; i < dados.rows().size() && achados.size() < maximo; i++) {
                        List<Object> linha = dados.rows().get(i);
                        String conteudo = TabularOps.normalizar(linha.toString());
                        if (!conteudo.contains(termo)) continue;
                        Map<String, Object> achado = new LinkedHashMap<>();
                        achado.put("aba", planilha.nome());
                        achado.put("linha", i + 2);
                        achado.put("valores", dados.asMaps(Integer.MAX_VALUE).get(i));
                        achados.add(achado);
                    }
                }
                String texto = arquivo.getTextoExtraido();
                if (texto != null && achados.size() < maximo) {
                    String normalizado = TabularOps.normalizar(texto);
                    int posicao = normalizado.indexOf(termo);
                    while (posicao >= 0 && achados.size() < maximo) {
                        int inicio = Math.max(0, posicao - 300);
                        int fim = Math.min(texto.length(), posicao + termo.length() + 300);
                        achados.add(Map.of("posicao", posicao, "trecho", texto.substring(inicio, Math.min(fim, texto.length()))));
                        posicao = normalizado.indexOf(termo, posicao + termo.length());
                    }
                }
                Map<String, Object> resposta = new LinkedHashMap<>();
                resposta.put("arquivo", arquivo.getCodigo());
                resposta.put("termo", termo);
                resposta.put("ocorrencias", achados);
                return ToolResult.of(formatter.json(resposta), achados.size() + " ocorrencias");
            }
        };
    }

    static ArquivoAssistente exigirArquivo(ArquivoService arquivos, ToolContext context, String referencia) {
        return arquivos.daConversa(context.conversaId(), referencia)
                .orElseThrow(() -> new ToolException("Arquivo '" + referencia + "' nao encontrado nesta conversa. Use os codigos listados (f1, f2...)."));
    }

    private static ArquivoExtraido.Planilha escolherAba(List<ArquivoExtraido.Planilha> planilhas, String aba) {
        if (aba == null || aba.isBlank()) return planilhas.getFirst();
        try {
            int indice = Integer.parseInt(aba.trim());
            if (indice >= 1 && indice <= planilhas.size()) return planilhas.get(indice - 1);
        } catch (NumberFormatException ignored) {
            // aba por nome
        }
        String alvo = TabularOps.normalizar(aba);
        return planilhas.stream().filter(planilha -> TabularOps.normalizar(planilha.nome()).equals(alvo)).findFirst()
                .or(() -> planilhas.stream().filter(planilha -> TabularOps.normalizar(planilha.nome()).contains(alvo)).findFirst())
                .orElseThrow(() -> new ToolException("Aba '" + aba + "' nao encontrada. Abas: "
                        + String.join(", ", planilhas.stream().map(ArquivoExtraido.Planilha::nome).toList())));
    }
}
