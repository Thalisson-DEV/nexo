package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import java.time.OffsetDateTime;

/** Regras comuns dos registros com início e fim (vínculo de condutor, acoplamento). */
final class Periodo {

    /** Tolerância para relógio de aparelho levemente adiantado. */
    private static final long TOLERANCIA_FUTURO_MINUTOS = 10;

    private Periodo() {
    }

    static OffsetDateTime inicioValido(OffsetDateTime inicio) {
        if (inicio == null) {
            throw new InvarianteVioladaException("Informe a data e hora de início.");
        }
        if (inicio.isAfter(OffsetDateTime.now().plusMinutes(TOLERANCIA_FUTURO_MINUTOS))) {
            throw new InvarianteVioladaException("A data de início não pode estar no futuro.");
        }
        return inicio;
    }

    static OffsetDateTime fimValido(OffsetDateTime inicio, OffsetDateTime fim) {
        if (fim == null) {
            throw new InvarianteVioladaException("Informe a data e hora de encerramento.");
        }
        if (fim.isBefore(inicio)) {
            throw new InvarianteVioladaException("O encerramento não pode ser anterior ao início.");
        }
        if (fim.isAfter(OffsetDateTime.now().plusMinutes(TOLERANCIA_FUTURO_MINUTOS))) {
            throw new InvarianteVioladaException("A data de encerramento não pode estar no futuro.");
        }
        return fim;
    }

    static boolean contem(OffsetDateTime inicio, OffsetDateTime fim, OffsetDateTime instante) {
        return !instante.isBefore(inicio) && (fim == null || instante.isBefore(fim));
    }
}
