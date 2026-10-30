package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.RelacionamentoProjeto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelacionamentoProjetoRepository extends JpaRepository<RelacionamentoProjeto, UUID> {

    Optional<RelacionamentoProjeto> findByProjetoAIdAndProjetoBId(UUID idProjetoA, UUID idProjetoB);

    @Query("""
            select distinct r from RelacionamentoProjeto r
            join fetch r.projetoA a
            join fetch a.statusObra
            join fetch r.projetoB b
            join fetch b.statusObra
            where r.ativo = true and (a.id = :idProjeto or b.id = :idProjeto)
            order by r.dataCriacao, r.id
            """)
    List<RelacionamentoProjeto> findAtivosPorProjeto(@Param("idProjeto") UUID idProjeto);

    @Query("""
            select count(r) from RelacionamentoProjeto r
            where r.ativo = true
              and (r.projetoA.id = :idProjeto or r.projetoB.id = :idProjeto)
            """)
    long countAtivosPorProjeto(@Param("idProjeto") UUID idProjeto);
}
