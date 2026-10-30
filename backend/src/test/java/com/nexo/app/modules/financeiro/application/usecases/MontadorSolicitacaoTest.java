package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.mappers.FormularioMapper;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.SolicitacaoPagamento;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MontadorSolicitacaoTest {

    @Mock private FormularioMapper formularioMapper;
    @Mock private ResolvedorReferencias resolvedor;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ColaboradorApi colaboradorApi;
    @Mock private DocumentoFinanceiroRepository documentoRepository;
    @Mock private MontadorDocumento montadorDocumento;
    @Mock private FormularioSolicitacao formulario;
    @Mock private NucleoSolicitacao nucleo;

    private MontadorSolicitacao montador;

    @BeforeEach
    void configurar() {
        montador = new MontadorSolicitacao(
                formularioMapper, resolvedor, usuarioApi, colaboradorApi,
                documentoRepository, montadorDocumento);
        when(formulario.camposOrdenados()).thenReturn(List.of());
    }

    @Test
    void compraDeveRetornarNomeDoColaboradorVinculadoAoSolicitante() {
        UUID usuarioId = UUID.randomUUID();
        SolicitacaoCompra compra = mock(SolicitacaoCompra.class);
        prepararSolicitacao(compra, usuarioId);
        when(colaboradorApi.buscarNomeCompletoPorUsuario(usuarioId)).thenReturn(Optional.of("MARIA SILVA"));

        var response = montador.montar(compra, List.of());

        assertEquals("MARIA SILVA", response.nomeColaboradorSolicitante());
    }

    @Test
    void pagamentoDeveRetornarNomeDoColaboradorVinculadoAoSolicitante() {
        UUID usuarioId = UUID.randomUUID();
        SolicitacaoPagamento pagamento = mock(SolicitacaoPagamento.class);
        prepararSolicitacao(pagamento, usuarioId);
        when(resolvedor.buscarCompra(null)).thenReturn(Optional.empty());
        when(colaboradorApi.buscarNomeCompletoPorUsuario(usuarioId)).thenReturn(Optional.of("JOAO SOUZA"));

        var response = montador.montar(pagamento, List.of());

        assertEquals("JOAO SOUZA", response.nomeColaboradorSolicitante());
    }

    private void prepararSolicitacao(SolicitacaoBase solicitacao, UUID usuarioId) {
        when(solicitacao.getFormulario()).thenReturn(formulario);
        when(solicitacao.getNucleo()).thenReturn(nucleo);
        when(solicitacao.getUsuarioSolicitanteId()).thenReturn(usuarioId);
        when(solicitacao.getStatus()).thenReturn(StatusSolicitacao.RASCUNHO);
    }
}
