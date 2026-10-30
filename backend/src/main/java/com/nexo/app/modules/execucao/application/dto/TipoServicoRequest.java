package com.nexo.app.modules.execucao.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload para criação ou atualização de tipo de serviço")
public record TipoServicoRequest(

        @NotBlank(message = "O código do tipo de serviço é obrigatório")
        @Size(max = 10, message = "O código pode ter no máximo 10 caracteres")
        @Schema(description = "Código da classificação da nota; pode se repetir entre tipos", example = "CL")
        String codigo,

        @NotBlank(message = "A descrição do tipo de serviço é obrigatória")
        @Size(min = 2, max = 120, message = "A descrição deve ter entre 2 e 120 caracteres")
        @Schema(description = "Descrição do tipo, única entre os tipos ativos", example = "LIGAÇÃO NOVA")
        String descricao
) {
}
