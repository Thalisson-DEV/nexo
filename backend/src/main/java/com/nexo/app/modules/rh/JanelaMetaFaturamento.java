package com.nexo.app.modules.rh;

import com.nexo.app.shared.domain.FeriadosNacionais;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Janela de apuração de uma meta de faturamento (intervalo fechado de datas) e a
 * competência a que ela se refere.
 *
 * <p>Os dias úteis seguem a mesma regra dos dashboards: segunda a sexta, descontados
 * os feriados nacionais ({@link FeriadosNacionais}).</p>
 */
public record JanelaMetaFaturamento(LocalDate inicio, LocalDate fim, YearMonth competencia) {

    public boolean contem(LocalDate dia) {
        return !dia.isBefore(inicio) && !dia.isAfter(fim);
    }

    public int diasUteis() {
        return diasUteis(inicio, fim);
    }

    public static boolean diaUtil(LocalDate dia) {
        DayOfWeek dow = dia.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !FeriadosNacionais.feriado(dia);
    }

    /** Dias úteis em {@code [inicio, fim]}; zero quando o intervalo é vazio. */
    public static int diasUteis(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            return 0;
        }
        int total = 0;
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            if (diaUtil(dia)) {
                total++;
            }
        }
        return total;
    }
}
