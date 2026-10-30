package com.nexo.app.modules.notificacao.application.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.OrigemNotificacao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;

/**
 * Uma notificação como o frontend a exibe. É também o payload enviado pelo
 * WebSocket, para que a chegada em tempo real e o carregamento por REST produzam
 * exatamente o mesmo objeto na tela.
 */
public record NotificacaoResponse(
        UUID id,
        OrigemNotificacao origem,
        UUID idReferencia,
        CategoriaNotificacao categoria,
        String categoriaRotulo,
        PrioridadeNotificacao prioridade,
        String prioridadeRotulo,
        String titulo,
        String corpo,
        String deeplink,
        Map<String, Object> contexto,
        boolean lida,
        OffsetDateTime lidaEm,
        OffsetDateTime expiraEm,
        OffsetDateTime dataCriacao
) {
}
