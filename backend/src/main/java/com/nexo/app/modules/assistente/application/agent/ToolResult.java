package com.nexo.app.modules.assistente.application.agent;

import java.util.List;
import java.util.Map;

/**
 * Resultado de uma ferramenta.
 *
 * @param llmContent  conteudo devolvido ao modelo (JSON ou texto); truncado pelo executor se exceder o limite
 * @param summary     resumo objetivo para a timeline ("2.483 registros encontrados")
 * @param resultCode  codigo do resultado tabular registrado (r1, r2...), quando houver
 * @param rowCount    quantidade de linhas do resultado, quando houver
 * @param preview     amostra pequena para expandir na interface ({colunas, linhas})
 * @param sources     fontes externas (pesquisa web) com numero de citacao
 * @param files       arquivos gerados
 * @param failed      a ferramenta executou, mas o trabalho falhou (ex.: codigo Python com erro); o modelo
 *                    recebe o diagnostico para corrigir e a interface exibe o passo como falho
 */
public record ToolResult(
        String llmContent,
        String summary,
        String resultCode,
        Integer rowCount,
        Map<String, Object> preview,
        List<Map<String, Object>> sources,
        List<Map<String, Object>> files,
        boolean failed) {

    public ToolResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static ToolResult of(String llmContent, String summary) {
        return new ToolResult(llmContent, summary, null, null, null, List.of(), List.of(), false);
    }

    public ToolResult withResult(String code, Integer rows, Map<String, Object> previewData) {
        return new ToolResult(llmContent, summary, code, rows, previewData, sources, files, failed);
    }

    public ToolResult withSources(List<Map<String, Object>> newSources) {
        return new ToolResult(llmContent, summary, resultCode, rowCount, preview, newSources, files, failed);
    }

    public ToolResult withFiles(List<Map<String, Object>> newFiles) {
        return new ToolResult(llmContent, summary, resultCode, rowCount, preview, sources, newFiles, failed);
    }

    public ToolResult asFailure() {
        return new ToolResult(llmContent, summary, resultCode, rowCount, preview, sources, files, true);
    }
}
