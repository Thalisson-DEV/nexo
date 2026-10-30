package com.nexo.app.modules.sst.infrastructure.analytics;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.sst.domain.StatusInspecao;
import com.nexo.app.shared.application.analytics.SemanticDataType;
import com.nexo.app.shared.application.analytics.SemanticDatasetDescriptor;
import com.nexo.app.shared.application.analytics.SemanticDatasetProvider;
import com.nexo.app.shared.application.analytics.SemanticFilterOperator;
import com.nexo.app.shared.application.analytics.SemanticQuery;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import com.nexo.app.shared.application.analytics.SemanticQueryResult;
import com.nexo.app.shared.infrastructure.analytics.JpaSemanticQueryExecutor;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SstInspecoesSemanticDataset implements SemanticDatasetProvider {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    private static final Set<SemanticFilterOperator> STANDARD = Set.of(
            SemanticFilterOperator.EQ, SemanticFilterOperator.NE, SemanticFilterOperator.IN,
            SemanticFilterOperator.IS_NULL, SemanticFilterOperator.IS_NOT_NULL);
    private static final Set<SemanticFilterOperator> ORDERED = Set.of(
            SemanticFilterOperator.EQ, SemanticFilterOperator.GT, SemanticFilterOperator.GTE,
            SemanticFilterOperator.LT, SemanticFilterOperator.LTE, SemanticFilterOperator.BETWEEN);

    private final EntityManager entityManager;
    private final EquipeApi equipeApi;
    private final ColaboradorApi colaboradorApi;
    private final UsuarioApi usuarioApi;
    private final ProcessoNegocioApi processoApi;
    private final BaseOperacionalApi baseApi;

    @Override
    public SemanticDatasetDescriptor descriptor() {
        return new SemanticDatasetDescriptor(
                "sst_inspecoes", "1.1.0", "Inspecoes de seguranca",
                "Inspecoes SST com tempo, status, inspetor, equipe, processo, base, colaborador inspecionado, "
                        + "desvios e cobertura. Para cumprimento de metas use a metrica oficial de atingimento de metas, "
                        + "e para cobertura do efetivo a metrica oficial de cobertura de colaboradores. "
                        + "Quando o usuario nao informa periodo, aplica os ultimos 12 meses.",
                "sst:dashboard:read", true, 200, 1825,
                List.of(
                        dimension("data_realizacao", "Data/hora da realizacao em America/Sao_Paulo", SemanticDataType.DATETIME, false, ORDERED),
                        dimension("ano", "Ano da realizacao", SemanticDataType.INTEGER, true, ORDERED),
                        dimension("mes", "Mes numerico da realizacao (1-12)", SemanticDataType.INTEGER, true, ORDERED),
                        dimension("status_inspecao", "Status da inspecao", SemanticDataType.ENUM, true, STANDARD),
                        dimension("inspetor", "Nome do inspetor que realizou a inspecao", SemanticDataType.TEXT, true, STANDARD),
                        dimension("equipe", "Codigo da equipe inspecionada", SemanticDataType.TEXT, true, STANDARD),
                        dimension("colaborador_inspecionado", "Nome do colaborador alcancado pela inspecao", SemanticDataType.TEXT, true, STANDARD),
                        dimension("processo", "Nome do processo de negocio da inspecao (atividade operacional)", SemanticDataType.TEXT, true, STANDARD),
                        dimension("processo_inspetor", "Nome do processo de negocio do inspetor (quem realizou a inspecao)", SemanticDataType.TEXT, true, STANDARD),
                        dimension("base_operacional", "Nome da base operacional", SemanticDataType.TEXT, true, STANDARD),
                        dimension("formulario_id", "Identificador do formulario", SemanticDataType.UUID, true, STANDARD)
                ),
                List.of(
                        measure("total_inspecoes", "Quantidade distinta de inspecoes", SemanticDataType.INTEGER, "count(distinct inspecao)"),
                        measure("total_desvios", "Quantidade distinta de desvios encontrados", SemanticDataType.INTEGER, "count(distinct desvio)"),
                        measure("total_colaboradores_inspecionados", "Quantidade distinta de colaboradores inspecionados", SemanticDataType.INTEGER, "count(distinct colaborador inspecionado)")
                ),
                List.of(
                        "Quantas inspecoes foram realizadas por equipe e mes?",
                        "Quais inspetores fizeram mais inspecoes concluidas neste trimestre?",
                        "Quais colaboradores foram mais inspecionados neste ano?",
                        "Quantos colaboradores distintos cada equipe inspecionou?",
                        "Compare desvios encontrados entre bases operacionais."));
    }

    @Override
    public SemanticQueryResult executar(SemanticQuery query, SemanticQueryContext context) {
        SemanticQuery effective = withDefaultPeriod(query, "data_realizacao", 365);
        Map<String, JpaSemanticQueryExecutor.FieldBinding> fields = new LinkedHashMap<>();
        fields.put("data_realizacao", field("i.dataRealizacao", SstInspecoesSemanticDataset::parseDateTime));
        fields.put("ano", field("year(i.dataRealizacao)", Integer::valueOf));
        fields.put("mes", field("month(i.dataRealizacao)", Integer::valueOf));
        fields.put("status_inspecao", field("i.status", value -> StatusInspecao.valueOf(value.toUpperCase())));
        fields.put("inspetor", field("i.idInspetor", UUID::fromString));
        fields.put("equipe", field("i.idEquipe", UUID::fromString));
        fields.put("colaborador_inspecionado", field("c.idColaborador", UUID::fromString));
        fields.put("processo", field("i.idProcesso", UUID::fromString));
        fields.put("processo_inspetor", field("cin.processo.id", UUID::fromString));
        fields.put("base_operacional", field("i.idBaseOperacional", UUID::fromString));
        fields.put("formulario_id", field("i.idFormulario", UUID::fromString));

        Map<String, String> measures = Map.of(
                "total_inspecoes", "count(distinct i.id)",
                "total_desvios", "count(distinct d.id)",
                "total_colaboradores_inspecionados", "count(distinct c.idColaborador)");

        return JpaSemanticQueryExecutor.execute(
                entityManager, descriptor(), effective,
                "from InspecaoSeguranca i left join i.desvios d left join i.colaboradoresInspecionados c left join com.nexo.app.modules.rh.domain.Colaborador cin on (cin.idUsuario = i.idInspetor or cin.id = i.idInspetor)",
                "i.ativo = true", Map.of(), fields, measures, this::enrichNames);
    }

    private void enrichNames(Map<String, Object> row) {
        replaceUuid(row, "equipe", id -> equipeApi.buscarPorIdResponse(id).map(e -> e.codigo()).orElse(id.toString()));
        replaceUuid(row, "colaborador_inspecionado", id -> colaboradorApi.buscarPorIdResponse(id)
                .map(colaborador -> colaborador.nomeCompleto()).orElse(id.toString()));
        replaceUuid(row, "inspetor", id -> colaboradorApi.buscarNomeCompletoPorUsuario(id)
                .or(() -> usuarioApi.buscarNomePorId(id)).orElse(id.toString()));
        replaceUuid(row, "processo", id -> processoApi.buscarPorIdResponse(id).map(p -> p.nome()).orElse(id.toString()));
        replaceUuid(row, "processo_inspetor", id -> processoApi.buscarPorIdResponse(id).map(p -> p.nome()).orElse(id.toString()));
        replaceUuid(row, "base_operacional", id -> baseApi.buscarPorIdResponse(id).map(b -> b.nome()).orElse(id.toString()));
    }

    private void replaceUuid(Map<String, Object> row, String field, java.util.function.Function<UUID, String> resolver) {
        Object value = row.get(field);
        if (value instanceof UUID id) row.put(field, resolver.apply(id));
    }

    private SemanticQuery withDefaultPeriod(SemanticQuery query, String field, int days) {
        if (query.filtros().stream().anyMatch(filter -> field.equals(filter.campo()))) return query;
        List<SemanticQuery.SemanticFilter> filters = new ArrayList<>(query.filtros());
        filters.add(new SemanticQuery.SemanticFilter(field, SemanticFilterOperator.GTE,
                List.of(LocalDate.now(ZONE_ID).minusDays(days).toString())));
        return new SemanticQuery(query.dataset(), query.dimensoes(), query.medidas(), filters,
                query.agruparPor(), query.ordenarPor(), query.limite());
    }

    private static OffsetDateTime parseDateTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException ignored) {
            return LocalDate.parse(value).atStartOfDay(ZONE_ID).toOffsetDateTime();
        }
    }

    private static SemanticDatasetDescriptor.SemanticDimension dimension(
            String id, String description, SemanticDataType type, boolean groupable, Set<SemanticFilterOperator> operators) {
        return new SemanticDatasetDescriptor.SemanticDimension(id, description, type, groupable, false, operators);
    }

    private static SemanticDatasetDescriptor.SemanticMeasure measure(
            String id, String description, SemanticDataType type, String definition) {
        return new SemanticDatasetDescriptor.SemanticMeasure(id, description, type, definition);
    }

    private static JpaSemanticQueryExecutor.FieldBinding field(String expression, java.util.function.Function<String, Object> parser) {
        return new JpaSemanticQueryExecutor.FieldBinding(expression, parser);
    }
}
