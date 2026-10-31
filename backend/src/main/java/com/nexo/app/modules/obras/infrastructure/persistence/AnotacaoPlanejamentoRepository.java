package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.AnotacaoPlanejamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnotacaoPlanejamentoRepository extends JpaRepository<AnotacaoPlanejamento, UUID> {

    List<AnotacaoPlanejamento> findByIdObraAndAtivoTrue(UUID idObra);

    Optional<AnotacaoPlanejamento> findByIdAndIdObraAndAtivoTrue(UUID id, UUID idObra);
}
