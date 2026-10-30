package com.nexo.app.modules.assistente.infrastructure.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.agent.AgentRunContext;
import com.nexo.app.modules.assistente.application.agent.ToolContext;
import com.nexo.app.modules.assistente.application.agent.ToolResult;
import com.nexo.app.modules.assistente.application.results.ResultFormatter;
import com.nexo.app.modules.assistente.application.results.ResultStore;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.application.semantic.ConsultaDadosService;
import com.nexo.app.modules.assistente.application.semantic.SemanticCatalog;
import com.nexo.app.modules.assistente.application.semantic.ViewQuery;
import com.nexo.app.modules.assistente.domain.ResultadoAssistente;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsultaToolsTest {

    @Test
    void comparaSemprePeriodoRecenteMenosAnteriorMesmoComArgumentosInvertidos() throws Exception {
        ConsultaDadosService consultas = mock(ConsultaDadosService.class);
        ResultStore resultados = mock(ResultStore.class);
        ToolContext contexto = mock(ToolContext.class);
        when(contexto.run()).thenReturn(mock(AgentRunContext.class));
        when(consultas.executarSemRegistro(any(ViewQuery.class), eq(contexto))).thenAnswer(invocacao -> {
            ViewQuery consulta = invocacao.getArgument(0);
            double valor = consulta.periodo().inicio().getMonthValue() == 8 ? 199 : 725.5;
            return new TabularData(List.of(
                    new TabularData.Column("material", TabularData.ColumnType.TEXTO),
                    new TabularData.Column("valor", TabularData.ColumnType.NUMERO)),
                    List.of(List.of("Material de teste", valor)), 1, false);
        });
        when(resultados.registrar(any(), eq("comparar_periodos"), eq(ResultadoAssistente.Natureza.CALCULO),
                anyString(), any(TabularData.class), anyMap())).thenAnswer(invocacao ->
                new ResultStore.StoredResult("r1", invocacao.getArgument(3), ResultadoAssistente.Natureza.CALCULO,
                        invocacao.getArgument(4), "comparar_periodos", Map.of()));

        ObjectMapper mapper = new ObjectMapper();
        ToolResult resposta = new ConsultaTools().compararPeriodosTool(consultas, mock(SemanticCatalog.class),
                resultados, new ResultFormatter(mapper), new AssistenteProperties()).execute(mapper.readTree("""
                {
                  "fonte": "movimentacoes", "funcao": "soma", "coluna": "quantidade", "dimensao": "material",
                  "periodo_a": {"inicio": "2026-09-01", "fim": "2026-09-25", "rotulo": "setembro"},
                  "periodo_b": {"inicio": "2026-08-01", "fim": "2026-08-31", "rotulo": "agosto"}
                }
                """), contexto);

        ArgumentCaptor<ViewQuery> consultasFeitas = ArgumentCaptor.forClass(ViewQuery.class);
        verify(consultas, times(2)).executarSemRegistro(consultasFeitas.capture(), eq(contexto));
        assertThat(consultasFeitas.getAllValues()).extracting(item -> item.periodo().inicio())
                .containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));
        ArgumentCaptor<TabularData> resultadoSalvo = ArgumentCaptor.forClass(TabularData.class);
        verify(resultados).registrar(any(), eq("comparar_periodos"), eq(ResultadoAssistente.Natureza.CALCULO),
                eq("Comparação agosto x setembro"), resultadoSalvo.capture(), anyMap());
        assertThat(resultadoSalvo.getValue().rows().getFirst()).containsExactly("Material de teste", 199L, 725.5, 526.5, 264.6);
        assertThat(mapper.readTree(resposta.llmContent()).path("variacao_total_percentual").asDouble()).isEqualTo(264.6);
        assertThat(resposta.llmContent()).contains("mais recente", "anterior");
    }
}
