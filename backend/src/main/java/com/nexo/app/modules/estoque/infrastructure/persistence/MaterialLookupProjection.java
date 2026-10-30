package com.nexo.app.modules.estoque.infrastructure.persistence;

import java.util.UUID;

/**
 * Colunas estritamente necessarias para o autocomplete de materiais.
 *
 * <p>O lookup não deve materializar {@code Material}, pois isso também carregaria
 * relacionamentos e os campos que não fazem parte do seletor.</p>
 */
public interface MaterialLookupProjection {

    UUID getId();

    String getCodigo();

    String getDescricao();

    /** Chave de ordenação já calculada pelo PostgreSQL, usada no cursor. */
    String getSortKey();
}
