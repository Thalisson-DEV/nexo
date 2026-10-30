package com.nexo.app.modules.execucao.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Notas concluídas lançadas num turno, somadas. */
public record ProducaoDoTurno(
        UUID idTurno,
        Long notas,
        BigDecimal producao,
        OffsetDateTime primeiroLancamento,
        OffsetDateTime ultimoLancamento
) {
}
