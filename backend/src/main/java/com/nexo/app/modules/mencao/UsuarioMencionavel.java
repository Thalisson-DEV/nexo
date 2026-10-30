package com.nexo.app.modules.mencao;

import java.util.UUID;

/**
 * Uma opção do autocomplete de marcação.
 *
 * <p>O {@code login} é o que se digita depois do {@code @}, então é ele — e não um
 * nome de exibição — que precisa aparecer na lista: escolher pelo nome e inserir o
 * login errado é o modo mais fácil de marcar a pessoa errada.</p>
 */
public record UsuarioMencionavel(UUID id, String login) {
}
