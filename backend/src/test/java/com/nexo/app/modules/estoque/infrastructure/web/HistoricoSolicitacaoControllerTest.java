package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.HistoricoSolicitacaoResponse;
import com.nexo.app.modules.estoque.application.usecases.HistoricoSolicitacaoUseCases;
import com.nexo.app.modules.estoque.domain.EscopoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import com.nexo.app.shared.application.dto.Paged;
import com.nexo.app.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HistoricoSolicitacaoControllerTest {

    private HistoricoSolicitacaoUseCases historico;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        historico = mock(HistoricoSolicitacaoUseCases.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new HistoricoSolicitacaoController(historico))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void historicoNaoDependeDoFluxoTecnicoLigado() {
        // A solicitação comercial abre a linha do tempo mesmo com o fluxo técnico desligado em produção.
        assertThat(HistoricoSolicitacaoController.class.getAnnotation(ConditionalOnProperty.class)).isNull();
    }

    @Test
    void retornaHistoricoComPayloadEstruturadoESemMetadadosInternosJsonNode() throws Exception {
        UUID idSolicitacao = UUID.randomUUID();
        HistoricoSolicitacaoResponse response = new HistoricoSolicitacaoResponse(
                List.of(new HistoricoSolicitacaoResponse.Evento(
                        UUID.randomUUID(),
                        EscopoEventoSolicitacao.SOLICITACAO,
                        idSolicitacao,
                        1L,
                        TipoEventoSolicitacao.DATA_PREVISTA_ALTERADA,
                        TipoSolicitacaoTecnica.ADICAO_COMUM,
                        TipoObraSnapshot.CORRETIVA,
                        "OC-1042",
                        new HistoricoSolicitacaoResponse.Autor(UUID.randomUUID(), "Thalisson"),
                        OrigemComandoTecnico.UI,
                        "Ajuste de cronograma",
                        2L,
                        OffsetDateTime.now(),
                        Map.of(
                                "comando", "REPLANEJAR_SOLICITACAO_TECNICA",
                                "alteracoes", List.of(Map.of(
                                        "caminho", "dataPrevista",
                                        "valorAnterior", "2026-08-09",
                                        "valorNovo", "2026-08-31"
                                )),
                                "snapshots", Map.of("dataPrevista", "2026-08-31")
                        )
                )),
                new Paged(20, 0, 1, 1),
                List.of()
        );
        when(historico.porSolicitacao(eq(idSolicitacao), any(), any())).thenReturn(response);

        mockMvc.perform(get("/v1/solicitacoes-material/{idSolicitacao}/historico", idSolicitacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventos[0].tipo").value("DATA_PREVISTA_ALTERADA"))
                .andExpect(jsonPath("$.eventos[0].autor.nome").value("Thalisson"))
                .andExpect(jsonPath("$.eventos[0].payload.comando").value("REPLANEJAR_SOLICITACAO_TECNICA"))
                .andExpect(jsonPath("$.eventos[0].payload.alteracoes[0].caminho").value("dataPrevista"))
                .andExpect(jsonPath("$.eventos[0].payload.alteracoes[0].valorAnterior").value("2026-08-09"))
                .andExpect(jsonPath("$.eventos[0].payload.alteracoes[0].valorNovo").value("2026-08-31"))
                .andExpect(jsonPath("$.eventos[0].payload.containerNode").doesNotExist())
                .andExpect(jsonPath("$.eventos[0].payload.array").doesNotExist())
                .andExpect(jsonPath("$.eventos[0].payload.nodeType").doesNotExist());
    }

}
