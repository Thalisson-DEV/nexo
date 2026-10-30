package com.nexo.app.modules.assistente.application.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PainelPromptRendererTest {

    @Test
    void descreveOPainelEstruturadoComRegrasGenericas() {
        ContextoPainel painel = new ContextoPainel("sst.desvios", "Desvios de SST", "SST", "Desvios registrados em inspeções",
                new ContextoPainel.Periodo("2026-07-01", "2026-09-30", "3º trimestre"),
                List.of(new ContextoPainel.Filtro("base", "Base", "Sul", "b-sul"), new ContextoPainel.Filtro("equipe", "Equipe", null, null)),
                List.of(new ContextoPainel.Metrica("desvios", "Desvios", 128.0, null, null, null,
                        new ContextoPainel.Comparacao("vs. trimestre anterior", 110.0, 16.4, "menor"))),
                List.of(new ContextoPainel.Grafico("porTipo", "barra", "Desvios por tipo", "desvios", "tipo", "inteiro",
                        IntStream.range(0, 50).mapToObj(i -> new ContextoPainel.Ponto("Tipo " + i, (double) i, null)).toList(), 50)),
                List.of(new ContextoPainel.Entidade("equipe", "e1", "Equipe 07")),
                List.of("sst_desvios", "sst_inspecoes"),
                new ContextoPainel.Foco("grafico", "porTipo", "Desvios por tipo"),
                Map.of("granularidade", "mensal"));

        String texto = PainelPromptRenderer.render(painel);

        assertThat(texto)
                .contains("# Painel em tela")
                .contains("Desvios de SST (SST) · id sst.desvios")
                .contains("3º trimestre (2026-07-01 a 2026-09-30)")
                .contains("Base: Sul (id b-sul)")
                .contains("Equipe: todos")
                .contains("Desvios: 128 — vs. trimestre anterior: +16,4%, anterior 110, melhor quando menor")
                .contains("Tipo 0 = 0; Tipo 1 = 1")
                .contains("mostrando 40 de 50 pontos")
                .contains("Seleção atual: equipe \"Equipe 07\" (id e1)")
                .contains("Elemento em foco")
                .contains("Datasets relacionados: sst_desvios, sst_inspecoes")
                .contains("consulte os dados pelas ferramentas")
                .contains("não concede acesso");
        // Nenhuma regra especifica de painel: as regras sao as mesmas para qualquer dashboard.
        assertThat(texto).doesNotContainIgnoringCase("if dashboard");
    }

    @Test
    void painelSemFiltrosDeixaIssoExplicito() {
        ContextoPainel painel = new ContextoPainel("x", "Painel X", null, null, null, List.of(), List.of(), List.of(),
                List.of(), List.of(), null, Map.of());
        assertThat(PainelPromptRenderer.render(painel)).contains("Filtros: nenhum filtro aplicado");
    }

    @Test
    void listaTabelasFiltraveisComCamposEOperadores() {
        ContextoPainel painel = new ContextoPainel("sst.inspecoes", "Inspeções", null, null, null, null, null, null, null, null,
                null, null, List.of(new ContextoPainel.Tabela("sst.inspecoes#porInspetor", "Produtividade · Inspetores",
                        List.of(new ContextoPainel.Coluna("percentualAtingido", "% da meta", "percentual", null)))));

        assertThat(PainelPromptRenderer.render(painel))
                .contains("Tabelas filtráveis")
                .contains("propor_filtro_painel")
                .contains("Produtividade · Inspetores · id sst.inspecoes#porInspetor")
                .contains("percentualAtingido (% da meta, percentual, operadores eq/ne/lt/lte/gt/gte)");
    }
}
