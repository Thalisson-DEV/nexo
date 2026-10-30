package com.nexo.app.modules.financeiro.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * O que a solicitação tinha antes de a decisão ser desfeita. As colunas de decisão
 * são limpas no ato; isto é o que sobra para o histórico e para avisar quem decidiu.
 */
public record DecisaoDesfeita(
        StatusSolicitacao statusAnterior,
        UUID usuarioDecisaoAnterior,
        BigDecimal valorAprovadoAnterior) {

    public boolean eraAprovacao() {
        return statusAnterior == StatusSolicitacao.APROVADO;
    }
}
