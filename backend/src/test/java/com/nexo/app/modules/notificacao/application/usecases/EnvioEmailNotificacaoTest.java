package com.nexo.app.modules.notificacao.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;
import com.nexo.app.modules.notificacao.application.events.DestinatarioNotificacao;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.LogEmail;
import com.nexo.app.modules.notificacao.domain.OrigemNotificacao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.modules.notificacao.domain.StatusEnvioEmail;
import com.nexo.app.modules.notificacao.infrastructure.persistence.LogEmailRepository;
import com.nexo.app.shared.infrastructure.email.MensagemEmail;
import com.nexo.app.shared.infrastructure.email.ProvedorEmail;
import com.nexo.app.shared.infrastructure.email.ResultadoEnvioEmail;
import com.nexo.app.shared.infrastructure.email.ServicoEmail;

class EnvioEmailNotificacaoTest {

    private UsuarioApi usuarioApi;
    private LogEmailRepository logRepository;
    private ServicoEmail servicoEmail;
    private MontadorEmailNotificacao montador;
    private EnvioEmailNotificacao envio;

    @BeforeEach
    void setUp() {
        usuarioApi = mock(UsuarioApi.class);
        logRepository = mock(LogEmailRepository.class);
        servicoEmail = mock(ServicoEmail.class);
        montador = mock(MontadorEmailNotificacao.class);

        when(montador.montar(any(Set.class), any(NotificacaoResponse.class), any()))
                .thenAnswer(inv -> {
                    Set<String> dests = inv.getArgument(0);
                    return new MensagemEmail(dests, "Assunto", "<html>corpo</html>", "texto", List.of());
                });

        envio = new EnvioEmailNotificacao(usuarioApi, logRepository, montador, servicoEmail);

        when(logRepository.save(any(LogEmail.class))).thenAnswer(inv -> inv.getArgument(0));
        when(logRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private NotificacaoResponse criarNotificacao(UUID id, String titulo, String corpo) {
        return new NotificacaoResponse(
                id,
                OrigemNotificacao.SISTEMA,
                null,
                CategoriaNotificacao.SISTEMA,
                "Sistema",
                PrioridadeNotificacao.NORMAL,
                "Normal",
                titulo,
                corpo,
                null,
                Map.of(),
                false,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("Consolida múltiplos destinatários em um único envio de e-mail")
    void consolidaMultiplosDestinatariosEmUmUnicoEmail() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        List<DestinatarioNotificacao> destinatarios = List.of(
                new DestinatarioNotificacao(u1, notifId),
                new DestinatarioNotificacao(u2, notifId),
                new DestinatarioNotificacao(u3, notifId)
        );

        when(usuarioApi.buscarEmailsPorIds(Set.of(u1, u2, u3))).thenReturn(Map.of(
                u1, "u1@exemplo.com.br",
                u2, "u2@exemplo.com.br",
                u3, "u3@exemplo.com.br"
        ));

        when(servicoEmail.enviar(any(MensagemEmail.class)))
                .thenReturn(ResultadoEnvioEmail.entregue(ProvedorEmail.RESEND, "re_abc123"));

        NotificacaoResponse notif = criarNotificacao(notifId, "Alerta Pendências", "Existem 5 solicitações");

        envio.enviar(destinatarios, notif, "financeiro.solicitacoes_pendentes");

        ArgumentCaptor<MensagemEmail> msgCaptor = ArgumentCaptor.forClass(MensagemEmail.class);
        verify(servicoEmail, times(1)).enviar(msgCaptor.capture());

        MensagemEmail msgEnviada = msgCaptor.getValue();
        assertThat(msgEnviada.destinatarios()).containsExactlyInAnyOrder(
                "u1@exemplo.com.br", "u2@exemplo.com.br", "u3@exemplo.com.br"
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LogEmail>> logsCaptor = ArgumentCaptor.forClass(List.class);
        verify(logRepository, times(2)).saveAll(logsCaptor.capture());

        List<LogEmail> logsFinais = logsCaptor.getAllValues().get(1);
        assertThat(logsFinais).hasSize(3);
        assertThat(logsFinais).allMatch(l -> "re_abc123".equals(l.getIdExterno()));
        assertThat(logsFinais).allMatch(l -> l.getStatus() == StatusEnvioEmail.ENVIADO);
    }

    @Test
    @DisplayName("Particiona envios quando a quantidade de destinatários ultrapassa 50 (limite do Resend)")
    void particionaEnviosQuandoUltrapassaLimite() {
        List<DestinatarioNotificacao> destinatarios = new ArrayList<>();
        Map<UUID, String> emails = new HashMap<>();

        for (int i = 0; i < 55; i++) {
            UUID id = UUID.randomUUID();
            destinatarios.add(new DestinatarioNotificacao(id, UUID.randomUUID()));
            emails.put(id, "usuario" + i + "@exemplo.com.br");
        }

        when(usuarioApi.buscarEmailsPorIds(emails.keySet())).thenReturn(emails);
        when(servicoEmail.enviar(any(MensagemEmail.class)))
                .thenReturn(ResultadoEnvioEmail.entregue(ProvedorEmail.RESEND, "re_batch"));

        NotificacaoResponse notif = criarNotificacao(UUID.randomUUID(), "Comunicado Geral", "Mensagem importante");

        envio.enviar(destinatarios, notif, "comunicado.geral");

        // 55 destinatários devem gerar 2 chamadas: uma com 50 e outra com 5
        ArgumentCaptor<MensagemEmail> captor = ArgumentCaptor.forClass(MensagemEmail.class);
        verify(servicoEmail, times(2)).enviar(captor.capture());

        List<MensagemEmail> msgs = captor.getAllValues();
        assertThat(msgs.get(0).destinatarios()).hasSize(50);
        assertThat(msgs.get(1).destinatarios()).hasSize(5);
    }

    @Test
    @DisplayName("Ignora destinatários sem e-mail cadastrado")
    void ignoraDestinatariosSemEmail() {
        UUID comEmail = UUID.randomUUID();
        UUID semEmail = UUID.randomUUID();

        List<DestinatarioNotificacao> destinatarios = List.of(
                new DestinatarioNotificacao(comEmail, UUID.randomUUID()),
                new DestinatarioNotificacao(semEmail, UUID.randomUUID())
        );

        when(usuarioApi.buscarEmailsPorIds(Set.of(comEmail, semEmail))).thenReturn(Map.of(
                comEmail, "com@exemplo.com.br"
        ));

        when(servicoEmail.enviar(any(MensagemEmail.class)))
                .thenReturn(ResultadoEnvioEmail.entregue(ProvedorEmail.RESEND, "re_ok"));

        NotificacaoResponse notif = criarNotificacao(UUID.randomUUID(), "Teste", "Texto");

        envio.enviar(destinatarios, notif, "regra.teste");

        ArgumentCaptor<MensagemEmail> captor = ArgumentCaptor.forClass(MensagemEmail.class);
        verify(servicoEmail, times(1)).enviar(captor.capture());
        assertThat(captor.getValue().destinatarios()).containsExactly("com@exemplo.com.br");
    }

    @Test
    @DisplayName("Registra falha para todos os destinatários do lote se o provedor falhar")
    void registraFalhaParaTodosOsDestinatariosDoLoteQuandoEnvioFalha() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        List<DestinatarioNotificacao> destinatarios = List.of(
                new DestinatarioNotificacao(u1, UUID.randomUUID()),
                new DestinatarioNotificacao(u2, UUID.randomUUID())
        );

        when(usuarioApi.buscarEmailsPorIds(Set.of(u1, u2))).thenReturn(Map.of(
                u1, "u1@exemplo.com.br",
                u2, "u2@exemplo.com.br"
        ));

        when(servicoEmail.enviar(any(MensagemEmail.class)))
                .thenReturn(ResultadoEnvioEmail.falhou(ProvedorEmail.RESEND, "API Key inválida"));

        NotificacaoResponse notif = criarNotificacao(UUID.randomUUID(), "Título", "Corpo");

        envio.enviar(destinatarios, notif, "gatilho.falha");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LogEmail>> logsCaptor = ArgumentCaptor.forClass(List.class);
        verify(logRepository, times(2)).saveAll(logsCaptor.capture());

        List<LogEmail> logsFinais = logsCaptor.getAllValues().get(1);
        assertThat(logsFinais).hasSize(2);
        assertThat(logsFinais).allMatch(l -> l.getStatus() == StatusEnvioEmail.FALHOU);
        assertThat(logsFinais).allMatch(l -> "API Key inválida".equals(l.getUltimoErro()));
    }

    @Test
    @DisplayName("Com links de decisão pessoais, cada destinatário recebe a própria mensagem")
    void acoesPessoaisSaemUmEmailPorDestinatario() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        when(usuarioApi.buscarEmailsPorIds(Set.of(u1, u2))).thenReturn(Map.of(
                u1, "u1@exemplo.com.br", u2, "u2@exemplo.com.br"));
        when(montador.exigeEnvioIndividual("financeiro.solicitacao_submetida")).thenReturn(true);
        when(montador.montarPara(any(String.class), any(UUID.class), any(NotificacaoResponse.class), any()))
                .thenAnswer(inv -> new MensagemEmail(Set.of((String) inv.getArgument(0)), "Assunto",
                        "<html>corpo</html>", "texto", List.of()));
        when(servicoEmail.enviar(any(MensagemEmail.class)))
                .thenReturn(ResultadoEnvioEmail.entregue(ProvedorEmail.RESEND, "re_1"));

        envio.enviar(List.of(new DestinatarioNotificacao(u1, notifId), new DestinatarioNotificacao(u2, notifId)),
                criarNotificacao(notifId, "Compra nº 8 aguardando aprovação", "Pneus"),
                "financeiro.solicitacao_submetida");

        verify(montador).montarPara(eq("u1@exemplo.com.br"), eq(u1), any(NotificacaoResponse.class), any());
        verify(montador).montarPara(eq("u2@exemplo.com.br"), eq(u2), any(NotificacaoResponse.class), any());
        verify(montador, never()).montar(any(Set.class), any(NotificacaoResponse.class), any());
        verify(servicoEmail, times(2)).enviar(any(MensagemEmail.class));
    }
}
