package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.ConexaoIa;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConexaoIaRepository extends JpaRepository<ConexaoIa, UUID> {

    Optional<ConexaoIa> findByIdAndAtivoTrue(UUID id);

    List<ConexaoIa> findByUsuarioIdAndAtivoTrueOrderByDataCriacaoDesc(UUID usuarioId);

    Page<ConexaoIa> findByAtivoTrue(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConexaoIa c where c.id = :id and c.ativo = true")
    Optional<ConexaoIa> findForUpdate(@Param("id") UUID id);

    /**
     * Ultimo uso sem passar pela auditoria generica (seria uma linha de log por chamada da IA) e no
     * maximo uma escrita por minuto por conexao.
     */
    @Modifying
    @Query("""
            update ConexaoIa c set c.ultimoUsoEm = :agora, c.ultimoIp = :ip
            where c.id = :id and (c.ultimoUsoEm is null or c.ultimoUsoEm < :limite)
            """)
    int registrarUso(@Param("id") UUID id, @Param("agora") OffsetDateTime agora, @Param("ip") String ip,
            @Param("limite") OffsetDateTime limite);

    @Modifying
    @Query("update ConexaoIa c set c.aplicativo = :aplicativo where c.id = :id")
    int registrarAplicativo(@Param("id") UUID id, @Param("aplicativo") String aplicativo);

    @Modifying
    @Query("update ConexaoIa c set c.totalChamadas = c.totalChamadas + 1, c.ultimoUsoEm = :agora where c.id = :id")
    int contarChamada(@Param("id") UUID id, @Param("agora") OffsetDateTime agora);
}
