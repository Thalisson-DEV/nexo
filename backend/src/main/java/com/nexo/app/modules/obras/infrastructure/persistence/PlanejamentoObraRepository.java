package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.PlanejamentoObra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanejamentoObraRepository extends JpaRepository<PlanejamentoObra, UUID> {

    Optional<PlanejamentoObra> findByIdObraAndAtivoTrue(UUID idObra);
}
