package com.nexo.app.modules.sst.application.dto;

import java.time.LocalDate;

/**
 * Um ponto da série temporal da acidentalidade. {@code data} é o início do balde
 * (dia, mês ou ano, conforme a granularidade), e o rótulo já vem pronto para o eixo.
 */
public record SerieEventosItem(
        LocalDate data,
        String rotulo,
        long total,
        long comLesao,
        long diasPerdidos
) {
}
