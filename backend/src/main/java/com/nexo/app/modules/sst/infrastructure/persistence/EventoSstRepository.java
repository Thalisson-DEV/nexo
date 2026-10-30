package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.application.dto.ContagemPorTipoEvento;
import com.nexo.app.modules.sst.domain.EventoSst;
import com.nexo.app.modules.sst.domain.StatusEventoSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventoSstRepository
        extends JpaRepository<EventoSst, UUID>, JpaSpecificationExecutor<EventoSst> {

    /**
     * Próximo número da sequência do identificador legível.
     *
     * <p>Vem do banco, e não de um {@code MAX(numero) + 1}: a sequência entrega valores
     * distintos a transações concorrentes sem que uma precise esperar a outra, e não
     * reaproveita número de transação desfeita — que é a garantia de "único e nunca
     * reutilizado" que o registro precisa ter para ser citado em CAT e em processo.</p>
     */
    @Query(value = "SELECT nextval('seq_evento_sst')", nativeQuery = true)
    long proximoNumeroSequencial();

    Optional<EventoSst> findByNumeroRegistro(String numeroRegistro);

    /** Última ocorrência válida de todo o histórico, sem recortes do dashboard. */
    @Query("""
            SELECT MAX(e.dataOcorrencia)
            FROM EventoSst e
            WHERE e.ativo = true
              AND e.status <> :cancelado
            """)
    Optional<OffsetDateTime> buscarUltimaDataOcorrenciaValida(
            @Param("cancelado") StatusEventoSst cancelado);

    /**
     * Eventos, dias perdidos e CATs por tipo de ocorrência no intervalo — a base da
     * métrica oficial de acidentalidade.
     *
     * <p>A agregação é do banco de propósito: número que o assistente cita precisa ser
     * determinístico, e somar linhas no modelo é exatamente o que a métrica tipada
     * existe para impedir. O intervalo é {@code [inicio, fim)}, como nas demais
     * métricas oficiais.</p>
     */
    @Query("""
            SELECT new com.nexo.app.modules.sst.application.dto.ContagemPorTipoEvento(
                       e.tipo,
                       COUNT(e),
                       COALESCE(SUM(e.diasPerdidos), 0L),
                       SUM(CASE WHEN e.possuiCat = true THEN 1L ELSE 0L END))
            FROM EventoSst e
            WHERE e.ativo = true
              AND e.status <> :cancelado
              AND e.dataOcorrencia >= :inicio
              AND e.dataOcorrencia < :fim
            GROUP BY e.tipo
            """)
    List<ContagemPorTipoEvento> agruparPorTipoNoPeriodo(
            @Param("cancelado") StatusEventoSst cancelado,
            @Param("inicio") OffsetDateTime inicio,
            @Param("fim") OffsetDateTime fim);
}
