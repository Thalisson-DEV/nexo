package com.nexo.app.shared.application.analytics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticQueryValidatorTest {

    private final SemanticDatasetDescriptor descriptor = new SemanticDatasetDescriptor(
            "dataset_teste", "1", "Teste", "Teste", "dados:read", true, 100, 365,
            List.of(new SemanticDatasetDescriptor.SemanticDimension(
                    "equipe", "Equipe", SemanticDataType.TEXT, true, false,
                    Set.of(SemanticFilterOperator.EQ))),
            List.of(new SemanticDatasetDescriptor.SemanticMeasure(
                    "total", "Total", SemanticDataType.INTEGER, "count")),
            List.of());

    @Test
    void validaConsultaAgregadaAbertaSobreCamposRegistrados() {
        SemanticQuery query = new SemanticQuery("dataset_teste", List.of("equipe"), List.of("total"),
                List.of(new SemanticQuery.SemanticFilter("equipe", SemanticFilterOperator.EQ, List.of("A"))),
                List.of("equipe"), List.of(), 500);

        assertThat(SemanticQueryValidator.validateAndResolveLimit(descriptor, query)).isEqualTo(100);
    }

    @Test
    void rejeitaCampoFisicoOuSqlNaoPublicado() {
        SemanticQuery query = new SemanticQuery("dataset_teste", List.of("select_from_usuarios"), List.of(),
                List.of(), List.of(), List.of(), 10);

        assertThatThrownBy(() -> SemanticQueryValidator.validateAndResolveLimit(descriptor, query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desconhecida");
    }

    @Test
    void rejeitaJanelaTemporalMaiorQueLimiteDoDataset() {
        var temporalDescriptor = new SemanticDatasetDescriptor(
                "dataset_teste", "1", "Teste", "Teste", "dados:read", true, 100, 365,
                List.of(new SemanticDatasetDescriptor.SemanticDimension(
                        "data_evento", "Data", SemanticDataType.DATE, false, false,
                        Set.of(SemanticFilterOperator.BETWEEN))), List.of(), List.of());
        var query = new SemanticQuery("dataset_teste", List.of("data_evento"), List.of(),
                List.of(new SemanticQuery.SemanticFilter("data_evento", SemanticFilterOperator.BETWEEN,
                        List.of("2020-01-01", "2026-01-01"))), List.of(), List.of(), 10);

        assertThatThrownBy(() -> SemanticQueryValidator.validateAndResolveLimit(temporalDescriptor, query))
                .hasMessageContaining("Periodo excede");
    }
}
