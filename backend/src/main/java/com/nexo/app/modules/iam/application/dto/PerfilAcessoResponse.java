package com.nexo.app.modules.iam.application.dto;

import java.util.UUID;

public record PerfilAcessoResponse(
    UUID id,
    String nome,
    boolean ativo
) {}
