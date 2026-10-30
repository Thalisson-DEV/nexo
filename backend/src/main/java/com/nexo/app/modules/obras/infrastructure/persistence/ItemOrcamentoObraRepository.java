package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.ItemOrcamentoObra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItemOrcamentoObraRepository extends JpaRepository<ItemOrcamentoObra, UUID> {

    List<ItemOrcamentoObra> findByIdObraAndAtivoTrueOrderByCodigoAsc(UUID idObra);
}
