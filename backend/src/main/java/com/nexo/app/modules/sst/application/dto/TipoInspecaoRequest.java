package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload para criação ou atualização de tipo de inspeção de SST")
public record TipoInspecaoRequest(

        @NotBlank(message = "O nome do tipo de inspeção é obrigatório")
        @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres")
        @Schema(description = "Nome do tipo de inspeção", example = "Campo")
        String nome,

        @Size(max = 500, message = "A descrição pode ter no máximo 500 caracteres")
        @Schema(description = "Descrição detalhada do contexto de aplicação do tipo", example = "Inspeções de segurança realizadas em campo e frentes de serviço.")
        String descricao,

        @Schema(description = "Ordem de exibição do tipo de inspeção", example = "1")
        Integer ordem
) {
}
