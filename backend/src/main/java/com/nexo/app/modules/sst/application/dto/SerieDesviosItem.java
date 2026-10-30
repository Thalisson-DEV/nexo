package com.nexo.app.modules.sst.application.dto;

import java.time.LocalDate;

public record SerieDesviosItem(
        LocalDate data,
        long abertos,
        long resolvidos,
        long backlogFimDoDia,
        long vencidosFimDoDia
) {}
