package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.EquipamentoObra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipamentoObraRepository extends JpaRepository<EquipamentoObra, UUID> {

    List<EquipamentoObra> findByIdObraAndAtivoTrue(UUID idObra);

    Optional<EquipamentoObra> findByIdPontoAndIdObraAndAtivoTrue(UUID idPonto, UUID idObra);
}
