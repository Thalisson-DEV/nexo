package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.KitAplicado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    @Query("""
            select distinct k.idObra, k.usuarioCriacao from KitAplicado k
            where k.idObra in :ids and k.ativo = true and k.usuarioCriacao is not null
            """)
    List<Object[]> levantadores(@Param("ids") Collection<UUID> ids);
}
