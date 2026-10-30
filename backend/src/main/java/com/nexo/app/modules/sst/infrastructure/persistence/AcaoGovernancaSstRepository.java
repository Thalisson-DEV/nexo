package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.AcaoGovernancaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcaoGovernancaSstRepository
        extends JpaRepository<AcaoGovernancaSst, UUID>, JpaSpecificationExecutor<AcaoGovernancaSst> {

    Optional<AcaoGovernancaSst> findByIdCorpoAndClientRequestId(UUID idCorpo, UUID clientRequestId);
}
