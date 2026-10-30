package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ConfirmarImportacaoFuncaoRequest(
    List<FuncaoImportacaoDTO> funcoes
) {
}
