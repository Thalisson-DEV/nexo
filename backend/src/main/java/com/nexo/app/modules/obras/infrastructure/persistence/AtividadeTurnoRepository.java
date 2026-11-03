package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.AtividadeTurno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtividadeTurnoRepository extends JpaRepository<AtividadeTurno, UUID> {

    List<AtividadeTurno> findByIdTurnoInAndAtivoTrue(Collection<UUID> idsTurnos);

    Optional<AtividadeTurno> findByIdAndIdTurnoAndAtivoTrue(UUID id, UUID idTurno);

    boolean existsByIdTurnoAndIdProgramacaoAndAtivoTrue(UUID idTurno, UUID idProgramacao);

    boolean existsByIdTurnoAndIdObraAndIdProgramacaoIsNullAndAtivoTrue(UUID idTurno, UUID idObra);
}
