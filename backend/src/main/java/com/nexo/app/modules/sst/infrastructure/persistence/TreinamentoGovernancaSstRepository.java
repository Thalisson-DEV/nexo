package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.TreinamentoGovernancaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TreinamentoGovernancaSstRepository extends JpaRepository<TreinamentoGovernancaSst, UUID>,
        JpaSpecificationExecutor<TreinamentoGovernancaSst> {

    Optional<TreinamentoGovernancaSst> findByIdAndAtivoTrue(UUID id);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByNomeNormalizadoAndAtivoTrue(String nomeNormalizado);

    List<TreinamentoGovernancaSst> findAllByIdIn(Collection<UUID> ids);
}
