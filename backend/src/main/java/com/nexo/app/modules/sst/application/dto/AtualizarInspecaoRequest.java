package com.nexo.app.modules.sst.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Requisição para alteração total de uma inspeção de segurança existente,
 * englobando cabeçalho, respostas do checklist, desvios identificados
 * e evidências fotográficas.
 */
public record AtualizarInspecaoRequest(
        UUID idFormulario,

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

        @Valid
        List<RegistrarInspecaoRequest.RespostaItem> respostas,

        @Valid
        List<RegistrarInspecaoRequest.DesvioItem> desvios,

        @NotEmpty(message = "Ao menos uma evidência fotográfica deve ser mantida")
        List<UUID> evidencias,

        @Size(max = 1000, message = "A justificativa deve ter no máximo 1000 caracteres")
        String justificativa
) {
    public AtualizarInspecaoRequest {
        colaboradoresInspecionados = colaboradoresInspecionados == null ? List.of() : colaboradoresInspecionados;
        respostas = respostas == null ? List.of() : respostas;
        desvios = desvios == null ? List.of() : desvios;
        evidencias = evidencias == null ? List.of() : evidencias;
    }
}
