package com.nexo.app.modules.estoque.application.dto;

import java.util.List;

public record ConfirmarImportacaoCentroInvestimentoRequest(
    List<CentroInvestimentoImportacaoDTO> centros
) {
}
