package com.nexo.app.modules.financeiro.infrastructure.web;

import com.nexo.app.modules.financeiro.application.usecases.ExportarSolicitacoesFinanceirasUseCase;
import com.nexo.app.modules.financeiro.application.usecases.SolicitacaoCompraUseCases;
import com.nexo.app.modules.financeiro.application.usecases.SolicitacaoPagamentoUseCases;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.iam.domain.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SolicitacaoFinanceiraDocumentoControllerTest {

    @Test
    void compraRecebeUmOuMaisArquivosNaMesmaParteMultipart() throws Exception {
        UUID solicitacaoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        var useCases = mock(SolicitacaoCompraUseCases.class);
        var usuario = usuario(usuarioId);
        MockMvc mockMvc = mockMvc(new SolicitacaoCompraController(
                useCases, mock(ExportarSolicitacoesFinanceirasUseCase.class)), usuario);
        var primeiro = arquivo("orcamento-a.pdf");
        var segundo = arquivo("orcamento-b.pdf");

        mockMvc.perform(multipart("/v1/financeiro/solicitacoes-compra/{id}/documentos", solicitacaoId)
                        .file(primeiro)
                        .file(segundo)
                        .param("tipo", "ORCAMENTO"))
                .andExpect(status().isCreated());

        var arquivos = capturarArquivosCompra(useCases, solicitacaoId, usuarioId);
        assertEquals(List.of("orcamento-a.pdf", "orcamento-b.pdf"), nomes(arquivos));
    }

    @Test
    void pagamentoMantemCompativelOEnvioDeUmUnicoArquivo() throws Exception {
        UUID solicitacaoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        var useCases = mock(SolicitacaoPagamentoUseCases.class);
        var usuario = usuario(usuarioId);
        MockMvc mockMvc = mockMvc(new SolicitacaoPagamentoController(
                useCases, mock(ExportarSolicitacoesFinanceirasUseCase.class)), usuario);
        var unico = arquivo("boleto.pdf");

        mockMvc.perform(multipart("/v1/financeiro/solicitacoes-pagamento/{id}/documentos", solicitacaoId)
                        .file(unico)
                        .param("tipo", "BOLETO"))
                .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.<List<MultipartFile>>captor();
        verify(useCases).adicionarDocumento(eq(solicitacaoId), captor.capture(),
                eq(TipoDocumentoFinanceiro.BOLETO), eq(usuarioId));
        assertEquals(List.of("boleto.pdf"), nomes(captor.getValue()));
    }

    private static List<MultipartFile> capturarArquivosCompra(SolicitacaoCompraUseCases useCases,
                                                               UUID solicitacaoId,
                                                               UUID usuarioId) {
        var captor = org.mockito.ArgumentCaptor.<List<MultipartFile>>captor();
        verify(useCases).adicionarDocumento(eq(solicitacaoId), captor.capture(),
                eq(TipoDocumentoFinanceiro.ORCAMENTO), eq(usuarioId));
        return captor.getValue();
    }

    private static List<String> nomes(List<MultipartFile> arquivos) {
        return arquivos.stream().map(MultipartFile::getOriginalFilename).toList();
    }

    private static MockMultipartFile arquivo(String nome) {
        return new MockMultipartFile("arquivo", nome, "application/pdf", nome.getBytes());
    }

    private static Usuario usuario(UUID id) {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(id);
        return usuario;
    }

    private static MockMvc mockMvc(Object controller, Usuario usuario) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new UsuarioArgumentResolver(usuario))
                .build();
    }

    private record UsuarioArgumentResolver(Usuario usuario) implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter,
                                      ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest,
                                      org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            return usuario;
        }
    }
}
