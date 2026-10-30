package com.nexo.app.modules.notificacao.domain;

/**
 * Como o comunicado se impõe na tela. É a diferença entre interromper o usuário e
 * esperar que ele venha ler.
 */
public enum ApresentacaoComunicado {

    /** Interrompe: abre sobre a aplicação e precisa ser fechado. */
    MODAL("Modal ao entrar"),

    /** Faixa fixa no topo, sem bloquear o uso. */
    BANNER("Faixa no topo"),

    /** Só aparece para quem abre o mural. */
    CARD("Card no mural");

    private final String rotulo;

    ApresentacaoComunicado(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
