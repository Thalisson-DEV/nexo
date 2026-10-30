package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload para criação ou atualização de Motivo de Rejeição")
public record MotivoRejeicaoRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 100, message = "O nome deve ter entre 3 e 100 caracteres")
        @Schema(description = "Nome curto do motivo", example = "Falta de Estoque")
        String nome,

        @NotBlank(message = "A descrição é obrigatória")
        @Size(min = 5, max = 255, message = "A descrição deve ter entre 5 e 255 caracteres")
        @Schema(description = "Descrição detalhada do motivo", example = "O material solicitado não possui saldo suficiente e não há previsão de reposição em curto prazo.")
        String descricao
) {}
