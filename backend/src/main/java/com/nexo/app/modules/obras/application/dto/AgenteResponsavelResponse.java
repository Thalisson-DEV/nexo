package com.nexo.app.modules.obras.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AgenteResponsavelResponse(
        UUID id,
        String nome,
        String email,
        String telefone,
        String empresaOrgao,
        boolean ativo,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataUltimaAlteracao
) {
}
