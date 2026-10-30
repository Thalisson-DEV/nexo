package com.nexo.app.modules.assistente.application.files;

import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.domain.ArquivoAssistente;

import java.util.List;
import java.util.Map;

/**
 * Conteudo extraido de um anexo: texto (PDF, DOCX, TXT, JSON) e/ou planilhas tabulares (XLSX, XLS, CSV,
 * JSON em lista) mais metadados para o agente decidir entre ler, buscar ou processar no Python.
 */
public record ArquivoExtraido(
        ArquivoAssistente.Categoria categoria,
        String texto,
        List<Planilha> planilhas,
        Map<String, Object> metadados) {

    public ArquivoExtraido {
        planilhas = planilhas == null ? List.of() : List.copyOf(planilhas);
        metadados = metadados == null ? Map.of() : Map.copyOf(metadados);
    }

    public record Planilha(String nome, TabularData dados) {
    }
}
