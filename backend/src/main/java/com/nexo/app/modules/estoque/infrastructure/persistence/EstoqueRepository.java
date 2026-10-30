package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.Estoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

@Repository
public interface EstoqueRepository extends JpaRepository<Estoque, UUID>, JpaSpecificationExecutor<Estoque> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select estoque from Estoque estoque where estoque.id = :id")
    Optional<Estoque> findByIdForUpdate(@Param("id") UUID id);

    Optional<Estoque> findByNomeIdentificadorIgnoreCase(String nome);
    Optional<Estoque> findByIdEquipe(UUID idEquipe);
    Optional<Estoque> findByIdObra(UUID idObra);

    @Query("""
            select estoque.id as id,
                   estoque.nomeIdentificador as nomeIdentificador,
                   cast(function('immutable_unaccent', lower(estoque.nomeIdentificador)) as string) as sortKey
              from Estoque estoque
              join estoque.tipoEstoque tipo
             where estoque.ativo = true
               and tipo.ativo = true
               and upper(tipo.nome) in :tipos
               and cast(function('immutable_unaccent', lower(estoque.nomeIdentificador)) as string)
                   like concat(:query, '%')
               and (
                    :lastNome is null
                    or cast(function('immutable_unaccent', lower(estoque.nomeIdentificador)) as string) > :lastNome
                    or (
                        cast(function('immutable_unaccent', lower(estoque.nomeIdentificador)) as string) = :lastNome
                        and estoque.id > :lastId
                    )
               )
             order by cast(function('immutable_unaccent', lower(estoque.nomeIdentificador)) as string), estoque.id
            """)
    List<EstoqueLookupProjection> buscarOpcoes(
            @Param("query") String query,
            @Param("tipos") List<String> tipos,
            @Param("lastNome") String lastNome,
            @Param("lastId") UUID lastId,
            Pageable pageable);

    /** Insere atomicamente o estoque da obra, ignorando uma corrida ja vencida. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO estoques (
                id, nome_identificador, id_tipo_estoque, id_base_operacional,
                id_obra, ativo, versao, data_criacao, usuario_criacao
            ) VALUES (
                :id, :nome, :idTipoEstoque, :idBaseOperacional,
                :idObra, TRUE, 0, CURRENT_TIMESTAMP, :usuarioCriacao
            )
            ON CONFLICT (id_obra) WHERE id_obra IS NOT NULL DO NOTHING
            """, nativeQuery = true)
    int inserirEstoqueObraSeAusente(
            @Param("id") UUID id,
            @Param("nome") String nome,
            @Param("idTipoEstoque") UUID idTipoEstoque,
            @Param("idBaseOperacional") UUID idBaseOperacional,
            @Param("idObra") UUID idObra,
            @Param("usuarioCriacao") String usuarioCriacao
    );

    boolean existsByNomeIdentificadorIgnoreCase(String nome);
    boolean existsByNomeIdentificadorIgnoreCaseAndIdNot(String nome, UUID id);
}
