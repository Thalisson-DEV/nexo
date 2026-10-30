package com.nexo.app.modules.automacao.domain;

/** De onde a automação veio: criada na tela, sugerida pelo Copiloto ou convertida de uma regra antiga. */
public enum OrigemAutomacao {
    USUARIO, COPILOTO, SISTEMA, REGRA_CONVERTIDA
}
