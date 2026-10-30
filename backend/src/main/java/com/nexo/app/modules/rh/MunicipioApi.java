package com.nexo.app.modules.rh;

import com.nexo.app.modules.rh.MunicipioResponse;

import java.util.Optional;
import java.util.UUID;

/**
 * API Pública do módulo de RH para consulta de municípios por outros módulos.
 */
public interface MunicipioApi {

    /**
     * Busca os dados resumidos de um município por ID.
     */
    Optional<MunicipioResponse> buscarPorIdResponse(UUID id);

    /**
     * Busca o UUID do município pelo nome.
     */
    Optional<UUID> buscarIdPorNome(String nome);
}
