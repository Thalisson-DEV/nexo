package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Leitura operacional da obra, sem expor a antiga raiz de agrupamento. */
public record ProjecaoSolicitacoesObraResponse(
        UUID idObra,
        TipoObraSnapshot tipoObra,
        List<OcorrenciaResponse> ocorrencias,
        List<SolicitacaoResumo> solicitacoes,
        List<MovimentacaoResumo> movimentacoes,
        boolean movimentacoesTruncadas
) {
    public ProjecaoSolicitacoesObraResponse {
        ocorrencias = ocorrencias == null ? List.of() : List.copyOf(ocorrencias);
        solicitacoes = solicitacoes == null ? List.of() : List.copyOf(solicitacoes);
        movimentacoes = movimentacoes == null ? List.of() : List.copyOf(movimentacoes);
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
            BigDecimal quantidadePendente,
            OffsetDateTime dataSolicitacao,
            String almoxarifadoNome,
            List<ItemSolicitacaoResumo> itens
    ) {
        public SolicitacaoResumo {
            itens = itens == null ? List.of() : List.copyOf(itens);
        }
    }

    public record ItemSolicitacaoResumo(
            UUID idMaterial,
            String codigo,
            String descricao,
            String unidade,
            BigDecimal quantidade,
            BigDecimal quantidadeAtendida,
            BigDecimal quantidadePendente
    ) {
    }

    /** Uma operação de estoque da obra, com o que foi movimentado nela. */
    public record MovimentacaoResumo(
            UUID idReferencia,
            MovimentacaoEstoque.TipoMovimentacao tipo,
            TipoSolicitacaoTecnica tipoTecnico,
            OffsetDateTime dataHora,
            String origemNome,
            String destinoNome,
            UUID idSolicitacao,
            List<ItemMovimentacaoResumo> itens
    ) {
        public MovimentacaoResumo {
            itens = itens == null ? List.of() : List.copyOf(itens);
        }
    }

    public record ItemMovimentacaoResumo(
            String codigo,
            String descricao,
            String unidade,
            BigDecimal quantidade,
            CondicaoMaterial condicao,
            String numSerie
    ) {
    }
}
