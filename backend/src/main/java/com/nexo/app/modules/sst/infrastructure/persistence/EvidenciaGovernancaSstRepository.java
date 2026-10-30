package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.EvidenciaGovernancaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvidenciaGovernancaSstRepository extends JpaRepository<EvidenciaGovernancaSst, UUID> {
    List<EvidenciaGovernancaSst> findByIdAcaoAndAtivoTrueOrderByDataCriacaoAsc(UUID idAcao);
    List<EvidenciaGovernancaSst> findByIdExecucaoAndAtivoTrueOrderByDataCriacaoAsc(UUID idExecucao);
    Optional<EvidenciaGovernancaSst> findByIdAndAtivoTrue(UUID id);
}
