package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.PontoPlanejamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PontoPlanejamentoRepository extends JpaRepository<PontoPlanejamento, UUID> {

    /** Pontos ainda sem kit por obra: [idObra, quantidade]. */
    @Query("""
            select p.idObra, count(p) from PontoPlanejamento p
            where p.idObra in :ids and p.ativo = true
              and not exists (select 1 from KitAplicado k where k.idPonto = p.id and k.ativo = true)
            group by p.idObra
            """)
    List<Object[]> contarSemKit(@Param("ids") Collection<UUID> ids);

    List<PontoPlanejamento> findByIdObraAndAtivoTrueOrderByPaginaAscNumeroAsc(UUID idObra);

    Optional<PontoPlanejamento> findByIdAndIdObraAndAtivoTrue(UUID id, UUID idObra);

    boolean existsByIdObraAndNumeroAndAtivoTrue(UUID idObra, String numero);

    /** Pontos por obra e tipo de execução: [idObra, tipoExecucao, pontos, sem kit, sem vão]. */
    @Query("""
            select p.idObra, p.tipoExecucao, count(p),
                sum(case when not exists (select 1 from KitAplicado k where k.idPonto = p.id and k.ativo = true) then 1 else 0 end),
                sum(case when not exists (select 1 from VaoPlanejamento v where v.ativo = true
                    and (v.idPontoA = p.id or v.idPontoB = p.id)) then 1 else 0 end)
            from PontoPlanejamento p
            where p.idObra in :ids and p.ativo = true
            group by p.idObra, p.tipoExecucao
            """)
    List<Object[]> contarPorObra(@Param("ids") Collection<UUID> ids);

    @Query("""
            select p.numero from PontoPlanejamento p
            where p.idObra = :idObra and p.ativo = true
              and not exists (select 1 from KitAplicado k where k.idPonto = p.id and k.ativo = true)
            order by p.pagina, p.numero
            """)
    List<String> numerosSemKit(@Param("idObra") UUID idObra);

    @Query("""
            select distinct p.idObra, p.usuarioCriacao from PontoPlanejamento p
            where p.idObra in :ids and p.ativo = true and p.usuarioCriacao is not null
            """)
    List<Object[]> levantadores(@Param("ids") Collection<UUID> ids);
}
