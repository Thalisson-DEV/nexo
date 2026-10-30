package com.nexo.app.modules.automacao.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.automacao.domain.ExecucaoAutomacao;
import com.nexo.app.modules.automacao.domain.StatusExecucao;

public interface ExecucaoAutomacaoRepository extends JpaRepository<ExecucaoAutomacao, UUID> {

    boolean existsByIdAutomacaoAndChaveDisparo(UUID idAutomacao, String chaveDisparo);

    Page<ExecucaoAutomacao> findByIdAutomacaoOrderByCriadaEmDesc(UUID idAutomacao, Pageable pageable);

    /**
     * Toma a execução para este servidor por alguns minutos. A atualização é atômica: dois
     * servidores (ou o tick e a fila) nunca executam a mesma execução ao mesmo tempo.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ExecucaoAutomacao e
               SET e.status = com.nexo.app.modules.automacao.domain.StatusExecucao.EXECUTANDO,
                   e.leaseAte = :ate,
                   e.iniciadaEm = COALESCE(e.iniciadaEm, :agora)
             WHERE e.id = :id
               AND (e.status = com.nexo.app.modules.automacao.domain.StatusExecucao.NA_FILA
                    OR (e.status = com.nexo.app.modules.automacao.domain.StatusExecucao.AGUARDANDO
                        AND (e.retomarEm IS NULL OR e.retomarEm <= :agora OR e.cancelamentoPedido = true))
                    OR (e.status = com.nexo.app.modules.automacao.domain.StatusExecucao.EXECUTANDO
                        AND (e.leaseAte IS NULL OR e.leaseAte < :agora)))
            """)
    int tomar(@Param("id") UUID id, @Param("agora") OffsetDateTime agora, @Param("ate") OffsetDateTime ate);

    /** Encerra o lease de uma execução em andamento, para o agendador retomá-la no próximo tick. */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ExecucaoAutomacao e SET e.leaseAte = :agora
             WHERE e.id = :id AND e.status = com.nexo.app.modules.automacao.domain.StatusExecucao.EXECUTANDO
            """)
    int liberar(@Param("id") UUID id, @Param("agora") OffsetDateTime agora);

    /** Aguardando aprovação sem prazo não retoma pelo relógio: só quando alguém responde. */
    @Query("""
            SELECT e.id FROM ExecucaoAutomacao e
             WHERE (e.status = com.nexo.app.modules.automacao.domain.StatusExecucao.NA_FILA AND e.criadaEm < :filaAntiga)
                OR (e.status = com.nexo.app.modules.automacao.domain.StatusExecucao.AGUARDANDO
                    AND ((e.retomarEm IS NOT NULL AND e.retomarEm <= :agora) OR e.cancelamentoPedido = true))
                OR (e.status = com.nexo.app.modules.automacao.domain.StatusExecucao.EXECUTANDO AND e.leaseAte < :agora)
            """)
    List<UUID> buscarParaRetomar(@Param("agora") OffsetDateTime agora, @Param("filaAntiga") OffsetDateTime filaAntiga);

    List<ExecucaoAutomacao> findByStatusIn(Collection<StatusExecucao> status);

    @Query("SELECT e FROM ExecucaoAutomacao e WHERE e.idAutomacao IN :ids AND e.criadaEm >= :desde ORDER BY e.criadaEm DESC")
    List<ExecucaoAutomacao> buscarRecentes(@Param("ids") Collection<UUID> ids, @Param("desde") OffsetDateTime desde);
}
