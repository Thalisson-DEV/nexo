package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoResposta;

import java.util.List;
import java.util.UUID;

public record PerguntaResponse(
        UUID id,
        String enunciado,
        TipoResposta tipoResposta,
        boolean obrigatoria,
        int ordem,
        List<OpcaoRespostaResponse> opcoes
) {}
