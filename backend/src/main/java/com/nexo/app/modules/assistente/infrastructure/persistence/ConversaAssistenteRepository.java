package com.nexo.app.modules.assistente.infrastructure.persistence;

import com.nexo.app.modules.assistente.domain.ConversaAssistente;
import com.nexo.app.modules.assistente.domain.OrigemConversa;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversaAssistenteRepository extends JpaRepository<ConversaAssistente, UUID> {

    Optional<ConversaAssistente> findFirstByAtivoTrueOrderByIdAsc();

    Optional<ConversaAssistente> findByIdAndUsuarioIdAndAtivoTrue(UUID id, UUID usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConversaAssistente c where c.id = :id and c.usuarioId = :usuarioId and c.ativo = true")
    Optional<ConversaAssistente> findOwnedForUpdate(@Param("id") UUID id, @Param("usuarioId") UUID usuarioId);

    Page<ConversaAssistente> findByUsuarioIdAndAtivoTrue(UUID usuarioId, Pageable pageable);

    /** Historico do Copiloto: sem as conversas tecnicas das conexoes de IA externas. */
    Page<ConversaAssistente> findByUsuarioIdAndAtivoTrueAndOrigemNot(UUID usuarioId, OrigemConversa origem, Pageable pageable);

    /** Conversas do usuario iniciadas em um painel especifico (Copiloto contextual do dashboard). */
    Page<ConversaAssistente> findByUsuarioIdAndAtivoTrueAndOrigemAndOrigemId(UUID usuarioId, OrigemConversa origem,
            String origemId, Pageable pageable);

    /**
     * Busca por titulo ou pelo texto das perguntas do proprio usuario. O termo ja chega normalizado
     * em minusculas e com curingas escapados.
     */
    @Query("""
            select c from ConversaAssistente c
            where c.usuarioId = :usuarioId and c.ativo = true
              and c.origem <> com.nexo.app.modules.assistente.domain.OrigemConversa.MCP
              and (lower(c.titulo) like :termo escape '\\'
                   or exists (select 1 from MensagemAssistente m
                              where m.conversa = c and m.ativo = true
                                and m.papel = com.nexo.app.modules.assistente.domain.PapelMensagem.USUARIO
                                and lower(m.conteudo) like :termo escape '\\'))
            """)
    Page<ConversaAssistente> buscarDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("termo") String termo,
            Pageable pageable);
}
