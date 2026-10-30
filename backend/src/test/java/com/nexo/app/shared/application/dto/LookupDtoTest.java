package com.nexo.app.shared.application.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookupDtoTest {

    @Test
    void expõeOpcaoMinimaSemEntidadeCompleta() {
        LookupOptionDto option = new LookupOptionDto("id-1", "Pessoa 1");

        assertThat(option.value()).isEqualTo("id-1");
        assertThat(option.label()).isEqualTo("Pessoa 1");
    }

    @Test
    void paginaProtegeListaEValidaContratoDeCursor() {
        List<LookupOptionDto> source = new ArrayList<>(List.of(new LookupOptionDto("1", "Um")));
        LookupPageDto<LookupOptionDto> page = new LookupPageDto<>(source, "cursor", true);
        source.clear();

        assertThat(page.items()).hasSize(1);
        assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(LookupPageDto.empty().items()).isEmpty();
        assertThat(LookupPageDto.empty().nextCursor()).isNull();
        assertThat(LookupPageDto.empty().hasMore()).isFalse();
    }

    @Test
    void rejeitaEstadoInconsistenteDaPagina() {
        assertThatThrownBy(() -> new LookupPageDto<>(List.of(), "cursor", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LookupPageDto<>(List.of(), null, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LookupOptionDto(null, "label"))
                .isInstanceOf(NullPointerException.class);
    }
}
