package com.nexo.app.modules.contrato.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContratoImportacaoDTO(
    Integer linha,
    String numeroContrato,
    String cliente,
    LocalDate dataVigencia,
    LocalDate dataEncerramento,
    BigDecimal coeficienteFaturamento,
    ImportacaoStatus status,
    String mensagemErro
) {
    public ContratoImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new ContratoImportacaoDTO(
            linha,
            numeroContrato != null ? numeroContrato.toUpperCase() : null,
            cliente,
            dataVigencia,
            dataEncerramento,
            coeficienteFaturamento,
            novoStatus,
            novaMensagem
        );
    }
}
