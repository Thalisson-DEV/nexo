package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.ItemAnaliticoProjection;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.MaterialAnaliticoProjection;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.ResumoPeriodoSolicitacaoProjection;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.SolicitacaoAnaliticaProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Consultas do painel analítico de solicitações. Todas aplicam o mesmo recorte:
 * <ul>
 *   <li>contexto (COMERCIAL/TECNICO) opcional;</li>
 *   <li>equipe = equipe de saque ou, na falta dela, a dona do estoque de destino;</li>
 *   <li>almoxarifado = almoxarifado de atendimento ou, na falta dele, o estoque de origem.</li>
 * </ul>
 * <b>Contrato:</b> com {@code filtrarPorEquipes = true}, {@code idsEquipes} não pode ser vazia.
 */
@Repository
public interface PainelSolicitacoesRepository extends JpaRepository<SolicitacaoMaterial, UUID> {

    String RECORTE = """
              AND (CAST(:contexto AS VARCHAR) IS NULL OR s.contexto = CAST(:contexto AS VARCHAR))
              AND (:filtrarPorEquipes = FALSE OR COALESCE(s.id_equipe_saque, ed.id_equipe) IN :idsEquipes)
              AND (CAST(:idAlmoxarifado AS VARCHAR) IS NULL
                   OR COALESCE(s.id_almoxarifado_atendimento, s.id_estoque_origem) = CAST(:idAlmoxarifado AS UUID))
            """;

    /** Mesma regra de quantidade operada de ItemSolicitacao.getQuantidadeOperadaLiquida(). */
    String ATENDIDA = """
            CASE
                WHEN s.tipo_tecnico IN ('221', '921')
                    THEN i.quantidade_entregue_bruta - i.quantidade_entrega_estornada
                WHEN s.tipo_tecnico IN ('222', '922')
                    THEN i.quantidade_devolvida_bruta - i.quantidade_devolucao_estornada
                ELSE i.quantidade_entregue
            END
            """;

