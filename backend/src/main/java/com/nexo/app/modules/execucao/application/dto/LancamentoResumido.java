package com.nexo.app.modules.execucao.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projeção mínima de um lançamento de nota, para agregações (dashboards) que não
 * precisam hidratar equipe, ações e materiais de cada execução.
 *
 * <p>Não carrega a referência da nota: ela é cifrada e nenhum agregado precisa dela.</p>
 */
public record LancamentoResumido(
        UUID idExecucao,
        UUID idEquipe,
        UUID idTurno,
        /* Nulo nas execuções sem tipo de serviço informado. */
        UUID idTipoServico,
        OffsetDateTime dataLancamento,
        BigDecimal valor
) {
}
