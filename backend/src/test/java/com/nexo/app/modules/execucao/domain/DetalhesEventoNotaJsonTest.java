package com.nexo.app.modules.execucao.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DetalhesEventoNotaJsonTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("Evento gravado antes dos dados alterados é lido com a lista vazia")
    void eventoAntigoSemDadosAlterados() {
        String antigo = """
                {"acoesAdicionadas":[],"acoesRemovidas":[],"materiaisAdicionados":[],"materiaisRemovidos":[],
                 "referenciaAnterior":null,"referenciaNova":null}""";

        DetalhesEventoNota detalhes = json.readValue(antigo, DetalhesEventoNota.class);

        assertThat(detalhes.dadosAlterados()).isEmpty();
    }

    @Test
    @DisplayName("Dados alterados vão e voltam do JSON")
    void dadosAlteradosIdaEVolta() {
        var detalhes = new DetalhesEventoNota(null, null, null, null, null, null,
                List.of(new DetalhesEventoNota.DadoAlterado("Município", "SENTO SÉ", "JUAZEIRO")));

        var lido = json.readValue(json.writeValueAsString(detalhes), DetalhesEventoNota.class);

        assertThat(lido.dadosAlterados()).containsExactly(new DetalhesEventoNota.DadoAlterado("Município", "SENTO SÉ", "JUAZEIRO"));
    }
}
