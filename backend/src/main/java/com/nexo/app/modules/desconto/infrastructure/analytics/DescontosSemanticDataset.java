package com.nexo.app.modules.desconto.infrastructure.analytics;

import com.nexo.app.modules.desconto.domain.StatusDesconto;
import com.nexo.app.shared.application.analytics.*;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticQueryExecutor;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DescontosSemanticDataset implements SemanticDatasetProvider {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    private static final Set<SemanticFilterOperator> STANDARD = Set.of(
            SemanticFilterOperator.EQ, SemanticFilterOperator.NE, SemanticFilterOperator.IN,
            SemanticFilterOperator.IS_NULL, SemanticFilterOperator.IS_NOT_NULL);
    private static final Set<SemanticFilterOperator> TEXT = Set.of(
            SemanticFilterOperator.EQ, SemanticFilterOperator.NE, SemanticFilterOperator.IN,
            SemanticFilterOperator.CONTAINS, SemanticFilterOperator.IS_NULL, SemanticFilterOperator.IS_NOT_NULL);
    private static final Set<SemanticFilterOperator> ORDERED = Set.of(
            SemanticFilterOperator.EQ, SemanticFilterOperator.GT, SemanticFilterOperator.GTE,
            SemanticFilterOperator.LT, SemanticFilterOperator.LTE, SemanticFilterOperator.BETWEEN);

    private final EntityManager entityManager;

    @Override
    public SemanticDatasetDescriptor descriptor() {
        return new SemanticDatasetDescriptor(
                "descontos", "1.0.0", "Descontos e seus itens",
                "Ciclo de vida dos descontos, motivos, tipos de equipamento, materiais e valores de itens. "
                        + "Nao inclui observacoes, rejeicoes livres, anexos ou dados pessoais. Periodo padrao: ultimos 12 meses.",
                "desconto:read", true, 200, 1825,
                List.of(
                        dimension("data_ocorrencia", "Data/hora da ocorrencia", SemanticDataType.DATETIME, false, ORDERED),
                        dimension("ano", "Ano da ocorrencia", SemanticDataType.INTEGER, true, ORDERED),
                        dimension("mes", "Mes da ocorrencia (1-12)", SemanticDataType.INTEGER, true, ORDERED),
                        dimension("status_desconto", "Status do desconto", SemanticDataType.ENUM, true, STANDARD),
                        dimension("motivo", "Descricao do motivo", SemanticDataType.TEXT, true, TEXT),
                        dimension("tipo_equipamento", "Descricao do tipo de equipamento", SemanticDataType.TEXT, true, TEXT),
                        dimension("material", "Descricao do material do item", SemanticDataType.TEXT, true, TEXT),
                        dimension("colaborador_id", "Identificador do colaborador afetado", SemanticDataType.UUID, true, STANDARD),
                        dimension("solicitante_id", "Identificador do usuario solicitante", SemanticDataType.UUID, true, STANDARD),
                        dimension("quantidade_parcelas", "Quantidade de parcelas", SemanticDataType.INTEGER, true, ORDERED)
                ),
                List.of(
                        measure("total_descontos", "Quantidade distinta de descontos", SemanticDataType.INTEGER, "count(distinct desconto)"),
                        measure("total_itens", "Quantidade distinta de itens", SemanticDataType.INTEGER, "count(distinct item)"),
                        measure("valor_total_itens", "Soma dos valores dos itens selecionados", SemanticDataType.DECIMAL, "sum(valor do item)"),
                        measure("descontos_baixados", "Quantidade distinta no status BAIXADO", SemanticDataType.INTEGER, "count distinct BAIXADO"),
                        measure("descontos_rejeitados", "Quantidade distinta no status REJEITADO", SemanticDataType.INTEGER, "count distinct REJEITADO"),
                        measure("resultados_finais", "BAIXADO + REJEITADO distintos", SemanticDataType.INTEGER, "count distinct finalizado")
                ),
                List.of(
                        "Quais materiais geraram maior valor de desconto por mes?",
                        "Compare baixados e rejeitados por motivo e tipo de equipamento.",
                        "Quantos descontos solicitados ainda aguardam validacao?"));
    }

    @Override
    public SemanticQueryResult executar(SemanticQuery query, SemanticQueryContext context) {
        SemanticQuery effective = withDefaultPeriod(query);
        Map<String, JpaSemanticQueryExecutor.FieldBinding> fields = new LinkedHashMap<>();
        fields.put("data_ocorrencia", field("d.dataOcorrencia", DescontosSemanticDataset::parseDateTime));
        fields.put("ano", field("year(d.dataOcorrencia)", Integer::valueOf));
        fields.put("mes", field("month(d.dataOcorrencia)", Integer::valueOf));
        fields.put("status_desconto", field("d.status", value -> StatusDesconto.valueOf(value.toUpperCase())));
        fields.put("motivo", field("motivo.descricao", value -> value));
        fields.put("tipo_equipamento", field("tipo.descricao", value -> value));
        fields.put("material", field("item.descricaoMaterial", value -> value));
        fields.put("colaborador_id", field("d.colaboradorId", UUID::fromString));
        fields.put("solicitante_id", field("d.usuarioSolicitanteId", UUID::fromString));
        fields.put("quantidade_parcelas", field("d.quantidadeParcelas", Integer::valueOf));

        Map<String, String> measures = Map.of(
                "total_descontos", "count(distinct d.id)",
                "total_itens", "count(distinct item.id)",
                "valor_total_itens", "coalesce(sum(item.valor), 0)",
                "descontos_baixados", "count(distinct case when d.status = com.nexo.app.modules.desconto.domain.StatusDesconto.BAIXADO then d.id else null end)",
                "descontos_rejeitados", "count(distinct case when d.status = com.nexo.app.modules.desconto.domain.StatusDesconto.REJEITADO then d.id else null end)",
                "resultados_finais", "count(distinct case when d.status in (com.nexo.app.modules.desconto.domain.StatusDesconto.BAIXADO, com.nexo.app.modules.desconto.domain.StatusDesconto.REJEITADO) then d.id else null end)");

        return JpaSemanticQueryExecutor.execute(entityManager, descriptor(), effective,
                "from Desconto d left join d.itens item left join item.tipoEquipamento tipo join d.motivo motivo",
                "d.ativo = true and (item is null or item.ativo = true)", Map.of(), fields, measures, null);
    }

    private SemanticQuery withDefaultPeriod(SemanticQuery query) {
        if (query.filtros().stream().anyMatch(filter -> "data_ocorrencia".equals(filter.campo()))) return query;
        List<SemanticQuery.SemanticFilter> filters = new ArrayList<>(query.filtros());
        filters.add(new SemanticQuery.SemanticFilter("data_ocorrencia", SemanticFilterOperator.GTE,
                List.of(LocalDate.now(ZONE_ID).minusDays(365).toString())));
        return new SemanticQuery(query.dataset(), query.dimensoes(), query.medidas(), filters,
                query.agruparPor(), query.ordenarPor(), query.limite());
    }

    private static OffsetDateTime parseDateTime(String value) {
        try { return OffsetDateTime.parse(value); }
        catch (RuntimeException ignored) { return LocalDate.parse(value).atStartOfDay(ZONE_ID).toOffsetDateTime(); }
    }

    private static SemanticDatasetDescriptor.SemanticDimension dimension(String id, String description,
            SemanticDataType type, boolean groupable, Set<SemanticFilterOperator> operators) {
        return new SemanticDatasetDescriptor.SemanticDimension(id, description, type, groupable, false, operators);
    }

    private static SemanticDatasetDescriptor.SemanticMeasure measure(String id, String description,
            SemanticDataType type, String definition) {
        return new SemanticDatasetDescriptor.SemanticMeasure(id, description, type, definition);
    }

    private static JpaSemanticQueryExecutor.FieldBinding field(String expression, java.util.function.Function<String, Object> parser) {
        return new JpaSemanticQueryExecutor.FieldBinding(expression, parser);
    }
}
