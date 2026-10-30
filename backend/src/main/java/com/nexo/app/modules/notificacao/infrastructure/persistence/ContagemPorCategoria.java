package com.nexo.app.modules.notificacao.infrastructure.persistence;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;

/**
 * Projeção do agrupamento de não lidas por categoria — evita trazer as entidades
 * só para contá-las.
 */
public interface ContagemPorCategoria {

    CategoriaNotificacao getCategoria();

    long getTotal();
}
