package com.nexo.app.modules.assistente.application.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AcaoInterfaceCompatibilityTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsLegacyPersistedActionAndSerializesCanonicalContract() throws Exception {
        String legacy = """
                {"tipo":"NAVEGAR","chave":"DESCONTO_NOVO","rotulo":"Abrir",\
                "autoridadeObrigatoria":"desconto:solicitar","parametros":{}}
                """;

        AcaoInterface action = mapper.readValue(legacy, AcaoInterface.class);
        String canonical = mapper.writeValueAsString(action);

        assertThat(action.tipo()).isEqualTo("NAVIGATE");
        assertThat(action.chaveDestino()).isEqualTo("DESCONTO_NOVO");
        assertThat(action.autoridade()).isEqualTo("desconto:solicitar");
        assertThat(canonical).contains("\"chaveDestino\"", "\"autoridade\"")
                .doesNotContain("\"chave\"", "autoridadeObrigatoria");
    }

    @Test
    void navegacaoNaoSerializaCamposDeFiltroEAplicarFiltroRoundTrip() throws Exception {
        AcaoInterface navegar = new AcaoInterface("NAVIGATE", "DESCONTO_NOVO", "Abrir", "desconto:solicitar", java.util.Map.of());
        assertThat(mapper.writeValueAsString(navegar)).doesNotContain("\"alvo\"", "\"filtros\"");

        AcaoInterface filtro = AcaoInterface.aplicarFiltro("sst.inspecoes#porInspetor", "Abaixo de 80%",
                java.util.List.of(new AcaoInterface.FiltroProposto("percentualAtingido", "lt", 80.0)));
        AcaoInterface lida = mapper.readValue(mapper.writeValueAsString(filtro), AcaoInterface.class);
        assertThat(lida.tipo()).isEqualTo("APPLY_FILTER");
        assertThat(lida.alvo()).isEqualTo("sst.inspecoes#porInspetor");
        assertThat(lida.filtros()).hasSize(1);
        assertThat(lida.filtros().getFirst().operador()).isEqualTo("lt");
    }

    @Test
    void rejeitaAplicarFiltroSemAlvoOuComFiltrosDemais() {
        var filtro = new AcaoInterface.FiltroProposto("a", "eq", 1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AcaoInterface.aplicarFiltro(null, "x", java.util.List.of(filtro)))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AcaoInterface.aplicarFiltro("t", "x",
                java.util.Collections.nCopies(6, filtro))).isInstanceOf(IllegalArgumentException.class);
    }
}