    /**
     * Solicitações criadas no período e, se pedido, as ainda abertas criadas antes dele (backlog),
     * das mais recentes para as mais antigas, até {@code limite} linhas.
     */
    @Query(value = """
        SELECT
            s.id AS id,
            s.contexto AS contexto,
            s.status AS status,
            s.tipo_tecnico AS tipoTecnico,
            s.tipo_obra_snapshot AS tipoObra,
            s.codigo_ocorrencia_snapshot AS ocorrencia,
            s.id_ocorrencia AS idOcorrencia,
            s.id_obra AS idObra,
            s.status_separacao AS statusSeparacao,
            CAST(EXTRACT(EPOCH FROM s.data_solicitacao) * 1000 AS BIGINT) AS dataSolicitacao,
            CAST(EXTRACT(EPOCH FROM s.data_verificacao) * 1000 AS BIGINT) AS dataVerificacao,
            CAST(EXTRACT(EPOCH FROM s.data_entrega) * 1000 AS BIGINT) AS dataEntrega,
            TO_CHAR(s.data_prevista_retirada, 'YYYY-MM-DD') AS dataPrevista,
            CAST(EXTRACT(EPOCH FROM s.separacao_iniciada_em) * 1000 AS BIGINT) AS separacaoIniciadaEm,
            CAST(EXTRACT(EPOCH FROM s.separacao_concluida_em) * 1000 AS BIGINT) AS separacaoConcluidaEm,
            CAST(EXTRACT(EPOCH FROM s.encerrada_em) * 1000 AS BIGINT) AS encerradaEm,
            eo.nome_identificador AS estoqueOrigem,
            ed.nome_identificador AS estoqueDestino,
            COALESCE(ea.id, eo.id) AS idAlmoxarifado,
            COALESCE(ea.nome_identificador, eo.nome_identificador) AS almoxarifado,
            COALESCE(s.id_equipe_saque, ed.id_equipe) AS idEquipe,
            s.id_equipe_saque AS idEquipeSaque,
            s.saque_fora_equipe AS saqueForaEquipe,
            mr.nome AS motivoRejeicao,
            mp.nome AS motivoPendencia,
            it.motivos AS motivosPendenciaItens,
            COALESCE(it.com_pendencia, 0) AS itensComPendencia,
            EXISTS (SELECT 1 FROM reservas_solicitacao_tecnica r WHERE r.id_solicitacao = s.id) AS temReserva,
            COALESCE(it.itens, 0) AS itens,
            COALESCE(it.solicitada, 0) AS quantidadeSolicitada,
            COALESCE(it.atendida, 0) AS quantidadeAtendida,
            COALESCE(it.com_saldo, FALSE) AS temSaldoPendente,
            (s.data_solicitacao < CAST(:inicio AS TIMESTAMP WITH TIME ZONE)) AS anteriorAoPeriodo
        FROM solicitacoes_material s
        LEFT JOIN estoques eo ON eo.id = s.id_estoque_origem
        LEFT JOIN estoques ed ON ed.id = s.id_estoque_destino
        LEFT JOIN estoques ea ON ea.id = s.id_almoxarifado_atendimento
        LEFT JOIN motivos_rejeicao mr ON mr.id = s.id_motivo_rejeicao
        LEFT JOIN motivos_pendencia mp ON mp.id = s.id_motivo_pendencia
        LEFT JOIN LATERAL (
            SELECT
                COUNT(*) AS itens,
                SUM(q.quantidade) AS solicitada,
                SUM(q.atendida) AS atendida,
                BOOL_OR(q.quantidade - q.atendida > 0) AS com_saldo,
                COUNT(q.id_motivo_pendencia) AS com_pendencia,
                STRING_AGG(DISTINCT mpi.nome, ', ') AS motivos
            FROM (
                SELECT i.quantidade, i.id_motivo_pendencia, """ + ATENDIDA + """
                    AS atendida
                FROM itens_solicitacao i
                WHERE i.id_solicitacao = s.id
            ) q
            LEFT JOIN motivos_pendencia mpi ON mpi.id = q.id_motivo_pendencia
        ) it ON TRUE
        WHERE (
                (s.data_solicitacao >= CAST(:inicio AS TIMESTAMP WITH TIME ZONE)
                 AND s.data_solicitacao <= CAST(:fim AS TIMESTAMP WITH TIME ZONE))
                OR (:incluirBacklog = TRUE
                    AND s.data_solicitacao < CAST(:inicio AS TIMESTAMP WITH TIME ZONE)
                    AND s.status IN ('PENDENTE', 'APROVADO', 'ENTREGUE_PARCIAL', 'DEVOLVIDO_PARCIAL')
                    AND s.encerrada_em IS NULL)
              )
        """ + RECORTE + """
        ORDER BY s.data_solicitacao DESC
        LIMIT :limite
        """, nativeQuery = true)
    List<SolicitacaoAnaliticaProjection> listarSolicitacoes(
            @Param("inicio") String inicio,
            @Param("fim") String fim,
            @Param("incluirBacklog") boolean incluirBacklog,
            @Param("contexto") String contexto,
            @Param("filtrarPorEquipes") boolean filtrarPorEquipes,
            @Param("idsEquipes") List<UUID> idsEquipes,
            @Param("idAlmoxarifado") String idAlmoxarifado,
            @Param("limite") int limite);

