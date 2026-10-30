package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MontadorDocumentoTest {

    @Mock private ResolvedorReferencias resolvedor;
    @Mock private UsuarioApi usuarioApi;
    @Mock private DocumentoFinanceiroRepository repository;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resumoInlineExplicaAsAcoesDisponiveisParaOFrontend() {
        UUID usuario = UUID.randomUUID();
        autenticar(usuario,
                "financeiro:documento:read",
                "financeiro:documento:write",
                "financeiro:documento:conferir");
        when(repository.buscarHashesDuplicados()).thenReturn(List.of());
        var montador = new MontadorDocumento(resolvedor, usuarioApi, repository);
        var documento = new DocumentoFinanceiro(
                new DocumentoFinanceiro.Arquivo("boleto.pdf", "financeiro/boleto.pdf",
                        "application/pdf", 100L, "hash"),
                new DocumentoFinanceiro.Metadados(
                        TipoDocumentoFinanceiro.BOLETO, "77", null, "0019000009",
                        null, null, null, null, null, null, null, null, null, null, null),
                usuario);

        var pendente = montador.montarResumo(documento);

        assertTrue(pendente.acoes().baixar());
        assertTrue(pendente.acoes().editarMetadados());
        assertTrue(pendente.acoes().remover());
        assertFalse(pendente.acoes().conferir());
        assertFalse(pendente.acoes().arquivar());
        assertFalse(pendente.acoes().reabrir());

        documento.conferir(usuario, "Documento valido");
        var conferidoResumo = montador.montarResumo(documento);

        assertTrue(conferidoResumo.acoes().baixar());
        assertFalse(conferidoResumo.acoes().editarMetadados());
        assertFalse(conferidoResumo.acoes().remover());
        assertFalse(conferidoResumo.acoes().apontarPendencia());
        assertFalse(conferidoResumo.acoes().arquivar());
        assertFalse(conferidoResumo.acoes().reabrir());
        assertFalse(conferidoResumo.acoes().desvincular());

        var conferidoAdmin = montador.montar(documento);
        assertTrue(conferidoAdmin.acoes().baixar());
        assertTrue(conferidoAdmin.acoes().editarMetadados());
        assertTrue(conferidoAdmin.acoes().remover());
        assertTrue(conferidoAdmin.acoes().arquivar());
        assertFalse(conferidoAdmin.acoes().reabrir());

        documento.arquivar();
        var arquivadoAdmin = montador.montar(documento);
        assertTrue(arquivadoAdmin.acoes().reabrir());
    }

    @Test
    void deveMontarDocumentoComOrigemNulaDoBancoSemErro() throws Exception {
        UUID usuario = UUID.randomUUID();
        autenticar(usuario, "financeiro:documento:read", "financeiro:documento:write");
        when(repository.buscarHashesDuplicados()).thenReturn(List.of());
        var montador = new MontadorDocumento(resolvedor, usuarioApi, repository);
        var documento = new DocumentoFinanceiro(
                new DocumentoFinanceiro.Arquivo("nota.pdf", "financeiro/nota.pdf",
                        "application/pdf", 100L, "hash123"),
                new DocumentoFinanceiro.Metadados(
                        TipoDocumentoFinanceiro.NOTA_FISCAL, "123", null, null,
                        null, "Emissor", null, null, null, null, null, null, null, null, null),
                usuario);

        // Simula comportamento do Hibernate quando as colunas embutidas de origem são todas NULL
        var campoOrigem = DocumentoFinanceiro.class.getDeclaredField("origem");
        campoOrigem.setAccessible(true);
        campoOrigem.set(documento, null);

        var response = montador.montar(documento);

        org.junit.jupiter.api.Assertions.assertNotNull(response);
        org.junit.jupiter.api.Assertions.assertNull(response.origemTipo());
        org.junit.jupiter.api.Assertions.assertNull(response.origemSolicitacaoId());
        org.junit.jupiter.api.Assertions.assertNull(response.origemNumero());
        assertTrue(response.acoes().baixar());

        var resumo = montador.montarResumo(documento);
        org.junit.jupiter.api.Assertions.assertNotNull(resumo);
        assertTrue(resumo.acoes().baixar());
    }

    private void autenticar(UUID usuario, String... autoridades) {
        var autenticacao = new UsernamePasswordAuthenticationToken(
                usuario.toString(), null,
                java.util.Arrays.stream(autoridades).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(autenticacao);
    }
}
