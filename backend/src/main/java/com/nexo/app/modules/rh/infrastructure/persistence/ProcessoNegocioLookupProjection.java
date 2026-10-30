package com.nexo.app.modules.rh.infrastructure.persistence;

import java.util.UUID;

/** Projecao minima dos processos expostos no bootstrap de lookups. */
public interface ProcessoNegocioLookupProjection {

    UUID getId();

    String getLabel();
}
