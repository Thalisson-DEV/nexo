package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.ExecucaoChecklistGovernancaSst;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExecucaoChecklistGovernancaSstRepository extends
        JpaRepository<ExecucaoChecklistGovernancaSst, UUID>,
        JpaSpecificationExecutor<ExecucaoChecklistGovernancaSst> {

    boolean existsByIdFormulario(UUID idFormulario);

    Optional<ExecucaoChecklistGovernancaSst> findByIdCorpoAndClientRequestId(
            UUID idCorpo, UUID clientRequestId);

    @EntityGraph(attributePaths = {"respostas", "respostas.opcoes", "constatacoes"})
    Optional<ExecucaoChecklistGovernancaSst> findDetalhadaById(UUID id);
}
