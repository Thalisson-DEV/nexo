package com.nexo.app.modules.financeiro.domain;

/**
 * Ciclo de conferência de um documento pela contabilidade.
 *
 * <pre>
 *   PENDENTE ──conferir───────▶ CONFERIDO ──arquivar──▶ ARQUIVADO
 *      │                            ▲                       │
 *      └──apontarPendencia──▶ COM_PENDENCIA ────────────────┘
 *                                   │        reabrir
 *                                   └──conferir──▶ CONFERIDO
 * </pre>
 *
 * <p>{@code COM_PENDENCIA} existe separado de {@code PENDENTE} porque as duas
 * situações cobram pessoas diferentes: a primeira espera a contabilidade olhar, a
 * segunda espera quem enviou corrigir. Somá-las numa fila só esconde de quem é a
 * vez de agir.</p>
 */
public enum SituacaoDocumento implements Rotulavel {

    /** Recebido e ainda não conferido pela contabilidade. */
    PENDENTE("Aguardando conferência", true),

    /** Conferido e aceito. */
    CONFERIDO("Conferido", false),

    /** Conferido e recusado: falta algo, e quem enviou precisa agir. */
    COM_PENDENCIA("Com pendência", true),

    /** Conferido e encerrado; sai das filas de trabalho e permanece consultável. */
    ARQUIVADO("Arquivado", false);

    private final String rotulo;
    private final boolean emAberto;

    SituacaoDocumento(String rotulo, boolean emAberto) {
        this.rotulo = rotulo;
        this.emAberto = emAberto;
    }

    @Override
    public String getRotulo() {
        return rotulo;
    }

    /** Indica que o documento ainda exige ação de alguém. */
    public boolean isEmAberto() {
        return emAberto;
    }
}
