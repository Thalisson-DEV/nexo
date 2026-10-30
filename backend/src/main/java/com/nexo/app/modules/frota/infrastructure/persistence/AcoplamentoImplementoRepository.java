package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.domain.AcoplamentoImplemento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcoplamentoImplementoRepository extends JpaRepository<AcoplamentoImplemento, UUID> {

    Optional<AcoplamentoImplemento> findByImplementoIdAndFimIsNullAndAtivoTrue(UUID idImplemento);

    @EntityGraph(attributePaths = {"implemento", "implemento.tipoImplemento"})
    List<AcoplamentoImplemento> findAllByIdVeiculoInAndFimIsNullAndAtivoTrue(Collection<UUID> idsVeiculo);

    List<AcoplamentoImplemento> findAllByImplementoIdInAndFimIsNullAndAtivoTrue(Collection<UUID> idsImplemento);

    List<AcoplamentoImplemento> findAllByImplementoIdAndAtivoTrueOrderByInicioDesc(UUID idImplemento);

    @EntityGraph(attributePaths = {"implemento", "implemento.tipoImplemento"})
    List<AcoplamentoImplemento> findAllByIdVeiculoAndAtivoTrueOrderByInicioDesc(UUID idVeiculo);
}
