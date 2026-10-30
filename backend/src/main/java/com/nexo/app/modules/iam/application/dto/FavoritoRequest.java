package com.nexo.app.modules.iam.application.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Solicitação para criação de um novo favorito")
public record FavoritoRequest(
    @NotBlank(message = "O título é obrigatório")
    @Schema(description = "Nome que aparecerá na lista", example = "Movimentações de Estoque")
    String titulo,

    @NotBlank(message = "A rota é obrigatória")
    @Schema(description = "Rota interna do Angular", example = "/almoxarifado/movimentacoes-estoque")
    String rota,

    @Schema(description = "Ícone opcional (classes PrimeIcons ou similar)", example = "pi pi-box")
    String icone
) {}
