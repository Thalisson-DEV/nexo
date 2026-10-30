package com.nexo.app.modules.chamado.infrastructure.persistence;

import com.nexo.app.modules.chamado.domain.TopicoChamado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopicoChamadoRepository extends JpaRepository<TopicoChamado, UUID>, JpaSpecificationExecutor<TopicoChamado> {
    List<TopicoChamado> findAllByAtivoTrueOrderByOrdemExibicaoAscNomeAsc();
    Optional<TopicoChamado> findByCodigoIgnoreCase(String codigo);
    boolean existsByEquipePadraoIdAndAtivoTrue(UUID equipeId);
}
