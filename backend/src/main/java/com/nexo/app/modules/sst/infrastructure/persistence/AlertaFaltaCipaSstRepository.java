package com.nexo.app.modules.sst.infrastructure.persistence;

import com.nexo.app.modules.sst.domain.AlertaFaltaCipaSst;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertaFaltaCipaSstRepository extends JpaRepository<AlertaFaltaCipaSst, UUID> {
    Optional<AlertaFaltaCipaSst> findByIdCorpoAndIdMembroCorpoAndAtivoTrue(UUID idCorpo, UUID idMembroCorpo);
    List<AlertaFaltaCipaSst> findAllByIdCorpoAndAtivoTrueOrderByEmitidoEmDesc(UUID idCorpo);
}
