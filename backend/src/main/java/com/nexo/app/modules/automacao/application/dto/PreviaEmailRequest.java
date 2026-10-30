package com.nexo.app.modules.automacao.application.dto;

import java.util.Map;

/**
 * Prévia do e-mail do editor: os parâmetros do passo "Enviar e-mail" (assunto, área,
 * corpo, botão, anexos, destaque) e o nome amigável de cada dado do fluxo usado nele.
 */
public record PreviaEmailRequest(
        Map<String, Object> params,
        Map<String, String> rotulos
) {
}
