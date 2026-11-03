package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.VaoPlanejamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VaoPlanejamentoRepository extends JpaRepository<VaoPlanejamento, UUID> {

    List<VaoPlanejamento> findByIdObraAndAtivoTrue(UUID idObra);

    Optional<VaoPlanejamento> findByIdAndIdObraAndAtivoTrue(UUID id, UUID idObra);

    /** Vãos e vãos sem kit por obra: [idObra, vãos, sem kit]. */
    @Query("""
            select v.idObra, count(v),
                sum(case when not exists (select 1 from KitAplicado k where k.idVao = v.id and k.ativo = true) then 1 else 0 end)
            from VaoPlanejamento v
            where v.idObra in :ids and v.ativo = true
            group by v.idObra
            """)
    List<Object[]> contarPorObra(@Param("ids") Collection<UUID> ids);

    @Query("""
            select distinct v.idObra, v.usuarioCriacao from VaoPlanejamento v
            where v.idObra in :ids and v.ativo = true and v.usuarioCriacao is not null
            """)
    List<Object[]> levantadores(@Param("ids") Collection<UUID> ids);
}
