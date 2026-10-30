package com.nexo.app.modules.execucao.application.dto;

import com.nexo.app.modules.execucao.domain.StatusExecucao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um lançamento de nota dentro do seu turno, com o que os relatórios precisam para
 * situá-lo (ordem, intervalo e distância até o anterior), sem hidratar ações e materiais.
 */
public record LancamentoDoTurno(
        UUID idExecucao,
        String referenciaNota,
        OffsetDateTime dataLancamento,
        StatusExecucao status,
        BigDecimal valor,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
