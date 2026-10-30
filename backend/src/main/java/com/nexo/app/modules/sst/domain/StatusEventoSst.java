package com.nexo.app.modules.sst.domain;

/**
 * Ciclo de vida de um {@link EventoSst}.
 *
 * <p>Deliberadamente curto. A investigação estruturada (causa raiz e plano de ação)
 * entra em uma fase seguinte como uma constante a mais entre {@link #REGISTRADO} e
 * {@link #EM_TRATATIVA} — o contrato de hoje não impede isso.</p>
 */
public enum StatusEventoSst {

    /** Comunicado e registrado; ainda sem tratativa. */
    REGISTRADO("Registrado"),

    /** Em análise/tratamento pelo SESMT. */
    EM_TRATATIVA("Em tratativa"),

    /** Tratativa encerrada. O registro fica imutável até ser reaberto. */
    CONCLUIDO("Concluído"),

    /** Registro indevido ou duplicado. Continua legível e sai dos indicadores. */
    CANCELADO("Cancelado");

    private final String rotulo;

    StatusEventoSst(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Um evento concluído ou cancelado não aceita edição — precisa ser reaberto antes. */
    public boolean permiteEdicao() {
        return this == REGISTRADO || this == EM_TRATATIVA;
    }

    /** Cancelados não entram em nenhum indicador de acidentalidade. */
    public boolean contaNosIndicadores() {
        return this != CANCELADO;
    }
}
