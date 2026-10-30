package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Leitura operacional da obra, sem expor a antiga raiz de agrupamento. */
public record ProjecaoSolicitacoesObraResponse(
        UUID idObra,
        TipoObraSnapshot tipoObra,
        List<OcorrenciaResponse> ocorrencias,
        List<SolicitacaoResumo> solicitacoes
) {
    public ProjecaoSolicitacoesObraResponse {
        ocorrencias = ocorrencias == null ? List.of() : List.copyOf(ocorrencias);
        solicitacoes = solicitacoes == null ? List.of() : List.copyOf(solicitacoes);
    }

    public record OcorrenciaResponse(
            UUID id,
            String codigo,
            List<SolicitacaoResumo> solicitacoes
    ) {
        public OcorrenciaResponse {
            solicitacoes = solicitacoes == null ? List.of() : List.copyOf(solicitacoes);
        }
    }

    public record SolicitacaoResumo(
            UUID id,
            TipoSolicitacaoTecnica tipo,
            SolicitacaoMaterial.StatusSolicitacao status,
            StatusSeparacao statusSeparacao,
            UUID idEquipe,
            LocalDate dataPrevista,
            BigDecimal quantidadeSolicitada,
            BigDecimal quantidadePendente
    ) {
    }
}
