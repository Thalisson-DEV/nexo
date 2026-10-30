package com.nexo.app.modules.telemetria.infrastructure.web;

import com.nexo.app.modules.telemetria.application.dto.TelemetriaMobileDtos.LoteRequest;
import com.nexo.app.modules.telemetria.application.dto.TelemetriaMobileDtos.LoteResponse;
import com.nexo.app.modules.telemetria.application.usecases.TelemetriaMobileUseCases;
import com.nexo.app.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TelemetriaMobileControllerTest {

    private TelemetriaMobileUseCases useCases;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCases = mock(TelemetriaMobileUseCases.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TelemetriaMobileController(useCases))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static String evento(String tipo) {
        return "{\"tipo\":\"" + tipo + "\",\"em\":\"2026-10-08T14:59:00Z\",\"dados\":{\"acao\":\"retomar\"}}";
    }

    @Test
    void loteValidoResponde202ComContagem() throws Exception {
        when(useCases.receber(any(LoteRequest.class))).thenReturn(new LoteResponse(1, 0));

        mockMvc.perform(post("/v1/telemetria/mobile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idAparelho\":\"abc-123\",\"versaoApp\":\"a1b2\",\"eventos\":[" + evento("retomada.decisao") + "]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recebidos").value(1))
                .andExpect(jsonPath("$.descartados").value(0));
    }

    @Test
    void rejeitaTipoForaDoPadrao() throws Exception {
        for (String tipo : new String[]{"Retomada", "retomada decisao", "a.b.c.d", "<script>", ""}) {
            mockMvc.perform(post("/v1/telemetria/mobile").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventos\":[" + evento(tipo) + "]}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(useCases);
    }

    @Test
    void rejeitaLoteVazioEAcimaDe100() throws Exception {
        mockMvc.perform(post("/v1/telemetria/mobile").contentType(MediaType.APPLICATION_JSON).content("{\"eventos\":[]}"))
                .andExpect(status().isBadRequest());
        String muitos = String.join(",", java.util.Collections.nCopies(101, evento("app.abertura")));
        mockMvc.perform(post("/v1/telemetria/mobile").contentType(MediaType.APPLICATION_JSON).content("{\"eventos\":[" + muitos + "]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCases);
    }

    @Test
    void rejeitaIdDeAparelhoComCaracteresInesperados() throws Exception {
        mockMvc.perform(post("/v1/telemetria/mobile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idAparelho\":\"joao@nexo.com\",\"eventos\":[" + evento("app.abertura") + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exigeUsuarioAutenticadoMasNenhumaPermissaoEspecifica() throws Exception {
        PreAuthorize auth = TelemetriaMobileController.class.getMethod("receber", LoteRequest.class).getAnnotation(PreAuthorize.class);
        assertThat(auth).isNotNull();
        assertThat(auth.value()).isEqualTo("isAuthenticated()");
    }
}
