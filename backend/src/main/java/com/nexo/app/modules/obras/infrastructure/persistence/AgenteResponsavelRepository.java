package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.AgenteResponsavel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgenteResponsavelRepository
        extends JpaRepository<AgenteResponsavel, UUID>, JpaSpecificationExecutor<AgenteResponsavel> {

    Optional<AgenteResponsavel> findByNomeIgnoreCase(String nome);

    Optional<AgenteResponsavel> findByEmailIgnoreCase(String email);

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);
}
