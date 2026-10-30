package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InspecaoSegurancaRepository
        extends JpaRepository<InspecaoSeguranca, UUID>, JpaSpecificationExecutor<InspecaoSeguranca> {

    Optional<InspecaoSeguranca> findByIdAndAtivoTrue(UUID id);

    Optional<InspecaoSeguranca> findFirstByAtivoTrueOrderByIdAsc();

    /**
     * Indica se já existe alguma inspeção lançada para um formulário — usado para
     * travar a edição estrutural do formulário após o primeiro uso.
     */
    boolean existsByIdFormulario(UUID idFormulario);

    @Query("""
            select count(i)
              from InspecaoSeguranca i
             where i.ativo = true
               and i.status <> com.nexo.app.modules.sst.domain.StatusInspecao.CANCELADA
               and i.dataRealizacao >= :inicio
               and i.dataRealizacao < :fim
            """)
    long countAtivasNaoCanceladasNoPeriodo(
            @Param("inicio") OffsetDateTime inicio,
            @Param("fim") OffsetDateTime fim);

    @Query("""
            select i.idEquipe as idEquipe,
                   count(distinct d.id) as totalDesvios,
                   count(distinct i.id) as totalInspecoes
              from InspecaoSeguranca i
              left join i.desvios d
             where i.ativo = true
               and i.status <> com.nexo.app.modules.sst.domain.StatusInspecao.CANCELADA
               and i.idEquipe is not null
               and i.dataRealizacao >= :inicio
               and i.dataRealizacao < :fim
             group by i.idEquipe
            """)
    List<EquipeDesviosProjection> agruparDesviosPorEquipe(
            @Param("inicio") OffsetDateTime inicio,
            @Param("fim") OffsetDateTime fim);

    /**
     * Datas das inspeções válidas lançadas por um conjunto de inspetores — base do
     * acompanhamento de metas, que compara cada realização com a janela do colaborador.
     */
    @Query("""
            select i.idInspetor as idInspetor,
                   i.idTipoInspecao as idTipoInspecao,
                   i.dataRealizacao as dataRealizacao
              from InspecaoSeguranca i
             where i.ativo = true
               and i.status <> com.nexo.app.modules.sst.domain.StatusInspecao.CANCELADA
               and i.idInspetor in :inspetores
               and i.dataRealizacao >= :inicio
               and i.dataRealizacao < :fim
            """)
    List<RealizacaoInspetorProjection> listarRealizacoesPorInspetor(
            @Param("inspetores") Collection<UUID> inspetores,
            @Param("inicio") OffsetDateTime inicio,
            @Param("fim") OffsetDateTime fim);

    /** Colaboradores distintos alcançados por inspeções válidas no período (cobertura). */
    @Query("""
            select count(distinct c.idColaborador)
              from InspecaoSeguranca i
              join i.colaboradoresInspecionados c
             where i.ativo = true
               and i.status <> com.nexo.app.modules.sst.domain.StatusInspecao.CANCELADA
               and i.dataRealizacao >= :inicio
               and i.dataRealizacao < :fim
            """)
    long contarColaboradoresInspecionadosNoPeriodo(
            @Param("inicio") OffsetDateTime inicio,
            @Param("fim") OffsetDateTime fim);

    interface EquipeDesviosProjection {
        UUID getIdEquipe();
        long getTotalDesvios();
        long getTotalInspecoes();
    }

    interface RealizacaoInspetorProjection {
        UUID getIdInspetor();
        UUID getIdTipoInspecao();
        OffsetDateTime getDataRealizacao();
    }
}
