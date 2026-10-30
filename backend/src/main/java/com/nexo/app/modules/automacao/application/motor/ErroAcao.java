package com.nexo.app.modules.automacao.application.motor;

/** Falha de um passo. Falhas {@code transitorias} seguem a política de repetição. */
public class ErroAcao extends RuntimeException {

    private final boolean transitoria;

    public ErroAcao(String mensagem) {
        this(mensagem, false);
    }

    public ErroAcao(String mensagem, boolean transitoria) {
        super(mensagem);
        this.transitoria = transitoria;
    }

    public boolean isTransitoria() {
        return transitoria;
    }
}
