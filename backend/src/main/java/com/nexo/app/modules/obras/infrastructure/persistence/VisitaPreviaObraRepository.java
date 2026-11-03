package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.VisitaPreviaObra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VisitaPreviaObraRepository extends JpaRepository<VisitaPreviaObra, UUID> {

    List<VisitaPreviaObra> findByIdObraAndAtivoTrueOrderByDataVisitaDescDataCriacaoDesc(UUID idObra);

    Optional<VisitaPreviaObra> findByIdAndIdObraAndAtivoTrue(UUID id, UUID idObra);

    boolean existsByIdModelo(UUID idModelo);
}
