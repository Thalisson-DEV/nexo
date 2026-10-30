package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.BaseOperacional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BaseOperacionalRepository extends JpaRepository<BaseOperacional, UUID>, JpaSpecificationExecutor<BaseOperacional> {
    List<BaseOperacional> findByAtivoTrue();
    boolean existsByNomeIgnoreCase(String nome);
    Optional<BaseOperacional> findByNomeIgnoreCase(String nome);

    @Query("""
            select base.id as id, base.nome as label
            from BaseOperacional base
            where base.ativo = true
            order by base.nome, base.id
            """)
    List<BaseOperacionalLookupProjection> listarOpcoesAtivas();

    @Query("""
            select base.id from BaseOperacional base
            where cast(function('immutable_unaccent', lower(base.nome)) as string) like concat('%', :trecho, '%')
               or cast(function('immutable_unaccent', lower(base.uf)) as string) like concat('%', :trecho, '%')
               or cast(function('immutable_unaccent', lower(base.endereco)) as string) like concat('%', :trecho, '%')
            """)
    List<UUID> buscarIdsPorTrecho(@Param("trecho") String trecho);

    @Query("""
            select base from BaseOperacional base
            join fetch base.centroFaturamento
            where base.id in :ids
            """)
    List<BaseOperacional> buscarComCentroPorIds(@Param("ids") Collection<UUID> ids);
}
