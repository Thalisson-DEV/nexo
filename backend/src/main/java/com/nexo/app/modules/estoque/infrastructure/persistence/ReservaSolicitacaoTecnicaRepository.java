package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReservaSolicitacaoTecnicaRepository extends JpaRepository<ReservaSolicitacaoTecnica, UUID> {

    Optional<ReservaSolicitacaoTecnica> findBySolicitacaoId(UUID idSolicitacao);
}
