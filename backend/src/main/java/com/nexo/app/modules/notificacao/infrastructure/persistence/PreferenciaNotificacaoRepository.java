package com.nexo.app.modules.notificacao.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.PreferenciaNotificacao;

public interface PreferenciaNotificacaoRepository extends JpaRepository<PreferenciaNotificacao, UUID> {

    List<PreferenciaNotificacao> findByIdUsuario(UUID idUsuario);

    /**
     * Sem filtro por {@code ativo}: a chave única do banco é (usuário, categoria)
     * independentemente do soft delete, então a busca do upsert precisa enxergar a
     * linha existente mesmo que ela esteja desativada.
     */
    Optional<PreferenciaNotificacao> findByIdUsuarioAndCategoria(UUID idUsuario, CategoriaNotificacao categoria);
}
