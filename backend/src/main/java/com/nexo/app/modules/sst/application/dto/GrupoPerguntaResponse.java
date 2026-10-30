package com.nexo.app.modules.sst.application.dto;

import java.util.List;
import java.util.UUID;

public record GrupoPerguntaResponse(
        UUID id,
        String titulo,
        int ordem,
        List<PerguntaResponse> perguntas
) {}
