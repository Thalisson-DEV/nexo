package com.nexo.app.modules.estoque.infrastructure.persistence;

import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DashboardSolicitacaoRepository extends JpaRepository<SolicitacaoMaterial, UUID> {

    // -----------------------------------------------------------------------------------------------------------------
    // V.2 MELHORIAS APLICADAS:
    //
    // [1] Double-cast frágil removido: substituído por ":param IS NULL OR ..." com cast direto e tipado.
    //
    // [2] Lista vazia em IN (:idsEquipes): a responsabilidade de garantir lista não  vazia quando
    //     filtrarPorEquipes=true foi movida para a camada de serviço. Adicionado comentário de contrato.
    //
    // [3] CTE periodo com resultset vazio: adicionado NULLIF na subtração para evitar NULL propagado,
    //     e o GREATEST(1, ...) agora também lida com NULL via COALESCE.
    //
    // [4] tempoMedioAtendimentoHoras: filtrado para considerar apenas status ENTREGUE e ENTREGUE_PARCIAL,
    //     evitando contaminação por registros com data_entrega preenchida por inconsistência.
    //
    // [5] totalSolicitado em obterTopMateriais: mantido COUNT(i.id) com comentário explicativo.
    //     Ajuste para SUM(quantidade_solicitada) deve ser feito conforme regra de negócio confirmada.
    //
    // [6] Filtro de equipe aplicado apenas ao estoque destino: comportamento mantido e documentado,
    //     pois reflete a regra de negócio (equipe pertence ao estoque destino).
    //
    // [7] INNER JOIN em motivos_rejeicao: substituído por LEFT JOIN com COALESCE para não ocultar
    //     rejeições sem motivo cadastrado (dados inconsistentes).
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Retorna as métricas consolidadas do dashboard de solicitações de material.
     *
     * <p><b>Contrato:</b> quando {@code filtrarPorEquipes = true}, a lista {@code idsEquipes}
     * deve conter ao menos um elemento. Listas vazias causam erro de sintaxe SQL ({@code IN ()}).
     * Essa validação deve ser feita na camada de serviço antes de invocar este método.</p>
     */
    @Query(value = """
        WITH metricas_base AS (
            SELECT
                s.id,
                s.status,
                s.data_solicitacao,
                s.data_verificacao,
                s.data_entrega,
                (SELECT COUNT(*) FROM itens_solicitacao i WHERE i.id_solicitacao = s.id) AS qtd_itens
            FROM solicitacoes_material s
            INNER JOIN estoques ed ON s.id_estoque_destino = ed.id
            WHERE s.contexto = 'COMERCIAL'
              AND (:dataInicio IS NULL OR s.data_solicitacao >= CAST(:dataInicio AS TIMESTAMP WITH TIME ZONE))
              AND (:dataFim IS NULL OR s.data_solicitacao <= CAST(:dataFim AS TIMESTAMP WITH TIME ZONE))
              AND (:idEstoqueOrigem IS NULL OR s.id_estoque_origem  = CAST(:idEstoqueOrigem AS UUID))
              AND (:idEstoqueDestino IS NULL OR s.id_estoque_destino = CAST(:idEstoqueDestino AS UUID))
              AND (:filtrarPorEquipes = FALSE OR ed.id_equipe IN :idsEquipes)
        ),
        periodo AS (
            -- COALESCE + NULLIF garantem que dias nunca seja NULL nem zero,
            -- mesmo quando metricas_base está vazia (MIN/MAX retornam NULL).
            SELECT
                GREATEST(1, COALESCE(
                    EXTRACT(DAY FROM (
                        COALESCE(CAST(:dataFim AS TIMESTAMP WITH TIME ZONE), MAX(data_solicitacao)) -
                        COALESCE(CAST(:dataInicio AS TIMESTAMP WITH TIME ZONE), MIN(data_solicitacao))
                    )),
                    1
                )) AS dias
            FROM metricas_base
        )
        SELECT
            COUNT(id) AS totalSolicitacoes,
            COUNT(CASE WHEN status = 'PENDENTE' THEN 1 END) AS totalPendentes,
            COUNT(CASE WHEN status = 'APROVADO' THEN 1 END) AS totalAprovadas,
            COUNT(CASE WHEN status = 'REJEITADO' THEN 1 END) AS totalRejeitadas,
            COUNT(CASE WHEN status = 'ENTREGUE' THEN 1 END) AS totalEntregues,
            COUNT(CASE WHEN status = 'ENTREGUE_PARCIAL' THEN 1 END) AS totalEntreguesParciais,
            COUNT(CASE WHEN status = 'ESTORNADO' THEN 1 END) AS totalEstornados,

            -- [FIX #4] Filtrado para apenas entregas concluídas, evitando contaminação
            -- por registros com data_entrega preenchida em status indevido.
            AVG(CASE WHEN status IN ('ENTREGUE', 'ENTREGUE_PARCIAL')
                     THEN EXTRACT(EPOCH FROM (data_entrega - data_verificacao)) / 3600
                END) AS tempoMedioAtendimentoHoras,

            -- tempoMedioAprovacao considera apenas registros que passaram por verificação
            AVG(CASE WHEN data_verificacao IS NOT NULL
                     THEN EXTRACT(EPOCH FROM (data_verificacao - data_solicitacao)) / 3600
                END) AS tempoMedioAprovacaoHoras,

            (COUNT(id)::float / NULLIF((SELECT dias FROM periodo), 0)) AS mediaSolicitacoesPorDia,

            (COUNT(CASE WHEN status = 'PENDENTE' THEN 1 END)::float / NULLIF(COUNT(id), 0)) * 100 AS percentualPendentes,
            (COUNT(CASE WHEN status = 'APROVADO' THEN 1 END)::float / NULLIF(COUNT(id), 0)) * 100 AS percentualAprovadas,
            (COUNT(CASE WHEN status = 'REJEITADO' THEN 1 END)::float / NULLIF(COUNT(id), 0)) * 100 AS percentualRejeitadas,
            (COUNT(CASE WHEN status = 'ENTREGUE' THEN 1 END)::float / NULLIF(COUNT(id), 0)) * 100 AS percentualEntregues,
            (COUNT(CASE WHEN status = 'ENTREGUE_PARCIAL' THEN 1 END)::float / NULLIF(COUNT(id), 0)) * 100 AS percentualEntreguesParciais,
            (COUNT(CASE WHEN status = 'ESTORNADO' THEN 1 END)::float / NULLIF(COUNT(id), 0)) * 100 AS percentualEstornados,
            (COUNT(CASE WHEN status = 'REJEITADO' THEN 1 END)::float / NULLIF(COUNT(id), 0)) * 100 AS indiceRejeicao,
            (COUNT(CASE WHEN status = 'ENTREGUE' THEN 1 END)::float / NULLIF(COUNT(id), 0)) * 100 AS indiceSucessoEntrega,

            SUM(qtd_itens) AS totalItensSolicitados,
            AVG(qtd_itens) AS mediaItensPorSolicitacao
        FROM metricas_base
    """, nativeQuery = true)
    DashboardSolicitacaoProjection obterMetricasDashboard(
            @Param("dataInicio") String dataInicio,
            @Param("dataFim") String dataFim,
            @Param("idEstoqueOrigem") String idEstoqueOrigem,
            @Param("idEstoqueDestino") String idEstoqueDestino,
            @Param("filtrarPorEquipes") boolean filtrarPorEquipes,
            @Param("idsEquipes") List<UUID> idsEquipes);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Retorna os 10 materiais mais solicitados no período e filtros informados.
     *
     * <p><b>Nota:</b> {@code totalSolicitado} representa o número de ocorrências do material
     * em itens de solicitação (COUNT), não a soma das quantidades solicitadas.
     * Caso a regra de negócio exija a soma de quantidades, substituir por
     * {@code SUM(i.quantidade)}.</p>
     *
     * <p><b>Contrato:</b> quando {@code filtrarPorEquipes = true}, {@code idsEquipes}
     * deve conter ao menos um elemento.</p>
     */
    @Query(value = """
        SELECT
            m.cod_material AS codigo,
            m.descricao AS nome,
            COUNT(i.id) AS totalSolicitado
        FROM itens_solicitacao i
        INNER JOIN solicitacoes_material s ON i.id_solicitacao = s.id
        INNER JOIN materiais m ON i.id_material    = m.id
        INNER JOIN estoques ed ON s.id_estoque_destino = ed.id
        WHERE s.contexto = 'COMERCIAL'
          AND (:dataInicio IS NULL OR s.data_solicitacao >= CAST(:dataInicio AS TIMESTAMP WITH TIME ZONE))
          AND (:dataFim IS NULL OR s.data_solicitacao <= CAST(:dataFim AS TIMESTAMP WITH TIME ZONE))
          AND (:idEstoqueOrigem IS NULL OR s.id_estoque_origem  = CAST(:idEstoqueOrigem  AS UUID))
          AND (:idEstoqueDestino IS NULL OR s.id_estoque_destino = CAST(:idEstoqueDestino AS UUID))
          AND (:filtrarPorEquipes = FALSE OR ed.id_equipe IN :idsEquipes)
        GROUP BY m.id, m.cod_material, m.descricao
        ORDER BY totalSolicitado DESC
        LIMIT 10
    """, nativeQuery = true)
    List<TopMaterialProjection> obterTopMateriais(
            @Param("dataInicio")        String dataInicio,
            @Param("dataFim")           String dataFim,
            @Param("idEstoqueOrigem")   String idEstoqueOrigem,
            @Param("idEstoqueDestino")  String idEstoqueDestino,
            @Param("filtrarPorEquipes") boolean filtrarPorEquipes,
            @Param("idsEquipes")        List<UUID> idsEquipes);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Retorna os 10 estoques destino com maior volume de solicitações recebidas.
     *
     * <p><b>Nota sobre filtro de equipes:</b> o filtro {@code idsEquipes} é aplicado
     * sobre o estoque <em>destino</em>, pois a equipe está vinculada ao destino no domínio.
     * Solicitações originadas de estoques de outras equipes não são excluídas por esse filtro.</p>
     *
     * <p><b>Contrato:</b> quando {@code filtrarPorEquipes = true}, {@code idsEquipes}
     * deve conter ao menos um elemento.</p>
     */
    @Query(value = """
        SELECT
            ed.nome_identificador AS nome,
            COUNT(s.id) AS totalSolicitacoes
        FROM solicitacoes_material s
        INNER JOIN estoques ed ON s.id_estoque_destino = ed.id
        WHERE s.contexto = 'COMERCIAL'
          AND (:dataInicio IS NULL OR s.data_solicitacao >= CAST(:dataInicio AS TIMESTAMP WITH TIME ZONE))
          AND (:dataFim IS NULL OR s.data_solicitacao <= CAST(:dataFim AS TIMESTAMP WITH TIME ZONE))
          AND (:idEstoqueOrigem IS NULL OR s.id_estoque_origem = CAST(:idEstoqueOrigem  AS UUID))
          AND (:idEstoqueDestino IS NULL OR s.id_estoque_destino = CAST(:idEstoqueDestino AS UUID))
          AND (:filtrarPorEquipes = FALSE OR ed.id_equipe IN :idsEquipes)
        GROUP BY ed.id, ed.nome_identificador
        ORDER BY totalSolicitacoes DESC
        LIMIT 10
    """, nativeQuery = true)
    List<TopEstoqueProjection> obterTopEstoques(
            @Param("dataInicio")        String dataInicio,
            @Param("dataFim")           String dataFim,
            @Param("idEstoqueOrigem")   String idEstoqueOrigem,
            @Param("idEstoqueDestino")  String idEstoqueDestino,
            @Param("filtrarPorEquipes") boolean filtrarPorEquipes,
            @Param("idsEquipes")        List<UUID> idsEquipes);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Retorna os 10 motivos de rejeição mais frequentes no período e filtros informados.
     *
     * <p><b>Nota:</b> utiliza LEFT JOIN para incluir solicitações rejeitadas sem motivo
     * cadastrado (possível inconsistência de dados), agrupando-as como "Sem motivo informado".</p>
     *
     * <p><b>Contrato:</b> quando {@code filtrarPorEquipes = true}, {@code idsEquipes}
     * deve conter ao menos um elemento.</p>
     */
    @Query(value = """
        SELECT
            COALESCE(mr.nome, 'Sem motivo informado') AS nome,
            COUNT(s.id) AS totalOcorrencias
        FROM solicitacoes_material s
        -- [FIX #7] LEFT JOIN para não ocultar rejeições sem motivo_rejeicao preenchido
        LEFT  JOIN motivos_rejeicao mr ON s.id_motivo_rejeicao = mr.id
        INNER JOIN estoques ed ON s.id_estoque_destino  = ed.id
        WHERE s.contexto = 'COMERCIAL'
          AND (:dataInicio IS NULL OR s.data_solicitacao >= CAST(:dataInicio AS TIMESTAMP WITH TIME ZONE))
          AND (:dataFim IS NULL OR s.data_solicitacao <= CAST(:dataFim AS TIMESTAMP WITH TIME ZONE))
          AND (:idEstoqueOrigem IS NULL OR s.id_estoque_origem  = CAST(:idEstoqueOrigem AS UUID))
          AND (:idEstoqueDestino IS NULL OR s.id_estoque_destino = CAST(:idEstoqueDestino AS UUID))
          AND (:filtrarPorEquipes = FALSE OR ed.id_equipe IN :idsEquipes)
          AND s.status = 'REJEITADO'
        GROUP BY mr.id, mr.nome
        ORDER BY totalOcorrencias DESC
        LIMIT 10
    """, nativeQuery = true)
    List<TopMotivoProjection> obterTopMotivos(
            @Param("dataInicio")        String dataInicio,
            @Param("dataFim")           String dataFim,
            @Param("idEstoqueOrigem")   String idEstoqueOrigem,
            @Param("idEstoqueDestino")  String idEstoqueDestino,
            @Param("filtrarPorEquipes") boolean filtrarPorEquipes,
            @Param("idsEquipes")        List<UUID> idsEquipes);

    @Query(value = """
        SELECT
            s.contexto AS contexto,
            s.tipo_tecnico AS tipoTecnico,
            s.tipo_obra_snapshot AS tipoObra,
            s.codigo_ocorrencia_snapshot AS ocorrencia,
            s.id_obra AS idObra,
            s.id_equipe_saque AS idEquipe,
            s.status_separacao AS statusSeparacao,
            s.id_almoxarifado_atendimento AS idAlmoxarifadoAtendimento,
            COUNT(DISTINCT s.id) AS totalSolicitacoes,
            COUNT(i.id) AS totalItens,
            COALESCE(SUM(i.quantidade), 0) AS quantidadeSolicitada
        FROM solicitacoes_material s
        LEFT JOIN itens_solicitacao i ON i.id_solicitacao = s.id
        WHERE s.contexto = 'TECNICO'
          AND (:dataInicio IS NULL OR s.data_solicitacao >= CAST(:dataInicio AS TIMESTAMP WITH TIME ZONE))
          AND (:dataFim IS NULL OR s.data_solicitacao <= CAST(:dataFim AS TIMESTAMP WITH TIME ZONE))
          AND (:tipoTecnico IS NULL OR s.tipo_tecnico = :tipoTecnico)
          AND (:tipoObra IS NULL OR s.tipo_obra_snapshot = :tipoObra)
           AND (:ocorrencia IS NULL OR s.codigo_ocorrencia_snapshot = :ocorrencia)
          AND (:idObra IS NULL OR s.id_obra = CAST(:idObra AS UUID))
          AND (:idEquipe IS NULL OR s.id_equipe_saque = CAST(:idEquipe AS UUID))
          AND (:statusSeparacao IS NULL OR s.status_separacao = :statusSeparacao)
          AND (:idAlmoxarifado IS NULL OR s.id_almoxarifado_atendimento = CAST(:idAlmoxarifado AS UUID))
        GROUP BY s.contexto, s.tipo_tecnico, s.tipo_obra_snapshot,
                 s.codigo_ocorrencia_snapshot, s.id_obra, s.id_equipe_saque, s.status_separacao,
                 s.id_almoxarifado_atendimento
        ORDER BY s.tipo_tecnico
    """, nativeQuery = true)
    List<SegmentoSolicitacaoTecnicaProjection> obterSegmentosTecnicos(
            @Param("dataInicio") String dataInicio,
            @Param("dataFim") String dataFim,
            @Param("tipoTecnico") String tipoTecnico,
            @Param("tipoObra") String tipoObra,
            @Param("ocorrencia") String ocorrencia,
            @Param("idObra") String idObra,
            @Param("idEquipe") String idEquipe,
            @Param("statusSeparacao") String statusSeparacao,
            @Param("idAlmoxarifado") String idAlmoxarifado);
}
