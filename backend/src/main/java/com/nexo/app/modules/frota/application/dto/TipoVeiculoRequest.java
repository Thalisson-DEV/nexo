package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.CategoriaCnh;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoVeiculoRequest(
        @NotBlank(message = "Informe o nome do tipo de veículo.") @Size(max = 80) String nome,
        @Size(max = 255) String descricao,
        CategoriaCnh categoriaCnhMinima
) {
}
