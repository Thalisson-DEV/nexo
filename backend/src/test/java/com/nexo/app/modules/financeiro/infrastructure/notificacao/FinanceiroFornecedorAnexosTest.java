package com.nexo.app.modules.financeiro.infrastructure.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroRepository;
import com.nexo.app.shared.domain.events.SolicitacaoFinanceiraSubmetidaEvent;

@ExtendWith(MockitoExtension.class)
class FinanceiroFornecedorAnexosTest {

    @Mock
    private DocumentoFinanceiroRepository repository;
    @InjectMocks
    private FinanceiroFornecedorAnexos fornecedor;

    @Test
    void atendeSomenteSubmissaoFinanceira() {
        assertThat(fornecedor.atende(SolicitacaoFinanceiraSubmetidaEvent.CHAVE_GATILHO)).isTrue();
        assertThat(fornecedor.atende("financeiro.solicitacao_aprovada")).isFalse();
        assertThat(fornecedor.atende(null)).isFalse();
    }

    @Test
    void converteDocumentosAtivosDeCompraOuPagamentoSemCarregarBytes() {
        UUID solicitacaoId = UUID.randomUUID();
        DocumentoFinanceiro ativo = documento(true, "orcamento.pdf", "application/pdf", "financeiro/x.pdf", 321L);
        DocumentoFinanceiro inativo = documento(false, "removido.pdf", "application/pdf", "financeiro/y.pdf", 100L);
        when(repository.findByOrigemSolicitacaoIdAndAtivoTrueOrderByDataCriacaoAsc(solicitacaoId))
                .thenReturn(List.of(ativo, inativo));

        var anexos = fornecedor.anexosDe(solicitacaoId);

        assertThat(anexos).singleElement().satisfies(anexo -> {
            assertThat(anexo.nomeArquivo()).isEqualTo("orcamento.pdf");
            assertThat(anexo.contentType()).isEqualTo("application/pdf");
            assertThat(anexo.chaveObjeto()).isEqualTo("financeiro/x.pdf");
            assertThat(anexo.tamanhoBytes()).isEqualTo(321L);
        });
        verify(repository).findByOrigemSolicitacaoIdAndAtivoTrueOrderByDataCriacaoAsc(solicitacaoId);
    }

    @Test
    void ausenciaDeDocumentosEhListaVazia() {
        UUID solicitacaoId = UUID.randomUUID();
        when(repository.findByOrigemSolicitacaoIdAndAtivoTrueOrderByDataCriacaoAsc(solicitacaoId))
                .thenReturn(List.of());

        assertThat(fornecedor.anexosDe(solicitacaoId)).isEmpty();
    }

    private static DocumentoFinanceiro documento(boolean ativo, String nome, String contentType,
                                                   String objectKey, long tamanho) {
        DocumentoFinanceiro documento = org.mockito.Mockito.mock(DocumentoFinanceiro.class);
        when(documento.isAtivo()).thenReturn(ativo);
        if (ativo) {
            when(documento.getNomeArquivo()).thenReturn(nome);
            when(documento.getContentType()).thenReturn(contentType);
            when(documento.getObjectKey()).thenReturn(objectKey);
            when(documento.getTamanhoBytes()).thenReturn(tamanho);
        }
        return documento;
    }
}
