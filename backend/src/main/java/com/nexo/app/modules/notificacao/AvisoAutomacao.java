package com.nexo.app.modules.notificacao;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pedido de aviso feito por uma automação.
 *
 * @param categoria  nome de {@code CategoriaNotificacao} (APROVACAO, OPERACIONAL…)
 * @param prioridade nome de {@code PrioridadeNotificacao} (BAIXA, NORMAL, ALTA, CRITICA)
 * @param chaveIdempotencia mesma chave, mesmo aviso: o reprocessamento não duplica
 */
public record AvisoAutomacao(
        Set<UUID> destinatarios,
        UUID idAutomacao,
        UUID idReferencia,
        String categoria,
        String prioridade,
        String titulo,
        String corpo,
        String deeplink,
        Map<String, Object> contexto,
        String chaveIdempotencia,
        boolean email,
        String chaveGatilho
) {
}
