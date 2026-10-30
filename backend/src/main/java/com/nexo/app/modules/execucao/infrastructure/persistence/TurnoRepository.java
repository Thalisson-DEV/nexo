package com.nexo.app.modules.execucao.infrastructure.persistence;

import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.execucao.domain.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TurnoRepository extends JpaRepository<Turno, UUID>, JpaSpecificationExecutor<Turno> {

    Optional<Turno> findFirstByAtivoTrueOrderByIdAsc();

    Optional<Turno> findFirstByIdEquipeAndStatusAndAtivoTrue(UUID idEquipe, StatusTurno status);

    boolean existsByIdEquipeAndStatusAndAtivoTrue(UUID idEquipe, StatusTurno status);

    List<Turno> findByStatusAndAtivoTrueAndDataInicioBefore(StatusTurno status, OffsetDateTime limite);

    @Query("SELECT t FROM Turno t "
           + "WHERE t.ativo = true AND t.idEquipe IN :idsEquipes "
           + "AND t.dataInicio < :fim AND (t.dataFim IS NULL OR t.dataFim >= :inicio) "
           + "ORDER BY t.dataInicio")
    List<Turno> listarSobrepostosAoPeriodo(@Param("inicio") OffsetDateTime inicio,
                                           @Param("fim") OffsetDateTime fim,
                                           @Param("idsEquipes") Collection<UUID> idsEquipes);
}
