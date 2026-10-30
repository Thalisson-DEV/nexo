package com.nexo.app.modules.rh;

import com.nexo.app.shared.application.dto.LookupOptionDto;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

/**
 * API Pública do módulo de RH para consulta de processos de negócio por outros módulos.
 */
public interface ProcessoNegocioApi {

    /**
     * Verifica se um processo de negócio existe e está ativo.
     */
    boolean existeProcesso(UUID id);

    /**
     * Busca os dados de um processo de negócio por ID para exibição em outros módulos.
     */
    Optional<ProcessoNegocioResponse> buscarPorIdResponse(UUID id);

    /** Resolve pelo nome exato para integrações e importações entre módulos. */
    Optional<UUID> buscarIdPorNome(String nome);

    List<UUID> buscarIdsPorTrecho(String trecho);

    /** Lista minima, ordenada e restrita a processos ativos para o bootstrap de seletores. */
    List<LookupOptionDto> listarOpcoesAtivas();
}
