package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.DesvioSeguranca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface DesvioSegurancaRepository
        extends JpaRepository<DesvioSeguranca, UUID>, JpaSpecificationExecutor<DesvioSeguranca> {
    Optional<DesvioSeguranca> findFirstByAtivoTrueOrderByIdAsc();
}
