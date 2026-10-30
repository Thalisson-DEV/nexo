package com.nexo.app.modules.notificacao.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexo.app.modules.notificacao.domain.LogEmail;
import com.nexo.app.modules.notificacao.domain.StatusEnvioEmail;

public interface LogEmailRepository extends JpaRepository<LogEmail, UUID> {

    /**
     * O que a varredura deve retentar.
     *
     * <p>Junta dois casos numa consulta só porque a resposta é a mesma. {@code FALHOU}
     * é a recusa explícita do provedor. {@code PENDENTE} antigo é a linha aberta cujo
     * envio nunca voltou — processo derrubado no meio —, e o corte por tempo é o que
     * a separa das que estão sendo enviadas neste instante.</p>
     */
    @Query("""
            SELECT l FROM LogEmail l
            WHERE l.ativo = true
              AND l.tentativas < :maximoTentativas
              AND (l.status = :falhou
                   OR (l.status = :pendente AND l.dataCriacao < :corte))
            ORDER BY l.dataCriacao ASC
            """)
    List<LogEmail> buscarParaReenvio(@Param("falhou") StatusEnvioEmail falhou,
                                     @Param("pendente") StatusEnvioEmail pendente,
                                     @Param("corte") OffsetDateTime corte,
                                     @Param("maximoTentativas") int maximoTentativas,
                                     Pageable pageable);

    /**
     * Se este destinatário já tem registro para esta notificação.
     *
     * <p>Guarda contra o reprocessamento do outbox do Modulith: o publicador é
     * idempotente para a notificação em si, mas a retentativa de um evento já
     * entregue chegaria aqui como um segundo pedido de e-mail.</p>
     */
    boolean existsByIdNotificacaoAndIdUsuario(UUID idNotificacao, UUID idUsuario);
}
