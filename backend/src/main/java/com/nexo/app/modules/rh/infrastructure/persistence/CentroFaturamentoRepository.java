package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.CentroFaturamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositório para a entidade CentroFaturamento.
 */
@Repository
public interface CentroFaturamentoRepository extends JpaRepository<CentroFaturamento, UUID>, JpaSpecificationExecutor<CentroFaturamento> {
    boolean existsByNomeIgnoreCase(String nome);

    Optional<CentroFaturamento> findByNomeIgnoreCase(String nome);
}
