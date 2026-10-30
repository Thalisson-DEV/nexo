package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID>, JpaSpecificationExecutor<Material> {
    Optional<Material> findFirstByAtivoTrueOrderByIdAsc();
    Optional<Material> findByCodigoIgnoreCase(String codigo);
    Optional<Material> findByCodigoHash(String codigoHash);
    boolean existsByCodigoIgnoreCase(String codigo);
    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, UUID id);

    /**
     * Busca leve para autocomplete. A ordenação por descrição normalizada e ID é
     * determinística, permitindo que o caso de uso aplique keyset pagination.
     */
    @Query("""
            select m.id as id, m.codigo as codigo, m.descricao as descricao,
                   cast(function('immutable_unaccent', lower(m.descricao)) as string) as sortKey
              from Material m
             where m.ativo = true
               and (
                    cast(function('immutable_unaccent', lower(m.codigo)) as string) like concat(:query, '%')
                    or cast(function('immutable_unaccent', lower(m.descricao)) as string) like concat(:query, '%')
               )
               and (
                    :lastDescricao is null
                    or cast(function('immutable_unaccent', lower(m.descricao)) as string) > :lastDescricao
                    or (
                        cast(function('immutable_unaccent', lower(m.descricao)) as string) = :lastDescricao
                        and m.id > :lastId
                    )
               )
             order by cast(function('immutable_unaccent', lower(m.descricao)) as string), m.id
            """)
    List<MaterialLookupProjection> buscarOpcoes(
            @Param("query") String query,
            @Param("lastDescricao") String lastDescricao,
            @Param("lastId") UUID lastId,
            Pageable pageable);
}
