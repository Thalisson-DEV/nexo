package com.nexo.app.modules.rh;

import java.util.UUID;

public record VeiculoResponse(
    UUID id,
    String placa,
    String modelo,
    String fabricante,
    BaseOperacionalResponse baseOperacional,
    boolean ativo
) {}
