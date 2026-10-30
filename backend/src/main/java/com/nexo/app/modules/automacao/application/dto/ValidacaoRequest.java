package com.nexo.app.modules.automacao.application.dto;

import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import java.util.Map;

/** Pedido de verificação de uma definição (o designer chama a cada edição). */
public record ValidacaoRequest(
        Map<String, Object> definicao,
        TipoAutomacao tipo
) {
}
