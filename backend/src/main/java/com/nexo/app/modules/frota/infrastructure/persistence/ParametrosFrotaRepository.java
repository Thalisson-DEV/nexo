package com.nexo.app.modules.frota.infrastructure.persistence;

import com.nexo.app.modules.frota.domain.ParametrosFrota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ParametrosFrotaRepository extends JpaRepository<ParametrosFrota, UUID> {
}
