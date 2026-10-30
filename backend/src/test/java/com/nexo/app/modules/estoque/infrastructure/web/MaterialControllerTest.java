package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.usecases.ImportarMaterialUseCase;
import com.nexo.app.modules.estoque.application.usecases.MaterialLookupUseCase;
import com.nexo.app.modules.estoque.application.usecases.MaterialUseCases;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.application.dto.LookupPageDto;
import com.nexo.app.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;

class MaterialControllerTest {

    private MaterialLookupUseCase lookup;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lookup = mock(MaterialLookupUseCase.class);
        MaterialController controller = new MaterialController(
                mock(MaterialUseCases.class), lookup, mock(ImportarMaterialUseCase.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void optionsAceitaQueryLimitECursorEDevolveContratoPadronizado() throws Exception {
        when(lookup.execute("água", 25, "abc"))
                .thenReturn(new LookupPageDto<>(List.of(new LookupOptionDto("id", "ABC — Água")), null, false));

        mockMvc.perform(get("/v1/materiais/options")
                        .param("q", "água")
                        .param("limit", "25")
                        .param("cursor", "abc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].value").value("id"))
                .andExpect(jsonPath("$.items[0].label").value("ABC — Água"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(content().string(containsString("\"nextCursor\":null")));

        verify(lookup).execute("água", 25, "abc");
    }

    @Test
    void endpointMantemMesmaAutoridadeDaListagemCompleta() throws Exception {
        PreAuthorize authorization = MaterialController.class
                .getMethod("options", String.class, Integer.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAnyAuthority('estoque:material:read')");
    }
}
