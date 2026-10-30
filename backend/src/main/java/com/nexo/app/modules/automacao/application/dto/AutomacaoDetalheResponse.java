package com.nexo.app.modules.automacao.application.dto;

import java.util.List;
import java.util.Map;

/** Automação completa para o detalhe e o designer. */
public record AutomacaoDetalheResponse(
        AutomacaoResumoResponse resumo,
        Map<String, Object> definicao,
        List<VersaoResponse> versoes,
        List<String> problemasAtivacao
) {
}
