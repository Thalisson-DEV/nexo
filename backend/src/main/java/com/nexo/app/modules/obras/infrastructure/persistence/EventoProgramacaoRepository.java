package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.EventoProgramacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EventoProgramacaoRepository
        extends JpaRepository<EventoProgramacao, UUID>, JpaSpecificationExecutor<EventoProgramacao> {
}
