package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquipesDoRecorteTest {

    private final EquipeApi equipeApi = mock(EquipeApi.class);
    private final EquipesDoRecorte recorte = new EquipesDoRecorte(equipeApi);

    private final UUID salvador = UUID.randomUUID();
    private final UUID feira = UUID.randomUUID();
    private final UUID ilheus = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(equipeApi.listarEquipesAtivas()).thenReturn(List.of(
                equipe("EQ-01", salvador),
                equipe("EQ-02", feira),
                equipe("EQ-03", ilheus),
                equipe("EQ-04", null)));
    }

    @Test
    @DisplayName("Várias bases selecionadas trazem as equipes de qualquer uma delas")
    void variasBases() {
        assertThat(recorte.listar(List.of(salvador, feira), List.of()))
                .extracting(EquipeResponse::codigo).containsExactly("EQ-01", "EQ-02");
    }

    @Test
    @DisplayName("Sem seleção não restringe, inclusive equipe sem base")
    void semSelecao() {
        assertThat(recorte.listar(null, null)).hasSize(4);
        assertThat(recorte.listar(List.of(), List.of())).hasSize(4);
    }

    @Test
    @DisplayName("Equipe sem base fica fora de uma seleção explícita")
    void semBaseForaDaSelecao() {
        assertThat(recorte.listar(List.of(ilheus), null))
                .extracting(EquipeResponse::codigo).containsExactly("EQ-03");
    }

    private static EquipeResponse equipe(String codigo, UUID idBase) {
        var base = idBase == null ? null
                : new BaseOperacionalResponse(idBase, "Base " + codigo, "BA", null, null, null, null, null, true);
        return new EquipeResponse(UUID.randomUUID(), codigo, null, null, null, null, null, base, null, true, List.of(), null);
    }
}
