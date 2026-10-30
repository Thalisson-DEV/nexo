package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.TipoEquipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TipoEquipeRepository extends JpaRepository<TipoEquipe, UUID>, JpaSpecificationExecutor<TipoEquipe> {
    Optional<TipoEquipe> findByNomeIgnoreCase(String nome);
    boolean existsByNomeIgnoreCase(String nome);
}
