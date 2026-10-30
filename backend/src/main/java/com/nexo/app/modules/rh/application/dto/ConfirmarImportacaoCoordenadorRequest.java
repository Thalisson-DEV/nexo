package com.nexo.app.modules.rh.application.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ConfirmarImportacaoCoordenadorRequest(
    @NotEmpty(message = "A lista de coordenadores não pode estar vazia")
    List<CoordenadorImportacaoDTO> coordenadores
) {}
