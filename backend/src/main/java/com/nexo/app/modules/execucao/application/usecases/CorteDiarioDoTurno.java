package com.nexo.app.modules.execucao.application.usecases;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Horário de corte diário dos turnos: turno ainda aberto nesse horário foi esquecido
 * pela equipe e é encerrado pelo servidor.
 */
@Component
public class CorteDiarioDoTurno {

    private final LocalTime horario;
    private final ZoneId zona;

    public CorteDiarioDoTurno(
            @Value("${ces.turno.encerramento-automatico.horario-corte:22:00}") String horario,
            @Value("${ces.turno.encerramento-automatico.zona:America/Sao_Paulo}") String zona) {
        this.horario = LocalTime.parse(horario);
        this.zona = ZoneId.of(zona);
    }

    /** Corte mais recente que já passou em {@code instante} (o de hoje ou o de ontem). */
    public OffsetDateTime ultimoCorteAte(OffsetDateTime instante) {
        ZonedDateTime local = instante.atZoneSameInstant(zona);
        ZonedDateTime corte = local.toLocalDate().atTime(horario).atZone(zona);
        return (corte.isAfter(local) ? corte.minusDays(1) : corte).toOffsetDateTime();
    }

    /** Primeiro corte depois da abertura: o turno vale até ele. */
    public OffsetDateTime corteDoTurno(OffsetDateTime abertura) {
        ZonedDateTime local = abertura.atZoneSameInstant(zona);
        ZonedDateTime corte = local.toLocalDate().atTime(horario).atZone(zona);
        return (corte.isAfter(local) ? corte : corte.plusDays(1)).toOffsetDateTime();
    }

    public LocalTime horario() {
        return horario;
    }

    public ZoneId zona() {
        return zona;
    }
}
