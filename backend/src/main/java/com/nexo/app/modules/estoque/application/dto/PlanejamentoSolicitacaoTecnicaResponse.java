package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado autocontido do replanejamento de uma unica solicitacao. */
@Schema(description = "Solicitacao atualizada apos substituicao do planejamento")
public record PlanejamentoSolicitacaoTecnicaResponse(
        SolicitacaoResponse solicitacaoAtualizada
) {
    public static PlanejamentoSolicitacaoTecnicaResponse de(SolicitacaoResponse detalhe) {
        return new PlanejamentoSolicitacaoTecnicaResponse(detalhe);
    }
}
