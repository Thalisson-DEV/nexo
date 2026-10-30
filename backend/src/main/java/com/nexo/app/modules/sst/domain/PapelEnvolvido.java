package com.nexo.app.modules.sst.domain;

/**
 * Como uma pessoa participou do evento.
 *
 * <p>O papel é do vínculo, não da pessoa: o mesmo colaborador pode ser acidentado em
 * um evento e testemunha em outro.</p>
 */
public enum PapelEnvolvido {

    /** Sofreu a lesão. É o papel que os tipos com lesão exigem. */
    ACIDENTADO("Acidentado"),

    /** Presenciou o evento. */
    TESTEMUNHA("Testemunha"),

    /** Participou da ocorrência sem se lesionar. */
    ENVOLVIDO("Envolvido");

    private final String rotulo;

    PapelEnvolvido(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
