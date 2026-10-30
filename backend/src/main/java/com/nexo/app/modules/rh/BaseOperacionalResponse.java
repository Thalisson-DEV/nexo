package com.nexo.app.modules.rh;

import java.util.UUID;

public record BaseOperacionalResponse(
    UUID id,
    String nome,
    String uf,
    String endereco,
    CentroFaturamentoResponse centroFaturamento,
    Double latitude,
    Double longitude,
    Integer raio,
    boolean ativo
) {}
