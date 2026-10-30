package com.nexo.app.shared.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento disparado quando uma equipe abre o turno, permitindo que outros módulos
 * (como a sincronização) liberem o que esperava por um turno aberto.
 */
public record TurnoAbertoEvent(
    UUID idTurno,
    UUID idEquipe,
    OffsetDateTime dataInicio
) {}
