package com.nexo.app.modules.execucao.infrastructure.persistence;

import com.nexo.app.modules.execucao.domain.EventoHistoricoNota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventoHistoricoNotaRepository extends JpaRepository<EventoHistoricoNota, UUID> {

    List<EventoHistoricoNota> findByIdExecucaoOrderByOcorridoEmAscRegistradoEmAsc(UUID idExecucao);

    Optional<EventoHistoricoNota> findByIdOperacao(UUID idOperacao);
}
