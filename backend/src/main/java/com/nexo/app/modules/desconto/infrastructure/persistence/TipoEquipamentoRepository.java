package com.nexo.app.modules.desconto.infrastructure.persistence;

import com.nexo.app.modules.desconto.domain.TipoEquipamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TipoEquipamentoRepository extends JpaRepository<TipoEquipamento, UUID>, JpaSpecificationExecutor<TipoEquipamento> {
}
