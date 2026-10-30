package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.AprovacaoRequest;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraResponse;
import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.domain.ValoresCanonicos;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoRepository;
import com.nexo.app.shared.domain.events.SolicitacaoFinanceiraDecisaoDesfeitaEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoCompraUseCasesTest {

    @Mock private SolicitacaoCompraRepository repository;
    @Mock private ResolvedorBuscaGlobalSolicitacaoFinanceira resolvedorBuscaGlobal;
    @Mock private FormularioSolicitacaoUseCases formularioUseCases;
    @Mock private ValidadorSolicitacao validador;
    @Mock private ResolvedorReferencias resolvedor;
    @Mock private MontadorSolicitacao montador;
    @Mock private DocumentoFinanceiroUseCases documentos;
    @Mock private EventosSolicitacaoFinanceira eventos;
    @Mock private HistoricoSolicitacaoFinanceira historico;
    @Mock private SolicitacaoPagamentoRepository pagamentoRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private SolicitacaoCompraUseCases useCases;
    private UUID solicitacaoId;
    private UUID usuarioDecisaoId;

    @BeforeEach
    void configurar() {
        useCases = new SolicitacaoCompraUseCases(
                repository, resolvedorBuscaGlobal, formularioUseCases, validador,
                resolvedor, montador, documentos, eventos, historico, pagamentoRepository, eventPublisher);
        solicitacaoId = UUID.randomUUID();
        usuarioDecisaoId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Ao aprovar compra, confere documentos da solicitação")
    void aprovarConfereDocumentos() {
        var formulario = new FormularioSolicitacao(TipoSolicitacao.COMPRA, "PADRAO", "Compra", null, true);
        var nucleo = NucleoSolicitacao.de(new ValoresCanonicos(Map.of(
                CampoCanonico.VALOR, new BigDecimal("1000.00"),
                CampoCanonico.DESCRICAO, "Compra de teste")));
        var compra = new SolicitacaoCompra(formulario, nucleo, UUID.randomUUID());
        compra.submeter();

        when(repository.findById(solicitacaoId)).thenReturn(Optional.of(compra));
        when(repository.save(compra)).thenReturn(compra);
        var responseEsperada = mock(SolicitacaoCompraResponse.class);
        when(montador.montar(compra)).thenReturn(responseEsperada);

        var request = new AprovacaoRequest(new BigDecimal("950.00"), "Desconto obtido");
        var resultado = useCases.aprovar(solicitacaoId, request, usuarioDecisaoId);

        assertEquals(responseEsperada, resultado);
        assertEquals(StatusSolicitacao.APROVADO, compra.getStatus());
        assertEquals(new BigDecimal("950.00"), compra.getValorAprovado());

        verify(documentos).conferirDocumentosDaSolicitacao(solicitacaoId, usuarioDecisaoId, "Desconto obtido");
    }

    @Test
    @DisplayName("Desfazer a aprovação devolve a compra à fila, registra o histórico e avisa")
    void desfazerDecisaoRegistraEAvisa() {
        var compra = compraAprovada();
        when(repository.findById(solicitacaoId)).thenReturn(Optional.of(compra));
        when(pagamentoRepository.numerosVinculadosEmAndamento(solicitacaoId)).thenReturn(List.of());
        when(repository.save(compra)).thenReturn(compra);
        var evento = mock(SolicitacaoFinanceiraDecisaoDesfeitaEvent.class);
        when(eventos.decisaoDesfeita(eq(compra), any(), eq("Fornecedor errado"), eq(usuarioDecisaoId)))
                .thenReturn(evento);

        useCases.desfazerDecisao(solicitacaoId, "Fornecedor errado", usuarioDecisaoId);

        assertEquals(StatusSolicitacao.SOLICITADO, compra.getStatus());
        verify(historico).registrar(compra, TipoEventoSolicitacao.DECISAO_DESFEITA, StatusSolicitacao.APROVADO,
                usuarioDecisaoId, new BigDecimal("1000.00"), "Fornecedor errado");
        verify(eventPublisher).publishEvent(evento);
    }

    @Test
    @DisplayName("Não desfaz a aprovação de compra com pagamento vinculado em andamento")
    void naoDesfazComPagamentoVinculado() {
        var compra = compraAprovada();
        when(repository.findById(solicitacaoId)).thenReturn(Optional.of(compra));
        when(pagamentoRepository.numerosVinculadosEmAndamento(solicitacaoId)).thenReturn(List.of(12L, 15L));

        var erro = assertThrows(InvarianteVioladaException.class,
                () -> useCases.desfazerDecisao(solicitacaoId, "Fornecedor errado", usuarioDecisaoId));

        assertTrue(erro.getMessage().contains("nº 12, nº 15"));
        assertEquals(StatusSolicitacao.APROVADO, compra.getStatus());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private SolicitacaoCompra compraAprovada() {
        var formulario = new FormularioSolicitacao(TipoSolicitacao.COMPRA, "PADRAO", "Compra", null, true);
        var nucleo = NucleoSolicitacao.de(new ValoresCanonicos(Map.of(
                CampoCanonico.VALOR, new BigDecimal("1000.00"),
                CampoCanonico.DESCRICAO, "Compra de teste")));
        var compra = new SolicitacaoCompra(formulario, nucleo, UUID.randomUUID());
        compra.submeter();
        compra.aprovar(UUID.randomUUID(), null, null);
        return compra;
    }
}
