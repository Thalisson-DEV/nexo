package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.EvidenciaPonto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvidenciaPontoRepository extends JpaRepository<EvidenciaPonto, UUID> {

    List<EvidenciaPonto> findByIdObraAndAtivoTrueOrderByDataCriacaoAsc(UUID idObra);

    Optional<EvidenciaPonto> findByIdAndIdObraAndAtivoTrue(UUID id, UUID idObra);

    Optional<EvidenciaPonto> findFirstByIdPontoAndIdModeloIsNotNullAndAtivoTrue(UUID idPonto);

    boolean existsByIdModelo(UUID idModelo);
}
