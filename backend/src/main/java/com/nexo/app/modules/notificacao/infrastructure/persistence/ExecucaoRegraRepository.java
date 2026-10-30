package com.nexo.app.modules.notificacao.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexo.app.modules.notificacao.domain.ExecucaoRegra;

public interface ExecucaoRegraRepository extends JpaRepository<ExecucaoRegra, UUID> {

    Optional<ExecucaoRegra> findFirstByAtivoTrueOrderByIdAsc();

    Optional<ExecucaoRegra> findByIdRegraAndChaveEscopo(UUID idRegra, String chaveEscopo);
}
