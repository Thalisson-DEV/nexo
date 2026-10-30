package com.nexo.app.modules.execucao.application.dto;

import com.nexo.app.modules.execucao.domain.DetalhesEventoNota;
import com.nexo.app.modules.execucao.domain.OrigemAlteracaoNota;
import com.nexo.app.modules.execucao.domain.TipoEventoNota;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Um ponto da linha do tempo da nota. */
public record EventoHistoricoNotaResponse(
        /* Nulo no lançamento reconstruído de notas anteriores ao histórico. */
        UUID id,
        TipoEventoNota tipo,
        OrigemAlteracaoNota origem,
        String autor,
        String motivo,
        BigDecimal valorAnterior,
        BigDecimal valorNovo,
        DetalhesEventoNota detalhes,
        OffsetDateTime ocorridoEm,
        OffsetDateTime registradoEm,
        /* Lançamento anterior ao histórico: montado a partir da nota, sem o retrato original. */
        boolean reconstruido
) {
}
