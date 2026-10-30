package com.nexo.app.modules.sst.domain;

/**
 * Origem da pessoa envolvida no evento: gente da casa ou de fora.
 *
 * <p>A distinção não é burocrática. Um {@link #COLABORADOR} tem cadastro no RH, e é o
 * id dele que permite agrupar eventos por pessoa, por base e por função sem depender
 * do nome — que aqui está cifrado. Um {@link #TERCEIRO} não tem cadastro: o que
 * identifica é o nome e a empresa, e por isso a empresa é obrigatória.</p>
 */
public enum TipoEnvolvido {

    COLABORADOR("Colaborador"),
    TERCEIRO("Terceiro / visitante");

    private final String rotulo;

    TipoEnvolvido(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
