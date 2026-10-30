package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.CodigoAutorizacaoIa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CodigoAutorizacaoIaRepository extends JpaRepository<CodigoAutorizacaoIa, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CodigoAutorizacaoIa c where c.hash = :hash")
    Optional<CodigoAutorizacaoIa> findByHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("delete from CodigoAutorizacaoIa c where c.expiraEm < :limite")
    int apagarVencidosAntesDe(@Param("limite") java.time.OffsetDateTime limite);
}
