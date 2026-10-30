package com.nexo.app.modules.notificacao.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificacaoTest {

    private final UUID destinatario = UUID.randomUUID();

    @Test
    void deveNascerNaoLidaENaoArquivada() {
        Notificacao notificacao = novaNotificacao(null);

        assertThat(notificacao.isLida()).isFalse();
        assertThat(notificacao.isArquivada()).isFalse();
        assertThat(notificacao.isExpirada()).isFalse();
        assertThat(notificacao.getPrioridade()).isEqualTo(PrioridadeNotificacao.ALTA);
        assertThat(notificacao.getContexto()).containsEntry("numero", 1042);
    }

    @Test
    void marcarLidaDuasVezesDevePreservarOInstanteDaPrimeiraLeitura() {
        Notificacao notificacao = novaNotificacao(null);

        notificacao.marcarLida();
        OffsetDateTime primeiraLeitura = notificacao.getLidaEm();
        notificacao.marcarLida();

        assertThat(notificacao.getLidaEm()).isEqualTo(primeiraLeitura);
    }

    @Test
    void marcarNaoLidaDeveDevolverAoContadorDoSininho() {
        Notificacao notificacao = novaNotificacao(null);
        notificacao.marcarLida();

        notificacao.marcarNaoLida();

        assertThat(notificacao.isLida()).isFalse();
    }

    @Test
    void arquivarDeveImplicarLida() {
        Notificacao notificacao = novaNotificacao(null);

        notificacao.arquivar();

        assertThat(notificacao.isArquivada()).isTrue();
        assertThat(notificacao.isLida())
                .as("uma notificação fora da caixa não pode continuar contando como não lida")
                .isTrue();
    }

    @Test
    void deveReconhecerNotificacaoExpirada() {
        Notificacao vencida = novaNotificacao(OffsetDateTime.now().minusMinutes(1));
        Notificacao vigente = novaNotificacao(OffsetDateTime.now().plusDays(1));

        assertThat(vencida.isExpirada()).isTrue();
        assertThat(vigente.isExpirada()).isFalse();
    }

    @Test
    void deveRecusarNotificacaoSemDestinatario() {
        assertThatThrownBy(() -> new Notificacao(null, OrigemNotificacao.SISTEMA, null, null,
                CategoriaNotificacao.SISTEMA, PrioridadeNotificacao.NORMAL,
                "Título", "Corpo", null, null, "chave", null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("destinatário");
    }

    @Test
    void deveRecusarNotificacaoSemChaveDeIdempotencia() {
        assertThatThrownBy(() -> new Notificacao(destinatario, OrigemNotificacao.SISTEMA, null, null,
                CategoriaNotificacao.SISTEMA, PrioridadeNotificacao.NORMAL,
                "Título", "Corpo", null, null, "  ", null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("idempotência");
    }

    @Test
    void deveRecusarTituloAcimaDoLimiteDaColuna() {
        String titulo = "x".repeat(151);

        assertThatThrownBy(() -> new Notificacao(destinatario, OrigemNotificacao.SISTEMA, null, null,
                CategoriaNotificacao.SISTEMA, PrioridadeNotificacao.NORMAL,
                titulo, "Corpo", null, null, "chave", null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("150");
    }

    @Test
    void apenasPrioridadeCriticaDeveIgnorarPreferencia() {
        assertThat(PrioridadeNotificacao.CRITICA.ignoraPreferencia()).isTrue();
        assertThat(PrioridadeNotificacao.ALTA.ignoraPreferencia()).isFalse();
        assertThat(PrioridadeNotificacao.NORMAL.ignoraPreferencia()).isFalse();
        assertThat(PrioridadeNotificacao.BAIXA.ignoraPreferencia()).isFalse();
    }

    private Notificacao novaNotificacao(OffsetDateTime expiraEm) {
        return new Notificacao(destinatario, OrigemNotificacao.REGRA, UUID.randomUUID(), UUID.randomUUID(),
                CategoriaNotificacao.APROVACAO, PrioridadeNotificacao.ALTA,
                "Desconto pendente de aprovação",
                "A solicitação 1042 aguarda sua análise.",
                "/descontos/1042", Map.of("numero", 1042),
                "desconto:1042:pendente", expiraEm);
    }
}
