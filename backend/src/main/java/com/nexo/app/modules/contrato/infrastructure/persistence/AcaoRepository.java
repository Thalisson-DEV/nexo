package com.nexo.app.modules.contrato.infrastructure.persistence;

import com.nexo.app.modules.contrato.domain.Acao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AcaoRepository extends JpaRepository<Acao, UUID>, JpaSpecificationExecutor<Acao> {
    Optional<Acao> findFirstByAtivoTrueOrderByIdAsc();
    Optional<Acao> findByCodigoIgnoreCase(String codigo);
    boolean existsByCodigoIgnoreCase(String codigo);
    boolean existsByIdAndAtivoTrue(UUID id);
}
