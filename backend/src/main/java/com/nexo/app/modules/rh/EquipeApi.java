package com.nexo.app.modules.rh;

import com.nexo.app.modules.rh.EquipeResponse;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * API Pública do módulo de RH para consulta de equipes por outros módulos.
 */
public interface EquipeApi {
    record EquipePublica(
            UUID id,
            String codigo,
            boolean ativa,
            UUID idBaseOperacional,
            UUID idProcesso,
            UUID idUsuarioResponsavel,
            UUID idContrato
    ) {
    }

    /** Snapshot mínimo, sem entidades ou DTOs internos de RH. */
    Optional<EquipePublica> buscarPublicaPorId(UUID id);

    default Optional<EquipePublica> buscarAtivaPorId(UUID idEquipe) {
        if (idEquipe == null) {
            return Optional.empty();
        }
        return buscarPublicaPorId(idEquipe).filter(EquipePublica::ativa);
    }

    /** Resolve somente uma equipe ativa cujo ID faça parte do vínculo informado pela obra. */
    default Optional<EquipePublica> buscarAtivaSeVinculada(UUID idEquipe,
                                                           Collection<UUID> idsEquipesVinculadas) {
        if (idEquipe == null || idsEquipesVinculadas == null || !idsEquipesVinculadas.contains(idEquipe)) {
            return Optional.empty();
        }
        return buscarPublicaPorId(idEquipe).filter(EquipePublica::ativa);
    }

    boolean existePorCodigo(String codigo);
    Optional<UUID> buscarIdPorCodigo(String codigo);
    List<UUID> buscarIdsPorFiltros(UUID idBaseOperacional, UUID idProcesso);

    /**
     * Busca os dados completos de uma equipe por ID.
     */
    Optional<EquipeResponse> buscarPorIdResponse(UUID id);

    /**
     * Busca os dados completos de várias equipes de uma vez, para quem monta listas.
     * Ids sem equipe correspondente ficam fora do resultado.
     */
    List<EquipeResponse> buscarPorIdsResponse(Collection<UUID> ids);

    /**
     * Busca os IDs das equipes vinculadas a uma lista de bases operacionais.
     */
    List<UUID> buscarIdsPorBases(List<UUID> idBases);

    /**
     * Busca o ID do contrato vinculado à equipe.
     */
    Optional<UUID> buscarContratoDaEquipe(UUID idEquipe);

    /**
     * Busca o ID da equipe onde o usuário é o responsável.
     */
    Optional<UUID> buscarIdEquipePorUsuario(UUID idUsuario);

    List<EquipeResponse> listarEquipesAtivas();

    /**
     * Verifica se o colaborador informado é um membro ativo da equipe indicada.
     */
    boolean isMembroAtivo(UUID idEquipe, UUID idColaborador);
}
