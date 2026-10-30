package com.nexo.app.modules.notificacao.application.usecases;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenderizadorTemplateTest {

    private final RenderizadorTemplate renderizador = new RenderizadorTemplate();

    @Test
    void deveSubstituirAsVariaveisDoContexto() {
        String texto = renderizador.renderizar(
                "Há {{total}} desvios, acima de {{limiar}}.",
                Map.of("total", 12L, "limiar", 10));

        assertThat(texto).isEqualTo("Há 12 desvios, acima de 10.");
    }

    @Test
    void deveAceitarEspacosDentroDoPlaceholder() {
        assertThat(renderizador.renderizar("Total: {{ total }}", Map.of("total", 3)))
                .isEqualTo("Total: 3");
    }

    @Test
    void variavelSemValorNaoDeveVazarAChaveParaOUsuario() {
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("motivo", null);

        assertThat(renderizador.renderizar("Desconto por {{motivo}} registrado.", contexto))
                .isEqualTo("Desconto por registrado.");
    }

    @Test
    void naoDeveInterpretarExpressoes() {
        assertThat(renderizador.renderizar("{{total}}", Map.of("total", "${7*7}")))
                .as("o renderizador é troca de texto, não avaliação")
                .isEqualTo("${7*7}");
    }

    @Test
    void deveTolerarTemplateNuloOuVazio() {
        assertThat(renderizador.renderizar(null, Map.of())).isNull();
        assertThat(renderizador.renderizar("  ", Map.of())).isEqualTo("  ");
    }
}
