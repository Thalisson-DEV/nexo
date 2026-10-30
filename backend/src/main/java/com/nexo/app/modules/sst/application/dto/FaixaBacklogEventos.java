package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.StatusEventoSst;

import java.util.Map;

/**
 * Eventos ainda abertos, agrupados por quanto tempo faz que ocorreram.
 *
 * <p>A idade é contada da <b>ocorrência</b>, e não do registro: é o tempo que o fato
 * está sem desfecho que importa para quem cobra tratativa.</p>
 */
public record FaixaBacklogEventos(
        String faixa,
        int idadeMinimaDias,
        Integer idadeMaximaDias,
        long total,
        Map<StatusEventoSst, Long> porStatus
) {
}
