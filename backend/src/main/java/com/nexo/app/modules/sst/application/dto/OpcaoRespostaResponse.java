package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GravidadeDesvio;

import java.util.UUID;

public record OpcaoRespostaResponse(
        UUID id,
        String texto,
        boolean geraDesvio,
        GravidadeDesvio gravidade,
        boolean padrao,
        int ordem
) {
    public OpcaoRespostaResponse(UUID id, String texto, boolean geraDesvio, GravidadeDesvio gravidade, int ordem) {
        this(id, texto, geraDesvio, gravidade, false, ordem);
    }
}
