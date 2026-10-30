package com.nexo.app.modules.rh.infrastructure.persistence;

import java.util.UUID;

/** Projecao minima das bases expostas no bootstrap de lookups. */
public interface BaseOperacionalLookupProjection {

    UUID getId();

    String getLabel();
}
