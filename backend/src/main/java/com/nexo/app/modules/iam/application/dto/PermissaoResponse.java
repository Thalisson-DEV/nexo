package com.nexo.app.modules.iam.application.dto;

import java.util.UUID;

public record PermissaoResponse(
    UUID id,
    String nome
) {}
