package com.nexo.app.modules.notificacao.domain;

/**
 * Em que pé está a tentativa de entrega registrada em {@code logs_email}.
 */
public enum StatusEnvioEmail {

    /**
     * A linha foi aberta e o envio ainda não voltou.
     *
     * <p>Gravar antes de tentar é o que torna o registro honesto: se o processo cair
     * no meio do envio, a linha fica em PENDENTE — que é literalmente o que se sabe.
     * Registrar só depois faria uma mensagem possivelmente entregue desaparecer da
     * trilha como se nunca tivesse existido.</p>
     */
    PENDENTE("Aguardando envio"),

    ENVIADO("Enviado"),

    /** O provedor recusou. O motivo fica em {@code ultimo_erro} e a varredura retenta. */
    FALHOU("Falhou"),

    /** Esgotou as tentativas. Só sai daqui por ação humana. */
    DESISTIU("Desistiu após várias tentativas");

    private final String rotulo;

    StatusEnvioEmail(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
