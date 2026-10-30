package com.nexo.app.modules.rh;

import java.util.Optional;
import java.util.UUID;

/**
 * API Pública do módulo de RH para consulta de Centros de Faturamento (Centros de Custo) por outros módulos.
 */
public interface CentroFaturamentoApi {

    boolean existePorId(UUID id);

    Optional<CentroFaturamentoResponse> buscarPorIdResponse(UUID id);

    Optional<UUID> buscarIdPorNome(String nome);
}
