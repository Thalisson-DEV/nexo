package com.nexo.app.modules.assistente.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.domain.ConversaAssistente;
import com.nexo.app.modules.assistente.domain.OrigemConversa;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ContextoPainelTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static ContextoPainel painel(String base) {
        return new ContextoPainel("almoxarifado.solicitacoes", "Solicitações de material", "Almoxarifado", null,
                new ContextoPainel.Periodo("2026-09-01", "2026-09-30", "setembro/2026"),
                List.of(new ContextoPainel.Filtro("base", "Base operacional", base, "id-" + base),
                        new ContextoPainel.Filtro("processo", "Processo", null, null)),
                List.of(new ContextoPainel.Metrica("total", "Total de solicitações", 4281.0, "4.281", null, null,
                        new ContextoPainel.Comparacao("vs. período anterior", 3622.0, 18.2, "maior"))),
                List.of(new ContextoPainel.Grafico("porStatus", "rosca", "Distribuição por status", "solicitações", "status",
                        "inteiro", List.of(new ContextoPainel.Ponto("Pendentes", 12.0, null)), null)),
                List.of(), List.of("solicitacoes", "materiais"), null, Map.of());
    }

    @Test
    void saneiaTextosTruncaListasEDescartaNumerosInvalidos() {
        List<ContextoPainel.Ponto> pontos = IntStream.range(0, 90)
                .mapToObj(i -> new ContextoPainel.Ponto("item " + i, i == 3 ? Double.NaN : (double) i, null)).toList();
        ContextoPainel contexto = new ContextoPainel("p1", "Nome\u0000com\ncontrole " + "x".repeat(400), null, null, null,
                null, null, List.of(new ContextoPainel.Grafico("g", "barra", "G", null, null, null, pontos, null)),
                null, List.of("a", "a", " ", "b"), null, null);

        assertThat(contexto.nome()).doesNotContain("\u0000").doesNotContain("\n").hasSizeLessThanOrEqualTo(120);
        assertThat(contexto.filtros()).isEmpty();
        assertThat(contexto.datasets()).containsExactly("a", "b");
        ContextoPainel.Grafico grafico = contexto.graficos().getFirst();
        assertThat(grafico.dados()).hasSize(ContextoPainel.MAX_PONTOS);
        assertThat(grafico.totalCategorias()).isEqualTo(90);
        assertThat(grafico.dados().get(3).valor()).isNull();
    }

    @Test
    void assinaturaDescrevePeriodoEFiltrosAtivos() {
        assertThat(painel("NORTE").assinatura())
                .contains("Solicitações de material")
                .contains("setembro/2026 (2026-09-01 a 2026-09-30)")
                .contains("Base operacional: NORTE")
                .doesNotContain("Processo");
    }

    @Test
    void contextoSemPainelContinuaCompativelComJsonAntigo() throws Exception {
        ContextoCes antigo = mapper.readValue("{\"pagina\":\"Obras\",\"filtros\":{\"status\":\"ATRASADA\"}}", ContextoCes.class);
        assertThat(antigo.painel()).isNull();
        assertThat(antigo.dePainel()).isFalse();
        assertThat(antigo.vazio()).isFalse();

        ContextoCes novo = new ContextoCes("Solicitações", "/almoxarifado/dashboard/solicitacoes", "DASHBOARD",
                "almoxarifado.solicitacoes", "Solicitações", Map.of("Base", "NORTE"), painel("NORTE"));
        ContextoCes lido = mapper.readValue(mapper.writeValueAsString(novo), ContextoCes.class);
        assertThat(lido.dePainel()).isTrue();
        assertThat(lido.painel().metricas().getFirst().comparacao().variacaoPercentual()).isEqualTo(18.2);
        assertThat(lido.painel().filtros()).hasSize(2);
    }

    @Test
    void painelSemIdentidadeEhDescartado() {
        ContextoPainel vazio = new ContextoPainel(null, " ", null, null, null, null, null, null, null, null, null, null);
        assertThat(new ContextoCes(null, null, null, null, null, null, vazio).vazio()).isTrue();
    }

    @Test
    void recorteMarcaPerguntaApenasQuandoHaPainel() {
        ContextoCes norte = new ContextoCes(null, null, null, null, null, null, painel("NORTE"));
        assertThat(ContextAssembler.comRecorte("Qual material mais contribuiu?", norte, "nesta pergunta"))
                .startsWith("[Recorte do painel nesta pergunta:")
                .contains("Base operacional: NORTE")
                .endsWith("Qual material mais contribuiu?");
        assertThat(ContextAssembler.comRecorte("Oi", new ContextoCes("Obras", null, null, null, null, null), "agora"))
                .isEqualTo("Oi");
    }

    @Test
    void conversaAssociadaAoPainelUmaUnicaVez() {
        ConversaAssistente conversa = new ConversaAssistente(UUID.randomUUID(), null);
        assertThat(conversa.getOrigem()).isEqualTo(OrigemConversa.WORKSPACE);
        conversa.associarPainel("almoxarifado.solicitacoes");
        conversa.associarPainel("financeiro.visao-geral");
        assertThat(conversa.getOrigem()).isEqualTo(OrigemConversa.DASHBOARD);
        assertThat(conversa.getOrigemId()).isEqualTo("almoxarifado.solicitacoes");
    }
}
