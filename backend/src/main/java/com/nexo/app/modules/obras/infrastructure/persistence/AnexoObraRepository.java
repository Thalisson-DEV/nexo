package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.AnexoObra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnexoObraRepository
        extends JpaRepository<AnexoObra, UUID>, JpaSpecificationExecutor<AnexoObra> {

    List<AnexoObra> findByObraIdAndAtivoTrue(UUID idObra);

    Optional<AnexoObra> findByIdAndObraIdAndAtivoTrue(UUID id, UUID idObra);
}
