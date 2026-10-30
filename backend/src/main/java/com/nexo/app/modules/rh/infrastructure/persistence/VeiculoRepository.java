package com.nexo.app.modules.rh.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexo.app.modules.rh.domain.Veiculo;

@Repository
public interface VeiculoRepository extends JpaRepository<Veiculo, UUID>, JpaSpecificationExecutor<Veiculo> {
    Optional<Veiculo> findFirstByAtivoTrueOrderByIdAsc();
    boolean existsByPlacaIgnoreCase(String placa);
    boolean existsByIdAndAtivoTrue(UUID id);
    Optional<Veiculo> findByPlacaIgnoreCase(String placa);
    @EntityGraph(attributePaths = "baseOperacional")
    List<Veiculo> findAllByAtivoTrue();

    @EntityGraph(attributePaths = "baseOperacional")
    List<Veiculo> findAllByBaseOperacionalIdInAndAtivoTrue(java.util.Collection<UUID> baseIds);

    @Query("""
            select v.id from Veiculo v
            where cast(function('immutable_unaccent', lower(v.placa)) as string) like concat('%', :trecho, '%')
               or cast(function('immutable_unaccent', lower(v.modelo)) as string) like concat('%', :trecho, '%')
               or cast(function('immutable_unaccent', lower(v.fabricante)) as string) like concat('%', :trecho, '%')
               or replace(replace(lower(v.placa), '-', ''), ' ', '') like concat('%', :trechoSemPontuacao, '%')
            """)
    List<UUID> buscarIdsPorTrecho(@Param("trecho") String trecho,
                                  @Param("trechoSemPontuacao") String trechoSemPontuacao);
}
