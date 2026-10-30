package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.TipoDocumentoFrota;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** Metadados do documento. Informe o veículo ou o implemento (só na criação). */
public record DocumentoFrotaRequest(
        UUID idVeiculo,
        UUID idImplemento,
        @NotNull(message = "Informe o tipo do documento.") TipoDocumentoFrota tipo,
        @Size(max = 150) String descricao,
        @Size(max = 80) String numero,
        LocalDate dataEmissao,
        LocalDate dataVencimento,
        @Size(max = 1000) String observacao
) {
}
