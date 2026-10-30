package com.nexo.app.modules.rh;

import java.util.Optional;
import java.util.UUID;

/**
 * API Pública do módulo de RH para consulta de funções por outros módulos.
 */
public interface FuncaoApi {

    /**
     * Verifica se uma função existe e está ativa.
     */
    boolean existeFuncao(UUID id);

    /**
     * Busca os dados de uma função por ID para exibição em outros módulos.
     */
    Optional<FuncaoResponse> buscarPorIdResponse(UUID id);
}
