package com.nexo.app.modules.financeiro.domain;

/**
 * Grau de urgência declarado pelo solicitante. Usado para ordenar a fila de
 * aprovação e para os indicadores de atendimento.
 */
public enum GrauPrioridade implements Rotulavel {

    /** Pode aguardar o fluxo normal de compras. */
    BAIXA("Baixa"),

    /** Necessidade prevista, sem impacto operacional imediato. */
    NORMAL("Normal"),

    /** Impacto operacional próximo; priorizar. */
    ALTA("Alta"),

    /** Operação parada ou risco iminente. */
    URGENTE("Urgente");

    private final String rotulo;

    GrauPrioridade(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Texto exibido ao usuário; publicado no schema para a tela não replicá-lo. */
    @Override
    public String getRotulo() {
        return rotulo;
    }
}
