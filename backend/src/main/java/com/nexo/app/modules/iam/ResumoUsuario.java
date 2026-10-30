package com.nexo.app.modules.iam;

import java.util.UUID;

/**
 * O mínimo que outro módulo precisa saber para exibir e endereçar um usuário: quem é
 * e por que nome ele é chamado.
 *
 * <p>Só o login, e não a entidade: quem consome isto está montando uma lista de
 * escolha, não administrando acesso. Devolver {@code Usuario} exporia perfil,
 * permissões e o hash da senha a módulos que não têm nada que ver com isso.</p>
 */
public record ResumoUsuario(UUID id, String login) {
}
