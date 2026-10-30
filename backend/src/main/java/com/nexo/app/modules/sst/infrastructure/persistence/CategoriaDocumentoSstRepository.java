package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.CategoriaDocumentoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoriaDocumentoSstRepository
        extends JpaRepository<CategoriaDocumentoSst, UUID>, JpaSpecificationExecutor<CategoriaDocumentoSst> {

    Optional<CategoriaDocumentoSst> findByIdAndAtivoTrue(UUID id);

    Optional<CategoriaDocumentoSst> findByCodigoAndAtivoTrue(String codigo);

    List<CategoriaDocumentoSst> findAllByIdIn(Collection<UUID> ids);

    boolean existsByCodigoAndAtivoTrue(String codigo);

    boolean existsByCodigo(String codigo);
}
