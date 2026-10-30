package com.nexo.app.modules.assistente.application.navigation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.agent.ContextoPainel;
import com.nexo.app.modules.assistente.application.dto.AcaoInterface;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PainelFiltroResolverTest {

    private final PainelFiltroResolver resolver = new PainelFiltroResolver();

    private static ContextoPainel painel() {
        ContextoPainel.Tabela inspetores = new ContextoPainel.Tabela("sst.inspecoes#porInspetor", "Produtividade · Inspetores", List.of(
                new ContextoPainel.Coluna("percentualAtingido", "% da meta", "percentual", null),
                new ContextoPainel.Coluna("nomeInspetor", "Inspetor", "texto", List.of("contains", "gt")),
                new ContextoPainel.Coluna("comDesvios", "Com desvios", "booleano", null)));
        return new ContextoPainel("sst.inspecoes", "Inspeções", null, null, null, null, null, null, null, null, null, null,
                List.of(inspetores));
    }

    @Test
    void aceitaFiltroDeclaradoEConverteValores() {
        var resolution = resolver.resolve(painel(), "sst.inspecoes#porInspetor", "Inspetores abaixo de 80% da meta", List.of(
                Map.of("campo", "percentualAtingido", "operador", "lt", "valor", "80,5%"),
                Map.of("campo", "comDesvios", "operador", "eq", "valor", "TRUE"),
                Map.of("campo", "nomeInspetor", "operador", "contains", "valor", " Ana ")));

        assertThat(resolution.accepted()).isTrue();
        AcaoInterface acao = resolution.action().orElseThrow();
        assertThat(acao.tipo()).isEqualTo(AcaoInterface.APPLY_FILTER);
        assertThat(acao.alvo()).isEqualTo("sst.inspecoes#porInspetor");
        assertThat(acao.chaveDestino()).isNull();
        assertThat(acao.filtros()).containsExactly(
                new AcaoInterface.FiltroProposto("percentualAtingido", "lt", 80.5),
                new AcaoInterface.FiltroProposto("comDesvios", "eq", true),
                new AcaoInterface.FiltroProposto("nomeInspetor", "contains", "Ana"));
    }

    @Test
    void recusaTabelaCampoOperadorEValorForaDoDeclarado() {
        String alvo = "sst.inspecoes#porInspetor";
        assertThat(resolver.resolve(painel(), "outra", "x", List.of(Map.of("campo", "a", "operador", "eq", "valor", 1))).rejection())
                .isEqualTo(PainelFiltroResolver.Rejection.TABELA_DESCONHECIDA);
        assertThat(resolver.resolve(painel(), alvo, "x", List.of(Map.of("campo", "senha", "operador", "eq", "valor", 1))).rejection())
                .isEqualTo(PainelFiltroResolver.Rejection.CAMPO_DESCONHECIDO);
        // "gt" foi declarado para texto, mas o tipo texto só aceita eq/contains.
        assertThat(resolver.resolve(painel(), alvo, "x", List.of(Map.of("campo", "nomeInspetor", "operador", "gt", "valor", "a"))).rejection())
                .isEqualTo(PainelFiltroResolver.Rejection.OPERADOR_INVALIDO);
        assertThat(resolver.resolve(painel(), alvo, "x", List.of(Map.of("campo", "percentualAtingido", "operador", "lt", "valor", "oitenta"))).rejection())
                .isEqualTo(PainelFiltroResolver.Rejection.VALOR_INVALIDO);
        assertThat(resolver.resolve(painel(), alvo, "x", List.of()).rejection())
                .isEqualTo(PainelFiltroResolver.Rejection.FILTROS_INVALIDOS);
        assertThat(resolver.resolve(painel(), alvo, " ", List.of(Map.of("campo", "comDesvios", "operador", "eq", "valor", true))).rejection())
                .isEqualTo(PainelFiltroResolver.Rejection.ROTULO_INVALIDO);
        assertThat(resolver.resolve(null, alvo, "x", List.of()).rejection()).isEqualTo(PainelFiltroResolver.Rejection.SEM_PAINEL);
    }

    @Test
    void contextoDoFrontendDescartaColunasETiposInvalidos() throws Exception {
        String json = """
                {"id":"sst.inspecoes","nome":"Inspeções","tabelas":[
                  {"id":"sst.inspecoes#porInspetor","rotulo":"Inspetores","colunas":[
                    {"campo":"realizado","tipo":"numero"},
                    {"campo":"x; drop","tipo":"numero"},
                    {"campo":"y","tipo":"script"}]},
                  {"id":"id com espaço","colunas":[{"campo":"a","tipo":"numero"}]}]}
                """;
        ContextoPainel contexto = new ObjectMapper().readValue(json, ContextoPainel.class);
        assertThat(contexto.tabelas()).hasSize(1);
        assertThat(contexto.tabelas().getFirst().colunas()).extracting(ContextoPainel.Coluna::campo).containsExactly("realizado");
        assertThat(contexto.tabelas().getFirst().colunas().getFirst().operadores()).containsExactly("eq", "ne", "lt", "lte", "gt", "gte");
    }
}
