package com.nexo.app.modules.automacao.application.dto;

import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** O rascunho montado: salvo como rascunho, nada roda até alguém ativar. */
public record RascunhoResponse(
        UUID automacaoId,
        String nome,
        TipoAutomacao tipo,
        String resumo,
        List<String> faltando,
        Map<String, Object> definicao,
        List<String> problemas
) {
}
