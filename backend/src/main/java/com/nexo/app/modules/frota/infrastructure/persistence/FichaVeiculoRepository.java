package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.domain.FichaVeiculo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FichaVeiculoRepository extends JpaRepository<FichaVeiculo, UUID>, JpaSpecificationExecutor<FichaVeiculo> {

    @EntityGraph(attributePaths = "tipoVeiculo")
    Optional<FichaVeiculo> findByIdVeiculo(UUID idVeiculo);

    @EntityGraph(attributePaths = "tipoVeiculo")
    List<FichaVeiculo> findAllByIdVeiculoIn(Collection<UUID> idsVeiculo);

    List<FichaVeiculo> findAllByIdBaseOperacionalIn(Collection<UUID> idsBase);

    boolean existsByIdVeiculo(UUID idVeiculo);
}
