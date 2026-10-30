package com.nexo.app.modules.mencao.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexo.app.modules.mencao.domain.Mencao;
import com.nexo.app.modules.mencao.domain.OrigemMencao;

public interface MencaoRepository extends JpaRepository<Mencao, UUID> {

    Optional<Mencao> findFirstByAtivoTrueOrderByIdAsc();

    /** Quem já está marcado neste texto — a guarda contra registrar duas vezes. */
    List<Mencao> findByOrigemAndIdOrigem(OrigemMencao origem, UUID idOrigem);
}
