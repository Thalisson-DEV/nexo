package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.Municipio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MunicipioRepository extends JpaRepository<Municipio, UUID>, JpaSpecificationExecutor<Municipio> {
    List<Municipio> findByBaseOperacionalId(UUID idBaseOperacional);
    boolean existsByNomeIgnoreCase(String nome);
    Optional<Municipio> findByNomeIgnoreCase(String nome);
}
