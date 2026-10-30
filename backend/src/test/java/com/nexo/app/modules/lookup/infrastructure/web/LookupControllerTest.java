package com.nexo.app.modules.lookup.infrastructure.web;

import com.nexo.app.modules.lookup.application.dto.LookupBootstrapDto;
import com.nexo.app.modules.lookup.application.usecases.LookupBootstrapUseCase;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LookupControllerTest {

    private LookupBootstrapUseCase useCase;
    private LookupBootstrapEtag etag;
    private MockMvc mockMvc;
    private LookupBootstrapDto body;

    @BeforeEach
    void setUp() {
        useCase = mock(LookupBootstrapUseCase.class);
        etag = mock(LookupBootstrapEtag.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LookupController(useCase, etag)).build();
        body = new LookupBootstrapDto(
                List.of(new LookupOptionDto("base-1", "Base Norte")),
                List.of(), List.of(), List.of(), List.of());
        when(useCase.execute()).thenReturn(body);
        when(etag.generate(body)).thenReturn("\"etag-v1\"");
    }

    @Test
    void retornaBootstrapComCacheControlEtagEVersaoDoContrato() throws Exception {
        mockMvc.perform(get("/lookups/bootstrap"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.operationalBases[0].value").value("base-1"))
                .andExpect(jsonPath("$.operationalBases[0].label").value("Base Norte"))
                .andExpect(header().string("Cache-Control", "max-age=300, private"))
                .andExpect(header().string("ETag", "\"etag-v1\""))
                .andExpect(header().string(LookupController.CONTRACT_VERSION_HEADER, "1"));
    }

    @Test
    void strongWeakEWildcardRetornam304QuandoCorrespondem() throws Exception {
        for (String ifNoneMatch : List.of("\"etag-v1\"", "W/\"etag-v1\"", "*")) {
            mockMvc.perform(get("/lookups/bootstrap").header("If-None-Match", ifNoneMatch))
                    .andExpect(status().isNotModified())
                    .andExpect(content().string(""))
                    .andExpect(header().string("ETag", "\"etag-v1\""))
                    .andExpect(header().string(LookupController.CONTRACT_VERSION_HEADER, "1"));
        }
    }

    @Test
    void etagDiferenteMantem200ComBody() throws Exception {
        mockMvc.perform(get("/lookups/bootstrap").header("If-None-Match", "\"outro\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalBases").isArray());
    }

    @Test
    void combinaListaDeTagsNoIfNoneMatch() {
        assertThat(LookupController.matches("\"outro\", W/\"etag-v1\"", "\"etag-v1\""))
                .isTrue();
        assertThat(LookupController.matches(null, "\"etag-v1\""))
                .isFalse();
    }
}
