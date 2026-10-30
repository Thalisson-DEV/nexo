package com.nexo.app.modules.contrato;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AcaoResponse(
    UUID id,
    String codigo,
    String nome,
    boolean ativo,
    OffsetDateTime dataCriacao,
    OffsetDateTime dataDesativacao
) {}
