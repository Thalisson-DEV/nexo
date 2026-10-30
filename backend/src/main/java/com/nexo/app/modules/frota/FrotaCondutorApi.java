package com.nexo.app.modules.frota;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta pública do histórico de condução. Multas, infrações de telemetria e avarias
 * usam para saber quem respondia pelo veículo num instante.
 */
public interface FrotaCondutorApi {

    /** Colaborador vinculado ao veículo no instante informado, se houver. */
    Optional<UUID> condutorEm(UUID idVeiculo, OffsetDateTime instante);
}
