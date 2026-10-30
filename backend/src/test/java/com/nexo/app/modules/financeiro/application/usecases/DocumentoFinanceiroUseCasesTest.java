package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.SituacaoDocumento;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.domain.ValoresCanonicos;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentoFinanceiroUseCasesTest {

    @Mock private DocumentoFinanceiroRepository repository;
    @Mock private SolicitacaoCompraRepository compraRepository;
    @Mock private SolicitacaoPagamentoRepository pagamentoRepository;
    @Mock private ArmazenamentoDocumentoService armazenamento;
    @Mock private MontadorDocumento montador;

    private DocumentoFinanceiroUseCases useCases;
    private UUID solicitacaoId;
    private UUID usuarioDecisaoId;

    @BeforeEach
    void configurar() {
        useCases = new DocumentoFinanceiroUseCases(
                repository, compraRepository, pagamentoRepository, armazenamento, montador);
        solicitacaoId = UUID.randomUUID();
        usuarioDecisaoId = UUID.randomUUID();
    }

    private DocumentoFinanceiro criarDocumento(TipoDocumentoFinanceiro tipo) {
        var arquivo = new DocumentoFinanceiro.Arquivo(
                "doc.pdf", "chave/doc.pdf", "application/pdf", 100L, "hash");
        return new DocumentoFinanceiro(arquivo, DocumentoFinanceiro.Metadados.apenasTipo(tipo), UUID.randomUUID());
    }

    @Test
    @DisplayName("Confere documentos pendentes e com pendência ao aprovar solicitação")
    void confereDocumentosElegiveis() {
        var docPendente = criarDocumento(TipoDocumentoFinanceiro.ORCAMENTO);
        var docComPendencia = criarDocumento(TipoDocumentoFinanceiro.NOTA_FISCAL);
        docComPendencia.apontarPendencia(UUID.randomUUID(), "Falta carimbo");

        when(repository.findByOrigemSolicitacaoIdAndAtivoTrueOrderByDataCriacaoAsc(solicitacaoId))
                .thenReturn(List.of(docPendente, docComPendencia));

        useCases.conferirDocumentosDaSolicitacao(solicitacaoId, usuarioDecisaoId, "Aprovado pela diretoria");

        assertEquals(SituacaoDocumento.CONFERIDO, docPendente.getSituacao());
        assertEquals(usuarioDecisaoId, docPendente.getUsuarioConferenciaId());
        assertEquals("Aprovado pela diretoria", docPendente.getObservacaoConferencia());

        assertEquals(SituacaoDocumento.CONFERIDO, docComPendencia.getSituacao());
        assertEquals(usuarioDecisaoId, docComPendencia.getUsuarioConferenciaId());
        assertEquals("Aprovado pela diretoria", docComPendencia.getObservacaoConferencia());
        assertNull(docComPendencia.getMotivoPendencia());

        verify(repository).saveAll(List.of(docPendente, docComPendencia));
    }

    @Test
    @DisplayName("Não altera documentos arquivados ou já conferidos")
    void preservaArquivadosEConferidos() {
        var docArquivado = criarDocumento(TipoDocumentoFinanceiro.CONTRATO);
        docArquivado.conferir(UUID.randomUUID(), "Conferido anterior");
        docArquivado.arquivar();

        when(repository.findByOrigemSolicitacaoIdAndAtivoTrueOrderByDataCriacaoAsc(solicitacaoId))
                .thenReturn(List.of(docArquivado));

        useCases.conferirDocumentosDaSolicitacao(solicitacaoId, usuarioDecisaoId, "Nova aprovação");

        assertEquals(SituacaoDocumento.ARQUIVADO, docArquivado.getSituacao());
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Ignora chamada com IDs nulos")
    void ignoraParametrosNulos() {
        useCases.conferirDocumentosDaSolicitacao(null, usuarioDecisaoId, "obs");
        useCases.conferirDocumentosDaSolicitacao(solicitacaoId, null, "obs");

        verify(repository, never()).findByOrigemSolicitacaoIdAndAtivoTrueOrderByDataCriacaoAsc(solicitacaoId);
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Ao enviar documento para solicitação já aprovada, marca automaticamente como conferido")
    void enviarParaSolicitacaoAprovadaMarcaConferido() {
        var formulario = new FormularioSolicitacao(TipoSolicitacao.COMPRA, "PADRAO", "Compra", null, true);
        var nucleo = NucleoSolicitacao.de(new ValoresCanonicos(Map.of(
                CampoCanonico.VALOR, new BigDecimal("1000.00"),
                CampoCanonico.DESCRICAO, "Compra aprovada")));
        var compra = new SolicitacaoCompra(formulario, nucleo, UUID.randomUUID());
        org.springframework.test.util.ReflectionTestUtils.setField(compra, "id", solicitacaoId);
        compra.submeter();
        compra.aprovar(usuarioDecisaoId, new BigDecimal("1000.00"), "Aprovada");

        var arquivo = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        var arquivoArmazenado = new DocumentoFinanceiro.Arquivo(
                "nota.pdf", "chave/nota.pdf", "application/pdf", 1024L, "hash");
        when(armazenamento.armazenar(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(arquivo)))
                .thenReturn(arquivoArmazenado);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<DocumentoFinanceiro>> captor = org.mockito.ArgumentCaptor.forClass(List.class);

        var usuarioEnvio = UUID.randomUUID();
        useCases.enviarPara(compra, List.of(arquivo), null, usuarioEnvio);

        verify(repository).saveAll(captor.capture());
        var salvos = captor.getValue();
        assertEquals(1, salvos.size());
        var docSalvo = salvos.getFirst();
        assertEquals(SituacaoDocumento.CONFERIDO, docSalvo.getSituacao());
        assertEquals(usuarioDecisaoId, docSalvo.getUsuarioConferenciaId());
        assertEquals("Conferência automática por inclusão em solicitação já aprovada.", docSalvo.getObservacaoConferencia());
    }

    @Test
    @DisplayName("Ao enviar documento para solicitação não aprovada, mantém situação pendente")
    void enviarParaSolicitacaoPendenteMantemPendente() {
        var formulario = new FormularioSolicitacao(TipoSolicitacao.COMPRA, "PADRAO", "Compra", null, true);
        var nucleo = NucleoSolicitacao.de(new ValoresCanonicos(Map.of(
                CampoCanonico.VALOR, new BigDecimal("1000.00"),
                CampoCanonico.DESCRICAO, "Compra em rascunho")));
        var compra = new SolicitacaoCompra(formulario, nucleo, UUID.randomUUID());
        org.springframework.test.util.ReflectionTestUtils.setField(compra, "id", solicitacaoId);

        var arquivo = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        var arquivoArmazenado = new DocumentoFinanceiro.Arquivo(
                "nota.pdf", "chave/nota.pdf", "application/pdf", 1024L, "hash");
        when(armazenamento.armazenar(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(arquivo)))
                .thenReturn(arquivoArmazenado);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<DocumentoFinanceiro>> captor = org.mockito.ArgumentCaptor.forClass(List.class);

        var usuarioEnvio = UUID.randomUUID();
        useCases.enviarPara(compra, List.of(arquivo), null, usuarioEnvio);

        verify(repository).saveAll(captor.capture());
        var salvos = captor.getValue();
        assertEquals(1, salvos.size());
        var docSalvo = salvos.getFirst();
        assertEquals(SituacaoDocumento.PENDENTE, docSalvo.getSituacao());
        assertNull(docSalvo.getUsuarioConferenciaId());
    }

    @Test
    @DisplayName("Ao vincular documento avulso a solicitação aprovada, confere automaticamente")
    void vincularSolicitacaoAprovadaMarcaConferido() {
        var doc = criarDocumento(TipoDocumentoFinanceiro.NOTA_FISCAL);
        var docId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(doc, "id", docId);

        var formulario = new FormularioSolicitacao(TipoSolicitacao.COMPRA, "PADRAO", "Compra", null, true);
        var nucleo = NucleoSolicitacao.de(new ValoresCanonicos(Map.of(
                CampoCanonico.VALOR, new BigDecimal("1000.00"),
                CampoCanonico.DESCRICAO, "Compra aprovada")));
        var compra = new SolicitacaoCompra(formulario, nucleo, UUID.randomUUID());
        org.springframework.test.util.ReflectionTestUtils.setField(compra, "id", solicitacaoId);
        compra.submeter();
        compra.aprovar(usuarioDecisaoId, new BigDecimal("1000.00"), "Aprovada");

        when(repository.findById(docId)).thenReturn(java.util.Optional.of(doc));
        when(compraRepository.findById(solicitacaoId)).thenReturn(java.util.Optional.of(compra));
        when(repository.save(doc)).thenReturn(doc);

        useCases.vincular(docId, TipoSolicitacao.COMPRA, solicitacaoId);

        assertEquals(SituacaoDocumento.CONFERIDO, doc.getSituacao());
        assertEquals(usuarioDecisaoId, doc.getUsuarioConferenciaId());
        assertEquals("Conferência automática por inclusão em solicitação já aprovada.", doc.getObservacaoConferencia());
    }

    @Test
    @DisplayName("Ao cadastrar documento avulso pelo formulário, marca automaticamente como conferido")
    void enviarDocumentoAvulsoMarcaConferido() {
        var arquivo = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        var arquivoArmazenado = new DocumentoFinanceiro.Arquivo(
                "recibo.pdf", "chave/recibo.pdf", "application/pdf", 2048L, "hashRecibo");
        when(armazenamento.armazenar(org.mockito.ArgumentMatchers.eq("financeiro/documentos"), org.mockito.ArgumentMatchers.eq(arquivo)))
                .thenReturn(arquivoArmazenado);

        org.mockito.ArgumentCaptor<DocumentoFinanceiro> captor = org.mockito.ArgumentCaptor.forClass(DocumentoFinanceiro.class);
        var usuarioEnvio = UUID.randomUUID();

        useCases.enviar(arquivo, null, usuarioEnvio);

        verify(repository).save(captor.capture());
        var docSalvo = captor.getValue();
        assertEquals(SituacaoDocumento.CONFERIDO, docSalvo.getSituacao());
        assertEquals(usuarioEnvio, docSalvo.getUsuarioConferenciaId());
        assertEquals("Conferência automática por inclusão direta de documento avulso.", docSalvo.getObservacaoConferencia());
    }

    @Test
    @DisplayName("Reabrir documento conferido lança exceção")
    void reabrirDocumentoConferidoLancaExcecao() {
        var doc = criarDocumento(TipoDocumentoFinanceiro.NOTA_FISCAL);
        var docId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(doc, "id", docId);
        doc.conferir(UUID.randomUUID(), "Conferido");

        when(repository.findById(docId)).thenReturn(Optional.of(doc));

        assertThrows(InvarianteVioladaException.class, () -> useCases.reabrir(docId));
        verify(repository, never()).save(doc);
    }

    @Test
    @DisplayName("Reabrir documento arquivado restaura situação para CONFERIDO")
    void reabrirDocumentoArquivadoRestauraConferido() {
        var doc = criarDocumento(TipoDocumentoFinanceiro.NOTA_FISCAL);
        var docId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(doc, "id", docId);
        doc.conferir(usuarioDecisaoId, "Conferido");
        doc.arquivar();

        when(repository.findById(docId)).thenReturn(Optional.of(doc));
        when(repository.save(doc)).thenReturn(doc);

        useCases.reabrir(docId);

        assertEquals(SituacaoDocumento.CONFERIDO, doc.getSituacao());
        assertEquals(usuarioDecisaoId, doc.getUsuarioConferenciaId());
        verify(repository).save(doc);
    }

    @Test
    @DisplayName("Reabrir documento com pendência retorna situação para PENDENTE")
    void reabrirDocumentoComPendenciaRetornaPendente() {
        var doc = criarDocumento(TipoDocumentoFinanceiro.NOTA_FISCAL);
        var docId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(doc, "id", docId);
        doc.apontarPendencia(usuarioDecisaoId, "Falta nota");

        when(repository.findById(docId)).thenReturn(Optional.of(doc));
        when(repository.save(doc)).thenReturn(doc);

        useCases.reabrir(docId);

        assertEquals(SituacaoDocumento.PENDENTE, doc.getSituacao());
        assertNull(doc.getMotivoPendencia());
        assertNull(doc.getUsuarioConferenciaId());
        verify(repository).save(doc);
    }
}