package com.nexo.app.modules.notificacao.domain;

/**
 * Meio pelo qual a notificação chega ao usuário.
 *
 * <p>Só {@link #IN_APP} está implementado. {@link #EMAIL} existe desde já para que
 * as preferências e as regras nasçam com a noção de canal — acrescentar o envio
 * depois é escrever um adaptador, não remodelar o que já estiver em produção.</p>
 */
public enum CanalNotificacao {

    IN_APP("Na aplicação"),
    EMAIL("E-mail");

    private final String rotulo;

    CanalNotificacao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
