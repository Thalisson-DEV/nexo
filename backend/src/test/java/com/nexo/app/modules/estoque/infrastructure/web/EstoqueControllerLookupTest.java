package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.usecases.EstoqueLookupUseCase;
import com.nexo.app.modules.estoque.application.usecases.EstoqueUseCases;
import com.nexo.app.modules.estoque.application.usecases.ImportarEstoqueUseCase;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.application.dto.LookupPageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EstoqueControllerLookupTest {

    private EstoqueLookupUseCase lookup;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lookup = mock(EstoqueLookupUseCase.class);
        EstoqueController controller = new EstoqueController(
                mock(EstoqueUseCases.class), lookup, mock(ImportarEstoqueUseCase.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void optionsEncaminhaBuscaCursorLimiteETiposRepetidos() throws Exception {
        when(lookup.execute("obra", 25, "cursor-1", List.of("OBRA", "FISICO")))
                .thenReturn(new LookupPageDto<>(
                        List.of(new LookupOptionDto("id-1", "Obra Norte")), "cursor-2", true));

        mockMvc.perform(get("/v1/estoques/options")
                        .param("q", "obra")
                        .param("limit", "25")
                        .param("cursor", "cursor-1")
                        .param("tipos", "OBRA", "FISICO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].label").value("Obra Norte"))
                .andExpect(jsonPath("$.nextCursor").value("cursor-2"))
                .andExpect(jsonPath("$.hasMore").value(true));

        verify(lookup).execute("obra", 25, "cursor-1", List.of("OBRA", "FISICO"));
    }

    @Test
    void optionsUsaPermissaoDeLeituraDeEstoque() throws Exception {
        PreAuthorize authorization = EstoqueController.class
                .getMethod("options", String.class, Integer.class, String.class, List.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('estoque:estoque:read')");
    }
}
