package com.nexo.app.modules.financeiro.infrastructure.web;

import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraFilter;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoPagamentoFilter;
import com.nexo.app.modules.financeiro.application.usecases.ExportarSolicitacoesFinanceirasUseCase;
import com.nexo.app.modules.financeiro.application.usecases.SolicitacaoCompraUseCases;
import com.nexo.app.modules.financeiro.application.usecases.SolicitacaoPagamentoUseCases;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SolicitacaoFinanceiraExportControllerTest {

    private SolicitacaoCompraUseCases compraUseCases;
    private SolicitacaoPagamentoUseCases pagamentoUseCases;
    private ExportarSolicitacoesFinanceirasUseCase exportacao;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        compraUseCases = mock(SolicitacaoCompraUseCases.class);
        pagamentoUseCases = mock(SolicitacaoPagamentoUseCases.class);
        exportacao = mock(ExportarSolicitacoesFinanceirasUseCase.class);

        doAnswer(responderCom("compra-resumo"))
                .when(exportacao).exportarComprasResumo(any(), any(Writer.class));
        doAnswer(responderCom("compra-detalhado"))
                .when(exportacao).exportarComprasDetalhado(any(), any(Writer.class));
        doAnswer(responderCom("pagamento-resumo"))
                .when(exportacao).exportarPagamentosResumo(any(), any(Writer.class));
        doAnswer(responderCom("pagamento-detalhado"))
                .when(exportacao).exportarPagamentosDetalhado(any(), any(Writer.class));

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SolicitacaoCompraController(compraUseCases, exportacao),
                        new SolicitacaoPagamentoController(pagamentoUseCases, exportacao))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void publicaAsQuatroRotasComNomeTipoEBomUtf8() throws Exception {
        conferirDownload("/v1/financeiro/solicitacoes-compra/exportar/resumo",
                "solicitacoes_compra_resumo.csv", "compra-resumo");
        conferirDownload("/v1/financeiro/solicitacoes-compra/exportar/detalhado",
                "solicitacoes_compra_detalhado.csv", "compra-detalhado");
        conferirDownload("/v1/financeiro/solicitacoes-pagamento/exportar/resumo",
                "solicitacoes_pagamento_resumo.csv", "pagamento-resumo");
        conferirDownload("/v1/financeiro/solicitacoes-pagamento/exportar/detalhado",
                "solicitacoes_pagamento_detalhado.csv", "pagamento-detalhado");
    }

    @Test
    void encaminhaFiltrosComAmesmaSemanticaDaListagem() throws Exception {
        mockMvc.perform(asyncDispatch(executarDownload(
                        "/v1/financeiro/solicitacoes-compra/exportar/resumo")))
                .andExpect(status().isOk());
        mockMvc.perform(asyncDispatch(executarDownload(
                        "/v1/financeiro/solicitacoes-pagamento/exportar/resumo")))
                .andExpect(status().isOk());

        ArgumentCaptor<SolicitacaoCompraFilter> compra = ArgumentCaptor.forClass(SolicitacaoCompraFilter.class);
        ArgumentCaptor<SolicitacaoPagamentoFilter> pagamento =
                ArgumentCaptor.forClass(SolicitacaoPagamentoFilter.class);
        verify(exportacao).exportarComprasResumo(compra.capture(), any(Writer.class));
        verify(exportacao).exportarPagamentosResumo(pagamento.capture(), any(Writer.class));

        assertThat(compra.getValue().status()).isEqualTo(StatusSolicitacao.APROVADO);
        assertThat(compra.getValue().apenasMinhas()).isTrue();
        assertThat(compra.getValue().buscaGlobal()).isEqualTo("fornecedor norte");
        assertThat(pagamento.getValue().status()).isEqualTo(StatusSolicitacao.APROVADO);
        assertThat(pagamento.getValue().apenasMinhas()).isTrue();
        assertThat(pagamento.getValue().buscaGlobal()).isEqualTo("fornecedor norte");
    }

    @Test
    void declaraAsAuthoritiesDeLeituraNasQuatroOperacoes() throws Exception {
        assertThat(authority(SolicitacaoCompraController.class, "exportarResumo", SolicitacaoCompraFilter.class))
                .isEqualTo("hasAuthority('financeiro:compra:read')");
        assertThat(authority(SolicitacaoCompraController.class, "exportarDetalhado", SolicitacaoCompraFilter.class))
                .isEqualTo("hasAuthority('financeiro:compra:read')");
        assertThat(authority(SolicitacaoPagamentoController.class, "exportarResumo",
                SolicitacaoPagamentoFilter.class)).isEqualTo("hasAuthority('financeiro:pagamento:read')");
        assertThat(authority(SolicitacaoPagamentoController.class, "exportarDetalhado",
                SolicitacaoPagamentoFilter.class)).isEqualTo("hasAuthority('financeiro:pagamento:read')");
    }

    private void conferirDownload(String caminho, String nomeArquivo, String conteudo) throws Exception {
        MvcResult resultado = executarDownload(caminho);
        mockMvc.perform(asyncDispatch(resultado))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + nomeArquivo + "\""))
                .andExpect(content().bytes(("\uFEFF" + conteudo).getBytes(StandardCharsets.UTF_8)));
    }

    private MvcResult executarDownload(String caminho) throws Exception {
        return mockMvc.perform(get(caminho)
                        .param("status", "APROVADO")
                        .param("apenasMinhas", "true")
                        .param("buscaGlobal", "fornecedor norte"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private org.mockito.stubbing.Answer<Void> responderCom(String conteudo) {
        return invocacao -> {
            Writer writer = invocacao.getArgument(1);
            writer.write(conteudo);
            return null;
        };
    }

    private String authority(Class<?> controller, String metodo, Class<?> filtro) throws Exception {
        PreAuthorize preAuthorize = controller.getMethod(metodo, filtro).getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        return preAuthorize.value();
    }
}
