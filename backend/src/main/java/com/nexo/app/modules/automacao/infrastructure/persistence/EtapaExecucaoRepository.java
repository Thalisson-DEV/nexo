package com.nexo.app.modules.automacao.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexo.app.modules.automacao.domain.EtapaExecucao;

public interface EtapaExecucaoRepository extends JpaRepository<EtapaExecucao, UUID> {

    List<EtapaExecucao> findByIdExecucao(UUID idExecucao);
}
