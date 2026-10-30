package com.nexo.app.modules.sst.domain;

import java.util.UUID;

/**
 * O técnico de segurança responsável pelo evento, copiado do RH no registro.
 */
public record SnapshotTecnico(
        UUID idColaborador,
        String nome,
        String funcao
) {
}
