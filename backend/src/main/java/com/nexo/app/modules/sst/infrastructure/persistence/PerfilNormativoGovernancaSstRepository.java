package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.PerfilNormativoGovernancaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerfilNormativoGovernancaSstRepository extends
        JpaRepository<PerfilNormativoGovernancaSst, UUID>,
        JpaSpecificationExecutor<PerfilNormativoGovernancaSst> {

    boolean existsByCodigoNormalizadoAndVersaoPerfilAndAtivoTrue(String codigo, Integer versaoPerfil);

    Optional<PerfilNormativoGovernancaSst> findByIdAndAtivoTrue(UUID id);
}
