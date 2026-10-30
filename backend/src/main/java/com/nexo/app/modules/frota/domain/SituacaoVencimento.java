package com.nexo.app.modules.frota.domain;

import java.time.LocalDate;

/** Como um documento ou CNH está em relação ao vencimento. */
public enum SituacaoVencimento {
    /** Sem data de vencimento. */
    SEM_VENCIMENTO,
    VIGENTE,
    /** Dentro da maior faixa de aviso. */
    A_VENCER,
    VENCIDO;

    public static SituacaoVencimento calcular(LocalDate vencimento, LocalDate hoje, int diasAviso) {
        if (vencimento == null) return SEM_VENCIMENTO;
        if (vencimento.isBefore(hoje)) return VENCIDO;
        if (!vencimento.isAfter(hoje.plusDays(diasAviso))) return A_VENCER;
        return VIGENTE;
    }
}
