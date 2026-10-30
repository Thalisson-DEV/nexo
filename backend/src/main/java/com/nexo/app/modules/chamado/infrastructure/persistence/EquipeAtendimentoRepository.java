package com.nexo.app.modules.chamado.infrastructure.persistence;

import com.nexo.app.modules.chamado.domain.EquipeAtendimento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipeAtendimentoRepository extends JpaRepository<EquipeAtendimento, UUID>,
        JpaSpecificationExecutor<EquipeAtendimento> {

    Optional<EquipeAtendimento> findByCodigoIgnoreCase(String codigo);

    @Override
    @EntityGraph(attributePaths = "membros")
    Optional<EquipeAtendimento> findById(UUID id);
}
