package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.ArquivoModeloAtaSst;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ArquivoModeloAtaSstRepository extends JpaRepository<ArquivoModeloAtaSst, UUID> {
    Optional<ArquivoModeloAtaSst> findByIdAndIdModeloAndAtivoTrue(UUID id, UUID idModelo);
    Optional<ArquivoModeloAtaSst> findFirstByIdModeloAndAtivoTrueOrderByDataCriacaoDesc(UUID idModelo);
}
