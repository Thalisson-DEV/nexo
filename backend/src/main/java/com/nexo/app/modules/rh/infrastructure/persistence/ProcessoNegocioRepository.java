package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.ProcessoNegocio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessoNegocioRepository extends JpaRepository<ProcessoNegocio, UUID>, JpaSpecificationExecutor<ProcessoNegocio> {
    Optional<ProcessoNegocio> findByNomeIgnoreCase(String nome);
    boolean existsByNomeIgnoreCase(String nome);
    boolean existsByIdAndAtivoTrue(UUID id);

    @Query("""
            select processo.id as id, processo.nome as label
            from ProcessoNegocio processo
            where processo.ativo = true
            order by processo.nome, processo.id
            """)
    List<ProcessoNegocioLookupProjection> listarOpcoesAtivas();

    @Query("""
            select processo.id from ProcessoNegocio processo
            where cast(function('immutable_unaccent', lower(processo.nome)) as string) like concat('%', :trecho, '%')
            """)
    java.util.List<UUID> buscarIdsPorTrecho(@Param("trecho") String trecho);
}
