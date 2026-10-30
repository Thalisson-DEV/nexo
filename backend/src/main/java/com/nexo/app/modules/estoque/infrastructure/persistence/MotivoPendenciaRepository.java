package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.MotivoPendencia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MotivoPendenciaRepository extends JpaRepository<MotivoPendencia, UUID> {
    boolean existsByNomeIgnoreCase(String nome);
    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);
    java.util.Optional<MotivoPendencia> findByNomeIgnoreCase(String nome);
    java.util.List<MotivoPendencia> findByAtivoTrueOrderByNomeAsc();
}
