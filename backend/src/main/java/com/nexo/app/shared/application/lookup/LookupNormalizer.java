package com.nexo.app.shared.application.lookup;

import java.text.Normalizer;
import java.util.Locale;

/** Normalização única para pesquisas e para a chave textual do cursor. */
public final class LookupNormalizer {

    private LookupNormalizer() {
    }

    /**
     * Remove espaços nas extremidades, acentos e transforma em minúsculas de
     * forma independente do locale da JVM. {@code null} permanece {@code null}.
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Alias em português para uso nos casos de uso do backend. */
    public static String normalizar(String value) {
        return normalize(value);
    }

    public static boolean isLongEnough(String value) {
        return LookupConstants.queryLongEnough(normalize(value));
    }
}
