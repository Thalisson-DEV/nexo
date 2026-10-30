package com.nexo.app.modules.chamado.application.usecases;

import com.nexo.app.modules.chamado.application.dto.AtualizarAtendimentoChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.ChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.InteracaoChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.InteracaoChamadoResponse;
import com.nexo.app.modules.chamado.application.mappers.ChamadoMapper;
import com.nexo.app.modules.chamado.domain.Chamado;
import com.nexo.app.modules.chamado.domain.EquipeAtendimento;
import com.nexo.app.modules.chamado.domain.InteracaoChamado;
import com.nexo.app.modules.chamado.domain.PrioridadeChamado;
import com.nexo.app.modules.chamado.domain.StatusChamado;
import com.nexo.app.modules.chamado.domain.TopicoChamado;
import com.nexo.app.modules.chamado.infrastructure.persistence.ChamadoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.mencao.ContextoMencao;
import com.nexo.app.modules.mencao.MencaoApi;
import com.nexo.app.shared.domain.events.ChamadoAtribuidoEvent;
import com.nexo.app.shared.domain.events.ChamadoMensagemRecebidaEvent;
import com.nexo.app.shared.domain.events.ChamadoResolvidoEvent;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChamadoUseCasesTest {

    private final UUID solicitanteId = UUID.randomUUID();

    @Mock
    private ChamadoRepository repository;
    @Mock
    private TopicoChamadoUseCases topicoUseCases;
    @Mock
    private EquipeAtendimentoUseCases equipeUseCases;
    @Mock
    private ChamadoMapper mapper;
    @Mock
    private ObjectStorageService objectStorage;
    @Mock
    private UsuarioApi usuarioApi;
    @Mock
    private MencaoApi mencaoApi;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChamadoUseCases useCases;

    @AfterEach
    void limparContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usuarioComumBuscaDetalheSomentePeloProprioId() {
        UUID usuarioId = UUID.randomUUID();
        UUID chamadoId = UUID.randomUUID();
        autenticar(usuarioId, "chamado:read");
        when(repository.findByIdAndUsuarioSolicitanteId(chamadoId, usuarioId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCases.buscarPorId(chamadoId, usuarioId))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(repository).findByIdAndUsuarioSolicitanteId(chamadoId, usuarioId);
        verify(repository, never()).findById(chamadoId);
    }

    @Test
    void centralPodeBuscarQualquerChamado() {
        UUID usuarioId = UUID.randomUUID();
        UUID chamadoId = UUID.randomUUID();
        autenticar(usuarioId, "chamado:manage");
        Chamado chamado = org.mockito.Mockito.mock(Chamado.class);
        ChamadoResponse response = org.mockito.Mockito.mock(ChamadoResponse.class);
        when(repository.findById(chamadoId)).thenReturn(Optional.of(chamado));
        when(mapper.toResponse(chamado)).thenReturn(response);

        assertThat(useCases.buscarPorId(chamadoId, usuarioId)).isSameAs(response);

        verify(repository).findById(chamadoId);
        verify(repository, never()).findByIdAndUsuarioSolicitanteId(chamadoId, usuarioId);
    }

    @Test
    void usuarioComumNaoDeveReceberComentariosInternosNaTimeline() {
        UUID usuarioId = UUID.randomUUID();
        UUID chamadoId = UUID.randomUUID();
        autenticar(usuarioId, "chamado:read");
        Chamado chamado = mock(Chamado.class);
        InteracaoChamado publica = mock(InteracaoChamado.class);
        InteracaoChamado interna = mock(InteracaoChamado.class);
        InteracaoChamadoResponse response = mock(InteracaoChamadoResponse.class);
        when(publica.isInterno()).thenReturn(false);
        when(interna.isInterno()).thenReturn(true);
        when(chamado.getInteracoes()).thenReturn(List.of(publica, interna));
        when(repository.findByIdAndUsuarioSolicitanteId(chamadoId, usuarioId))
                .thenReturn(Optional.of(chamado));
        when(mapper.toResponse(publica)).thenReturn(response);

        assertThat(useCases.listarInteracoes(chamadoId, usuarioId)).containsExactly(response);

        verify(mapper).toResponse(publica);
        verify(mapper, never()).toResponse(interna);
    }

    // ── Tradução de interações em avisos ────────────────────────────────────────

    @Test
    void atribuirAOutraPessoaAnunciaONovoResponsavel() {
        UUID operadorId = UUID.randomUUID();
        UUID atendenteId = UUID.randomUUID();
        UUID chamadoId = UUID.randomUUID();
        Chamado chamado = chamadoAberto(Set.of(atendenteId));
        when(repository.findById(chamadoId)).thenReturn(Optional.of(chamado));

        useCases.atualizarAtendimento(chamadoId,
                new AtualizarAtendimentoChamadoRequest(null, null, null, atendenteId, null), operadorId);

        var evento = ArgumentCaptor.forClass(ChamadoAtribuidoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().idResponsavel()).isEqualTo(atendenteId);
        assertThat(evento.getValue().protocolo()).isEqualTo(chamado.getProtocolo());
    }

    /**
     * Quem assume o próprio chamado já sabe. O fato continua sendo anunciado — ele
     * aconteceu —, mas o evento não preenche o papel, então nenhuma regra o alcança.
     * É onde mora a diferença entre "registrar o que ocorreu" e "decidir quem ouve".
     */
    @Test
    void atendenteQueAssumeSozinhoNaoOcupaOPapelDeResponsavel() {
        UUID atendenteId = UUID.randomUUID();
        UUID chamadoId = UUID.randomUUID();
        Chamado chamado = chamadoAberto(Set.of(atendenteId));
        when(repository.findById(chamadoId)).thenReturn(Optional.of(chamado));

        useCases.atualizarAtendimento(chamadoId,
                new AtualizarAtendimentoChamadoRequest(null, null, null, atendenteId, null), atendenteId);

        var evento = ArgumentCaptor.forClass(ChamadoAtribuidoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().papeis()).isEmpty();
    }

    @Test
    void mensagemPublicaAnunciaOOutroLadoEEntregaOTextoAsMarcacoes() {
        UUID atendenteId = UUID.randomUUID();
        UUID chamadoId = UUID.randomUUID();
        Chamado chamado = chamadoAberto(Set.of(atendenteId));
        when(repository.findById(chamadoId)).thenReturn(Optional.of(chamado));
        when(usuarioApi.buscarNomePorId(atendenteId)).thenReturn(Optional.of("SUPORTE"));

        useCases.adicionarInteracaoAtendimento(chamadoId,
                new InteracaoChamadoRequest("Já estamos vendo, @thalisson acompanha.", false), atendenteId);

        var evento = ArgumentCaptor.forClass(ChamadoMensagemRecebidaEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().autor()).isEqualTo("SUPORTE");
        assertThat(evento.getValue().idSolicitante()).isEqualTo(solicitanteId);

        var texto = ArgumentCaptor.forClass(String.class);
        verify(mencaoApi).registrar(any(ContextoMencao.class), texto.capture());
        assertThat(texto.getValue()).contains("@thalisson");
    }

    /**
     * Comentário interno não é conversa com o solicitante — mas a marcação dentro dele
     * continua sendo dirigida a alguém.
     */
    @Test
    void comentarioInternoNaoViraMensagemMasAindaRegistraMarcacoes() {
        UUID atendenteId = UUID.randomUUID();
        UUID chamadoId = UUID.randomUUID();
        Chamado chamado = chamadoAberto(Set.of(atendenteId));
        when(repository.findById(chamadoId)).thenReturn(Optional.of(chamado));

        useCases.adicionarInteracaoAtendimento(chamadoId,
                new InteracaoChamadoRequest("@thalisson confere o log antes de responder.", true),
                atendenteId);

        verify(eventPublisher, never()).publishEvent(any(ChamadoMensagemRecebidaEvent.class));

        var contexto = ArgumentCaptor.forClass(ContextoMencao.class);
        verify(mencaoApi).registrar(contexto.capture(), any());
        assertThat(contexto.getValue().visibilidadeRestrita()).isTrue();
    }

    @Test
    void mudancaDeStatusSoAnunciaResolucaoQuandoOChamadoEhResolvido() {
        UUID atendenteId = UUID.randomUUID();
        UUID chamadoId = UUID.randomUUID();
        Chamado chamado = chamadoAberto(Set.of(atendenteId));
        when(repository.findById(chamadoId)).thenReturn(Optional.of(chamado));

        useCases.atualizarAtendimento(chamadoId,
                new AtualizarAtendimentoChamadoRequest(StatusChamado.EM_ATENDIMENTO, null, null, null, null),
                atendenteId);
        verify(eventPublisher, never()).publishEvent(any(ChamadoResolvidoEvent.class));

        useCases.atualizarAtendimento(chamadoId,
                new AtualizarAtendimentoChamadoRequest(StatusChamado.RESOLVIDO, null, null, null,
                        "Senha redefinida."), atendenteId);

        var evento = ArgumentCaptor.forClass(ChamadoResolvidoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().solucao()).isEqualTo("Senha redefinida.");
        assertThat(evento.getValue().idSolicitante()).isEqualTo(solicitanteId);
    }

    private Chamado chamadoAberto(Set<UUID> membrosDaEquipe) {
        EquipeAtendimento equipe = new EquipeAtendimento("CENTRAL", "Equipe CENTRAL",
                "Equipe de atendimento para testes.", membrosDaEquipe);
        TopicoChamado topico = new TopicoChamado("ACESSO_SENHA", "Acesso e senha",
                "Login, senha e autenticação.", PrioridadeChamado.NORMAL,
                equipe, 2, 8, true, 72, 10);
        Chamado chamado = new Chamado(topico, "Falha no acesso",
                "O sistema informa credenciais inválidas desde hoje.",
                PrioridadeChamado.ALTA, solicitanteId);
        // A abertura já enfileirou uma interação; o teste só se interessa pelo que vem
        // depois dela.
        chamado.drenarNovasInteracoes();
        return chamado;
    }

    private void autenticar(UUID usuarioId, String autoridade) {
        var authentication = new UsernamePasswordAuthenticationToken(
                usuarioId.toString(), null, List.of(new SimpleGrantedAuthority(autoridade)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
