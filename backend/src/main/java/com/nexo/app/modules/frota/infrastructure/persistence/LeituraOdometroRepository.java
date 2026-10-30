package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.domain.LeituraOdometro;
import com.nexo.app.modules.frota.domain.OrigemLeituraOdometro;
import com.nexo.app.modules.frota.domain.SituacaoLeituraOdometro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeituraOdometroRepository extends JpaRepository<LeituraOdometro, UUID>, JpaSpecificationExecutor<LeituraOdometro> {

    Optional<LeituraOdometro> findByOrigemAndIdOrigem(OrigemLeituraOdometro origem, UUID idOrigem);

    Optional<LeituraOdometro> findFirstByIdVeiculoAndSituacaoAndLidoEmLessThanEqualOrderByLidoEmDescKmDesc(
            UUID idVeiculo, SituacaoLeituraOdometro situacao, OffsetDateTime instante);

    Optional<LeituraOdometro> findFirstByIdVeiculoAndSituacaoAndLidoEmAfterOrderByLidoEmAscKmAsc(
            UUID idVeiculo, SituacaoLeituraOdometro situacao, OffsetDateTime instante);

    Optional<LeituraOdometro> findFirstByIdVeiculoAndSituacaoOrderByKmDescLidoEmDesc(
            UUID idVeiculo, SituacaoLeituraOdometro situacao);

    Page<LeituraOdometro> findAllByIdVeiculoOrderByLidoEmDescKmDesc(UUID idVeiculo, Pageable pageable);

    /** KM atual (maior leitura válida) e data da leitura válida mais recente, por veículo. */
    @Query("""
            SELECT l.idVeiculo AS idVeiculo, MAX(l.km) AS km, MAX(l.lidoEm) AS lidoEm
              FROM LeituraOdometro l
             WHERE l.situacao = com.nexo.app.modules.frota.domain.SituacaoLeituraOdometro.VALIDA
               AND l.idVeiculo IN :idsVeiculo
             GROUP BY l.idVeiculo
            """)
    List<KmAtualProjection> buscarKmAtual(@Param("idsVeiculo") Collection<UUID> idsVeiculo);

    @Query("""
            SELECT l.idVeiculo AS idVeiculo, COUNT(l) AS total
              FROM LeituraOdometro l
             WHERE l.situacao = com.nexo.app.modules.frota.domain.SituacaoLeituraOdometro.INCONSISTENTE
               AND l.idVeiculo IN :idsVeiculo
             GROUP BY l.idVeiculo
            """)
    List<ContagemPorVeiculoProjection> contarInconsistentes(@Param("idsVeiculo") Collection<UUID> idsVeiculo);

    interface KmAtualProjection {
        UUID getIdVeiculo();

        Long getKm();

        OffsetDateTime getLidoEm();
    }

    interface ContagemPorVeiculoProjection {
        UUID getIdVeiculo();

        Long getTotal();
    }
}
