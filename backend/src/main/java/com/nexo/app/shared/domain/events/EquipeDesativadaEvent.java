package com.nexo.app.shared.domain.events;

import java.util.UUID;

/**
 * Evento de integração (Shared): RH notifica que uma equipe foi desativada.
 * O IAM deve usar este evento para desativar o usuário correspondente.
 */
public record EquipeDesativadaEvent(
    UUID equipeId,
    UUID usuarioId
) {
}
