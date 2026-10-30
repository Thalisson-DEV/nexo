package com.nexo.app.modules.sst.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Dados de retorno de um tipo de inspeção de SST")
public record TipoInspecaoResponse(

        @Schema(description = "Identificador único do tipo de inspeção")
        UUID id,

        @Schema(description = "Nome do tipo de inspeção", example = "Campo")
        String nome,

        @Schema(description = "Descrição detalhada", example = "Inspeções de segurança realizadas em campo e frentes de serviço.")
        String descricao,

        @Schema(description = "Ordem de exibição", example = "1")
        int ordem,

        @Schema(description = "Indica se o tipo está ativo para novos lançamentos", example = "true")
        boolean ativo,

        @Schema(description = "Data e hora de criação")
        OffsetDateTime dataCriacao,

        @Schema(description = "Data e hora da última alteração")
        OffsetDateTime dataUltimaAlteracao
) {
}
