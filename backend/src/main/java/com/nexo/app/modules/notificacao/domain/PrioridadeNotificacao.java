package com.nexo.app.modules.notificacao.domain;

/**
 * Urgência da notificação. Governa o destaque visual e, no caso de
 * {@link #CRITICA}, a capacidade de furar o silenciamento do usuário.
 */
public enum PrioridadeNotificacao {

    BAIXA("Baixa"),
    NORMAL("Normal"),
    ALTA("Alta"),
    CRITICA("Crítica");

    private final String rotulo;

    PrioridadeNotificacao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /**
     * Notificação crítica é entregue mesmo com a categoria silenciada.
     *
     * <p>Sem esta exceção, um usuário que desligou "Segurança do trabalho" deixaria
     * de receber um aviso de acidente — o silenciamento existe para cortar ruído,
     * não para desligar comunicação obrigatória.</p>
     */
    public boolean ignoraPreferencia() {
        return this == CRITICA;
    }
}
