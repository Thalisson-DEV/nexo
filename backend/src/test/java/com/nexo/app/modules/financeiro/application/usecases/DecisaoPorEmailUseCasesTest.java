package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.AprovacaoRequest;
import com.nexo.app.modules.financeiro.application.dto.DecisaoEmailRequest;
import com.nexo.app.modules.financeiro.application.dto.DecisaoEmailResponse.Situacao;
import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.DecisaoPorEmail;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.domain.ValoresCanonicos;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DecisaoPorEmailRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O link de decisão do e-mail: credencial de uso único, conferida de novo no uso. */
@ExtendWith(MockitoExtension.class)
class DecisaoPorEmailUseCasesTest {

    private static final UUID APROVADOR = UUID.randomUUID();
    private static final Set<String> COM_ALCADA = Set.of("financeiro:compra:aprovar");

    @Mock private DecisaoPorEmailRepository repository;
    @Mock private FichaSolicitacaoFinanceira ficha;
    @Mock private SolicitacaoCompraUseCases compraUseCases;
    @Mock private SolicitacaoPagamentoUseCases pagamentoUseCases;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ColaboradorApi colaboradorApi;

    private DecisaoPorEmailUseCases useCases;
    private SolicitacaoCompra compra;

    @BeforeEach
    void configurar() {
        useCases = new DecisaoPorEmailUseCases(repository, ficha, compraUseCases, pagamentoUseCases,
                usuarioApi, colaboradorApi);
        var formulario = new FormularioSolicitacao(TipoSolicitacao.COMPRA, "PADRAO", "Compra", null, true);
        compra = new SolicitacaoCompra(formulario, NucleoSolicitacao.de(new ValoresCanonicos(Map.of(
                CampoCanonico.VALOR, new BigDecimal("123.00")))), UUID.randomUUID());
        ReflectionTestUtils.setField(compra, "id", UUID.randomUUID());
        compra.submeter();
        lenient().when(ficha.buscar(compra.getId())).thenReturn(Optional.of(compra));
        lenient().when(ficha.montar(any())).thenReturn(List.of());
        lenient().when(usuarioApi.buscarPermissoes(APROVADOR)).thenReturn(COM_ALCADA);
        lenient().when(usuarioApi.buscarNomePorId(APROVADOR)).thenReturn(Optional.of("diretora"));
        lenient().when(colaboradorApi.buscarNomeCompletoPorUsuario(APROVADOR)).thenReturn(Optional.of("Carla Diretora"));
    }

    /** Emite um link e devolve o token, guardando o registro que o repositório receberia. */
    private String emitir(AtomicReference<DecisaoPorEmail> salvo) {
        when(repository.save(any(DecisaoPorEmail.class))).thenAnswer(inv -> {
            salvo.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        String token = useCases.emitir(compra, APROVADOR);
        lenient().when(repository.findByHashToken(DecisaoPorEmailUseCases.hash(token)))
                .thenAnswer(inv -> Optional.of(salvo.get()));
        return token;
    }

    @Test
    @DisplayName("Guarda só o hash do token, com 7 dias de validade")
    void guardaSoOHash() {
        var salvo = new AtomicReference<DecisaoPorEmail>();
        String token = emitir(salvo);

        assertThat(token).hasSizeGreaterThanOrEqualTo(43);
        assertThat(salvo.get().getHashToken()).isEqualTo(DecisaoPorEmailUseCases.hash(token)).isNotEqualTo(token);
        assertThat(salvo.get().getExpiraEm()).isEqualTo(salvo.get().getCriadoEm().plusDays(7));
    }

    @Test
    @DisplayName("Aprova como o dono do link e não deixa usar o link de novo")
    void aprovaUmaVezSo() {
        var salvo = new AtomicReference<DecisaoPorEmail>();
        String token = emitir(salvo);
        var usuarioNaDecisao = new AtomicReference<UUID>();
        doAnswer(inv -> {
            usuarioNaDecisao.set(SecurityUtils.getUsuarioLogadoId());
            compra.aprovar(APROVADOR, new BigDecimal("110.00"), "ok");
            return null;
        }).when(compraUseCases).aprovar(eq(compra.getId()), any(AprovacaoRequest.class), eq(APROVADOR));

        var resposta = useCases.decidir(token,
                new DecisaoEmailRequest(DecisaoPorEmail.Acao.APROVAR, new BigDecimal("110.00"), "ok"));

        assertThat(usuarioNaDecisao.get()).isEqualTo(APROVADOR);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(resposta.situacao()).isEqualTo(Situacao.DECIDIDA_POR_ESTE_LINK);
        assertThatThrownBy(() -> useCases.decidir(token,
                new DecisaoEmailRequest(DecisaoPorEmail.Acao.REJEITAR, null, "de novo")))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    @DisplayName("Não decide o que outro diretor já decidiu")
    void naoDecideOQueJaFoiDecidido() {
        var salvo = new AtomicReference<DecisaoPorEmail>();
        String token = emitir(salvo);
        compra.rejeitar(UUID.randomUUID(), "Fora do orçamento");

        assertThat(useCases.consultar(token).situacao()).isEqualTo(Situacao.JA_DECIDIDA);
        assertThatThrownBy(() -> useCases.decidir(token,
                new DecisaoEmailRequest(DecisaoPorEmail.Acao.APROVAR, null, null)))
                .isInstanceOf(InvarianteVioladaException.class);
        verify(compraUseCases, never()).aprovar(any(), any(), any());
    }

    @Test
    @DisplayName("Link vencido ou de quem perdeu a alçada não decide")
    void expiradoESemAlcada() {
        var salvo = new AtomicReference<DecisaoPorEmail>();
        String token = emitir(salvo);

        when(usuarioApi.buscarPermissoes(APROVADOR)).thenReturn(Set.of());
        assertThat(useCases.consultar(token).situacao()).isEqualTo(Situacao.SEM_PERMISSAO);

        ReflectionTestUtils.setField(salvo.get(), "expiraEm", OffsetDateTime.now().minusMinutes(1));
        assertThat(useCases.consultar(token).situacao()).isEqualTo(Situacao.EXPIRADO);
        assertThatThrownBy(() -> useCases.decidir(token,
                new DecisaoEmailRequest(DecisaoPorEmail.Acao.APROVAR, null, null)))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    @DisplayName("Link inexistente não revela nada")
    void linkInexistente() {
        when(repository.findByHashToken(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCases.consultar("qualquer-coisa"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Só ganha botões quem tem alçada")
    void botoesSoComAlcada() {
        assertThat(useCases.podeDecidir(APROVADOR, TipoSolicitacao.COMPRA)).isTrue();
        assertThat(useCases.podeDecidir(APROVADOR, TipoSolicitacao.PAGAMENTO)).isFalse();
        assertThat(useCases.podeDecidir(null, TipoSolicitacao.COMPRA)).isFalse();
    }

    @Test
    @DisplayName("O registro salvo é o do destinatário e da solicitação")
    void registroDoDestinatario() {
        ArgumentCaptor<DecisaoPorEmail> captor = ArgumentCaptor.forClass(DecisaoPorEmail.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        useCases.emitir(compra, APROVADOR);

        assertThat(captor.getValue().getIdUsuario()).isEqualTo(APROVADOR);
        assertThat(captor.getValue().getIdSolicitacao()).isEqualTo(compra.getId());
        assertThat(captor.getValue().usado()).isFalse();
    }
}
