package com.nexo.app.modules.automacao.application.usecases;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.nexo.app.modules.automacao.application.dto.PreviaEmailRequest;
import com.nexo.app.modules.automacao.application.dto.PreviaEmailResponse;
import com.nexo.app.modules.automacao.application.nos.NosAvisos;

/** Prévia do editor de e-mail: o layout institucional real, montado no servidor. */
@Service
public class PreviaEmailUseCases {

    private final NosAvisos avisos;

    public PreviaEmailUseCases(NosAvisos avisos) {
        this.avisos = avisos;
    }

    public PreviaEmailResponse previa(PreviaEmailRequest request) {
        Map<String, Object> params = request == null || request.params() == null ? Map.of() : request.params();
        NosAvisos.Previa previa = avisos.previa(params, request == null ? Map.of() : request.rotulos());
        return new PreviaEmailResponse(previa.assunto(), previa.html());
    }
}
