package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.application.usecases.ColaboradorUseCases;
import com.nexo.app.modules.rh.application.usecases.ImportarColaboradoresUseCase;
import com.nexo.app.modules.rh.application.usecases.SearchColaboradorOptionsUseCase;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.application.dto.LookupPageDto;
import com.nexo.app.shared.application.lookup.InvalidLookupCursorException;
import com.nexo.app.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

class ColaboradorControllerLookupTest {

    private SearchColaboradorOptionsUseCase searchOptions;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        searchOptions = mock(SearchColaboradorOptionsUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ColaboradorController(
                        mock(ColaboradorUseCases.class),
                        mock(ImportarColaboradoresUseCase.class),
                        searchOptions))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void delegaParametrosDoLookupERetornaEnvelopePadronizado() throws Exception {
        when(searchOptions.execute("ab", 20, "cursor-1"))
                .thenReturn(new LookupPageDto<>(List.of(new LookupOptionDto("id-1", "Pessoa")), "cursor-2", true));

        mockMvc.perform(get("/v1/rh/colaboradores/options")
                        .param("q", "ab")
                        .param("limit", "20")
                        .param("cursor", "cursor-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].value").value("id-1"))
                .andExpect(jsonPath("$.items[0].label").value("Pessoa"))
                .andExpect(jsonPath("$.nextCursor").value("cursor-2"))
                .andExpect(jsonPath("$.hasMore").value(true));

        verify(searchOptions).execute(eq("ab"), eq(20), eq("cursor-1"));
    }

    @Test
    void cursorInvalidoRetornaProblemDetail400() throws Exception {
        when(searchOptions.execute("ab", 50, "invalido"))
                .thenThrow(new InvalidLookupCursorException("Cursor de lookup inválido"));

        mockMvc.perform(get("/v1/rh/colaboradores/options")
                        .param("q", "ab")
                        .param("cursor", "invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parâmetro Inválido"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void endpointMantemMesmaAutoridadeDaListagemCompleta() throws Exception {
        PreAuthorize authorization = ColaboradorController.class
                .getMethod("searchOptions", String.class, Integer.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('rh:colaborador:read')");
    }
}
