package com.nexo.app.modules.financeiro.infrastructure.persistence;

import com.nexo.app.modules.financeiro.domain.EventoSolicitacaoFinanceira;
import com.nexo.app.modules.financeiro.domain.TipoEventoSolicitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventoSolicitacaoFinanceiraRepository extends JpaRepository<EventoSolicitacaoFinanceira, UUID> {

    List<EventoSolicitacaoFinanceira> findByIdSolicitacaoOrderByOcorridoEmAsc(UUID idSolicitacao);

    List<EventoSolicitacaoFinanceira> findByIdSolicitacaoInAndTipoOrderByOcorridoEmAsc(
            Collection<UUID> idsSolicitacao, TipoEventoSolicitacao tipo);

    long countByIdSolicitacaoInAndTipo(Collection<UUID> idsSolicitacao, TipoEventoSolicitacao tipo);
}
