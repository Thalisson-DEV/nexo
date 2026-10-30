package com.nexo.app.shared.domain.events;

import java.util.UUID;

/**
 * Evento de integração (Shared): IAM confirma ao RH que o usuário foi criado.
 */
public record UsuarioCriadoParaColaboradorEvent(
    UUID colaboradorId,
    UUID usuarioId
) {
}
