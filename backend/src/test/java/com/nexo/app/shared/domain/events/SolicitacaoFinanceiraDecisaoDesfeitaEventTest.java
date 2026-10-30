package com.nexo.app.shared.domain.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quem é avisado quando uma decisão financeira é desfeita. */
class SolicitacaoFinanceiraDecisaoDesfeitaEventTest {

    private static final UUID SOLICITANTE = UUID.randomUUID();
    private static final UUID APROVADOR = UUID.randomUUID();
    private static final UUID OUTRO = UUID.randomUUID();

    private SolicitacaoFinanceiraDecisaoDesfeitaEvent evento(UUID responsavel) {
        return new SolicitacaoFinanceiraDecisaoDesfeitaEvent(UUID.randomUUID(), "PAGAMENTO", 7L,
                new BigDecimal("100.00"), "Boleto", "Aprovação", "Boleto vencido", SOLICITANTE, APROVADOR, responsavel);
    }

    @Test
    @DisplayName("Avisa quem pediu e quem tinha decidido quando um terceiro desfaz")
    void avisaOsDoisLados() {
        var papeis = evento(OUTRO).papeis();

        assertEquals(Map.of("solicitante", Set.of(SOLICITANTE), "decisorAnterior", Set.of(APROVADOR)), papeis);
    }

    @Test
    @DisplayName("Quem desfaz não é avisado do próprio ato")
    void naoAvisaQuemDesfez() {
        var papeis = evento(APROVADOR).papeis();

        assertEquals(Map.of("solicitante", Set.of(SOLICITANTE)), papeis);
    }

    @Test
    @DisplayName("O contexto traz o que os textos da regra usam")
    void contextoCompleto() {
        var contexto = evento(OUTRO).contexto();

        assertEquals("Aprovação", contexto.get("decisaoAnterior"));
        assertEquals("Boleto vencido", contexto.get("motivo"));
        assertTrue(contexto.containsKey("idSolicitacao"));
        assertEquals(SolicitacaoFinanceiraDecisaoDesfeitaEvent.CHAVE_GATILHO, evento(OUTRO).chaveGatilho());
    }
}
