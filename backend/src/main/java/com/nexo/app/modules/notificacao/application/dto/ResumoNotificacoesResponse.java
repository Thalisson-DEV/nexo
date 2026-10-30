package com.nexo.app.modules.notificacao.application.dto;

import java.util.Map;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;

/**
 * Alimenta o sininho: o total não lido e a quebra por categoria.
 *
 * <p>Serve também de rede de segurança do WebSocket — se a conexão STOMP cair, o
 * front volta a consultar este resumo e o contador se corrige sozinho, sem
 * precisar recarregar a lista inteira.</p>
 */
public record ResumoNotificacoesResponse(
        long naoLidas,
        Map<CategoriaNotificacao, Long> naoLidasPorCategoria
) {
}
