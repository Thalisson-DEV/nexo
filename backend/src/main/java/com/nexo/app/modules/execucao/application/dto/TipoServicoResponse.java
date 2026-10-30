package com.nexo.app.modules.execucao.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Tipo de serviço de uma nota executada em campo")
public record TipoServicoResponse(

        @Schema(description = "Identificador do tipo de serviço")
        UUID id,

        @Schema(description = "Código da classificação da nota", example = "CL")
        String codigo,

        @Schema(description = "Descrição do tipo", example = "LIGAÇÃO NOVA")
        String descricao,

        @Schema(description = "Indica se o tipo está disponível para novos lançamentos", example = "true")
        boolean ativo,

        OffsetDateTime dataCriacao,

        OffsetDateTime dataUltimaAlteracao
) {
}
