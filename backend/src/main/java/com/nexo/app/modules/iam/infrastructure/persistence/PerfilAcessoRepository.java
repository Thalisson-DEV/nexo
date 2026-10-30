package com.nexo.app.modules.iam.infrastructure.persistence;

import com.nexo.app.modules.iam.domain.PerfilAcesso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerfilAcessoRepository extends JpaRepository<PerfilAcesso, UUID>, JpaSpecificationExecutor<PerfilAcesso> {
    Optional<PerfilAcesso> findByNomeIgnoreCase(String nome);
    boolean existsByNomeIgnoreCase(String nome);
}
