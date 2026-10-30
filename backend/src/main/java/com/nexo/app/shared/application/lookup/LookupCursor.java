package com.nexo.app.shared.application.lookup;

import java.util.Objects;
import java.util.UUID;

/** Valores da última linha de uma página keyset. */
public record LookupCursor(String sortKey, UUID id) {

    public LookupCursor {
        sortKey = LookupNormalizer.normalize(sortKey);
        if (sortKey == null || sortKey.isBlank()) {
            throw new InvalidLookupCursorException("Cursor de lookup sem chave de ordenação");
        }
        Objects.requireNonNull(id, "Cursor de lookup sem ID");
    }
}
