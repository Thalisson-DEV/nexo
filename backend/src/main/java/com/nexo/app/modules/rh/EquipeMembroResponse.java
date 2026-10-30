package com.nexo.app.modules.rh;

import com.nexo.app.modules.rh.domain.EquipeMembro;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Record para resposta de membros de uma equipe.
 */
public record EquipeMembroResponse(
    UUID id,
    UUID idColaborador,
    String nomeColaborador,
    EquipeMembro.FuncaoMembro funcao,
    OffsetDateTime dataEntrada,
    OffsetDateTime dataSaida,
    boolean vinculoAtivo
) {}
