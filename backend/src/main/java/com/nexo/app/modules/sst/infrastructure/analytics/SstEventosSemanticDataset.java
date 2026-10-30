package com.nexo.app.modules.sst.infrastructure.analytics;

import com.nexo.app.modules.sst.domain.StatusEventoSst;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
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
import java.util.function.Function;

/**
 * Dataset semântico da acidentalidade.
 *
 * <p>Mais simples que o de desvios, e por um motivo de modelagem: atividade, gestor,
 * base e equipe estão <b>snapshotados</b> no evento, em texto claro (exceto o nome do
 * gestor, que é dado pessoal e fica fora daqui). Não há id a traduzir depois da
 * consulta — o que o banco agrupa já é o rótulo.</p>
 *
 * <p>Nenhum nome de pessoa é exposto: nem o do acidentado, nem o do técnico, nem o do
 * gestor. Quem responde perguntas agregadas não precisa saber quem se machucou, e o que
 * não trafega não vaza. Descrição e tratativa também ficam de fora — são texto livre,
 * onde nome próprio costuma aparecer sem controle.</p>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SstEventosSemanticDataset implements SemanticDatasetProvider {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    private static final Set<SemanticFilterOperator> PADRAO = Set.of(
            SemanticFilterOperator.EQ, SemanticFilterOperator.NE, SemanticFilterOperator.IN,
            SemanticFilterOperator.IS_NULL, SemanticFilterOperator.IS_NOT_NULL);
    private static final Set<SemanticFilterOperator> ORDENAVEL = Set.of(
            SemanticFilterOperator.EQ, SemanticFilterOperator.GT, SemanticFilterOperator.GTE,
            SemanticFilterOperator.LT, SemanticFilterOperator.LTE, SemanticFilterOperator.BETWEEN,
            SemanticFilterOperator.IS_NULL, SemanticFilterOperator.IS_NOT_NULL);

    private final EntityManager entityManager;

    @Override
    public SemanticDatasetDescriptor descriptor() {
        return new SemanticDatasetDescriptor(
                "sst_eventos", "1.0.0", "Eventos de seguranca",
                "Acidentes, quase-acidentes, incidentes, danos materiais e doencas ocupacionais, sem "
                        + "nomes de pessoas, descricoes livres nem anexos. Eventos cancelados ficam de fora. "
                        + "O periodo padrao usa os ultimos 12 meses de ocorrencia.",
                "sst:dashboard:read", true, 200, 1825,
                List.of(
                        dimensao("data_ocorrencia", "Data/hora da ocorrencia", SemanticDataType.DATETIME, false, ORDENAVEL),
                        dimensao("ano", "Ano da ocorrencia", SemanticDataType.INTEGER, true, ORDENAVEL),
                        dimensao("mes", "Mes da ocorrencia (1-12)", SemanticDataType.INTEGER, true, ORDENAVEL),
                        dimensao("tipo", "Tipo da ocorrencia", SemanticDataType.ENUM, true, PADRAO),
                        dimensao("status", "Situacao da tratativa", SemanticDataType.ENUM, true, PADRAO),
                        dimensao("atividade", "Atividade em execucao", SemanticDataType.TEXT, true, PADRAO),
                        dimensao("base_operacional", "Base operacional", SemanticDataType.TEXT, true, PADRAO),
                        dimensao("equipe", "Codigo da equipe", SemanticDataType.TEXT, true, PADRAO),
                        dimensao("possui_cat", "Se houve CAT emitida", SemanticDataType.BOOLEAN, true, PADRAO)),
                List.of(
                        medida("total_eventos", "Quantidade de eventos", SemanticDataType.INTEGER, "count(evento)"),
                        medida("eventos_com_lesao", "Eventos de tipos que implicam lesao", SemanticDataType.INTEGER,
                                "count de acidentes tipicos, de trajeto e doencas ocupacionais"),
                        medida("dias_perdidos", "Soma dos dias perdidos declarados", SemanticDataType.INTEGER,
                                "sum(dias_perdidos)"),
                        medida("eventos_com_cat", "Eventos com CAT declarada", SemanticDataType.INTEGER,
                                "count de eventos com possui_cat verdadeiro")),
                List.of(
                        "Quantos eventos com lesao aconteceram por base neste ano?",
                        "Qual atividade concentra mais quase-acidentes?",
                        "Mostre a tendencia mensal de dias perdidos."));
    }

    @Override
    public SemanticQueryResult executar(SemanticQuery query, SemanticQueryContext context) {
        SemanticQuery efetiva = comPeriodoPadrao(query);

        Map<String, JpaSemanticQueryExecutor.FieldBinding> campos = new LinkedHashMap<>();
        campos.put("data_ocorrencia", campo("e.dataOcorrencia", SstEventosSemanticDataset::parseDataHora));
        campos.put("ano", campo("year(e.dataOcorrencia)", Integer::valueOf));
        campos.put("mes", campo("month(e.dataOcorrencia)", Integer::valueOf));
        campos.put("tipo", campo("e.tipo", valor -> TipoEventoSst.valueOf(valor.toUpperCase())));
        campos.put("status", campo("e.status", valor -> StatusEventoSst.valueOf(valor.toUpperCase())));
        campos.put("atividade", campo("e.atividadeNome", valor -> valor));
        campos.put("base_operacional", campo("e.baseNome", valor -> valor));
        campos.put("equipe", campo("e.equipeCodigo", valor -> valor));
        campos.put("possui_cat", campo("e.possuiCat", Boolean::valueOf));

        String tiposComLesao = "(com.nexo.app.modules.sst.domain.TipoEventoSst.ACIDENTE_TIPICO, "
                + "com.nexo.app.modules.sst.domain.TipoEventoSst.ACIDENTE_TRAJETO, "
                + "com.nexo.app.modules.sst.domain.TipoEventoSst.DOENCA_OCUPACIONAL)";

        Map<String, String> medidas = Map.of(
                "total_eventos", "count(e.id)",
                "eventos_com_lesao", "count(case when e.tipo in " + tiposComLesao + " then e.id else null end)",
                "dias_perdidos", "coalesce(sum(e.diasPerdidos), 0)",
                "eventos_com_cat", "count(case when e.possuiCat = true then e.id else null end)");

        return JpaSemanticQueryExecutor.execute(entityManager, descriptor(), efetiva,
                "from EventoSst e",
                "e.ativo = true and e.status <> com.nexo.app.modules.sst.domain.StatusEventoSst.CANCELADO",
                Map.of(), campos, medidas, linha -> {
                });
    }

    /** Sem recorte de data, os últimos doze meses — pergunta aberta não varre a série inteira. */
    private SemanticQuery comPeriodoPadrao(SemanticQuery query) {
        if (query.filtros().stream().anyMatch(filtro -> "data_ocorrencia".equals(filtro.campo()))) {
            return query;
        }
        List<SemanticQuery.SemanticFilter> filtros = new ArrayList<>(query.filtros());
        filtros.add(new SemanticQuery.SemanticFilter("data_ocorrencia", SemanticFilterOperator.GTE,
                List.of(LocalDate.now(ZONE_ID).minusDays(365).toString())));
        return new SemanticQuery(query.dataset(), query.dimensoes(), query.medidas(), filtros,
                query.agruparPor(), query.ordenarPor(), query.limite());
    }

    private static OffsetDateTime parseDataHora(String valor) {
        try {
            return OffsetDateTime.parse(valor);
        } catch (RuntimeException ignorado) {
            return LocalDate.parse(valor).atStartOfDay(ZONE_ID).toOffsetDateTime();
        }
    }

    private static SemanticDatasetDescriptor.SemanticDimension dimensao(
            String id, String descricao, SemanticDataType tipo, boolean agrupavel,
            Set<SemanticFilterOperator> operadores) {
        return new SemanticDatasetDescriptor.SemanticDimension(id, descricao, tipo, agrupavel, false, operadores);
    }

    private static SemanticDatasetDescriptor.SemanticMeasure medida(
            String id, String descricao, SemanticDataType tipo, String definicao) {
        return new SemanticDatasetDescriptor.SemanticMeasure(id, descricao, tipo, definicao);
    }

    private static JpaSemanticQueryExecutor.FieldBinding campo(String expressao, Function<String, Object> parser) {
        return new JpaSemanticQueryExecutor.FieldBinding(expressao, parser);
    }
}
