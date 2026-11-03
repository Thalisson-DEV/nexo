package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SaldoSerieRepository extends JpaRepository<SaldoSerie, UUID>, JpaSpecificationExecutor<SaldoSerie> {
    Optional<SaldoSerie> findByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(UUID estoqueId, UUID materialId, String numSerie, CondicaoMaterial condicao);
    Optional<SaldoSerie> findByEstoqueIdAndMaterialIdAndNumSerieAndCondicao(UUID estoqueId, UUID materialId, String numSerie, CondicaoMaterial condicao);
    Optional<SaldoSerie> findByEstoqueIdAndMaterialIdAndNumSerieAndAtivoTrue(UUID estoqueId, UUID materialId, String numSerie);
    Optional<SaldoSerie> findByNumSerieAndAtivoTrue(String numSerie);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"estoque", "material"})
    java.util.List<SaldoSerie> findAllByNumSerieInAndAtivoTrue(java.util.Collection<String> numSeries);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"material"})
    java.util.List<SaldoSerie> findByEstoqueIdAndAtivoTrue(UUID estoqueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select serie from SaldoSerie serie
            where serie.estoque.id = :estoqueId
              and serie.material.id = :materialId
              and serie.numSerie = :numSerie
              and serie.condicao = :condicao
              and serie.ativo = true
            """)
    Optional<SaldoSerie> findWithLockByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(
            @Param("estoqueId") UUID estoqueId,
            @Param("materialId") UUID materialId,
            @Param("numSerie") String numSerie,
            @Param("condicao") CondicaoMaterial condicao);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select serie from SaldoSerie serie
            where serie.estoque.id = :estoqueId
              and serie.material.id = :materialId
              and serie.numSerie = :numSerie
              and serie.condicao = :condicao
            """)
    Optional<SaldoSerie> findWithLockByEstoqueIdAndMaterialIdAndNumSerieAndCondicao(
            @Param("estoqueId") UUID estoqueId,
            @Param("materialId") UUID materialId,
            @Param("numSerie") String numSerie,
            @Param("condicao") CondicaoMaterial condicao);

    boolean existsByEstoqueIdAndMaterialIdAndNumSerieAndCondicaoAndAtivoTrue(UUID estoqueId, UUID materialId, String numSerie, CondicaoMaterial condicao);
    boolean existsByEstoqueIdAndMaterialIdAndNumSerieAndAtivoTrue(UUID estoqueId, UUID materialId, String numSerie);
    boolean existsByEstoqueIdAndAtivoTrue(UUID estoqueId);
}
