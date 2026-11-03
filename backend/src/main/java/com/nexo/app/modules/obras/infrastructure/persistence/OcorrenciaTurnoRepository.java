package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.OcorrenciaTurno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OcorrenciaTurnoRepository extends JpaRepository<OcorrenciaTurno, UUID> {

    List<OcorrenciaTurno> findByIdTurnoInAndAtivoTrue(Collection<UUID> idsTurnos);

    Optional<OcorrenciaTurno> findByIdAndIdTurnoAndAtivoTrue(UUID id, UUID idTurno);
}
