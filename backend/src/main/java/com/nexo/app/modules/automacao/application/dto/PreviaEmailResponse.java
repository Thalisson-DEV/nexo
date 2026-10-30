package com.nexo.app.modules.automacao.application.dto;

/** O e-mail como vai chegar: assunto e HTML completo do layout institucional. */
public record PreviaEmailResponse(
        String assunto,
        String html
) {
}
