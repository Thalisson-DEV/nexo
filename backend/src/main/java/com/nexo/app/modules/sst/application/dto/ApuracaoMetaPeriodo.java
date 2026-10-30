package com.nexo.app.modules.sst.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Meta esperada em um intervalo arbitrário, obtida pelo rateio diário das metas
 * vigentes. Serve de base para as séries do dashboard, que raramente coincidem
 * com as janelas de apuração das metas.
 *
 * @param metaPorDia       meta acumulada de todos os colaboradores em cada dia do intervalo
 * @param metaPorInspetor  meta do intervalo por usuário inspetor (só quem tem acesso ao sistema)
 * @param colaboradoresSemAcesso colaboradores com meta mas sem usuário vinculado — não conseguem lançar inspeções
 */
public record ApuracaoMetaPeriodo(
        BigDecimal metaTotal,
        Map<LocalDate, BigDecimal> metaPorDia,
        Map<UUID, BigDecimal> metaPorInspetor,
        long colaboradoresComMeta,
        long colaboradoresSemAcesso
) {

    public static ApuracaoMetaPeriodo vazia() {
        return new ApuracaoMetaPeriodo(BigDecimal.ZERO, Map.of(), Map.of(), 0, 0);
    }

    public boolean possuiMeta() {
        return metaTotal.signum() > 0;
    }
}
