package com.nexo.app.modules.sst.application.dto;

import java.time.LocalDate;

/**
 * Ponto de série temporal (contagem por dia) para gráficos de evolução.
 */
public record SerieTemporalItem(
        LocalDate data,
        long total
) {}