    /** Materiais das solicitações criadas no período (sem rejeitadas/canceladas), por processo e sentido. */
    @Query(value = """
        SELECT
            s.contexto AS contexto,
            CASE WHEN s.contexto = 'TECNICO' AND s.tipo_tecnico IN ('222', '922')
                 THEN 'DEVOLUCAO' ELSE 'SAIDA' END AS sentido,
            m.cod_material AS codigo,
            m.descricao AS descricao,
            m.unidade_medida AS unidade,
            COUNT(DISTINCT s.id) AS solicitacoes,
            COALESCE(SUM(i.quantidade), 0) AS quantidadeSolicitada,
            COALESCE(SUM(""" + ATENDIDA + """
            ), 0) AS quantidadeAtendida
        FROM itens_solicitacao i
        JOIN solicitacoes_material s ON s.id = i.id_solicitacao
        JOIN materiais m ON m.id = i.id_material
        LEFT JOIN estoques ed ON ed.id = s.id_estoque_destino
        WHERE s.data_solicitacao >= CAST(:inicio AS TIMESTAMP WITH TIME ZONE)
          AND s.data_solicitacao <= CAST(:fim AS TIMESTAMP WITH TIME ZONE)
          AND s.status NOT IN ('REJEITADO', 'CANCELADO')
        """ + RECORTE + """
        GROUP BY 1, 2, m.id, m.cod_material, m.descricao, m.unidade_medida
        ORDER BY solicitacoes DESC, quantidadeSolicitada DESC
        LIMIT :limite
        """, nativeQuery = true)
    List<MaterialAnaliticoProjection> listarMateriais(
            @Param("inicio") String inicio,
            @Param("fim") String fim,
            @Param("contexto") String contexto,
            @Param("filtrarPorEquipes") boolean filtrarPorEquipes,
            @Param("idsEquipes") List<UUID> idsEquipes,
            @Param("idAlmoxarifado") String idAlmoxarifado,
            @Param("limite") int limite);

    /** Resumo por processo de um período (usado para o período anterior, de mesma duração). */
    @Query(value = """
        SELECT
            s.contexto AS contexto,
            COUNT(*) AS total,
            COUNT(*) FILTER (WHERE s.status IN ('ENTREGUE', 'DEVOLVIDO') OR s.encerrada_em IS NOT NULL) AS concluidas,
            COUNT(*) FILTER (WHERE s.status IN ('REJEITADO', 'CANCELADO')) AS rejeitadas,
            CAST(AVG(EXTRACT(EPOCH FROM (s.data_entrega - s.data_solicitacao)) / 3600)
                FILTER (WHERE s.status IN ('ENTREGUE', 'DEVOLVIDO') AND s.data_entrega IS NOT NULL)
                AS DOUBLE PRECISION) AS cicloMedioHoras,
            CAST(AVG(EXTRACT(EPOCH FROM (s.data_verificacao - s.data_solicitacao)) / 3600)
                FILTER (WHERE s.data_verificacao IS NOT NULL) AS DOUBLE PRECISION) AS aprovacaoMediaHoras
        FROM solicitacoes_material s
        LEFT JOIN estoques ed ON ed.id = s.id_estoque_destino
        WHERE s.data_solicitacao >= CAST(:inicio AS TIMESTAMP WITH TIME ZONE)
          AND s.data_solicitacao < CAST(:fim AS TIMESTAMP WITH TIME ZONE)
        """ + RECORTE + """
        GROUP BY s.contexto
        """, nativeQuery = true)
    List<ResumoPeriodoSolicitacaoProjection> resumirPeriodo(
            @Param("inicio") String inicio,
            @Param("fim") String fim,
            @Param("contexto") String contexto,
            @Param("filtrarPorEquipes") boolean filtrarPorEquipes,
            @Param("idsEquipes") List<UUID> idsEquipes,
            @Param("idAlmoxarifado") String idAlmoxarifado);

    /** Itens das solicitações informadas (exportação detalhada); chamar em lotes de até 1.000 IDs. */
    @Query(value = """
        SELECT
            i.id_solicitacao AS idSolicitacao,
            m.cod_material AS codigo,
            m.descricao AS descricao,
            m.unidade_medida AS unidade,
            i.num_serie AS numeroSerie,
            i.quantidade AS quantidadeSolicitada,
            """ + ATENDIDA + """
            AS quantidadeAtendida,
            mp.nome AS motivoPendencia,
            i.justificativa_pendencia AS justificativaPendencia
        FROM itens_solicitacao i
        JOIN solicitacoes_material s ON s.id = i.id_solicitacao
        JOIN materiais m ON m.id = i.id_material
        LEFT JOIN motivos_pendencia mp ON mp.id = i.id_motivo_pendencia
        WHERE i.id_solicitacao IN :ids
        ORDER BY i.id_solicitacao, m.cod_material
        """, nativeQuery = true)
    List<ItemAnaliticoProjection> listarItens(@Param("ids") List<UUID> ids);
}
