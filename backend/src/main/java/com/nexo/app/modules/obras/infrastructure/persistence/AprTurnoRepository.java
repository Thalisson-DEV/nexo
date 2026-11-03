package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.AprTurno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AprTurnoRepository extends JpaRepository<AprTurno, UUID> {

    List<AprTurno> findByIdTurnoInAndAtivoTrue(Collection<UUID> idsTurnos);

    Optional<AprTurno> findByIdAndIdTurnoAndAtivoTrue(UUID id, UUID idTurno);
}
