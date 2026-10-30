package com.nexo.app.modules.assistente.application.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.agent.ToolResult;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.*;

/**
 * Converte um resultado registrado no conteudo devolvido ao modelo (amostra que cabe no orcamento de
 * contexto + referencia ao resultado completo) e no resumo/previa exibidos na timeline.
 */
@Component
public class ResultFormatter {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final int PREVIEW_ROWS = 12;

    private final ObjectMapper objectMapper;

    public ResultFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ToolResult toToolResult(ResultStore.StoredResult stored, int maxChars, Map<String, Object> extra,
            String summaryPrefix) {
        TabularData data = stored.data();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resultado_id", stored.codigo());
        payload.put("titulo", stored.titulo());
        payload.put("procedencia", stored.natureza().name());
        payload.put("total_linhas", data.totalRows());
        payload.put("colunas", data.columns().stream()
                .map(column -> Map.of("nome", column.name(), "tipo", column.type().name().toLowerCase(Locale.ROOT)))
                .toList());
        if (extra != null) payload.putAll(extra);

        int budget = Math.max(2_000, maxChars - 600);
        List<List<Object>> included = new ArrayList<>();
        int size = json(payload).length();
        for (List<Object> row : data.rows()) {
            int rowSize = json(row).length() + 1;
            if (size + rowSize > budget) break;
            included.add(row);
            size += rowSize;
        }
        payload.put("linhas", included);
        if (included.size() < data.rows().size() || data.truncated()) {
            payload.put("observacao", "Somente " + included.size() + " de " + data.totalRows()
                    + " linhas foram incluidas aqui para caber no contexto. O resultado completo esta salvo como "
                    + stored.codigo() + ": use-o em analisar_resultado, comparar_resultados, criar_artefato, "
                    + "gerar_relatorio_pdf, exportar_planilha ou em executar_python (arquivo " + stored.codigo()
                    + ".csv). Nao invente as linhas omitidas.");
        }
        String summary = (summaryPrefix == null ? "" : summaryPrefix + " · ") + describeCount(data.totalRows());
        return ToolResult.of(json(payload), summary).withResult(stored.codigo(), data.totalRows(), preview(data));
    }

    public Map<String, Object> preview(TabularData data) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("colunas", data.columns().stream().map(TabularData.Column::name).toList());
        preview.put("linhas", data.rows().stream().limit(PREVIEW_ROWS).toList());
        preview.put("total", data.totalRows());
        return preview;
    }

    public static String describeCount(int total) {
        String number = NumberFormat.getIntegerInstance(PT_BR).format(total);
        if (total == 0) return "nenhum registro encontrado";
        return total == 1 ? "1 registro encontrado" : number + " registros encontrados";
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao serializar resultado", exception);
        }
    }
}
