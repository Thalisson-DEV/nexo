package com.nexo.app.modules.iam.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Dados detalhados do favorito do usuário")
public record FavoritoResponse(
    @Schema(description = "ID do favorito")
    UUID id,

    @Schema(description = "Nome que aparecerá na lista")
    String titulo,

    @Schema(description = "Rota interna do Angular")
    String rota,

    @Schema(description = "Ícone opcional")
    String icone
) {}
