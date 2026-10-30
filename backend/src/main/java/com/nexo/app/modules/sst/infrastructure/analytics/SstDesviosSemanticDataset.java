package com.nexo.app.modules.sst.infrastructure.analytics;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.StatusDesvio;
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
public class SstDesviosSemanticDataset implements SemanticDatasetProvider {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    private static final Set<SemanticFilterOperator> STANDARD = Set.of(
            SemanticFilterOperator.EQ, SemanticFilterOperator.NE, SemanticFilterOperator.IN,
            SemanticFilterOperator.IS_NULL, SemanticFilterOperator.IS_NOT_NULL);
    private static final Set<SemanticFilterOperator> ORDERED = Set.of(
            SemanticFilterOperator.EQ, SemanticFilterOperator.GT, SemanticFilterOperator.GTE,
            SemanticFilterOperator.LT, SemanticFilterOperator.LTE, SemanticFilterOperator.BETWEEN,
            SemanticFilterOperator.IS_NULL, SemanticFilterOperator.IS_NOT_NULL);

    private final EntityManager entityManager;
    private final EquipeApi equipeApi;
    private final ColaboradorApi colaboradorApi;
    private final UsuarioApi usuarioApi;
    private final ProcessoNegocioApi processoApi;

    @Override
    public SemanticDatasetDescriptor descriptor() {
        return new SemanticDatasetDescriptor(
                "sst_desvios", "1.1.0", "Desvios de seguranca",
                "Nao conformidades de SST sem descricoes livres, anexos ou dados pessoais nao relacionados. "
                        + "O periodo padrao usa as inspecoes dos ultimos 12 meses.",
                "sst:dashboard:read", true, 200, 1825,
                List.of(
                        dimension("data_inspecao", "Data/hora da inspecao de origem", SemanticDataType.DATETIME, false, ORDERED),
                        dimension("ano", "Ano da inspecao", SemanticDataType.INTEGER, true, ORDERED),
                        dimension("mes", "Mes da inspecao (1-12)", SemanticDataType.INTEGER, true, ORDERED),
                        dimension("status_desvio", "Status de tratamento", SemanticDataType.ENUM, true, STANDARD),
                        dimension("gravidade", "Gravidade do desvio", SemanticDataType.ENUM, true, STANDARD),
                        dimension("prazo", "Prazo de tratamento", SemanticDataType.DATE, false, ORDERED),
                        dimension("equipe", "Codigo da equipe inspecionada", SemanticDataType.TEXT, true, STANDARD),
                        dimension("inspetor", "Nome do inspetor", SemanticDataType.TEXT, true, STANDARD),
                        dimension("processo", "Nome do processo de negocio da inspecao (atividade operacional)", SemanticDataType.TEXT, true, STANDARD),
                        dimension("processo_inspetor", "Nome do processo de negocio do inspetor (quem realizou a inspecao)", SemanticDataType.TEXT, true, STANDARD),
                        dimension("pergunta_id", "Identificador do item/pergunta", SemanticDataType.UUID, true, STANDARD)
                ),
                List.of(
                        measure("total_desvios", "Quantidade distinta de desvios", SemanticDataType.INTEGER, "count(distinct desvio)"),
                        measure("total_inspecoes", "Quantidade distinta de inspecoes com os desvios", SemanticDataType.INTEGER, "count(distinct inspecao)"),
                        measure("desvios_resolvidos", "Quantidade distinta com status RESOLVIDO", SemanticDataType.INTEGER, "count de desvios resolvidos")
                ),
                List.of(
                        "Quais gravidades estao mais frequentes por equipe?",
                        "Quantos desvios abertos estao vencidos?",
                        "Mostre a tendencia mensal de desvios resolvidos."));
    }

    @Override
    public SemanticQueryResult executar(SemanticQuery query, SemanticQueryContext context) {
        SemanticQuery effective = withDefaultPeriod(query);
        Map<String, JpaSemanticQueryExecutor.FieldBinding> fields = new LinkedHashMap<>();
        fields.put("data_inspecao", field("i.dataRealizacao", SstDesviosSemanticDataset::parseDateTime));
        fields.put("ano", field("year(i.dataRealizacao)", Integer::valueOf));
        fields.put("mes", field("month(i.dataRealizacao)", Integer::valueOf));
        fields.put("status_desvio", field("d.status", value -> StatusDesvio.valueOf(value.toUpperCase())));
        fields.put("gravidade", field("d.gravidade", value -> GravidadeDesvio.valueOf(value.toUpperCase())));
        fields.put("prazo", field("d.prazo", LocalDate::parse));
        fields.put("equipe", field("i.idEquipe", UUID::fromString));
        fields.put("inspetor", field("i.idInspetor", UUID::fromString));
        fields.put("processo", field("i.idProcesso", UUID::fromString));
        fields.put("processo_inspetor", field("cin.processo.id", UUID::fromString));
        fields.put("pergunta_id", field("d.idPergunta", UUID::fromString));
        Map<String, String> measures = Map.of(
                "total_desvios", "count(distinct d.id)",
                "total_inspecoes", "count(distinct i.id)",
                "desvios_resolvidos", "count(distinct case when d.status = com.nexo.app.modules.sst.domain.StatusDesvio.RESOLVIDO then d.id else null end)");

        return JpaSemanticQueryExecutor.execute(entityManager, descriptor(), effective,
                "from DesvioSeguranca d join d.inspecao i left join com.nexo.app.modules.rh.domain.Colaborador cin on (cin.idUsuario = i.idInspetor or cin.id = i.idInspetor)",
                "d.ativo = true and i.ativo = true", Map.of(), fields, measures, this::enrichNames);
    }

    private void enrichNames(Map<String, Object> row) {
        replace(row, "equipe", id -> equipeApi.buscarPorIdResponse(id).map(e -> e.codigo()).orElse(id.toString()));
        replace(row, "inspetor", id -> colaboradorApi.buscarNomeCompletoPorUsuario(id)
                .or(() -> usuarioApi.buscarNomePorId(id)).orElse(id.toString()));
        replace(row, "processo", id -> processoApi.buscarPorIdResponse(id).map(p -> p.nome()).orElse(id.toString()));
        replace(row, "processo_inspetor", id -> processoApi.buscarPorIdResponse(id).map(p -> p.nome()).orElse(id.toString()));
    }

    private void replace(Map<String, Object> row, String field, java.util.function.Function<UUID, String> resolver) {
        if (row.get(field) instanceof UUID id) row.put(field, resolver.apply(id));
    }

    private SemanticQuery withDefaultPeriod(SemanticQuery query) {
        if (query.filtros().stream().anyMatch(filter -> "data_inspecao".equals(filter.campo()))) return query;
        List<SemanticQuery.SemanticFilter> filters = new ArrayList<>(query.filtros());
        filters.add(new SemanticQuery.SemanticFilter("data_inspecao", SemanticFilterOperator.GTE,
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
