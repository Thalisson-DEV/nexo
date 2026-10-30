package com.nexo.app.modules.rh.application.dto;

import java.util.List;

/**
 * Resposta para o processo de importação de Processos de Negócio.
 */
public record ImportacaoProcessoNegocioResponse(
    List<ProcessoNegocioImportacaoDTO> registros,
    int totalProcessados,
    int totalSucesso,
    int totalFalha
) {
}
