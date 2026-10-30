package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.AprovacaoRequest;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoPagamentoResponse;
import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.SolicitacaoPagamento;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.domain.ValoresCanonicos;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoPagamentoUseCasesTest {

    @Mock private SolicitacaoPagamentoRepository repository;
    @Mock private ResolvedorBuscaGlobalSolicitacaoFinanceira resolvedorBuscaGlobal;
    @Mock private SolicitacaoCompraUseCases compraUseCases;
    @Mock private FormularioSolicitacaoUseCases formularioUseCases;
    @Mock private ValidadorSolicitacao validador;
    @Mock private ResolvedorReferencias resolvedor;
    @Mock private MontadorSolicitacao montador;
    @Mock private DocumentoFinanceiroUseCases documentos;
    @Mock private EventosSolicitacaoFinanceira eventos;
    @Mock private HistoricoSolicitacaoFinanceira historico;
    @Mock private ApplicationEventPublisher eventPublisher;

    private SolicitacaoPagamentoUseCases useCases;
    private UUID solicitacaoId;
    private UUID usuarioDecisaoId;

    @BeforeEach
    void configurar() {
        useCases = new SolicitacaoPagamentoUseCases(
                repository, resolvedorBuscaGlobal, compraUseCases, formularioUseCases, validador,
                resolvedor, montador, documentos, eventos, historico, eventPublisher);
        solicitacaoId = UUID.randomUUID();
        usuarioDecisaoId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Ao aprovar pagamento, confere documentos da solicitação")
    void aprovarConfereDocumentos() {
        var formulario = new FormularioSolicitacao(TipoSolicitacao.PAGAMENTO, "PADRAO", "Pagamento", null, true);
        var nucleo = NucleoSolicitacao.de(new ValoresCanonicos(Map.of(
                CampoCanonico.VALOR, new BigDecimal("2000.00"),
                CampoCanonico.DESCRICAO, "Pagamento de serviço")));
        var pagamento = new SolicitacaoPagamento(formulario, nucleo, UUID.randomUUID());
        pagamento.submeter();

        when(repository.findById(solicitacaoId)).thenReturn(Optional.of(pagamento));
        when(repository.save(pagamento)).thenReturn(pagamento);
        var responseEsperada = mock(SolicitacaoPagamentoResponse.class);
        when(montador.montar(pagamento)).thenReturn(responseEsperada);

        var request = new AprovacaoRequest(new BigDecimal("2000.00"), "Aprovado integral");
        var resultado = useCases.aprovar(solicitacaoId, request, usuarioDecisaoId);

        assertEquals(responseEsperada, resultado);
        assertEquals(StatusSolicitacao.APROVADO, pagamento.getStatus());
        assertEquals(new BigDecimal("2000.00"), pagamento.getValorAprovado());

        verify(documentos).conferirDocumentosDaSolicitacao(solicitacaoId, usuarioDecisaoId, "Aprovado integral");
    }
}