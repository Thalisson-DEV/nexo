package com.nexo.app.modules.notificacao.application.dto;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;

/**
 * Preferência de uma categoria. A listagem devolve sempre todas as categorias
 * existentes — as que o usuário nunca tocou vêm com o padrão — para que a tela
 * possa ser montada direto da resposta.
 */
public record PreferenciaNotificacaoResponse(
        CategoriaNotificacao categoria,
        String categoriaRotulo,
        boolean inApp,
        boolean email,
        /**
         * Se a categoria aceita o canal de e-mail. {@code false} apenas em
         * {@code MENCAO} — a tela deve desabilitar o interruptor, e não apenas
         * desmarcá-lo, porque a recusa é do domínio e não uma escolha do usuário.
         */
        boolean permiteEmail
) {
}
