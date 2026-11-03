package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.RelatorioTurno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelatorioTurnoRepository extends JpaRepository<RelatorioTurno, UUID> {

    Optional<RelatorioTurno> findByIdTurno(UUID idTurno);

    List<RelatorioTurno> findByIdTurnoInAndAtivoTrue(Collection<UUID> idsTurnos);
}
