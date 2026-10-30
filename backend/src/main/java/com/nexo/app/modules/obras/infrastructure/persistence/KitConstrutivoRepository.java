package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.KitConstrutivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KitConstrutivoRepository
        extends JpaRepository<KitConstrutivo, UUID>, JpaSpecificationExecutor<KitConstrutivo> {

    Optional<KitConstrutivo> findByIdContratoAndCodigoAndAtivoTrue(UUID idContrato, String codigo);

    boolean existsByIdContratoAndCodigoAndAtivoTrue(UUID idContrato, String codigo);

    boolean existsByIdContratoAndCodigoAndAtivoTrueAndIdNot(UUID idContrato, String codigo, UUID id);

    @Query("select distinct k.grupo from KitConstrutivo k where k.ativo = true and k.grupo is not null "
            + "and (:idContrato is null or k.idContrato = :idContrato) order by k.grupo")
    List<String> listarGrupos(UUID idContrato);
}
