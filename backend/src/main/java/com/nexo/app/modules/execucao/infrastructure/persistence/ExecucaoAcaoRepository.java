package com.nexo.app.modules.execucao.infrastructure.persistence;

import com.nexo.app.modules.execucao.domain.ExecucaoAcao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExecucaoAcaoRepository extends JpaRepository<ExecucaoAcao, UUID> {
}
