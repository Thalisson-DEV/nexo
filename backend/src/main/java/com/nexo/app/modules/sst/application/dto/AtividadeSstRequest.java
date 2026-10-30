package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Cadastro/edição de uma atividade de SST.
 */
public record AtividadeSstRequest(

        @NotBlank(message = "O nome da atividade é obrigatório")
        @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres")
        @Schema(description = "Natureza do que estava sendo feito", example = "Detonação")
        String nome,

        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
        @Schema(description = "O que a atividade abrange")
        String descricao,

        @Schema(description = """
                Colaborador responsável pela atividade. O evento copia este gestor no registro.
                Opcional: enquanto a atividade estiver sem gestor, o registro do evento exige
                que ele seja informado.""")
        UUID idGestor,

        @Schema(description = "Processo de negócio da atividade, quando ela for específica de um")
        UUID idProcesso
) {
}
