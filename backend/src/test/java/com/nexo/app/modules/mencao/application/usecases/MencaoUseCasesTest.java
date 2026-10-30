package com.nexo.app.modules.mencao.application.usecases;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.nexo.app.modules.iam.ResumoUsuario;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.mencao.ContextoMencao;
import com.nexo.app.modules.mencao.domain.Mencao;
import com.nexo.app.modules.mencao.domain.OrigemMencao;
import com.nexo.app.modules.mencao.infrastructure.persistence.MencaoRepository;
import com.nexo.app.shared.domain.events.UsuarioMencionadoEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MencaoUseCasesTest {

    private static final UUID AUTOR = UUID.randomUUID();
    private static final UUID THALISSON = UUID.randomUUID();
    private static final UUID MARIA = UUID.randomUUID();

    @Mock
    private MencaoRepository repository;
    @Mock
    private UsuarioApi usuarioApi;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MencaoUseCases useCases;

    @Test
    void reconheceMarcacaoNoMeioDaFraseEAnunciaUmaVezSo() {
        when(usuarioApi.buscarIdsAtivosPorLogins(List.of("THALISSON")))
                .thenReturn(Map.of("THALISSON", THALISSON));
        when(usuarioApi.buscarNomePorId(AUTOR)).thenReturn(Optional.of("CARLOS"));
        when(repository.findByOrigemAndIdOrigem(any(), any())).thenReturn(List.of());

        List<UUID> marcados = useCases.registrar(
                contexto(false), "@thalisson o processo fica a cargo de você agora.");

        assertThat(marcados).containsExactly(THALISSON);
        verify(repository).save(any(Mencao.class));

        var evento = ArgumentCaptor.forClass(UsuarioMencionadoEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        assertThat(evento.getValue().mencionados()).containsExactly(THALISSON);
        assertThat(evento.getValue().autor()).isEqualTo("CARLOS");
        assertThat(evento.getValue().trecho()).contains("a cargo de você");
    }

    /**
     * A âncora à esquerda do padrão existe para isto: sem ela, todo endereço de e-mail
     * escrito num chamado viraria uma tentativa de marcação.
     */
    @Test
    void naoConfundeEnderecoDeEmailComMarcacao() {
        List<UUID> marcados = useCases.registrar(
                contexto(false), "Encaminhei para suporte@fornecedor.com ontem.");

        assertThat(marcados).isEmpty();
        verify(usuarioApi, never()).buscarIdsAtivosPorLogins(any());
        verify(eventPublisher, never()).publishEvent(any(UsuarioMencionadoEvent.class));
    }

    /** Um @ que não corresponde a ninguém é erro de digitação, não falha de operação. */
    @Test
    void ignoraLoginInexistenteSemDerrubarAMensagem() {
        when(usuarioApi.buscarIdsAtivosPorLogins(List.of("NINGUEM"))).thenReturn(Map.of());

        assertThat(useCases.registrar(contexto(false), "@ninguem pode ajudar?")).isEmpty();

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(UsuarioMencionadoEvent.class));
    }

    @Test
    void marcarASiMesmoNaoNotificaNinguem() {
        when(usuarioApi.buscarIdsAtivosPorLogins(List.of("CARLOS")))
                .thenReturn(Map.of("CARLOS", AUTOR));

        assertThat(useCases.registrar(contexto(false), "Assumindo eu mesmo, @carlos.")).isEmpty();

        verify(eventPublisher, never()).publishEvent(any(UsuarioMencionadoEvent.class));
    }

    @Test
    void recusaMarcacaoEmTextoRestritoParaQuemNaoPodeLer() {
        when(usuarioApi.buscarIdsAtivosPorLogins(List.of("MARIA")))
                .thenReturn(Map.of("MARIA", MARIA));
        when(usuarioApi.buscarIdsAtivosComPermissao("chamado:manage")).thenReturn(List.of(THALISSON));

        assertThatThrownBy(() -> useCases.registrar(contexto(true), "@maria, veja isto."))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("@MARIA");

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(UsuarioMencionadoEvent.class));
    }

    @Test
    void aceitaMarcacaoEmTextoRestritoParaQuemPodeLer() {
        when(usuarioApi.buscarIdsAtivosPorLogins(List.of("THALISSON")))
                .thenReturn(Map.of("THALISSON", THALISSON));
        when(usuarioApi.buscarIdsAtivosComPermissao("chamado:manage")).thenReturn(List.of(THALISSON));
        when(usuarioApi.buscarNomePorId(AUTOR)).thenReturn(Optional.of("CARLOS"));
        when(repository.findByOrigemAndIdOrigem(any(), any())).thenReturn(List.of());

        assertThat(useCases.registrar(contexto(true), "@thalisson, interno."))
                .containsExactly(THALISSON);

        verify(eventPublisher).publishEvent(any(UsuarioMencionadoEvent.class));
    }

    /**
     * Duas marcações da mesma pessoa na mesma frase são um fato só — e a linha já
     * existente não pode virar uma segunda.
     */
    @Test
    void naoRegistraDuasVezesAMesmaPessoaNoMesmoTexto() {
        when(usuarioApi.buscarIdsAtivosPorLogins(List.of("THALISSON", "THALISSON")))
                .thenReturn(Map.of("THALISSON", THALISSON));
        when(usuarioApi.buscarNomePorId(AUTOR)).thenReturn(Optional.of("CARLOS"));
        when(repository.findByOrigemAndIdOrigem(any(), any())).thenReturn(List.of());

        assertThat(useCases.registrar(contexto(false), "@thalisson e de novo @thalisson"))
                .containsExactly(THALISSON);

        verify(repository).save(any(Mencao.class));
    }

    @Test
    void sugestaoEmTextoRestritoOmiteQuemNaoPodeLer() {
        lenient().when(usuarioApi.buscarAtivosPorTrechoDoLogin("th", 40)).thenReturn(List.of(
                new ResumoUsuario(THALISSON, "THALISSON"),
                new ResumoUsuario(MARIA, "THAIS")));
        when(usuarioApi.buscarIdsAtivosComPermissao("chamado:manage")).thenReturn(List.of(THALISSON));

        var sugestoes = useCases.sugerir("@th", OrigemMencao.CHAMADO_INTERACAO, true);

        assertThat(sugestoes).extracting(u -> u.login()).containsExactly("THALISSON");
    }

    private static ContextoMencao contexto(boolean restrito) {
        return new ContextoMencao(
                OrigemMencao.CHAMADO_INTERACAO,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "chamado CH-20260805-ABC – Impressora não liga",
                "/chamados/abc",
                AUTOR,
                restrito);
    }
}
