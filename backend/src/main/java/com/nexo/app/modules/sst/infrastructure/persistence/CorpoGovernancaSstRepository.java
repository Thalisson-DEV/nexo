package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.CorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CorpoGovernancaSstRepository extends
        JpaRepository<CorpoGovernancaSst, UUID>, JpaSpecificationExecutor<CorpoGovernancaSst> {

    Optional<CorpoGovernancaSst> findByIdAndAtivoTrue(UUID id);

    boolean existsByTipoAndIdBaseOperacionalAndUnidadeGovernancaNormalizadaAndStatusAndAtivoTrue(
            TipoCorpoGovernancaSst tipo,
            UUID idBaseOperacional,
            String unidadeGovernancaNormalizada,
            StatusCorpoGovernancaSst status);
}
