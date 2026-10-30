package com.nexo.app.modules.automacao.application.motor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Map;

import com.nexo.app.modules.automacao.domain.expressao.Valores;

/** Quando o passo "Esperar" retoma: por um tempo, até um dia da semana ou até uma data. */
public final class Espera {

    public static final long MAX_DIAS = 90;

    private Espera() {
    }

    public static OffsetDateTime ate(Map<String, Object> params, OffsetDateTime agora) {
        String modo = Valores.texto(params.getOrDefault("mode", "duration"));
        if (modo.isEmpty() || modo.equals("duration")) {
            long minutos = Math.round(Valores.dbl(params.get("days")) * 1440 + Valores.dbl(params.get("hours")) * 60
                    + Valores.dbl(params.get("minutes")));
            if (minutos <= 0) throw new IllegalArgumentException("Esperar: informe dias, horas ou minutos");
            if (minutos > MAX_DIAS * 1440) throw new IllegalArgumentException("Esperar: no máximo 90 dias");
            return agora.plusMinutes(minutos);
        }
        if (modo.equals("datetime")) {
            ZonedDateTime momento = Valores.instante(params.get("until"));
            if (momento == null) throw new IllegalArgumentException("Esperar até: data e hora inválidas");
            OffsetDateTime alvo = momento.toOffsetDateTime();
            if (alvo.isAfter(agora.plusDays(MAX_DIAS))) throw new IllegalArgumentException("Esperar: no máximo 90 dias");
            return alvo.isBefore(agora) ? agora : alvo;
        }
        int dia = (int) Valores.dbl(params.getOrDefault("weekday", 0));
        int[] hm = Recorrencia.horario(params.getOrDefault("time", "08:00"));
        ZonedDateTime local = agora.atZoneSameInstant(Valores.BRASILIA);
        for (int i = 0; i < 8; i++) {
            LocalDate d = local.toLocalDate().plusDays(i);
            if (d.getDayOfWeek() != DayOfWeek.of(Math.max(0, Math.min(6, dia)) + 1)) continue;
            ZonedDateTime slot = d.atTime(hm[0], hm[1]).atZone(Valores.BRASILIA);
            if (slot.isAfter(local)) return slot.toOffsetDateTime();
        }
        throw new IllegalArgumentException("Esperar até: dia inválido");
    }
}
