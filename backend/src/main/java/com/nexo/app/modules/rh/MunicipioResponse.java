package com.nexo.app.modules.rh;

import java.util.UUID;

public record MunicipioResponse(
    UUID id,
    String nome,
    BaseOperacionalResponse baseOperacional,
    boolean ativo
) {}
