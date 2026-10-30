package com.nexo.app.shared.domain.events;

import com.nexo.app.modules.iam.domain.TipoUsuario;
import java.util.UUID;

/**
 * Evento de integração (Shared): RH solicita ao IAM a criação de um usuário.
 */
public record AcessoColaboradorSolicitadoEvent(
    UUID colaboradorId,
    String login,
    String email,
    String telefone,
    String senha,
    UUID idPerfil,
    TipoUsuario tipo
) {
}
