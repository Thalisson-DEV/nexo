package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.AnexoTurno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnexoTurnoRepository extends JpaRepository<AnexoTurno, UUID> {

    List<AnexoTurno> findByIdTurnoAndAtivoTrue(UUID idTurno);

    Optional<AnexoTurno> findByIdAndIdTurnoAndAtivoTrue(UUID id, UUID idTurno);
}
