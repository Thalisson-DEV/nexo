package com.nexo.app.modules.automacao.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Pedido de aprovação aberto por uma automação. */
public record AprovacaoResponse(
        UUID id,
        UUID automacaoId,
        String automacaoNome,
        UUID execucaoId,
        String titulo,
        String detalhes,
        List<String> opcoes,
        String status,
        String resposta,
        String comentario,
        String respondidaPor,
        OffsetDateTime respondidaEm,
        OffsetDateTime expiraEm,
        OffsetDateTime criadaEm,
        boolean podeResponder
) {
}
