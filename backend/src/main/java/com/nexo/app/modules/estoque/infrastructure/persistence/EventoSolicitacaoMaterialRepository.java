package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.EscopoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.EventoSolicitacaoMaterial;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoSolicitacaoMaterialRepository
        extends JpaRepository<EventoSolicitacaoMaterial, UUID>, JpaSpecificationExecutor<EventoSolicitacaoMaterial> {

    Optional<EventoSolicitacaoMaterial> findTopByEscopoAndIdSolicitacaoOrderBySequenciaDesc(
            EscopoEventoSolicitacao escopo,
            UUID idSolicitacao
    );

    List<EventoSolicitacaoMaterial> findAllByEscopoAndIdSolicitacaoOrderBySequenciaAsc(
            EscopoEventoSolicitacao escopo,
            UUID idSolicitacao
    );

    @Query("""
            select evento.idSolicitacao as idSolicitacao, max(evento.ocorridoEm) as ultimaAtividade
              from EventoSolicitacaoMaterial evento
             where evento.idSolicitacao in :idsSolicitacoes
             group by evento.idSolicitacao
            """)
    List<UltimaAtividadeSolicitacaoProjection> findUltimaAtividadeByIdSolicitacaoIn(
            @Param("idsSolicitacoes") Collection<UUID> idsSolicitacoes
    );

    interface UltimaAtividadeSolicitacaoProjection {
        UUID getIdSolicitacao();

        OffsetDateTime getUltimaAtividade();
    }
}
