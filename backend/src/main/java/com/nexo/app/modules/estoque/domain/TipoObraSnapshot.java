package com.nexo.app.modules.estoque.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Snapshot do tipo da obra; CORRETIVA/PREVENTIVA exigem ocorrencia, OBRA/LPT nao aceitam")
public enum TipoObraSnapshot {
    OBRA,
    CORRETIVA,
    PREVENTIVA,
    LPT
}
