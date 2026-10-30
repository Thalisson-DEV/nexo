package com.nexo.app.modules.notificacao.domain;

/**
 * Natureza do comunicado. Governa o ícone e a cor no mural, e é o filtro que o
 * usuário usa para reencontrar algo que já leu.
 */
public enum TipoComunicado {

    DICA("Dica de uso"),
    CHANGELOG("Novidades da versão"),
    AVISO("Aviso"),
    MANUTENCAO("Manutenção programada"),
    URGENTE("Urgente");

    private final String rotulo;

    TipoComunicado(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
