package com.nexo.app.modules.rh.application.dto;

import java.util.List;

/**
 * Request para confirmar a importação de Processos de Negócio.
 */
public record ConfirmarImportacaoProcessoNegocioRequest(
    List<ProcessoNegocioImportacaoDTO> processos
) {
}
