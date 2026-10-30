package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.AcaoPendenteAssistente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcaoPendenteAssistenteRepository extends JpaRepository<AcaoPendenteAssistente, UUID> {

    Optional<AcaoPendenteAssistente> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    List<AcaoPendenteAssistente> findByConversaIdAndStatus(UUID conversaId, AcaoPendenteAssistente.Status status);

    List<AcaoPendenteAssistente> findByConversaIdAndUsuarioIdOrderByDataCriacao(UUID conversaId, UUID usuarioId);

    List<AcaoPendenteAssistente> findByMensagemIdIn(Collection<UUID> mensagens);
}
