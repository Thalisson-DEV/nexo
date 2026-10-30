package com.nexo.app.modules.assistente.application.agent;

import com.nexo.app.shared.domain.exception.BaseBusinessException;
import org.springframework.http.HttpStatus;

/** O Copiloto nao pode atender agora (provedor de IA nao configurado ou desabilitado). */
public class AssistenteIndisponivelException extends BaseBusinessException {

    public AssistenteIndisponivelException(String detail) {
        super("Assistente indisponível", detail, HttpStatus.SERVICE_UNAVAILABLE, "assistente-indisponivel");
    }
}
