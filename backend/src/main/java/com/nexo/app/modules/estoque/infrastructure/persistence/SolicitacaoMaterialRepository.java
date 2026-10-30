package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SolicitacaoMaterialRepository extends JpaRepository<SolicitacaoMaterial, UUID>, JpaSpecificationExecutor<SolicitacaoMaterial> {

    List<SolicitacaoMaterial> findAllByStatusAndEncerradaEmIsNullAndDataEntregaBefore(
        SolicitacaoMaterial.StatusSolicitacao status, 
        OffsetDateTime dataEntrega
    );

    boolean existsByIdObraAndContextoAndEncerradaEmIsNullAndStatusIn(
            UUID idObra,
            ContextoSolicitacaoMaterial contexto,
            Collection<SolicitacaoMaterial.StatusSolicitacao> statuses);
}
