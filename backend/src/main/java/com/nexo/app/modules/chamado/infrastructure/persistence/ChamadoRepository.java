package com.nexo.app.modules.chamado.infrastructure.persistence;

import com.nexo.app.modules.chamado.domain.Chamado;
import com.nexo.app.modules.chamado.domain.StatusChamado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface ChamadoRepository extends JpaRepository<Chamado, UUID>, JpaSpecificationExecutor<Chamado> {

    Optional<Chamado> findFirstByAtivoTrueOrderByIdAsc();

    @Override
    @EntityGraph(attributePaths = {"topico", "anexos"})
    Optional<Chamado> findById(UUID id);

    @EntityGraph(attributePaths = {"topico", "anexos"})
    Optional<Chamado> findByIdAndUsuarioSolicitanteId(UUID id, UUID usuarioSolicitanteId);

    boolean existsByEquipeAtendimentoIdAndAtivoTrueAndStatusIn(
            UUID equipeId, Collection<StatusChamado> status);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT i.chamado.id FROM InteracaoChamado i WHERE i.tipo = com.nexo.app.modules.chamado.domain.TipoInteracaoChamado.REABERTURA AND i.chamado.id IN :ids")
    java.util.Set<UUID> findIdsComReabertura(@org.springframework.data.repository.query.Param("ids") Collection<UUID> ids);
}
