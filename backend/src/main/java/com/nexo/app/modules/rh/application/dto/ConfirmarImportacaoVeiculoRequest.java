package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ConfirmarImportacaoVeiculoRequest(
    @NotEmpty(message = "A lista de veículos não pode estar vazia")
    List<VeiculoImportacaoDTO> veiculos
) {}
