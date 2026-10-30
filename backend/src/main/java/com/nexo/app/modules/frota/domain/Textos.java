package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

/** Normalização de texto livre usada pelas entidades da frota. */
final class Textos {

    private Textos() {
    }

    static String obrigatorio(String valor, String campo, int max) {
        if (valor == null || valor.isBlank()) {
            throw new InvarianteVioladaException("Informe o " + campo + ".");
        }
        return limitar(valor.trim(), campo, max);
    }

    static String opcional(String valor, int max) {
        if (valor == null || valor.isBlank()) return null;
        return limitar(valor.trim(), "texto", max);
    }

    private static String limitar(String valor, String campo, int max) {
        if (valor.length() > max) {
            throw new InvarianteVioladaException("O " + campo + " deve ter no máximo " + max + " caracteres.");
        }
        return valor;
    }
}
