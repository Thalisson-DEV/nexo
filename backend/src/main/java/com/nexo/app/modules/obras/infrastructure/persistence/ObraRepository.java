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

    /** Obras ativas com coordenadas cujo deslocamento nunca foi calculado ou usou outra base/coordenadas. */
    @Query("SELECT o.id FROM Obra o WHERE o.ativo = true AND o.latitude IS NOT NULL AND o.longitude IS NOT NULL "
            + "AND (o.distanciaBaseKm IS NULL OR o.idBaseDeslocamento IS NULL OR o.idBaseDeslocamento <> o.idBaseOperacional "
            + "OR o.latitudeDeslocamento <> o.latitude OR o.longitudeDeslocamento <> o.longitude) "
            + "ORDER BY o.dataUltimaAlteracao DESC NULLS LAST")
    List<UUID> buscarIdsComDeslocamentoDesatualizado(org.springframework.data.domain.Pageable pageable);

    /** Pré-filtro por retângulo para a busca de obras próximas; a distância exata é calculada em memória. */
    @Query("SELECT o FROM Obra o LEFT JOIN FETCH o.statusObra WHERE o.ativo = true AND o.id <> :idIgnorado "
            + "AND o.latitude BETWEEN :latMin AND :latMax AND o.longitude BETWEEN :lngMin AND :lngMax")
    List<Obra> buscarNoRetangulo(@Param("idIgnorado") UUID idIgnorado,
                                 @Param("latMin") double latMin, @Param("latMax") double latMax,
                                 @Param("lngMin") double lngMin, @Param("lngMax") double lngMax);

    @Modifying
    @Query(value = "INSERT INTO obras_equipes (id_obra, id_equipe) VALUES (:idObra, :idEquipe) "
            + "ON CONFLICT (id_obra, id_equipe) DO NOTHING", nativeQuery = true)
    int vincularEquipeSeAusente(@Param("idObra") UUID idObra, @Param("idEquipe") UUID idEquipe);
}
