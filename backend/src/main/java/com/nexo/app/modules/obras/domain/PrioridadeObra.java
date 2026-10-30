package com.nexo.app.modules.obras.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

/**
 * Prioridade da obra na carteira, usada para ordenar a programação.
 */
public enum PrioridadeObra {
    BAIXA,
    NORMAL,
    ALTA,
    URGENTE;

    public static Optional<PrioridadeObra> interpretar(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String limpo = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        return switch (limpo) {
            case "BAIXA", "1" -> Optional.of(BAIXA);
            case "NORMAL", "MEDIA", "2" -> Optional.of(NORMAL);
            case "ALTA", "3" -> Optional.of(ALTA);
            case "URGENTE", "CRITICA", "4" -> Optional.of(URGENTE);
            default -> Optional.empty();
        };
    }
}
