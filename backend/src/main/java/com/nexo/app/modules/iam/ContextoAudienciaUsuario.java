package com.nexo.app.modules.iam;

import java.util.UUID;

/**
 * Os atributos de um usuário que servem para dizer se ele pertence a um público-alvo.
 *
 * <p>O tipo vai como texto de propósito: a enumeração {@code TipoUsuario} é interna
 * ao IAM e expô-la faria os módulos consumidores dependerem do domínio dele.</p>
 */
public record ContextoAudienciaUsuario(UUID idUsuario, UUID idPerfil, String tipoUsuario) {
}
