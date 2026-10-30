package com.nexo.app.modules.automacao.application.dto;

import com.nexo.app.modules.automacao.domain.StatusAutomacao;
import com.nexo.app.modules.automacao.domain.TipoAutomacao;

/** Filtro da listagem de automações. */
public record AutomacaoFilter(
        String busca,
        TipoAutomacao tipo,
        StatusAutomacao status
) {
}
