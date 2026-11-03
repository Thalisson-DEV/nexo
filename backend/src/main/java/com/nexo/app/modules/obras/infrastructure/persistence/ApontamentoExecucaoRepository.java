package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.ApontamentoExecucao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApontamentoExecucaoRepository extends JpaRepository<ApontamentoExecucao, UUID> {

    /** Valor executado e serviços apontados de um turno. */
    record ResumoDoTurno(UUID idTurno, BigDecimal valor, long servicos) {
    }

    /** Valor executado por uma equipe numa data. */
    record ValorNaData(UUID idEquipe, LocalDate data, BigDecimal valor) {
    }

    Optional<ApontamentoExecucao> findByIdAndAtivoTrue(UUID id);

    boolean existsByIdObraAndAtivoTrue(UUID idObra);

    @Query("""
            select a from ApontamentoExecucao a join fetch a.itemPlanejado i join fetch i.kitAplicado
            where a.ativo = true and a.idTurno = :idTurno
            order by a.data, a.dataCriacao
            """)
    List<ApontamentoExecucao> listarDoTurno(@Param("idTurno") UUID idTurno);

    @Query("""
            select a from ApontamentoExecucao a join fetch a.itemPlanejado i join fetch i.kitAplicado
            where a.ativo = true and a.idProgramacao = :idProgramacao
            order by a.data desc, a.dataCriacao desc
            """)
    List<ApontamentoExecucao> listarDaProgramacao(@Param("idProgramacao") UUID idProgramacao);

    @Query("""
            select new com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository$ResumoDoTurno(
                a.idTurno, coalesce(sum(a.valorTotal), 0),
                sum(case when a.itemPlanejado.tipo = com.nexo.app.modules.obras.domain.TipoItemKit.SERVICO then 1L else 0L end))
            from ApontamentoExecucao a
            where a.ativo = true and a.idTurno in :idsTurnos
            group by a.idTurno
            """)
    List<ResumoDoTurno> resumirPorTurnos(@Param("idsTurnos") Collection<UUID> idsTurnos);

    @Query("""
            select new com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository$ValorNaData(
                a.idEquipe, a.data, coalesce(sum(a.valorTotal), 0))
            from ApontamentoExecucao a
            where a.ativo = true and a.idEquipe in :idsEquipes and a.data between :de and :ate
            group by a.idEquipe, a.data
            """)
    List<ValorNaData> somarPorEquipeEData(@Param("idsEquipes") Collection<UUID> idsEquipes,
                                          @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Valor executado e serviços apontados por obra, equipe e data. */
    record ExecutadoPorObra(UUID idObra, String numero, String titulo, UUID idEquipe, LocalDate data,
                            BigDecimal valor, long servicos) {
    }

    @Query("""
            select new com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository$ExecutadoPorObra(
                a.idObra, o.numero, o.titulo, a.idEquipe, a.data, coalesce(sum(a.valorTotal), 0),
                sum(case when i.tipo = com.nexo.app.modules.obras.domain.TipoItemKit.SERVICO then 1L else 0L end))
            from ApontamentoExecucao a join a.itemPlanejado i, Obra o
            where o.id = a.idObra and a.ativo = true and a.idEquipe in :idsEquipes and a.data between :de and :ate
            group by a.idObra, o.numero, o.titulo, a.idEquipe, a.data
            """)
    List<ExecutadoPorObra> somarPorObraEquipeEData(@Param("idsEquipes") Collection<UUID> idsEquipes,
                                                   @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Quantidade e valor apontados por item da obra e o dia do último apontamento. */
    record ExecucaoDoItem(UUID idItem, BigDecimal quantidade, BigDecimal valor, LocalDate ultimaData) {
    }

    @Query("""
            select new com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository$ExecucaoDoItem(
                a.itemPlanejado.id, sum(a.quantidade), sum(a.valorTotal), max(a.data))
            from ApontamentoExecucao a
            where a.ativo = true and a.idObra = :idObra
            group by a.itemPlanejado.id
            """)
    List<ExecucaoDoItem> somarPorItemDaObra(@Param("idObra") UUID idObra);

    @Query("""
            select new com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository$ExecucaoDoItem(
                a.itemPlanejado.id, sum(a.quantidade), sum(a.valorTotal), max(a.data))
            from ApontamentoExecucao a
            where a.ativo = true and a.idObra = :idObra and a.data <= :ate
            group by a.itemPlanejado.id
            """)
    List<ExecucaoDoItem> somarPorItemDaObraAte(@Param("idObra") UUID idObra, @Param("ate") LocalDate ate);

    /** Serviços concluídos e ainda não medidos, por obra, com o valor apontado neles. */
    record PendenteDeMedicao(UUID idObra, long servicos, BigDecimal valor, LocalDate ultimaData) {
    }

    @Query("""
            select new com.nexo.app.modules.obras.infrastructure.persistence.ApontamentoExecucaoRepository$PendenteDeMedicao(
                k.idObra, count(distinct i.id), coalesce(sum(a.valorTotal), 0), max(a.data))
            from ItemPlanejado i join i.kitAplicado k
              left join ApontamentoExecucao a on a.itemPlanejado = i and a.ativo = true
            where i.ativo = true and k.ativo = true and k.executadoAntes = false
              and i.tipo = com.nexo.app.modules.obras.domain.TipoItemKit.SERVICO
              and i.status = com.nexo.app.modules.obras.domain.StatusItemPlanejado.CONCLUIDO
            group by k.idObra
            """)
    List<PendenteDeMedicao> somarPendentesDeMedicao();

    /** Valor de serviço apontado por obra. */
    @Query("""
            select a.idObra, coalesce(sum(a.valorTotal), 0) from ApontamentoExecucao a
            where a.ativo = true and a.idObra in :ids
            group by a.idObra
            """)
    List<Object[]> somarValorPorObra(@Param("ids") Collection<UUID> ids);
}
