package com.nexo.app.modules.rh;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TipoEquipeResponse(
    UUID id,
    String nome,
    boolean ativo,
    Integer jornadaMinutos,
    Integer plataformaMinutos,
    OffsetDateTime dataCriacao,
    OffsetDateTime dataDesativacao
) {}
