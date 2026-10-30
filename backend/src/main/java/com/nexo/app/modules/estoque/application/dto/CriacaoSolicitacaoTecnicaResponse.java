package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;

import java.util.List;
import java.util.UUID;

public record CriacaoSolicitacaoTecnicaResponse(
        List<SolicitacaoCriada> solicitacoesCriadas
) {
    public CriacaoSolicitacaoTecnicaResponse {
        solicitacoesCriadas = solicitacoesCriadas == null ? List.of() : List.copyOf(solicitacoesCriadas);
    }

    public record SolicitacaoCriada(UUID id, TipoSolicitacaoTecnica tipo, String codigo) {
        public SolicitacaoCriada(TipoSolicitacaoTecnica tipo, UUID id) {
            this(id, tipo, tipo.getCodigo());
        }
    }
}
