package com.nexo.app.modules.automacao.application.dto;

import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import java.util.List;

/** Resultado do verificador: problemas por nó e o que impede a ativação. */
public record ValidacaoResponse(
        List<ProblemaResponse> problemas,
        List<String> ativacao,
        String erroEstrutura,
        TipoAutomacao tipoSugerido
) {
}
