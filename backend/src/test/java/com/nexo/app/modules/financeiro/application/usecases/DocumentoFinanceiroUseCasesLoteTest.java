package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentoFinanceiroUseCasesLoteTest {

    @Mock private DocumentoFinanceiroRepository repository;
    @Mock private SolicitacaoCompraRepository compraRepository;
    @Mock private SolicitacaoPagamentoRepository pagamentoRepository;
    @Mock private ArmazenamentoDocumentoService armazenamento;
    @Mock private MontadorDocumento montador;
    @Mock private SolicitacaoBase solicitacao;
    @Mock private NucleoSolicitacao nucleo;
    @Mock private MultipartFile primeiro;
    @Mock private MultipartFile segundo;

    private DocumentoFinanceiroUseCases useCases;
    private UUID solicitacaoId;
    private UUID usuarioId;
    private String prefixo;

    @BeforeEach
    void configurar() {
        useCases = new DocumentoFinanceiroUseCases(
                repository, compraRepository, pagamentoRepository, armazenamento, montador);
        solicitacaoId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
        prefixo = "financeiro/compras/" + solicitacaoId;

    }

    @Test
    void armazenaEPersisteTodosOsArquivosDoLote() {
        configurarSolicitacao();
        when(armazenamento.armazenar(prefixo, primeiro)).thenReturn(arquivo("primeiro.pdf", "chave-1"));
        when(armazenamento.armazenar(prefixo, segundo)).thenReturn(arquivo("segundo.pdf", "chave-2"));

        useCases.enviarPara(solicitacao, List.of(primeiro, segundo),
                com.nexo.app.modules.financeiro.application.dto.DocumentoRequest
                        .deTipo(TipoDocumentoFinanceiro.ORCAMENTO),
                usuarioId);

        var captor = ArgumentCaptor.<List<DocumentoFinanceiro>>captor();
        verify(repository).saveAll(captor.capture());
        assertEquals(List.of("primeiro.pdf", "segundo.pdf"), captor.getValue().stream()
                .map(DocumentoFinanceiro::getNomeArquivo)
                .toList());
        assertEquals(List.of(solicitacaoId, solicitacaoId), captor.getValue().stream()
                .map(documento -> documento.getOrigem().getSolicitacaoId())
                .toList());
        verify(montador, org.mockito.Mockito.times(2)).montar(any(DocumentoFinanceiro.class));
    }

    @Test
    void removeDoStorageOsArquivosAnterioresQuandoUmItemFalha() {
        configurarSolicitacao();
        when(armazenamento.armazenar(prefixo, primeiro)).thenReturn(arquivo("primeiro.pdf", "chave-1"));
        when(armazenamento.armazenar(prefixo, segundo))
                .thenThrow(new InvarianteVioladaException("Arquivo inválido."));

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.enviarPara(solicitacao, List.of(primeiro, segundo), null, usuarioId));

        verify(armazenamento).removerSilenciosamente("chave-1");
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void rejeitaLoteSemArquivos() {
        var erro = assertThrows(InvarianteVioladaException.class,
                () -> useCases.enviarPara(solicitacao, List.of(), null, usuarioId));

        assertEquals("Envie ao menos um arquivo.", erro.getMessage());
        verify(armazenamento, never()).armazenar(any(), any());
        verify(repository, never()).saveAll(anyList());
    }

    private static DocumentoFinanceiro.Arquivo arquivo(String nome, String chave) {
        return new DocumentoFinanceiro.Arquivo(nome, chave, "application/pdf", 10L, chave + "-hash");
    }

    private void configurarSolicitacao() {
        when(solicitacao.getId()).thenReturn(solicitacaoId);
        when(solicitacao.getTipoSolicitacao()).thenReturn(TipoSolicitacao.COMPRA);
        when(solicitacao.getNucleo()).thenReturn(nucleo);
    }
}
