package com.nexo.app.modules.frota.application.dto;

import java.util.List;

public record ParametrosFrotaResponse(List<Integer> faixasAlertaDias, Integer limiteKmDia) {
}
