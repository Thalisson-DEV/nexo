package com.nexo.app.modules.notificacao.application.dto;

import java.time.OffsetDateTime;

/**
 * Janela de exibição escolhida na publicação.
 *
 * <p>{@code dataInicio} nula publica imediatamente; no futuro, agenda. {@code dataFim}
 * nula deixa no ar até alguém arquivar.</p>
 */
public record PublicarComunicadoRequest(
        OffsetDateTime dataInicio,
        OffsetDateTime dataFim
) {
}
