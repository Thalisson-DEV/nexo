package com.nexo.app.shared.domain.events;

import java.util.UUID;

/**
 * Evento de integração (Shared): RH notifica que um colaborador foi desativado.
 * O IAM deve usar este evento para desativar o usuário correspondente.
 */
public record ColaboradorDesativadoEvent(
    UUID colaboradorId,
    UUID usuarioId
) {
}
