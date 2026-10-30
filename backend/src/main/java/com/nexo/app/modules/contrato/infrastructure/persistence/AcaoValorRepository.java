package com.nexo.app.modules.contrato.infrastructure.persistence;

import com.nexo.app.modules.contrato.domain.AcaoValor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcaoValorRepository extends JpaRepository<AcaoValor, UUID>, JpaSpecificationExecutor<AcaoValor> {
    Optional<AcaoValor> findByAcaoIdAndContratoIdAndDataVigencia(UUID idAcao, UUID idContrato, LocalDate dataVigencia);
    boolean existsByAcaoIdAndContratoIdAndDataVigencia(UUID idAcao, UUID idContrato, LocalDate dataVigencia);

    /**
     * Busca o valor de uma ação vigente em uma determinada data para um contrato.
     */
    Optional<AcaoValor> findFirstByAcaoIdAndContratoIdAndDataVigenciaLessThanEqualOrderByDataVigenciaDesc(
            UUID idAcao, UUID idContrato, LocalDate dataVigencia);

    /**
     * Busca todos os valores de ações vigentes em uma data para um contrato.
     */
    @org.springframework.data.jpa.repository.Query("SELECT av FROM AcaoValor av JOIN FETCH av.acao a " +
            "WHERE av.contrato.id = :idContrato AND av.dataVigencia <= :dataVigencia AND av.ativo = true AND a.ativo = true " +
            "ORDER BY av.dataVigencia DESC")
    java.util.List<AcaoValor> findValoresVigentesPorContrato(
            @org.springframework.data.repository.query.Param("idContrato") UUID idContrato,
            @org.springframework.data.repository.query.Param("dataVigencia") LocalDate dataVigencia);
}
