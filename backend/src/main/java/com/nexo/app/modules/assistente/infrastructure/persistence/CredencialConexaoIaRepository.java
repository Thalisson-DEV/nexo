package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.CredencialConexaoIa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CredencialConexaoIaRepository extends JpaRepository<CredencialConexaoIa, UUID> {

    Optional<CredencialConexaoIa> findByHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CredencialConexaoIa c where c.hash = :hash")
    Optional<CredencialConexaoIa> findByHashForUpdate(@Param("hash") String hash);

    /** Tokens vencidos ou ja trocados (renovacao com rotacao) ha mais tempo que o limite. */
    @Modifying
    @Query("delete from CredencialConexaoIa c where c.expiraEm < :limite or c.usadaEm < :limite")
    int apagarInuteisAntesDe(@Param("limite") java.time.OffsetDateTime limite);
}
