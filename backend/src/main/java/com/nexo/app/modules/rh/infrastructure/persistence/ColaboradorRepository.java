package com.nexo.app.modules.rh.infrastructure.persistence;

import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Funcao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ColaboradorRepository extends JpaRepository<Colaborador, UUID>, JpaSpecificationExecutor<Colaborador> {
    Optional<Colaborador> findFirstByAtivoTrueOrderByIdAsc();
    Optional<Colaborador> findByCpf(String cpf);

    Optional<Colaborador> findByCpfHash(String cpfHash);

    Optional<Colaborador> findByIdUsuario(UUID idUsuario);

    boolean existsByIdAndAtivoTrue(UUID id);

    Optional<Colaborador> findByNomeCompletoHash(String nomeCompletoHash);

    List<Colaborador> findAllByNomeCompletoHash(String nomeCompletoHash);

    boolean existsByNomeCompletoHash(String nomeCompletoHash);

    boolean existsByCpf(String cpf);

    boolean existsByCpfHash(String cpfHash);

    List<Colaborador> findByAtivoTrueAndFuncaoIn(Collection<Funcao> funcoes);

    List<Colaborador> findByAtivoTrueAndFuncaoId(UUID idFuncao);

    @Query("""
            select c from Colaborador c
            join fetch c.funcao funcao
            join fetch c.baseOperacional base
            where c.ativo = true and base.id in :idsBases
            order by c.nomeBusca, c.id
            """)
    List<Colaborador> buscarAtivosPorBases(@Param("idsBases") Collection<UUID> idsBases);

    @Query("""
            select c from Colaborador c
            join fetch c.funcao funcao
            join fetch c.baseOperacional base
            where c.ativo = true and funcao.id in :idsFuncoes
            order by c.nomeBusca, c.id
            """)
    List<Colaborador> buscarAtivosPorFuncoes(@Param("idsFuncoes") Collection<UUID> idsFuncoes);

    @Query("""
            select c from Colaborador c
            join fetch c.funcao funcao
            join fetch c.baseOperacional base
            where c.ativo = true
              and base.id in :idsBases
              and funcao.id in :idsFuncoes
            order by c.nomeBusca, c.id
            """)
    List<Colaborador> buscarAtivosPorBasesEFuncoes(
            @Param("idsBases") Collection<UUID> idsBases,
            @Param("idsFuncoes") Collection<UUID> idsFuncoes);

    Page<Colaborador> findByNomeBuscaIsNull(Pageable pageable);

    /**
     * Carrega os colaboradores informados já com função e base resolvidas. O
     * {@code join fetch} existe porque quem consome isto monta um rótulo com os três
     * dados: sem ele, uma página de 20 usuários viraria 41 consultas.
     */
    @Query("""
            select c from Colaborador c
            join fetch c.funcao
            join fetch c.baseOperacional
            where c.id in :ids
            """)
    List<Colaborador> buscarComVinculosPorIds(@Param("ids") Collection<UUID> ids);

    /**
     * Como o anterior, mas descendo mais um nível: além de função e base, resolve o
     * centro de custo da base e o processo de negócio.
     *
     * <p>Serve a quem <b>agrupa</b> por estrutura organizacional, não a quem só exibe
     * um rótulo. O centro de custo mora atrás da base, então sem este {@code fetch} um
     * dashboard que quebra valores por regional dispararia uma consulta por colaborador
     * distinto no período — exatamente a dimensão que mais cresce.</p>
     */
    @Query("""
            select c from Colaborador c
            join fetch c.funcao
            join fetch c.baseOperacional base
            join fetch base.centroFaturamento
            join fetch c.processo
            where c.id in :ids
            """)
    List<Colaborador> buscarComEstruturaPorIds(@Param("ids") Collection<UUID> ids);

    /**
     * IDs cujo nome normalizado contém o trecho. O termo já chega normalizado (minúsculo
     * e sem acento) para casar com {@code nome_busca}, que é gravado da mesma forma.
     */
    @Query("select c.id from Colaborador c where c.nomeBusca like :termo")
    List<UUID> buscarIdsPorNomeBusca(@Param("termo") String termo);

    /**
     * Busca apenas os campos necessários para o seletor, já ordenados pela
     * chave de paginação estável. O {@code Pageable} é usado somente para
     * aplicar o limite (o caso de uso pede uma linha extra para calcular
     * {@code hasMore}).
     */
    @Query("""
            select c.id as id, c.nomeCompleto as nomeCompleto, c.nomeBusca as nomeBusca
            from Colaborador c
            where c.ativo = true
              and c.nomeBusca like concat(:query, '%')
            order by c.nomeBusca asc, c.id asc
            """)
    List<ColaboradorLookupProjection> buscarOpcoes(@Param("query") String query, Pageable pageable);

    /** Consulta a página seguinte sem OFFSET, usando a chave textual e o UUID do cursor. */
    @Query("""
            select c.id as id, c.nomeCompleto as nomeCompleto, c.nomeBusca as nomeBusca
            from Colaborador c
            where c.ativo = true
              and c.nomeBusca like concat(:query, '%')
              and (
                    c.nomeBusca > :cursorName
                    or (c.nomeBusca = :cursorName and c.id > :cursorId)
                  )
            order by c.nomeBusca asc, c.id asc
            """)
    List<ColaboradorLookupProjection> buscarOpcoesAposCursor(
            @Param("query") String query,
            @Param("cursorName") String cursorName,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
