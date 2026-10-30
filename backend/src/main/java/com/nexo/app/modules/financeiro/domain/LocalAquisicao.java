package com.nexo.app.modules.financeiro.domain;

/**
 * Onde a aquisição será efetivada — na unidade que solicitou (base ou obra) ou
 * centralizada na matriz.
 */
public enum LocalAquisicao implements Rotulavel {

    /** Na própria base / na própria obra. */
    LOCAL("Local — na própria base/obra"),

    /** Na central / matriz. */
    CENTRAL("Central — matriz");

    private final String rotulo;

    LocalAquisicao(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Texto exibido ao usuário; publicado no schema para a tela não replicá-lo. */
    @Override
    public String getRotulo() {
        return rotulo;
    }
}
