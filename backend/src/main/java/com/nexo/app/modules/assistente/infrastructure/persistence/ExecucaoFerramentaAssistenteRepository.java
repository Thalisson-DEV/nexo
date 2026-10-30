package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.ExecucaoFerramentaAssistente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExecucaoFerramentaAssistenteRepository extends JpaRepository<ExecucaoFerramentaAssistente, UUID> {

    List<ExecucaoFerramentaAssistente> findByMensagemIdOrderByDataCriacaoAsc(UUID mensagemId);
}
