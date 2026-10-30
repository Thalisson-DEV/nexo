package com.nexo.app.modules.chamado.application.dto;

import java.time.LocalDate;

public record SerieTemporalChamadoItem(
        String chave,
        String rotulo,
        LocalDate data,
        long totalAbertos,
        long totalResolvidos,
        long totalVencidos
) {}
