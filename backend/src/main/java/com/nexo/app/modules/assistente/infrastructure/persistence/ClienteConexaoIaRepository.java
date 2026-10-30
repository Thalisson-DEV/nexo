package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.ClienteConexaoIa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ClienteConexaoIaRepository extends JpaRepository<ClienteConexaoIa, UUID> {

    Optional<ClienteConexaoIa> findByClientIdAndAtivoTrue(String clientId);

    long countByIpRegistroAndDataCriacaoAfter(String ipRegistro, OffsetDateTime desde);
}
