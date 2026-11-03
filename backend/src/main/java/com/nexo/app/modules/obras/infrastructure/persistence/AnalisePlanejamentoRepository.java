package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.AnalisePlanejamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalisePlanejamentoRepository extends JpaRepository<AnalisePlanejamento, UUID> {

    Optional<AnalisePlanejamento> findByIdAndIdObraAndAtivoTrue(UUID id, UUID idObra);

    Optional<AnalisePlanejamento> findFirstByIdObraAndPaginaOrderByDataCriacaoDesc(UUID idObra, Integer pagina);
}
