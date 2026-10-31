package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.VaoPlanejamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VaoPlanejamentoRepository extends JpaRepository<VaoPlanejamento, UUID> {

    List<VaoPlanejamento> findByIdObraAndAtivoTrue(UUID idObra);

    Optional<VaoPlanejamento> findByIdAndIdObraAndAtivoTrue(UUID id, UUID idObra);
}
