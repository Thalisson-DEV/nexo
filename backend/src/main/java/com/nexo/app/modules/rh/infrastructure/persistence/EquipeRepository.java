package com.nexo.app.modules.rh.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexo.app.modules.rh.domain.Equipe;

@Repository
public interface EquipeRepository extends JpaRepository<Equipe, UUID>, JpaSpecificationExecutor<Equipe> {
    Optional<Equipe> findByCodigoIgnoreCase(String codigo);
    boolean existsByCodigoIgnoreCase(String codigo);
    @Query("SELECT e FROM Equipe e LEFT JOIN e.membros m " +
           "WHERE (e.idUsuario = :idUsuario AND e.ativo = true) " +
           "OR (m.colaborador.idUsuario = :idUsuario AND m.dataSaida IS NULL AND e.ativo = true)")
    Optional<Equipe> findEquipePorUsuario(@Param("idUsuario") UUID idUsuario);

    @Query("SELECT e.id FROM Equipe e WHERE " +
            "(:idBaseOperacional IS NULL OR e.baseOperacional.id = :idBaseOperacional) AND " +
            "(:idProcesso IS NULL OR e.processo.id = :idProcesso)")
    List<UUID> findIdsByFiltros(@Param("idBaseOperacional") UUID idBaseOperacional, 
                                @Param("idProcesso") UUID idProcesso);

    @Query("SELECT e.id FROM Equipe e WHERE e.baseOperacional.id IN :idBases")
    List<UUID> findIdsByIdBaseOperacionalIn(@Param("idBases") List<UUID> idBases);

    List<Equipe> findAllByBaseOperacionalIdInAndAtivoTrue(Collection<UUID> idBases);

    List<Equipe> findAllByAtivoTrue();
}
