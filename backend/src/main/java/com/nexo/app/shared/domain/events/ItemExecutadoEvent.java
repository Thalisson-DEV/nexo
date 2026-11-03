package com.nexo.app.shared.domain.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento disparado a cada apontamento de execução de um item planejado de obra, para quem acompanha o
 * executado (como as medições) não depender de ler o planejamento.
 */
public record ItemExecutadoEvent(
    UUID idApontamento,
    UUID idItemPlanejado,
    UUID idObra,
    UUID idProgramacao,
    UUID idEquipe,
    UUID idTurno,
    LocalDate data,
    BigDecimal quantidade,
    BigDecimal valor
) {}
