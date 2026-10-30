package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.SituacaoOperacional;

import java.util.UUID;

public record ImplementoResponse(
        UUID id,
        String identificacao,
        UUID idTipoImplemento,
        String tipoImplemento,
        String fabricante,
        String modelo,
        String numeroSerie,
        Integer anoFabricacao,
        UUID idBaseOperacional,
        String baseOperacional,
        SituacaoOperacional situacao,
        String observacao,
        AcoplamentoImplementoResponse acoplamentoAtual,
        boolean ativo
) {
}
