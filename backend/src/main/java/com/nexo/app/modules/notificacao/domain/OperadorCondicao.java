package com.nexo.app.modules.notificacao.domain;

/**
 * Operadores disponíveis para as condições de uma regra.
 *
 * <p>Catálogo fechado de propósito. A alternativa — deixar o administrador escrever
 * SpEL ou um fragmento de SQL — daria mais poder e transformaria a tela de regras
 * numa execução de código arbitrário por quem tem acesso administrativo.</p>
 */
public enum OperadorCondicao {

    IGUAL("é igual a"),
    DIFERENTE("é diferente de"),
    MAIOR_QUE("é maior que"),
    MAIOR_OU_IGUAL("é maior ou igual a"),
    MENOR_QUE("é menor que"),
    MENOR_OU_IGUAL("é menor ou igual a"),
    CONTEM("contém"),
    EM("está entre os valores");

    private final String rotulo;

    OperadorCondicao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Operadores que só fazem sentido sobre números. */
    public boolean exigeNumero() {
        return this == MAIOR_QUE || this == MAIOR_OU_IGUAL
                || this == MENOR_QUE || this == MENOR_OU_IGUAL;
    }
}
