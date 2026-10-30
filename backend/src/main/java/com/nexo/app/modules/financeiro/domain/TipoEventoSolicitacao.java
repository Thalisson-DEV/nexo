package com.nexo.app.modules.financeiro.domain;

/**
 * O que aconteceu num ponto do histórico da solicitação. Cada valor corresponde a
 * uma transição de {@link StatusSolicitacao}.
 */
public enum TipoEventoSolicitacao implements Rotulavel {

    CRIADA("Aberta"),
    SUBMETIDA("Enviada para aprovação"),
    APROVADA("Aprovada"),
    REJEITADA("Rejeitada"),
    DECISAO_DESFEITA("Decisão desfeita"),
    CANCELADA("Cancelada");

    private final String rotulo;

    TipoEventoSolicitacao(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String getRotulo() {
        return rotulo;
    }
}
