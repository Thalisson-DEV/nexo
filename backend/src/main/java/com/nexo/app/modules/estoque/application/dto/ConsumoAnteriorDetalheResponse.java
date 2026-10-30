package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque.TipoMovimentacao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Como o consumo de um material desde a última solicitação foi calculado: cada movimentação do
 * estoque de destino no período e a conta do saldo NOVO, do início ao fim da janela.
 */
public record ConsumoAnteriorDetalheResponse(
        UUID idMaterial,
        /** Primeira entrega do material na última solicitação ou, sem ela, o começo dos últimos 90 dias. */
        OffsetDateTime inicio,
        OffsetDateTime fim,
        /** O estoque nunca recebeu o material por solicitação comercial: a janela é a dos últimos dias. */
        boolean semSolicitacaoAnterior,
        /** Saldo NOVO logo antes do início da janela. */
        BigDecimal saldoInicial,
        /** Saldo NOVO no fim da janela: saldoInicial mais as movimentações NOVO do período. */
        BigDecimal saldoFinal,
        BigDecimal saldoAtual,
        /** Movimentações do período em ordem cronológica, de qualquer condição. */
        List<Movimentacao> movimentacoes
) {

    public record Movimentacao(
            UUID id,
            TipoMovimentacao tipo,
            OffsetDateTime dataHora,
            /** Positiva quando entrou no estoque, negativa quando saiu. */
            BigDecimal quantidade,
            CondicaoMaterial condicao,
            String numSerie,
            UUID idReferenciaOperacao,
            /** Solicitação que originou a movimentação (entregas, estornos). */
            SolicitacaoRef solicitacao,
            /** O outro estoque da movimentação, quando houver (almoxarifado, outra equipe). */
            EstoqueRef contraparte,
            /** Nota em que o material foi aplicado ou retirado. */
            NotaRef nota
    ) {}

    public record SolicitacaoRef(UUID id, ContextoSolicitacaoMaterial contexto) {}

    public record EstoqueRef(UUID id, String nomeIdentificador) {}

    public record NotaRef(UUID idExecucao, String referencia, String tipoServico) {}
}
