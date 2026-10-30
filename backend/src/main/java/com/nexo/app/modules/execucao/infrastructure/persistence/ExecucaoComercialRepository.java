package com.nexo.app.modules.execucao.infrastructure.persistence;

import com.nexo.app.modules.execucao.application.dto.LancamentoResumido;
import com.nexo.app.modules.execucao.application.dto.ProducaoDoTurno;
import com.nexo.app.modules.execucao.application.dto.UltimoLancamentoDoTurno;
import com.nexo.app.modules.execucao.domain.ExecucaoComercial;
import com.nexo.app.modules.execucao.domain.StatusExecucao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExecucaoComercialRepository extends JpaRepository<ExecucaoComercial, UUID>, JpaSpecificationExecutor<ExecucaoComercial> {
    java.util.Optional<ExecucaoComercial> findFirstByAtivoTrueOrderByIdAsc();

    @Query("SELECT new com.nexo.app.modules.execucao.application.dto.LancamentoResumido("
           + "e.id, e.idEquipe, e.idTurno, e.idTipoServico, e.dataLancamento, e.valorTotalFaturado) "
           + "FROM ExecucaoComercial e "
           + "WHERE e.ativo = true AND e.statusExecucao = :status "
           + "AND e.dataLancamento >= :inicio AND e.dataLancamento < :fim "
           + "AND e.idEquipe IN :idsEquipes "
           + "ORDER BY e.dataLancamento")
    List<LancamentoResumido> listarLancamentosResumidos(@Param("inicio") OffsetDateTime inicio,
                                                        @Param("fim") OffsetDateTime fim,
                                                        @Param("idsEquipes") Collection<UUID> idsEquipes,
                                                        @Param("status") StatusExecucao status);

    List<ExecucaoComercial> findByIdTurnoAndAtivoTrueOrderByDataLancamentoAsc(UUID idTurno);

    @Query("SELECT new com.nexo.app.modules.execucao.application.dto.ProducaoDoTurno("
           + "e.idTurno, COUNT(e), COALESCE(SUM(e.valorTotalFaturado), 0), MIN(e.dataLancamento), MAX(e.dataLancamento)) "
           + "FROM ExecucaoComercial e "
           + "WHERE e.ativo = true AND e.statusExecucao = :status AND e.idTurno IN :idsTurnos "
           + "GROUP BY e.idTurno")
    List<ProducaoDoTurno> resumirPorTurnos(@Param("idsTurnos") Collection<UUID> idsTurnos,
                                           @Param("status") StatusExecucao status);

    @Query("SELECT new com.nexo.app.modules.execucao.application.dto.UltimoLancamentoDoTurno("
           + "e.idTurno, MAX(e.dataLancamento)) "
           + "FROM ExecucaoComercial e "
           + "WHERE e.ativo = true AND e.idTurno IN :idsTurnos "
           + "GROUP BY e.idTurno")
    List<UltimoLancamentoDoTurno> buscarUltimosLancamentos(@Param("idsTurnos") Collection<UUID> idsTurnos);
}
