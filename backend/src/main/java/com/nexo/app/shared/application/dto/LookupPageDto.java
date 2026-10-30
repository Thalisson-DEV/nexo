package com.nexo.app.shared.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

/**
 * Página de um lookup. O cursor é opaco para o consumidor e só é preenchido
 * quando existe uma página seguinte.
 */
@Schema(description = "Página keyset de opções de lookup")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LookupPageDto<T>(
        @Schema(description = "Opções da página atual") List<T> items,
        @Schema(description = "Cursor opaco da próxima página; nulo na última página") String nextCursor,
        @Schema(description = "Indica se existe uma próxima página") boolean hasMore) {

    public LookupPageDto {
        items = List.copyOf(Objects.requireNonNull(items, "items é obrigatório"));
        if (!hasMore && nextCursor != null) {
            throw new IllegalArgumentException("nextCursor deve ser nulo na última página");
        }
        if (hasMore && (nextCursor == null || nextCursor.isBlank())) {
            throw new IllegalArgumentException("nextCursor é obrigatório quando há mais resultados");
        }
    }

    public static <T> LookupPageDto<T> empty() {
        return new LookupPageDto<>(List.of(), null, false);
    }
}
