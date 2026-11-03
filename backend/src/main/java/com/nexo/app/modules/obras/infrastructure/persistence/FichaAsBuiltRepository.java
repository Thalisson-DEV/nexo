package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.FichaAsBuilt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FichaAsBuiltRepository extends JpaRepository<FichaAsBuilt, UUID> {

    List<FichaAsBuilt> findByIdObraAndAtivoTrue(UUID idObra);

    Optional<FichaAsBuilt> findByIdPontoAndAtivoTrue(UUID idPonto);

    boolean existsByIdModelo(UUID idModelo);
}
