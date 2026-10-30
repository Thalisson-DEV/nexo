package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.ResultadoAssistente;

import java.time.OffsetDateTime;

/** Projecao sem as linhas de um resultado, usada para listar o que ja foi apurado na conversa. */
public record ResumoResultadoAssistente(
        String codigo,
        String titulo,
        String origem,
        ResultadoAssistente.Natureza natureza,
        String colunasJson,
        int totalLinhas,
        boolean truncado,
        OffsetDateTime dataCriacao) {
}
