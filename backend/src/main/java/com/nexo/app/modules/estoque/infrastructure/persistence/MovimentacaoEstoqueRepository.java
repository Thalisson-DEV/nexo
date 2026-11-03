package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque.TipoMovimentacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, UUID>, JpaSpecificationExecutor<MovimentacaoEstoque> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"estoqueOrigem", "estoqueDestino", "material", "solicitacao"})
    java.util.List<MovimentacaoEstoque> findByIdObraOrderByDataHoraDesc(UUID idObra, Pageable pageable);

    /** O que foi lançado para a obra e o que entrou ou saiu do estoque dela, mesmo sem vínculo com a obra. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"estoqueOrigem", "estoqueDestino", "material", "solicitacao"})
    @org.springframework.data.jpa.repository.Query("""
            select movimentacao from MovimentacaoEstoque movimentacao
            where movimentacao.idObra = :idObra
               or movimentacao.estoqueOrigem.id = :idEstoque
               or movimentacao.estoqueDestino.id = :idEstoque
            order by movimentacao.dataHora desc
            """)
    java.util.List<MovimentacaoEstoque> findDaObraOuDoEstoque(
            @org.springframework.data.repository.query.Param("idObra") UUID idObra,
            @org.springframework.data.repository.query.Param("idEstoque") UUID idEstoque,
            Pageable pageable);

    List<MovimentacaoEstoque> findAllBySolicitacaoIdOrderByDataHoraAscIdAsc(UUID idSolicitacao);

    List<MovimentacaoEstoque> findAllByIdReferenciaOperacaoInOrderByDataHoraAscIdAsc(
            java.util.Collection<UUID> idsOperacoes);

    @Query(
            value = """
        SELECT me.id_referencia_operacao
        FROM movimentacoes_estoque me
        WHERE (CAST(:idEstoque AS UUID) IS NULL
                  OR me.id_estoque_origem  = :idEstoque
                  OR me.id_estoque_destino = :idEstoque)
          AND (CAST(:idMaterial AS UUID) IS NULL OR me.id_material = :idMaterial)
          AND (CAST(:condicao AS VARCHAR) IS NULL OR me.condicao = :condicao)
          AND (CAST(:tipo AS VARCHAR) IS NULL OR me.tipo_movimentacao = :tipo)
          AND (CAST(:dataInicio AS TIMESTAMPTZ) IS NULL OR me.data_hora >= :dataInicio)
          AND (CAST(:dataFim AS TIMESTAMPTZ) IS NULL OR me.data_hora <= :dataFim)
          AND (CAST(:dataInicioRef AS TIMESTAMPTZ) IS NULL OR me.data_hora >= :dataInicioRef)
          AND (CAST(:dataFimRef AS TIMESTAMPTZ) IS NULL OR me.data_hora <= :dataFimRef)
          AND (CAST(:idUsuarioOperador AS UUID) IS NULL OR me.id_usuario_operador = :idUsuarioOperador)
          AND (CAST(:numSerieLike AS VARCHAR) IS NULL OR LOWER(me.num_serie) LIKE LOWER(:numSerieLike))
          AND (CAST(:idBaseOrigem AS UUID) IS NULL OR EXISTS (
                  SELECT 1 FROM estoques eo WHERE eo.id = me.id_estoque_origem AND eo.id_base_operacional = :idBaseOrigem))
          AND (CAST(:idBaseDestino AS UUID) IS NULL OR EXISTS (
                  SELECT 1 FROM estoques ed WHERE ed.id = me.id_estoque_destino AND ed.id_base_operacional = :idBaseDestino))
          AND (CAST(:buscaGlobalLike AS VARCHAR) IS NULL
                  OR immutable_unaccent(lower(me.num_serie)) LIKE immutable_unaccent(lower(:buscaGlobalLike))
                  OR EXISTS (SELECT 1 FROM materiais m WHERE m.id = me.id_material
                          AND (immutable_unaccent(lower(m.cod_material)) LIKE immutable_unaccent(lower(:buscaGlobalLike))
                               OR immutable_unaccent(lower(m.descricao)) LIKE immutable_unaccent(lower(:buscaGlobalLike))))
                  OR immutable_unaccent(lower(me.codigo_ocorrencia_snapshot)) LIKE immutable_unaccent(lower(:buscaGlobalLike)))
          AND (CAST(:contexto AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM solicitacoes_material sm
                  WHERE sm.id = me.id_solicitacao AND sm.contexto = :contexto))
          AND (CAST(:idSolicitacao AS UUID) IS NULL OR me.id_solicitacao = :idSolicitacao)
          AND (CAST(:tipoTecnico AS VARCHAR) IS NULL OR me.tipo_tecnico = :tipoTecnico)
          AND (CAST(:tipoObra AS VARCHAR) IS NULL OR me.tipo_obra_snapshot = :tipoObra)
          AND (CAST(:idObra AS UUID) IS NULL OR me.id_obra = :idObra)
          AND (CAST(:ocorrencia AS VARCHAR) IS NULL OR me.codigo_ocorrencia_snapshot = :ocorrencia)
          AND (CAST(:idEquipe AS UUID) IS NULL OR me.id_equipe = :idEquipe)
          AND (CAST(:idReserva AS UUID) IS NULL OR me.id_reserva = :idReserva)
          AND (CAST(:numeroReserva AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM reservas_solicitacao_tecnica rs
                  WHERE rs.id = me.id_reserva AND CAST(rs.numero AS VARCHAR) = :numeroReserva))
          AND (CAST(:idAgenteReserva AS UUID) IS NULL OR EXISTS (SELECT 1 FROM reservas_solicitacao_tecnica rs
                  WHERE rs.id = me.id_reserva AND rs.id_agente_responsavel = :idAgenteReserva))
          AND (CAST(:statusSolicitacao AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM solicitacoes_material sm
                  WHERE sm.id = me.id_solicitacao AND sm.status = :statusSolicitacao))
          AND (CAST(:statusSeparacao AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM solicitacoes_material sm
                  WHERE sm.id = me.id_solicitacao AND sm.status_separacao = :statusSeparacao))
        GROUP BY me.id_referencia_operacao
        ORDER BY 
            CASE WHEN :sortCol = 'dataHora' AND :sortDir = 'ASC' THEN MAX(me.data_hora) END ASC,
            CASE WHEN :sortCol = 'dataHora' AND :sortDir = 'DESC' THEN MAX(me.data_hora) END DESC,
            CASE WHEN :sortCol = 'tipo' AND :sortDir = 'ASC' THEN MAX(me.tipo_movimentacao) END ASC,
            CASE WHEN :sortCol = 'tipo' AND :sortDir = 'DESC' THEN MAX(me.tipo_movimentacao) END DESC,
            CASE WHEN :sortCol = 'operador' AND :sortDir = 'ASC' THEN MAX(CAST(me.id_usuario_operador AS TEXT)) END ASC,
            CASE WHEN :sortCol = 'operador' AND :sortDir = 'DESC' THEN MAX(CAST(me.id_usuario_operador AS TEXT)) END DESC,
            CASE WHEN :sortCol = 'id' AND :sortDir = 'ASC' THEN me.id_referencia_operacao END ASC,
            CASE WHEN :sortCol = 'id' AND :sortDir = 'DESC' THEN me.id_referencia_operacao END DESC,
            MAX(me.data_hora) DESC,
            me.id_referencia_operacao ASC
        """,
            countQuery = """
        SELECT COUNT(DISTINCT me.id_referencia_operacao)
        FROM movimentacoes_estoque me
        WHERE (CAST(:idEstoque AS UUID) IS NULL
                  OR me.id_estoque_origem  = :idEstoque
                  OR me.id_estoque_destino = :idEstoque)
          AND (CAST(:idMaterial AS UUID) IS NULL OR me.id_material = :idMaterial)
          AND (CAST(:condicao AS VARCHAR) IS NULL OR me.condicao = :condicao)
          AND (CAST(:tipo AS VARCHAR) IS NULL OR me.tipo_movimentacao = :tipo)
          AND (CAST(:dataInicio AS TIMESTAMPTZ) IS NULL OR me.data_hora >= :dataInicio)
          AND (CAST(:dataFim AS TIMESTAMPTZ) IS NULL OR me.data_hora <= :dataFim)
          AND (CAST(:dataInicioRef AS TIMESTAMPTZ) IS NULL OR me.data_hora >= :dataInicioRef)
          AND (CAST(:dataFimRef AS TIMESTAMPTZ) IS NULL OR me.data_hora <= :dataFimRef)
          AND (CAST(:idUsuarioOperador AS UUID) IS NULL OR me.id_usuario_operador = :idUsuarioOperador)
          AND (CAST(:numSerieLike AS VARCHAR) IS NULL OR LOWER(me.num_serie) LIKE LOWER(:numSerieLike))
          AND (CAST(:idBaseOrigem AS UUID) IS NULL OR EXISTS (
                  SELECT 1 FROM estoques eo WHERE eo.id = me.id_estoque_origem AND eo.id_base_operacional = :idBaseOrigem))
          AND (CAST(:idBaseDestino AS UUID) IS NULL OR EXISTS (
                  SELECT 1 FROM estoques ed WHERE ed.id = me.id_estoque_destino AND ed.id_base_operacional = :idBaseDestino))
          AND (CAST(:buscaGlobalLike AS VARCHAR) IS NULL
                  OR immutable_unaccent(lower(me.num_serie)) LIKE immutable_unaccent(lower(:buscaGlobalLike))
                  OR EXISTS (SELECT 1 FROM materiais m WHERE m.id = me.id_material
                          AND (immutable_unaccent(lower(m.cod_material)) LIKE immutable_unaccent(lower(:buscaGlobalLike))
                               OR immutable_unaccent(lower(m.descricao)) LIKE immutable_unaccent(lower(:buscaGlobalLike))))
                  OR immutable_unaccent(lower(me.codigo_ocorrencia_snapshot)) LIKE immutable_unaccent(lower(:buscaGlobalLike)))
          AND (CAST(:contexto AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM solicitacoes_material sm
                  WHERE sm.id = me.id_solicitacao AND sm.contexto = :contexto))
          AND (CAST(:idSolicitacao AS UUID) IS NULL OR me.id_solicitacao = :idSolicitacao)
          AND (CAST(:tipoTecnico AS VARCHAR) IS NULL OR me.tipo_tecnico = :tipoTecnico)
          AND (CAST(:tipoObra AS VARCHAR) IS NULL OR me.tipo_obra_snapshot = :tipoObra)
          AND (CAST(:idObra AS UUID) IS NULL OR me.id_obra = :idObra)
          AND (CAST(:ocorrencia AS VARCHAR) IS NULL OR me.codigo_ocorrencia_snapshot = :ocorrencia)
          AND (CAST(:idEquipe AS UUID) IS NULL OR me.id_equipe = :idEquipe)
          AND (CAST(:idReserva AS UUID) IS NULL OR me.id_reserva = :idReserva)
          AND (CAST(:numeroReserva AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM reservas_solicitacao_tecnica rs
                  WHERE rs.id = me.id_reserva AND CAST(rs.numero AS VARCHAR) = :numeroReserva))
          AND (CAST(:idAgenteReserva AS UUID) IS NULL OR EXISTS (SELECT 1 FROM reservas_solicitacao_tecnica rs
                  WHERE rs.id = me.id_reserva AND rs.id_agente_responsavel = :idAgenteReserva))
          AND (CAST(:statusSolicitacao AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM solicitacoes_material sm
                  WHERE sm.id = me.id_solicitacao AND sm.status = :statusSolicitacao))
          AND (CAST(:statusSeparacao AS VARCHAR) IS NULL OR EXISTS (SELECT 1 FROM solicitacoes_material sm
                  WHERE sm.id = me.id_solicitacao AND sm.status_separacao = :statusSeparacao))
        """,
            nativeQuery = true
    )
    Page<UUID> findUniqueOperacoesIds(
            @Param("idEstoque")         UUID idEstoque,
            @Param("idMaterial")        UUID idMaterial,
            @Param("condicao")          String condicao,
            @Param("tipo")              String tipo,
            @Param("dataInicio")        OffsetDateTime dataInicio,
            @Param("dataFim")           OffsetDateTime dataFim,
            @Param("dataInicioRef")     OffsetDateTime dataInicioRef,
            @Param("dataFimRef")        OffsetDateTime dataFimRef,
            @Param("idUsuarioOperador") UUID idUsuarioOperador,
            @Param("numSerieLike")      String numSerieLike,
            @Param("idBaseOrigem")      UUID idBaseOrigem,
            @Param("idBaseDestino")     UUID idBaseDestino,
            @Param("buscaGlobalLike")   String buscaGlobalLike,
            @Param("contexto")          String contexto,
            @Param("idSolicitacao")     UUID idSolicitacao,
            @Param("tipoTecnico")       String tipoTecnico,
            @Param("tipoObra")          String tipoObra,
            @Param("idObra")            UUID idObra,
            @Param("ocorrencia")        String ocorrencia,
            @Param("idEquipe")          UUID idEquipe,
            @Param("idReserva")         UUID idReserva,
            @Param("numeroReserva")     String numeroReserva,
            @Param("idAgenteReserva")   UUID idAgenteReserva,
            @Param("statusSolicitacao") String statusSolicitacao,
            @Param("statusSeparacao")   String statusSeparacao,
            @Param("sortCol")           String sortCol,
            @Param("sortDir")           String sortDir,
            Pageable pageable
    );

    /** Compatibility overload for callers that do not use the technical context filters. */
    default Page<UUID> findUniqueOperacoesIds(
            UUID idEstoque,
            UUID idMaterial,
            String tipo,
            OffsetDateTime dataInicio,
            OffsetDateTime dataFim,
            OffsetDateTime dataInicioRef,
            OffsetDateTime dataFimRef,
            UUID idUsuarioOperador,
            String numSerieLike,
            UUID idBaseOrigem,
            UUID idBaseDestino,
            String buscaGlobalLike,
            String sortCol,
            String sortDir,
            Pageable pageable
    ) {
        return findUniqueOperacoesIds(
                idEstoque, idMaterial, null, tipo, dataInicio, dataFim, dataInicioRef, dataFimRef,
                idUsuarioOperador, numSerieLike, idBaseOrigem, idBaseDestino, buscaGlobalLike,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                sortCol, sortDir, pageable);
    }

    List<MovimentacaoEstoque> findAllByIdReferenciaOperacaoInOrderByDataHoraDesc(List<UUID> ids);

    /** Primeira movimentação de cada material em cada solicitação: [idSolicitacao, idMaterial, dataHora]. */
    @Query("""
            SELECT m.solicitacao.id, m.material.id, MIN(m.dataHora) FROM MovimentacaoEstoque m
            WHERE m.tipo = :tipo AND m.solicitacao.id IN :solicitacoes
            GROUP BY m.solicitacao.id, m.material.id
            """)
    List<Object[]> primeiraDataPorSolicitacaoEMaterial(
            @Param("solicitacoes") java.util.Collection<UUID> solicitacoes,
            @Param("tipo") TipoMovimentacao tipo);

    /** Saídas de material NOVO do estoque a partir de uma data: [idMaterial, tipo, dataHora, quantidade]. */
    @Query("""
            SELECT m.material.id, m.tipo, m.dataHora, m.quantidade FROM MovimentacaoEstoque m
            WHERE m.estoqueOrigem.id = :idEstoque AND m.material.id IN :materiais
              AND m.tipo IN :tipos AND m.dataHora >= :desde
              AND m.condicao = com.nexo.app.modules.estoque.domain.CondicaoMaterial.NOVO
            """)
    List<Object[]> saidasDoEstoqueDesde(
            @Param("idEstoque") UUID idEstoque,
            @Param("materiais") java.util.Collection<UUID> materiais,
            @Param("tipos") java.util.Collection<TipoMovimentacao> tipos,
            @Param("desde") OffsetDateTime desde);

    /**
     * Material NOVO que voltou ao estoque porque saiu de uma nota alterada:
     * [idMaterial, dataHora, quantidade]. É o estorno sem origem e sem solicitação
     * (os estornos de solicitação ficam presos a ela).
     */
    @Query("""
            SELECT m.material.id, m.dataHora, m.quantidade FROM MovimentacaoEstoque m
            WHERE m.estoqueDestino.id = :idEstoque AND m.material.id IN :materiais
              AND m.tipo = com.nexo.app.modules.estoque.domain.MovimentacaoEstoque.TipoMovimentacao.ESTORNO
              AND m.estoqueOrigem IS NULL AND m.solicitacao IS NULL
              AND m.condicao = com.nexo.app.modules.estoque.domain.CondicaoMaterial.NOVO
              AND m.dataHora >= :desde
            """)
    List<Object[]> estornosDeNotaNoEstoqueDesde(
            @Param("idEstoque") UUID idEstoque,
            @Param("materiais") java.util.Collection<UUID> materiais,
            @Param("desde") OffsetDateTime desde);

    /** Movimentações ativas de um material que entraram ou saíram do estoque a partir de uma data, da mais antiga à mais nova. */
    @Query("""
            SELECT m FROM MovimentacaoEstoque m
            LEFT JOIN FETCH m.estoqueOrigem o
            LEFT JOIN FETCH m.estoqueDestino d
            LEFT JOIN FETCH m.solicitacao
            WHERE m.material.id = :idMaterial AND m.ativo = true AND m.dataHora >= :desde
              AND (o.id = :idEstoque OR d.id = :idEstoque)
            ORDER BY m.dataHora ASC
            """)
    List<MovimentacaoEstoque> doMaterialNoEstoqueDesde(
            @Param("idEstoque") UUID idEstoque,
            @Param("idMaterial") UUID idMaterial,
            @Param("desde") OffsetDateTime desde);
}
