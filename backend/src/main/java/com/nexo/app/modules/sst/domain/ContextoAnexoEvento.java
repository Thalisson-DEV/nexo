package com.nexo.app.modules.sst.domain;

/**
 * A que parte do {@link EventoSst} um anexo se refere.
 *
 * <p>Separado de {@link ContextoAnexo} (que é do desvio) porque as peças de um
 * acidente não são as de uma não-conformidade: aqui convivem a foto do local, o PDF
 * da CAT e o documento que comprova a tratativa.</p>
 */
public enum ContextoAnexoEvento {

    /** Fotos do local, boletim de ocorrência, croqui — a ocorrência em si. */
    EVENTO("Evento"),

    /** Comunicação de Acidente de Trabalho emitida. */
    CAT("CAT"),

    /** Comprovação do que foi feito depois: relatório, ata, evidência da ação. */
    TRATATIVA("Tratativa");

    private final String rotulo;

    ContextoAnexoEvento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
