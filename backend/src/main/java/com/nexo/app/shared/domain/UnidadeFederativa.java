package com.nexo.app.shared.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import java.util.Locale;

/** Unidades federativas brasileiras aceitas nos contratos e no domínio. */
public enum UnidadeFederativa {
    AC, AL, AP, AM, BA, CE, DF, ES, GO, MA, MT, MS, MG, PA, PB, PR, PE, PI,
    RJ, RN, RS, RO, RR, SC, SP, SE, TO;

    public static final String REGEX =
            "(?i)^(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)$";

    public static String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new InvarianteVioladaException("Informe uma UF brasileira válida.");
        }
        String normalizada = valor.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalizada).name();
        } catch (IllegalArgumentException exception) {
            throw new InvarianteVioladaException("Informe uma UF brasileira válida.");
        }
    }

    public static boolean isValida(String valor) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        try {
            valueOf(valor.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
