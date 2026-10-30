package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcaoGovernancaSstTest {

    @Test
    @DisplayName("ação percorre estados preservando resultado e autoria")
    void deveIniciarEConcluir() {
        AcaoGovernancaSst acao = nova(LocalDate.now().plusDays(2));
        OffsetDateTime inicio = OffsetDateTime.now();
        UUID usuario = UUID.randomUUID();

        acao.iniciar(inicio);
        acao.concluir("Extintores conferidos e sinalização atualizada", usuario, inicio.plusHours(2));

        assertEquals(StatusAcaoGovernancaSst.CONCLUIDA, acao.getStatus());
        assertEquals(StatusPrazoGovernancaSst.ENCERRADA, acao.statusPrazo(LocalDate.now()));
        assertEquals("Extintores conferidos e sinalização atualizada", acao.getResultado());
        assertNotNull(acao.getDataConclusao());
        assertThrows(InvarianteVioladaException.class,
                () -> acao.atualizar("Outro título", "Outra descrição", TipoAcaoGovernancaSst.CORRETIVA,
                        PrioridadeAcaoGovernancaSst.MEDIA, UUID.randomUUID(), LocalDate.now().plusDays(4)));
    }

    @Test
    @DisplayName("status do prazo é calculado na consulta e não envelhece persistido")
    void deveDerivarStatusPrazo() {
        assertEquals(StatusPrazoGovernancaSst.VENCIDA,
                nova(LocalDate.now().minusDays(1)).statusPrazo(LocalDate.now()));
        assertEquals(StatusPrazoGovernancaSst.VENCE_HOJE,
                nova(LocalDate.now()).statusPrazo(LocalDate.now()));
        assertEquals(StatusPrazoGovernancaSst.NO_PRAZO,
                nova(LocalDate.now().plusDays(1)).statusPrazo(LocalDate.now()));
    }

    @Test
    @DisplayName("cancelamento exige motivo")
    void deveExigirMotivoNoCancelamento() {
        AcaoGovernancaSst acao = nova(LocalDate.now().plusDays(1));

        assertThrows(InvarianteVioladaException.class,
                () -> acao.cancelar("  ", UUID.randomUUID(), OffsetDateTime.now()));
        assertEquals(StatusAcaoGovernancaSst.ABERTA, acao.getStatus());
    }

    private AcaoGovernancaSst nova(LocalDate prazo) {
        return new AcaoGovernancaSst(UUID.randomUUID(), UUID.randomUUID(), null, null,
                UUID.randomUUID(), "Adequar rota de fuga", "Corrigir a sinalização identificada",
                TipoAcaoGovernancaSst.CORRETIVA, PrioridadeAcaoGovernancaSst.ALTA,
                UUID.randomUUID(), prazo);
    }
}
