package com.nexo.app.modules.assistente.application.dto;

import jakarta.validation.constraints.NotBlank;

/** Parametros do pedido de autorizacao do OAuth, repassados pela tela de consentimento. */
public record AutorizarIaRequest(
        @NotBlank String responseType,
        @NotBlank String clientId,
        @NotBlank String redirectUri,
        String codeChallenge,
        String codeChallengeMethod,
        String state,
        String scope,
        String resource,
        boolean permiteEscrita) {
}
