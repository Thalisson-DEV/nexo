package com.nexo.app.modules.estoque.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * O que o estoque de destino pegou na última solicitação comercial de cada material e o que
 * fez com ele desde então: base para o backoffice avaliar um novo pedido.
 */
public record ConsumoAnteriorSolicitacaoResponse(
        UUID idSolicitacao,
        UUID idEstoque,
        /** Fim da janela de consumo: primeira entrega desta solicitação ou o momento da consulta. */
        OffsetDateTime consultadoAte,
        List<Item> itens
) {

    public record Item(
            UUID idMaterial,
            /** Null quando o estoque nunca recebeu o material por solicitação comercial. */
            UltimaSolicitacao ultimaSolicitacao,
            BigDecimal quantidadeAplicada,
            BigDecimal quantidadeDevolvida,
            /** Saldo NOVO logo antes da entrega da última solicitação; null quando não houve uma. */
            BigDecimal saldoAntes,
            /** Saldo NOVO atual do estoque de destino. */
            BigDecimal saldoAtual
    ) {}

    public record UltimaSolicitacao(
            UUID id,
            OffsetDateTime dataSolicitacao,
            /** Primeira entrega do material naquela solicitação: início da janela de consumo. */
            OffsetDateTime dataEntrega,
            BigDecimal quantidadeSolicitada,
            BigDecimal quantidadeRecebida
    ) {}
}
