package com.nexo.app.modules.obras.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * Pré-visualização (validação) ou confirmação de uma importação de planilha de Obras: as linhas com a situação
 * de cada uma. Na confirmação, {@code idImportacao} identifica o registro no histórico de importações.
 */
public record PreviaImportacaoObraResponse<T>(
        List<T> registros,
        int totalProcessados,
        int totalSucesso,
        int totalFalha,
        UUID idImportacao
) {
}
