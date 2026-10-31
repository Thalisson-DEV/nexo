package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PontoPlanejamentoRepository extends JpaRepository<PontoPlanejamento, UUID> {

    List<PontoPlanejamento> findByIdObraAndAtivoTrueOrderByPaginaAscNumeroAsc(UUID idObra);

    Optional<PontoPlanejamento> findByIdAndIdObraAndAtivoTrue(UUID id, UUID idObra);

    boolean existsByIdObraAndNumeroAndAtivoTrue(UUID idObra, String numero);
}
