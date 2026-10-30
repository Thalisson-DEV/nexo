package com.nexo.app.shared.application.analytics;

import java.util.Set;
import java.util.UUID;

public record SemanticQueryContext(UUID usuarioId, Set<String> autoridades) {
    public SemanticQueryContext {
        autoridades = autoridades == null ? Set.of() : Set.copyOf(autoridades);
    }

    public boolean possuiAutoridade(String autoridade) {
        return autoridades.contains(autoridade);
    }
}
