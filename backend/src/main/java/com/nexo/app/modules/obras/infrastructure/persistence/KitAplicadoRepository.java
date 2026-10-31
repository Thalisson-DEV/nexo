package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.KitAplicado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KitAplicadoRepository extends JpaRepository<KitAplicado, UUID> {

    @Query("select distinct k from KitAplicado k left join fetch k.itens where k.idObra = :idObra and k.ativo = true")
    List<KitAplicado> listarComItens(@Param("idObra") UUID idObra);

    Optional<KitAplicado> findByIdAndIdObraAndAtivoTrue(UUID id, UUID idObra);

    List<KitAplicado> findByIdPontoAndAtivoTrue(UUID idPonto);

    List<KitAplicado> findByIdVaoAndAtivoTrue(UUID idVao);
}
