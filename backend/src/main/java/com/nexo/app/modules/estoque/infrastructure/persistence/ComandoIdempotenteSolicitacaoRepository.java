package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.ComandoIdempotenteSolicitacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ComandoIdempotenteSolicitacaoRepository extends JpaRepository<ComandoIdempotenteSolicitacao, UUID> {

    Optional<ComandoIdempotenteSolicitacao> findByEscopoAndIdUsuarioAndChave(
            String escopo,
            UUID idUsuario,
            String chave
    );
}
