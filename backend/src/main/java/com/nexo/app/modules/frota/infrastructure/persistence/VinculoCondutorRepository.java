package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.domain.VinculoCondutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VinculoCondutorRepository extends JpaRepository<VinculoCondutor, UUID> {

    Optional<VinculoCondutor> findByIdVeiculoAndFimIsNullAndAtivoTrue(UUID idVeiculo);

    Optional<VinculoCondutor> findByIdColaboradorAndFimIsNullAndAtivoTrue(UUID idColaborador);

    List<VinculoCondutor> findAllByIdVeiculoInAndFimIsNullAndAtivoTrue(Collection<UUID> idsVeiculo);

    List<VinculoCondutor> findAllByIdColaboradorInAndFimIsNullAndAtivoTrue(Collection<UUID> idsColaborador);

    Page<VinculoCondutor> findAllByIdVeiculoAndAtivoTrueOrderByInicioDesc(UUID idVeiculo, Pageable pageable);

    Page<VinculoCondutor> findAllByIdColaboradorAndAtivoTrueOrderByInicioDesc(UUID idColaborador, Pageable pageable);

    /** Quem conduzia o veículo no instante informado. */
    @Query("""
            SELECT v FROM VinculoCondutor v
             WHERE v.idVeiculo = :idVeiculo AND v.ativo = TRUE
               AND v.inicio <= :instante AND (v.fim IS NULL OR v.fim > :instante)
            """)
    Optional<VinculoCondutor> buscarVigente(@Param("idVeiculo") UUID idVeiculo,
                                            @Param("instante") OffsetDateTime instante);

    /**
     * Há vínculo já encerrado do veículo ou do condutor que termina depois do instante?
     * Um novo vínculo começando antes disso sobreporia um período fechado.
     */
    @Query("""
            SELECT COUNT(v) > 0 FROM VinculoCondutor v
             WHERE v.ativo = TRUE AND v.fim IS NOT NULL AND v.fim > :instante
               AND (v.idVeiculo = :idVeiculo OR v.idColaborador = :idColaborador)
            """)
    boolean existeEncerradoDepois(@Param("idVeiculo") UUID idVeiculo,
                                  @Param("idColaborador") UUID idColaborador,
                                  @Param("instante") OffsetDateTime instante);
}
