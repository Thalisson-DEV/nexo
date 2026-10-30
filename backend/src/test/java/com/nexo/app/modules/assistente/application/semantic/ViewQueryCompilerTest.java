package com.nexo.app.modules.assistente.application.semantic;

import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewQueryCompilerTest {

    private ViewQueryCompiler compiler;
    private final SemanticQueryContext contexto = new SemanticQueryContext(UUID.randomUUID(), Set.of("estoque:movimentacao:read"));

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                coluna("movimentacoes", "material_id", "uuid"),
                coluna("movimentacoes", "material", "text"),
                coluna("movimentacoes", "tipo_movimentacao", "text"),
                coluna("movimentacoes", "quantidade", "numeric"),
                coluna("movimentacoes", "data", "date"),
                coluna("movimentacoes", "data_hora", "timestamp with time zone")));
        compiler = new ViewQueryCompiler(new SemanticCatalog(jdbc));
    }

    @Test
    void compilaAgregacaoComFiltrosEPeriodoEmSqlParametrizado() {
        ViewQuery consulta = new ViewQuery("movimentacoes", List.of(), List.of("material"),
                List.of(new ViewQuery.Metrica("soma", "quantidade", null)),
                List.of(new ViewQuery.Filtro("tipo_movimentacao", "em", List.of("ENTREGA", "APLICACAO"))),
                new ViewQuery.Periodo(null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 25)),
                List.of(), 20);

        ViewQueryCompiler.CompiledQuery compilada = compiler.compilar(consulta, contexto);

        assertThat(compilada.sql())
                .startsWith("SELECT \"material\", coalesce(sum(\"quantidade\"), 0) AS \"soma_quantidade\" FROM copiloto.movimentacoes")
                .contains("\"tipo_movimentacao\" IN (?, ?)")
                .contains("(\"data_hora\" AT TIME ZONE 'America/Sao_Paulo')::date >= ?")
                .contains("GROUP BY \"material\"")
                .contains("ORDER BY \"soma_quantidade\" DESC")
                .endsWith("LIMIT 21");
        assertThat(compilada.parametros()).hasSize(4).startsWith("ENTREGA", "APLICACAO");
        assertThat(compilada.sql()).doesNotContain("ENTREGA");
    }

    @Test
    void valoresDoModeloNuncaEntramNoTextoDoSql() {
        ViewQuery consulta = new ViewQuery("movimentacoes", List.of("material", "quantidade"), List.of(), List.of(),
                List.of(new ViewQuery.Filtro("material", "contem", List.of("cabo'; DROP TABLE x; --")),
                        new ViewQuery.Filtro("quantidade", "maior", List.of("10,5"))),
                null, List.of(new ViewQuery.Ordem("quantidade", true)), null);

        ViewQueryCompiler.CompiledQuery compilada = compiler.compilar(consulta, contexto);

        assertThat(compilada.sql()).doesNotContain("DROP").contains("LIKE ? ESCAPE").contains("\"quantidade\" > ?");
        assertThat(compilada.parametros()).containsExactly("%cabo'; drop table x; --%", new BigDecimal("10.5"));
    }

    @Test
    void rejeitaColunaInexistenteOuIdentificadorMalicioso() {
        ViewQuery inexistente = new ViewQuery("movimentacoes", List.of("salario"), null, null, null, null, null, null);
        ViewQuery injecao = new ViewQuery("movimentacoes", List.of("material\" , (select 1) as \"x"), null, null, null, null, null, null);
        assertThatThrownBy(() -> compiler.compilar(inexistente, contexto)).isInstanceOf(ToolException.class)
                .hasMessageContaining("nao existe");
        assertThatThrownBy(() -> compiler.compilar(injecao, contexto)).isInstanceOf(ToolException.class);
    }

    @Test
    void negaFonteSemPermissao() {
        SemanticQueryContext semPermissao = new SemanticQueryContext(UUID.randomUUID(), Set.of());
        ViewQuery consulta = new ViewQuery("movimentacoes", List.of(), null, null, null, null, null, null);
        assertThatThrownBy(() -> compiler.compilar(consulta, semPermissao)).isInstanceOf(ToolException.class)
                .hasMessageContaining("nao tem acesso");
    }

    @Test
    void metricaNumericaExigeColunaNumerica() {
        ViewQuery consulta = new ViewQuery("movimentacoes", null, List.of("tipo_movimentacao"),
                List.of(new ViewQuery.Metrica("soma", "material", null)), null, null, null, null);
        assertThatThrownBy(() -> compiler.compilar(consulta, contexto)).isInstanceOf(ToolException.class)
                .hasMessageContaining("nao e numerica");
    }

    private static Map<String, Object> coluna(String tabela, String nome, String tipo) {
        return Map.of("table_name", tabela, "column_name", nome, "data_type", tipo);
    }
}
