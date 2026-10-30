package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GravidadeDesvio;

import java.util.Map;

public record FaixaBacklog(
        String faixa,
        int idadeMinimaDias,
        Integer idadeMaximaDias,
        long total,
        Map<GravidadeDesvio, Long> porGravidade
) {}
