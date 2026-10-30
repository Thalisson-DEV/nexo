package com.nexo.app.modules.chamado.domain;

import java.time.LocalDate;

/**
 * Recorte temporal das séries do dashboard de chamados. Quando não informada, é
 * escolhida automaticamente pelo tamanho do período consultado.
 */
public enum GranularidadeDashboard {

    DIA,
    MES,
    ANO;

    /** Primeiro dia do balde ao qual a data pertence. */
    public LocalDate inicioDoBalde(LocalDate data) {
        return switch (this) {
            case DIA -> data;
            case MES -> data.withDayOfMonth(1);
            case ANO -> data.withDayOfYear(1);
        };
    }

    public LocalDate proximoBalde(LocalDate inicioDoBalde) {
        return switch (this) {
            case DIA -> inicioDoBalde.plusDays(1);
            case MES -> inicioDoBalde.plusMonths(1);
            case ANO -> inicioDoBalde.plusYears(1);
        };
    }

    /**
     * Escolhe o recorte que mantém o gráfico legível: dias para períodos de até
     * ~2 meses, meses para até ~3 anos e anos acima disso.
     */
    public static GranularidadeDashboard automatica(long diasDoPeriodo) {
        if (diasDoPeriodo <= 62) {
            return DIA;
        }
        return diasDoPeriodo <= 1096 ? MES : ANO;
    }
}
