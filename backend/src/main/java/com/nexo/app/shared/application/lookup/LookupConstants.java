package com.nexo.app.shared.application.lookup;

/** Constantes e regras comuns a endpoints de lookup. */
public final class LookupConstants {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
    public static final int MIN_QUERY_LENGTH = 2;

    private LookupConstants() {
    }

    /**
     * Aplica o contrato de limite: ausência ou valor não positivo usa o default;
     * valores abusivos são limitados ao teto.
     */
    public static int effectiveLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    public static boolean queryLongEnough(String normalizedQuery) {
        return normalizedQuery != null && normalizedQuery.length() >= MIN_QUERY_LENGTH;
    }
}
