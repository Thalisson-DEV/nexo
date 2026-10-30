package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.OperacaoSolicitacaoMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

public interface OperacaoSolicitacaoMaterialRepository extends JpaRepository<OperacaoSolicitacaoMaterial, UUID> {
    List<OperacaoSolicitacaoMaterial> findAllBySolicitacaoIdOrderByRealizadaEmAscIdAsc(UUID idSolicitacao);
}
