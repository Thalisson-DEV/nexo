package com.nexo.app.shared.domain.events;

import java.util.UUID;

/**
 * Evento de integração (Shared): RH solicita ao IAM a criação de um usuário 
 * para uma Equipe de Campo. O login será o código da equipe (ex: SJP433).
 */
public record AcessoEquipeSolicitadoEvent(
    UUID equipeId,
    String codEquipe,
    String senhaPadrao,
    UUID idPerfil
) {
}
