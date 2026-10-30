package com.nexo.app.modules.assistente.application.artifacts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.agent.ToolException;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.domain.ResultadoAssistente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID conversa = UUID.randomUUID();
    private ArtifactResolver resolver;

    @BeforeEach
    void setUp() {
        ResultStore store = mock(ResultStore.class);
        TabularData movimentacoes = new TabularData(
                List.of(new TabularData.Column("obra", TabularData.ColumnType.TEXTO),
                        new TabularData.Column("material", TabularData.ColumnType.TEXTO),
                        new TabularData.Column("data", TabularData.ColumnType.DATA),
                        new TabularData.Column("quantidade", TabularData.ColumnType.NUMERO)),
                List.of(List.of("Obra A", "Cabo", "2026-09-01", 10),
                        List.of("Obra A", "Poste", "2026-09-02", 5),
                        List.of("Obra B", "Cabo", "2026-09-02", 7),
                        List.of("Obra C", "Cabo", "2026-09-03", 1),
                        List.of("Obra D", "Isolador", "2026-09-03", 2)),
                5, false);
        TabularData agosto = new TabularData(
                List.of(new TabularData.Column("quantidade", TabularData.ColumnType.NUMERO)),
                List.of(List.of(20), List.of(5)), 2, false);
        when(store.exigir(eq(conversa), eq("r1"))).thenReturn(new ResultStore.StoredResult("r1", "Saidas de setembro",
                ResultadoAssistente.Natureza.INTERNO, movimentacoes, "consultar_movimentacoes", Map.of("periodo", "setembro")));
        when(store.exigir(eq(conversa), eq("r2"))).thenReturn(new ResultStore.StoredResult("r2", "Saidas de agosto",
                ResultadoAssistente.Natureza.INTERNO, agosto, "consultar_movimentacoes", Map.of()));
        when(store.exigir(eq(conversa), eq("r9"))).thenThrow(new ToolException("Resultado 'r9' nao existe"));
        resolver = new ArtifactResolver(store);
    }

    @Test
    void graficoDeBarrasSomaPorCategoriaOrdenaELimita() throws Exception {
        ArtifactResolver.Procedencia procedencia = new ArtifactResolver.Procedencia();
        Map<String, Object> grafico = resolver.componente(conversa, json("""
                {"tipo":"grafico","grafico":"barra","resultado":"r1","x":"obra","y":["quantidade"],"limite":2}
                """), procedencia);

        assertThat(grafico.get("rotulos")).isEqualTo(List.of("Obra A", "Obra B"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) grafico.get("series");
        assertThat(series).hasSize(1);
        assertThat(series.getFirst().get("valores")).isEqualTo(List.of(15L, 7L));
        assertThat(grafico.get("observacao")).asString().contains("2 de 4");
        assertThat(procedencia.codigos()).containsExactly("r1");
    }

    @Test
    void pizzaAgrupaCategoriasMenoresEmOutros() throws Exception {
        Map<String, Object> pizza = resolver.componente(conversa, json("""
                {"tipo":"grafico","grafico":"pizza","resultado":"r1","x":"obra","y":["quantidade"],"limite":3}
                """), new ArtifactResolver.Procedencia());
        assertThat(pizza.get("rotulos")).isEqualTo(List.of("Obra A", "Obra B", "Outros"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) pizza.get("series");
        assertThat(series.getFirst().get("valores")).isEqualTo(List.of(15L, 7L, 3L));
    }

    @Test
    void serieTemporalPorDiaComSeriesPorMaterial() throws Exception {
        Map<String, Object> linha = resolver.componente(conversa, json("""
                {"tipo":"grafico","grafico":"linha","resultado":"r1","x":"data","y":["quantidade"],"serie":"material"}
                """), new ArtifactResolver.Procedencia());
        assertThat(linha.get("rotulos")).isEqualTo(List.of("01/09/2026", "02/09/2026", "03/09/2026"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) linha.get("series");
        assertThat(series).extracting(serie -> serie.get("nome")).containsExactly("Cabo", "Poste", "Isolador");
    }

    @Test
    void kpiComComparacaoCalculaVariacaoAPartirDosDados() throws Exception {
        Map<String, Object> kpi = resolver.componente(conversa, json("""
                {"tipo":"kpi","titulo":"Saidas","resultado":"r1","coluna":"quantidade","agregacao":"soma",
                 "comparar_com":{"resultado":"r2","rotulo":"vs. agosto"},"melhor_quando":"menor"}
                """), new ArtifactResolver.Procedencia());
        assertThat(kpi.get("valor")).isEqualTo(25L);
        @SuppressWarnings("unchecked")
        Map<String, Object> comparacao = (Map<String, Object>) kpi.get("comparacao");
        assertThat(comparacao.get("valor")).isEqualTo(25L);
        assertThat(comparacao.get("variacao")).isEqualTo(0L);
        assertThat(kpi.get("melhorQuando")).isEqualTo("menor");
    }

    @Test
    void tabelaSelecionaColunasOrdenaELimita() throws Exception {
        Map<String, Object> tabela = resolver.componente(conversa, json("""
                {"tipo":"tabela","resultado":"r1","colunas":["obra","quantidade"],"ordenar_por":"quantidade","limite":2}
                """), new ArtifactResolver.Procedencia());
        assertThat(tabela.get("linhas")).isEqualTo(List.of(List.of("Obra A", 10), List.of("Obra B", 7)));
        assertThat(tabela.get("total")).isEqualTo(5);
    }

    @Test
    void errosApontamOComponente() throws Exception {
        JsonNode componentes = json("""
                [{"tipo":"texto","conteudo":"ok"},{"tipo":"grafico","resultado":"r9","x":"obra"}]
                """);
        assertThatThrownBy(() -> resolver.componentes(conversa, componentes, new ArtifactResolver.Procedencia()))
                .isInstanceOf(ToolException.class).hasMessageStartingWith("Componente 2:");
        assertThatThrownBy(() -> resolver.componente(conversa, json("""
                {"tipo":"grafico","resultado":"r1","x":"inexistente"}
                """), new ArtifactResolver.Procedencia())).isInstanceOf(ToolException.class);
    }

    private JsonNode json(String texto) throws Exception {
        return mapper.readTree(texto);
    }
}
