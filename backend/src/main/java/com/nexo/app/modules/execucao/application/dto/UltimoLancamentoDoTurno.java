package com.nexo.app.modules.execucao.application.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Hora do último lançamento ativo de um turno, qualquer que seja o status da execução. */
public record UltimoLancamentoDoTurno(UUID idTurno, OffsetDateTime dataLancamento) {
}
