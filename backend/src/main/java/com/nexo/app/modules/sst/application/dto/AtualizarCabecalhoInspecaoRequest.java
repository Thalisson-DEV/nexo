package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AtualizarCabecalhoInspecaoRequest(
        @NotNull(message = "O tipo de inspeção é obrigatório")
        UUID idTipoInspecao,

        @NotNull(message = "O processo de negócio é obrigatório")
        UUID idProcesso,

        @NotNull(message = "A base operacional é obrigatória")
        UUID idBaseOperacional,

        UUID idVeiculo,

        UUID idEquipe,

        @NotNull(message = "A data de realização é obrigatória")
        OffsetDateTime dataRealizacao,

        @Size(max = 1000, message = "As observações devem ter no máximo 1000 caracteres")
        String observacoes,

        BigDecimal latitude,

        BigDecimal longitude,

        @NotEmpty(message = "Ao menos um colaborador inspecionado é obrigatório")
        List<UUID> colaboradoresInspecionados,

        @Size(max = 1000, message = "A justificativa deve ter no máximo 1000 caracteres")
        String justificativa
) {}
