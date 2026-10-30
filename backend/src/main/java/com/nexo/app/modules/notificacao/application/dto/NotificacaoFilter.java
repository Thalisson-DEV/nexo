package com.nexo.app.modules.notificacao.application.dto;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;

/**
 * Filtros da caixa de entrada.
 *
 * <p>Não há filtro por usuário: o escopo é sempre o próprio autenticado, definido
 * no use case. Deixá-lo no filtro abriria a caixa de qualquer um a quem soubesse
 * montar a query string.</p>
 */
public record NotificacaoFilter(
        String buscaGlobal,
        CategoriaNotificacao categoria,
        PrioridadeNotificacao prioridade,
        Boolean apenasNaoLidas,
        Boolean incluirArquivadas
) {
}
