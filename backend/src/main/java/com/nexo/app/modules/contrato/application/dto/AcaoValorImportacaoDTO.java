package com.nexo.app.modules.contrato.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AcaoValorImportacaoDTO(
    Integer linha,
    String codigoAcao,
    String numeroContrato,
    BigDecimal valorBase,
    LocalDate dataVigencia,
    ImportacaoStatus status,
    String mensagemErro
) {
    public AcaoValorImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new AcaoValorImportacaoDTO(
            linha,
            codigoAcao != null ? codigoAcao.toUpperCase() : null,
            numeroContrato != null ? numeroContrato.toUpperCase() : null,
            valorBase,
            dataVigencia,
            novoStatus,
            novaMensagem
        );
    }
}
