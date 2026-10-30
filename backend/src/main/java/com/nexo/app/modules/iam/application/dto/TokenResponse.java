package com.nexo.app.modules.iam.application.dto;

import java.time.OffsetDateTime;

/**
 * @param token          JWT de acesso, curto, enviado como {@code Authorization: Bearer}.
 * @param sessaoExpiraEm até quando a sessão do aparelho pode ser renovada sem senha, se
 *                       continuar em uso. O refresh token em si só trafega no cookie HttpOnly.
 */
public record TokenResponse(String token, OffsetDateTime sessaoExpiraEm) {}
