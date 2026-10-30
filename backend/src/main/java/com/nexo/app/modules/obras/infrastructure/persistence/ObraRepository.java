package com.nexo.app.modules.obras.infrastructure.persistence;

import com.nexo.app.modules.obras.domain.Obra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ObraRepository
        extends JpaRepository<Obra, UUID>, JpaSpecificationExecutor<Obra> {

    Optional<Obra> findFirstByAtivoTrueOrderByIdAsc();

    Optional<Obra> findByNumeroIgnoreCaseAndAtivoTrue(String numero);

    Optional<Obra> findByNumeroIgnoreCase(String numero);

    boolean existsByNumeroIgnoreCaseAndAtivoTrue(String numero);

    boolean existsByNumeroIgnoreCaseAndIdNotAndAtivoTrue(String numero, UUID id);

    List<Obra> findByObraPaiIdAndAtivoTrue(UUID idObraPai);

    long countByStatusObraIdAndAtivoTrue(UUID idStatusObra);

    @Query("SELECT o FROM Obra o LEFT JOIN FETCH o.statusObra LEFT JOIN FETCH o.agenteResponsavel LEFT JOIN FETCH o.obraPai WHERE o.id = :id")
    Optional<Obra> findByIdWithDetails(@Param("id") UUID id);

    @Modifying
    @Query(value = "INSERT INTO obras_equipes (id_obra, id_equipe) VALUES (:idObra, :idEquipe) "
            + "ON CONFLICT (id_obra, id_equipe) DO NOTHING", nativeQuery = true)
    int vincularEquipeSeAusente(@Param("idObra") UUID idObra, @Param("idEquipe") UUID idEquipe);
}
