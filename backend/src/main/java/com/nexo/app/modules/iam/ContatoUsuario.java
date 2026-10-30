package com.nexo.app.modules.iam;

import java.util.UUID;

/**
 * Como alcançar um usuário: login, e-mail (pode faltar — usuários de campo costumam não
 * ter) e o colaborador vinculado, que é por onde se chega à base, à função e à equipe.
 */
public record ContatoUsuario(UUID id, String login, String email, UUID idColaborador, boolean ativo) {
}
