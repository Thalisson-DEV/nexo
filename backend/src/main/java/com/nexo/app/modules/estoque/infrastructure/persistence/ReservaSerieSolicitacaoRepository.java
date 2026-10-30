package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.ReservaSerieSolicitacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservaSerieSolicitacaoRepository extends JpaRepository<ReservaSerieSolicitacao, UUID> {
}
