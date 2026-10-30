package com.nexo.app.modules.rh;

import com.nexo.app.shared.application.dto.LookupOptionDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API Pública do módulo de RH para consumo por outros módulos.
 */
public interface BaseOperacionalApi {
    boolean existePorNome(String nome);
    Optional<UUID> buscarIdPorNome(String nome);

    List<UUID> buscarIdsPorTrecho(String trecho);

    /**
     * Verifica se uma base operacional existe e está ativa.
     */
    boolean existeBase(UUID id);

    /**
     * Busca os dados de uma base operacional por ID para exibição em outros módulos.
     */
    Optional<BaseOperacionalResponse> buscarPorIdResponse(UUID id);

    /** Resolve bases em lote, sem expor entidades JPA e sem uma consulta por ID. */
    Map<UUID, BaseOperacionalResponse> buscarPorIdsResponse(Collection<UUID> ids);

    /** Lista minima, ordenada e restrita a bases ativas para o bootstrap de seletores. */
    List<LookupOptionDto> listarOpcoesAtivas();
}
