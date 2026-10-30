package com.nexo.app.modules.sst.domain;

import java.util.UUID;

/** Retrato obtido exclusivamente da API pública do RH no ingresso. */
public record SnapshotMembroGovernancaSst(
        UUID idColaborador,
        String nome,
        UUID idFuncao,
        String funcao,
        UUID idBaseOperacional,
        String baseOperacional,
        String turnoDisponivel
) {
}
