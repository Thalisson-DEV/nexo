package com.nexo.app.modules.contrato.infrastructure.persistence;

import com.nexo.app.modules.contrato.domain.Contrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContratoRepository extends JpaRepository<Contrato, UUID>, JpaSpecificationExecutor<Contrato> {
    Optional<Contrato> findFirstByAtivoTrueOrderByIdAsc();
    Optional<Contrato> findByNumeroContratoIgnoreCase(String numeroContrato);
    boolean existsByNumeroContratoIgnoreCase(String numeroContrato);

    @Query("""
            select contrato.id from Contrato contrato
            where cast(function('immutable_unaccent', lower(contrato.numeroContrato)) as string) like concat('%', :trecho, '%')
               or cast(function('immutable_unaccent', lower(contrato.cliente)) as string) like concat('%', :trecho, '%')
            """)
    List<UUID> buscarIdsPorTrecho(@Param("trecho") String trecho);

    /**
     * Busca o contrato ativo dentro da vigência para a data informada.
     */
    Optional<Contrato> findByIdAndAtivoTrueAndDataVigenciaLessThanEqualAndDataEncerramentoGreaterThanEqual(
            UUID id, LocalDate inicio, LocalDate fim);
}
