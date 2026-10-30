package com.nexo.app.modules.automacao.application.dto;

/** Um erro ou aviso do verificador. */
public record ProblemaResponse(
        String severidade,
        String mensagem,
        String noId,
        String param
) {
}
