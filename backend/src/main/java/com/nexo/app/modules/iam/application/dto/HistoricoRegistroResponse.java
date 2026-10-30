package com.nexo.app.modules.iam.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HistoricoRegistroResponse(
    UUID registroId,
    String tabela,
    List<LogEntryResponse> alteracoes
) {
    public record LogEntryResponse(
        UUID logId,
        OffsetDateTime dataAlteracao,
        String operacao,
        String usuario,
        List<AlteracaoCampo> camposAlterados
    ) {}

    public record AlteracaoCampo(
        String campo,
        Object valorAnterior,
        Object valorNovo
    ) {}
}
