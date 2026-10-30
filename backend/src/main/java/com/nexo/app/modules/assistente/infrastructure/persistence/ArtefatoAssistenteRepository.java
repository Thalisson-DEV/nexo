package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.ArtefatoAssistente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtefatoAssistenteRepository extends JpaRepository<ArtefatoAssistente, UUID> {

    List<ArtefatoAssistente> findByConversaIdAndAtivoTrueOrderByDataCriacaoAsc(UUID conversaId);

    Optional<ArtefatoAssistente> findByIdAndAtivoTrue(UUID id);

    Optional<ArtefatoAssistente> findByConversaIdAndCodigoAndAtivoTrue(UUID conversaId, String codigo);

    long countByConversaId(UUID conversaId);
}
