package com.nexo.app.modules.dashboard.domain;

import com.nexo.app.shared.domain.FeriadosNacionais;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Regras de calendário que os dashboards de execução aplicam às metas das equipes.
 *
 * <p>Dias úteis vão de segunda a sexta, descontados os feriados nacionais
 * ({@link FeriadosNacionais}). A distribuição da meta da equipe pelos
 * dias (por ciclo comercial ou mês civil) fica no {@code CronogramaMetaFaturamento}
 * do RH.</p>
 *
 * <p>O dia de operação é o dia civil de São Paulo.</p>
 */
public final class CalendarioOperacional {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private CalendarioOperacional() {
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

    public static int diasUteis(YearMonth mes) {
        return diasUteis(mes.atDay(1), mes.atEndOfMonth());
    }

    public static OffsetDateTime inicioDoDia(LocalDate dia) {
        return dia.atStartOfDay(FUSO).toOffsetDateTime();
    }

    public static LocalDate diaDe(OffsetDateTime instante) {
        return instante.atZoneSameInstant(FUSO).toLocalDate();
    }

    /** {@code parte / todo} em percentual com uma casa; {@code null} quando o todo é zero ou ausente. */
    public static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        if (parte == null || todo == null || todo.signum() <= 0) {
            return null;
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(todo, 1, RoundingMode.HALF_UP);
    }
}
