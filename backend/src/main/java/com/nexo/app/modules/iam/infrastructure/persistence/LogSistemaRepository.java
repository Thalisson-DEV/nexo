package com.nexo.app.modules.iam.infrastructure.persistence;

import com.nexo.app.modules.iam.domain.LogSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LogSistemaRepository extends JpaRepository<LogSistema, UUID> {
    List<LogSistema> findByRegistroIdOrderByDataCriacaoDesc(UUID registroId);
}
