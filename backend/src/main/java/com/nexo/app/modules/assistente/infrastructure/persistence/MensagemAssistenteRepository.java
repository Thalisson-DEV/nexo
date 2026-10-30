package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.MensagemAssistente;
import com.nexo.app.modules.assistente.domain.PapelMensagem;
import com.nexo.app.modules.assistente.domain.StatusMensagemAssistente;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MensagemAssistenteRepository extends JpaRepository<MensagemAssistente, UUID> {

    Optional<MensagemAssistente> findByConversaIdAndClientMessageIdAndPapel(
            UUID conversaId, UUID clientMessageId, PapelMensagem papel);

    List<MensagemAssistente> findByConversaIdAndAtivoTrueOrderByDataCriacaoAsc(UUID conversaId);

    List<MensagemAssistente> findByConversaIdAndAtivoTrueOrderByDataCriacaoDesc(UUID conversaId, Pageable pageable);

    long countByConversaIdAndPapelAndAtivoTrue(UUID conversaId, PapelMensagem papel);

    List<MensagemAssistente> findByStatus(StatusMensagemAssistente status);

    @Query("select m.conversa.id from MensagemAssistente m where m.id = :id")
    Optional<UUID> findConversaIdById(@Param("id") UUID id);
}
