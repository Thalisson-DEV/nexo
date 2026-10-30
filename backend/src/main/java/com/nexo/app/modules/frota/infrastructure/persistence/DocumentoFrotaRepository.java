package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.domain.DocumentoFrota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentoFrotaRepository extends JpaRepository<DocumentoFrota, UUID>, JpaSpecificationExecutor<DocumentoFrota> {

    List<DocumentoFrota> findAllByAtivoTrueAndDataVencimentoLessThanEqual(LocalDate limite);

    /** Contagem de documentos vencidos e a vencer por veículo, para a listagem da frota. */
    @Query("""
            SELECT d.idVeiculo AS idVeiculo,
                   SUM(CASE WHEN d.dataVencimento < :hoje THEN 1 ELSE 0 END) AS vencidos,
                   SUM(CASE WHEN d.dataVencimento >= :hoje AND d.dataVencimento <= :limite THEN 1 ELSE 0 END) AS aVencer
              FROM DocumentoFrota d
             WHERE d.ativo = TRUE AND d.idVeiculo IN :idsVeiculo AND d.dataVencimento IS NOT NULL
             GROUP BY d.idVeiculo
            """)
    List<ResumoVencimentoProjection> resumirPorVeiculo(@Param("idsVeiculo") Collection<UUID> idsVeiculo,
                                                       @Param("hoje") LocalDate hoje,
                                                       @Param("limite") LocalDate limite);

    interface ResumoVencimentoProjection {
        UUID getIdVeiculo();

        Long getVencidos();

        Long getAVencer();
    }
}
