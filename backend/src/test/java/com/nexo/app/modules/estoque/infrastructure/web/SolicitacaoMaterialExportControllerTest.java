package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.SolicitacaoFilter;
import com.nexo.app.modules.estoque.application.services.AssinaturaSolicitacaoMaterialService;
import com.nexo.app.modules.estoque.application.services.OperacaoSolicitacaoComercialService;
import com.nexo.app.modules.estoque.application.usecases.ExportarSolicitacoesMaterialUseCase;
import com.nexo.app.modules.estoque.application.usecases.ImportarSolicitacaoComercialUseCase;
import com.nexo.app.modules.estoque.application.usecases.SolicitacaoMaterialUseCases;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial.StatusSolicitacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SolicitacaoMaterialExportControllerTest {

    private SolicitacaoMaterialUseCases useCases;
    private OperacaoSolicitacaoComercialService operacaoComercial;
    private AssinaturaSolicitacaoMaterialService assinaturaService;
    private ExportarSolicitacoesMaterialUseCase exportacao;
    private ImportarSolicitacaoComercialUseCase importarComercial;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        useCases = mock(SolicitacaoMaterialUseCases.class);
        operacaoComercial = mock(OperacaoSolicitacaoComercialService.class);
        assinaturaService = mock(AssinaturaSolicitacaoMaterialService.class);
        exportacao = mock(ExportarSolicitacoesMaterialUseCase.class);
        importarComercial = mock(ImportarSolicitacaoComercialUseCase.class);

        doAnswer(responderCom("resumo-conteudo"))
                .when(exportacao).exportarResumo(any(), any(), any(), any(Writer.class));
        doAnswer(responderCom("detalhado-conteudo"))
                .when(exportacao).exportarDetalhado(any(), any(), any(), any(Writer.class));

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SolicitacaoController(useCases,
                                mock(com.nexo.app.modules.estoque.application.usecases.PendenciaSolicitacaoUseCases.class),
                                operacaoComercial, assinaturaService, exportacao, importarComercial,
                                mock(com.nexo.app.modules.estoque.application.usecases.SeparacaoSolicitacaoTecnicaUseCases.class),
                                mock(com.nexo.app.modules.estoque.application.usecases.KanbanSolicitacaoTecnicaUseCases.class),
                                mock(com.nexo.app.modules.estoque.application.usecases.ConsumoAnteriorSolicitacaoUseCase.class),
                                mock(com.nexo.app.modules.estoque.application.usecases.ReservaSerieSolicitacaoUseCase.class)))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void publicaRotasExportacaoComNomeTipoEBomUtf8() throws Exception {
        conferirDownload("/v1/solicitacoes-material/exportar/resumo",
                "solicitacoes_consolidado_resumo.csv", "resumo-conteudo");
        conferirDownload("/v1/solicitacoes-material/exportar/detalhado",
                "solicitacoes_consolidado_detalhado.csv", "detalhado-conteudo");

        // Contextos específicos
        conferirDownload("/v1/solicitacoes-material/exportar/resumo?contexto=COMERCIAL",
                "solicitacoes_comerciais_resumo.csv", "resumo-conteudo");
        conferirDownload("/v1/solicitacoes-material/exportar/resumo?contexto=TECNICO",
                "solicitacoes_tecnicas_resumo.csv", "resumo-conteudo");
    }

    @Test
    void encaminhaFiltrosComAmesmaSemanticaDaListagem() throws Exception {
        mockMvc.perform(asyncDispatch(executarDownload(
                        "/v1/solicitacoes-material/exportar/resumo?status=APROVADO&contexto=TECNICO&apenasMinhas=true&buscaGlobal=cabo")))
                .andExpect(status().isOk());

        ArgumentCaptor<SolicitacaoFilter> captor = ArgumentCaptor.forClass(SolicitacaoFilter.class);
        ArgumentCaptor<Boolean> apenasMinhasCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(exportacao).exportarResumo(captor.capture(), any(), apenasMinhasCaptor.capture(), any(Writer.class));

        assertThat(captor.getValue().status()).isEqualTo(StatusSolicitacao.APROVADO);
        assertThat(captor.getValue().contexto()).isEqualTo(ContextoSolicitacaoMaterial.TECNICO);
        assertThat(captor.getValue().buscaGlobal()).isEqualTo("cabo");
        assertThat(apenasMinhasCaptor.getValue()).isTrue();
    }

    @Test
    void exportaPacoteZipQuandoSolicitado() throws Exception {
        mockMvc.perform(asyncDispatch(executarDownload(
                        "/v1/solicitacoes-material/exportar/resumo?pacoteZip=true")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"solicitacoes_material_resumo.zip\""))
                .andExpect(header().string("Content-Type", "application/zip"));

        verify(exportacao).exportarZipResumo(any(), any(), any(), any(OutputStream.class));
    }

    @Test
    void declaraAuthorityDeLeituraNasOperacoesDeExportacao() throws Exception {
        assertThat(authority(SolicitacaoController.class, "exportarResumo", SolicitacaoFilter.class, Boolean.class, Boolean.class))
                .isEqualTo("hasAuthority('estoque:solicitacao:read')");
        assertThat(authority(SolicitacaoController.class, "exportarDetalhado", SolicitacaoFilter.class, Boolean.class, Boolean.class))
                .isEqualTo("hasAuthority('estoque:solicitacao:read')");
    }

    private void conferirDownload(String url, String nomeArquivoEsperado, String conteudoEsperado) throws Exception {
        MvcResult result = mockMvc.perform(asyncDispatch(executarDownload(url)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + nomeArquivoEsperado + "\""))
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String corpo = new String(bytes, StandardCharsets.UTF_8);
        assertThat(corpo).contains(conteudoEsperado);
    }

    private MvcResult executarDownload(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private Answer<Void> responderCom(String conteudo) {
        return invocation -> {
            Writer writer = invocation.getArgument(3, Writer.class);
            writer.write(conteudo);
            writer.flush();
            return null;
        };
    }

    private String authority(Class<?> controller, String method, Class<?>... parameterTypes) throws NoSuchMethodException {
        PreAuthorize preAuthorize = controller.getMethod(method, parameterTypes).getAnnotation(PreAuthorize.class);
        return preAuthorize != null ? preAuthorize.value() : null;
    }
}
