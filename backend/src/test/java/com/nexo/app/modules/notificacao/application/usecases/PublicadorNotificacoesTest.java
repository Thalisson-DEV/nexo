package com.nexo.app.modules.notificacao.application.usecases;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.nexo.app.modules.notificacao.application.dto.NovaNotificacao;
import com.nexo.app.modules.notificacao.application.dto.PreferenciaNotificacaoResponse;
import com.nexo.app.modules.notificacao.application.events.DestinatarioNotificacao;
import com.nexo.app.modules.notificacao.application.events.EmailSolicitadoEvent;
import com.nexo.app.modules.notificacao.application.events.NotificacaoEntregueEvent;
import com.nexo.app.modules.notificacao.application.mappers.NotificacaoMapper;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.Notificacao;
import com.nexo.app.modules.notificacao.domain.OrigemNotificacao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.modules.notificacao.infrastructure.persistence.NotificacaoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicadorNotificacoesTest {

    @Mock
    private NotificacaoRepository repository;
    @Mock
    private PreferenciaNotificacaoUseCases preferencias;
    @Mock
    private NotificacaoMapper mapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PublicadorNotificacoes publicador;

    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();

    @Test
    void deveGravarUmaNotificacaoPorDestinatario() {
        liberarPreferencias(ana, bruno);
        when(repository.save(any(Notificacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        int entregues = publicador.publicar(comando(Set.of(ana, bruno), PrioridadeNotificacao.ALTA));

        assertThat(entregues).isEqualTo(2);
        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notificacao::getIdUsuario)
                .containsExactlyInAnyOrder(ana, bruno);
        assertThat(captor.getAllValues()).allSatisfy(notificacao ->
                assertThat(notificacao.getChaveIdempotencia()).isEqualTo("desconto:1042:pendente"));
    }

    @Test
    void naoDeveEntregarDuasVezesOMesmoFatoAoMesmoUsuario() {
        liberarPreferencias(ana);
        when(repository.existsByIdUsuarioAndChaveIdempotencia(ana, "desconto:1042:pendente")).thenReturn(true);

        int entregues = publicador.publicar(comando(Set.of(ana), PrioridadeNotificacao.ALTA));

        assertThat(entregues).isZero();
        verify(repository, never()).save(any(Notificacao.class));
        verify(eventPublisher, never()).publishEvent(any(NotificacaoEntregueEvent.class));
    }

    @Test
    void deveRespeitarCategoriaSilenciadaPeloUsuario() {
        silenciar(ana, CategoriaNotificacao.APROVACAO);
        liberarPreferencias(bruno);
        when(repository.save(any(Notificacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        int entregues = publicador.publicar(comando(Set.of(ana, bruno), PrioridadeNotificacao.ALTA));

        assertThat(entregues).isEqualTo(1);
        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getIdUsuario()).isEqualTo(bruno);
    }

    @Test
    void prioridadeCriticaDeveFurarOSilenciamento() {
        silenciar(ana, CategoriaNotificacao.APROVACAO);
        when(repository.save(any(Notificacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        int entregues = publicador.publicar(comando(Set.of(ana), PrioridadeNotificacao.CRITICA));

        assertThat(entregues).isEqualTo(1);
        // Nem consulta as preferências: crítica é entregue por definição.
        verify(preferencias, never()).listar(eq(ana));
    }

    @Test
    void devePublicarEventoDePushParaCadaEntrega() {
        liberarPreferencias(ana);
        when(repository.save(any(Notificacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        publicador.publicar(comando(Set.of(ana), PrioridadeNotificacao.NORMAL));

        ArgumentCaptor<NotificacaoEntregueEvent> captor =
                ArgumentCaptor.forClass(NotificacaoEntregueEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().idUsuario()).isEqualTo(ana);
    }

    @Test
    void devePublicarEmailConsolidadoParaMultiplosDestinatarios() {
        liberarPreferencias(ana, bruno);
        when(repository.save(any(Notificacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        NovaNotificacao comando = NovaNotificacao.builder()
                .destinatarios(Set.of(ana, bruno))
                .origem(OrigemNotificacao.REGRA)
                .categoria(CategoriaNotificacao.APROVACAO)
                .prioridade(PrioridadeNotificacao.NORMAL)
                .titulo("Solicitações financeiras pendentes")
                .corpo("Há solicitações aguardando decisão.")
                .deeplink("/financeiro/solicitacoes")
                .chaveIdempotencia("financeiro:pendentes:lote")
                .email(true)
                .chaveGatilho("financeiro.solicitacoes_pendentes")
                .build();

        int entregues = publicador.publicar(comando);

        assertThat(entregues).isEqualTo(2);
        ArgumentCaptor<EmailSolicitadoEvent> captorEmail = ArgumentCaptor.forClass(EmailSolicitadoEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captorEmail.capture());
        EmailSolicitadoEvent evento = captorEmail.getValue();
        assertThat(evento.destinatarios()).extracting(DestinatarioNotificacao::idUsuario)
                .containsExactlyInAnyOrder(ana, bruno);
        assertThat(evento.chaveGatilho()).isEqualTo("financeiro.solicitacoes_pendentes");
    }

    @Test
    void devePublicarEmailApenasParaDestinatariosQueAceitamEmail() {
        liberarPreferencias(ana);
        silenciar(bruno, CategoriaNotificacao.APROVACAO);
        // bruno tem preferência in-app false e email false para APROVACAO
        when(repository.save(any(Notificacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        NovaNotificacao comando = NovaNotificacao.builder()
                .destinatarios(Set.of(ana, bruno))
                .origem(OrigemNotificacao.REGRA)
                .categoria(CategoriaNotificacao.APROVACAO)
                .prioridade(PrioridadeNotificacao.ALTA)
                .titulo("Aviso urgente")
                .corpo("Corpo")
                .chaveIdempotencia("aviso:1")
                .email(true)
                .build();

        publicador.publicar(comando);

        ArgumentCaptor<EmailSolicitadoEvent> captorEmail = ArgumentCaptor.forClass(EmailSolicitadoEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captorEmail.capture());
        assertThat(captorEmail.getValue().destinatarios()).singleElement()
                .satisfies(d -> assertThat(d.idUsuario()).isEqualTo(ana));
    }

    @Test
    void contextoComValorNuloDeveSerAceito() {
        // Regressão: Map.copyOf rejeita nulos, e um campo vazio no contexto é legítimo
        // (nem todo registro traz todos os dados). O comando quebrava antes de chegar
        // ao banco, e a falha era engolida pelo isolamento por regra no motor.
        java.util.Map<String, Object> contexto = new java.util.HashMap<>();
        contexto.put("motivo", "Dano em EPI");
        contexto.put("dataOcorrencia", null);
        liberarPreferencias(ana);
        when(repository.save(any(Notificacao.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        NovaNotificacao comando = NovaNotificacao.builder()
                .destinatarios(Set.of(ana))
                .origem(OrigemNotificacao.REGRA)
                .categoria(CategoriaNotificacao.APROVACAO)
                .titulo("Desconto registrado")
                .corpo("Seu desconto foi registrado.")
                .contexto(contexto)
                .chaveIdempotencia("desconto:1:solicitado")
                .build();

        assertThat(publicador.publicar(comando)).isEqualTo(1);
        assertThat(comando.contexto()).containsEntry("dataOcorrencia", null);
    }

    @Test
    void deveRecusarComandoSemDestinatarios() {
        assertThatThrownBy(() -> comando(Set.of(), PrioridadeNotificacao.NORMAL))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("destinatário");
    }

    private NovaNotificacao comando(Set<UUID> destinatarios, PrioridadeNotificacao prioridade) {
        return NovaNotificacao.builder()
                .destinatarios(destinatarios)
                .origem(OrigemNotificacao.REGRA)
                .categoria(CategoriaNotificacao.APROVACAO)
                .prioridade(prioridade)
                .titulo("Desconto pendente de aprovação")
                .corpo("A solicitação 1042 aguarda sua análise.")
                .deeplink("/descontos/1042")
                .chaveIdempotencia("desconto:1042:pendente")
                .build();
    }

    private void liberarPreferencias(UUID... usuarios) {
        for (UUID usuario : usuarios) {
            when(preferencias.listar(usuario)).thenReturn(todasLigadas());
            when(repository.existsByIdUsuarioAndChaveIdempotencia(eq(usuario), anyString())).thenReturn(false);
        }
    }

    private void silenciar(UUID usuario, CategoriaNotificacao silenciada) {
        List<PreferenciaNotificacaoResponse> preferenciasDoUsuario = todasLigadas().stream()
                .map(p -> p.categoria() == silenciada
                        ? new PreferenciaNotificacaoResponse(p.categoria(), p.categoriaRotulo(),
                                false, false, p.categoria().permiteEmail())
                        : p)
                .toList();
        when(preferencias.listar(usuario)).thenReturn(preferenciasDoUsuario);
        when(repository.existsByIdUsuarioAndChaveIdempotencia(eq(usuario), anyString())).thenReturn(false);
    }

    private List<PreferenciaNotificacaoResponse> todasLigadas() {
        return java.util.Arrays.stream(CategoriaNotificacao.values())
                .map(categoria -> new PreferenciaNotificacaoResponse(
                        categoria, categoria.getRotulo(), true,
                        categoria.permiteEmail(), categoria.permiteEmail()))
                .toList();
    }
}
