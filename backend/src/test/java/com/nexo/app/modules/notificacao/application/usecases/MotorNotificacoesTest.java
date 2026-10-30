package com.nexo.app.modules.notificacao.application.usecases;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexo.app.modules.notificacao.domain.ExecucaoRegra;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository;
import com.nexo.app.shared.domain.events.DescontoSolicitadoEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O motor só orquestra: seleciona os ids das regras do gatilho e delega cada uma ao
 * {@link AplicadorRegra}. A decisão de notificar é testada em {@code AplicadorRegraTest}.
 */
@ExtendWith(MockitoExtension.class)
class MotorNotificacoesTest {

    @Mock
    private RegraNotificacaoRepository regraRepository;
    @Mock
    private AplicadorRegra aplicadorRegra;

    @InjectMocks
    private MotorNotificacoes motor;

    @Test
    void eventoDeveDelegarCadaRegraDoGatilhoComOEscopoDoRegistro() {
        UUID idDesconto = UUID.randomUUID();
        UUID solicitante = UUID.randomUUID();
        UUID primeira = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        when(regraRepository.buscarIdsAtivosPorGatilho(DescontoSolicitadoEvent.CHAVE_GATILHO))
                .thenReturn(List.of(primeira, segunda));
        when(aplicadorRegra.aplicar(any(), any(), any(), any(), any(), any())).thenReturn(1);

        int entregues = motor.processarEvento(new DescontoSolicitadoEvent(
                idDesconto, solicitante, "Dano em EPI", 3, null));

        assertThat(entregues)
                .as("o total é a soma do que cada regra entregou")
                .isEqualTo(2);
        Map<String, Set<UUID>> papeis = Map.of("solicitante", Set.of(solicitante));
        verify(aplicadorRegra).aplicar(eq(primeira), any(), eq(papeis),
                eq(idDesconto), eq(idDesconto.toString()), eq(null));
        verify(aplicadorRegra).aplicar(eq(segunda), any(), eq(papeis),
                eq(idDesconto), eq(idDesconto.toString()), eq(null));
    }

    @Test
    void gatilhoSemRegraConfiguradaNaoDeveFazerNada() {
        when(regraRepository.buscarIdsAtivosPorGatilho(any())).thenReturn(List.of());

        int entregues = motor.processarEvento(new DescontoSolicitadoEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Dano em EPI", 1, null));

        assertThat(entregues).isZero();
        verify(aplicadorRegra, never()).aplicar(any(), any(), any(), any(), any(), any());
    }

    @Test
    void limiarDeveDelegarComEscopoGlobalEOValorMedido() {
        UUID idRegra = UUID.randomUUID();
        when(aplicadorRegra.aplicar(any(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(motor.avaliarLimiar(idRegra, 12)).isEqualTo(1);

        // Sem registro de origem, o silêncio vale para a fila inteira.
        verify(aplicadorRegra).aplicar(eq(idRegra), eq(Map.of("total", 12L)), eq(Map.of()),
                eq(null), eq(ExecucaoRegra.ESCOPO_GLOBAL), eq(12L));
    }
}
