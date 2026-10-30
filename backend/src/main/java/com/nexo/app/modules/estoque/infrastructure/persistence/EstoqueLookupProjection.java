package com.nexo.app.modules.estoque.infrastructure.persistence;

import java.util.UUID;

/** Colunas leves e deterministicas do autocomplete de estoques. */
public interface EstoqueLookupProjection {
    UUID getId();
    String getNomeIdentificador();
    String getSortKey();
}

