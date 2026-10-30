package com.nexo.app.modules.notificacao.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexo.app.modules.notificacao.domain.Notificacao;

public interface NotificacaoRepository
        extends JpaRepository<Notificacao, UUID>, JpaSpecificationExecutor<Notificacao> {

    /**
     * Busca sempre escopada pelo dono. É o que impede um usuário de marcar como
     * lida — ou de ler — a notificação de outro apenas conhecendo o id: para quem
     * não é o destinatário, o registro simplesmente não existe.
     */
    Optional<Notificacao> findByIdAndIdUsuarioAndAtivoTrue(UUID id, UUID idUsuario);

    boolean existsByIdUsuarioAndChaveIdempotencia(UUID idUsuario, String chaveIdempotencia);

    @Query("""
            SELECT COUNT(n) FROM Notificacao n
             WHERE n.idUsuario = :idUsuario
               AND n.ativo = true
               AND n.lidaEm IS NULL
               AND n.arquivadaEm IS NULL
               AND (n.expiraEm IS NULL OR n.expiraEm > :agora)
            """)
    long contarNaoLidas(@Param("idUsuario") UUID idUsuario, @Param("agora") OffsetDateTime agora);

    @Query("""
            SELECT n.categoria AS categoria, COUNT(n) AS total FROM Notificacao n
             WHERE n.idUsuario = :idUsuario
               AND n.ativo = true
               AND n.lidaEm IS NULL
               AND n.arquivadaEm IS NULL
               AND (n.expiraEm IS NULL OR n.expiraEm > :agora)
             GROUP BY n.categoria
            """)
    List<ContagemPorCategoria> contarNaoLidasPorCategoria(@Param("idUsuario") UUID idUsuario,
                                                          @Param("agora") OffsetDateTime agora);

    /**
     * "Marcar todas como lidas" em massa. Não passa pelo domínio de propósito: são
     * potencialmente centenas de linhas e carregá-las só para chamar
     * {@code marcarLida()} em cada uma não acrescenta nenhuma regra.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notificacao n
               SET n.lidaEm = :agora, n.dataUltimaAlteracao = :agora
             WHERE n.idUsuario = :idUsuario
               AND n.ativo = true
               AND n.lidaEm IS NULL
               AND n.arquivadaEm IS NULL
            """)
    int marcarTodasComoLidas(@Param("idUsuario") UUID idUsuario, @Param("agora") OffsetDateTime agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notificacao n
               SET n.arquivadaEm = :agora, n.dataUltimaAlteracao = :agora
             WHERE n.idUsuario = :idUsuario
               AND n.ativo = true
               AND n.lidaEm IS NOT NULL
               AND n.arquivadaEm IS NULL
            """)
    int arquivarLidas(@Param("idUsuario") UUID idUsuario, @Param("agora") OffsetDateTime agora);

    /**
     * Expurgo: retira de circulação o que já foi resolvido pelo usuário e passou da
     * janela de retenção. Usa soft delete porque a política do projeto proíbe
     * remoção física em tabelas de negócio.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notificacao n
               SET n.ativo = false, n.dataDesativacao = :agora, n.dataUltimaAlteracao = :agora
             WHERE n.ativo = true
               AND n.dataCriacao < :corte
               AND (n.lidaEm IS NOT NULL OR n.arquivadaEm IS NOT NULL)
            """)
    int desativarResolvidasAnterioresA(@Param("corte") OffsetDateTime corte,
                                       @Param("agora") OffsetDateTime agora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notificacao n
               SET n.ativo = false, n.dataDesativacao = :agora, n.dataUltimaAlteracao = :agora
             WHERE n.ativo = true
               AND n.expiraEm IS NOT NULL
               AND n.expiraEm < :agora
            """)
    int desativarExpiradas(@Param("agora") OffsetDateTime agora);
}
