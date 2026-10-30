package com.nexo.app.modules.automacao.application.motor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.nexo.app.modules.automacao.domain.expressao.Valores;

/**
 * Recorrência do gatilho agendado, no horário de Brasília.
 *
 * <p>{@link #ultimo} dá o momento planejado mais recente até agora (o tick dispara uma
 * vez por momento, com a chave do momento); {@link #proximo} dá o seguinte, mostrado nas
 * telas. Minutos e horas se alinham à meia-noite local e dias a 01/01/2026, para que os
 * mesmos parâmetros sempre gerem os mesmos momentos.</p>
 */
public final class Recorrencia {

    public static final int MIN_MINUTOS = 5;
    public static final String[] DIAS = {"segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo"};
    private static final LocalDate ANCORA = LocalDate.of(2026, 1, 1);

    private Recorrencia() {
    }

    public static int[] horario(Object valor) {
        String bruto = Valores.texto(valor).isBlank() ? "08:00" : Valores.texto(valor).strip();
        String[] partes = bruto.split(":");
        try {
            int h = Integer.parseInt(partes[0]);
            int m = partes.length > 1 ? Integer.parseInt(partes[1]) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) throw new IllegalArgumentException("Horário inválido; use HH:MM");
            return new int[]{h, m};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Horário inválido; use HH:MM");
        }
    }

    private static int intervalo(Map<String, Object> p) {
        double v = p.get("interval") == null || "".equals(p.get("interval")) ? 1 : Valores.dbl(p.get("interval"));
        if (v < 1 || v > 1000) throw new IllegalArgumentException("Intervalo entre 1 e 1000");
        return (int) v;
    }

    private static String frequencia(Map<String, Object> p) {
        String f = Valores.texto(p.getOrDefault("frequency", "week"));
        return f.isEmpty() ? "week" : f;
    }

    private static TreeSet<Integer> diasDaSemana(Map<String, Object> p) {
        TreeSet<Integer> dias = new TreeSet<>();
        for (Object d : Valores.lista(p.get("weekdays"))) dias.add((int) Valores.dbl(d));
        return dias;
    }

    private static int diaDoMes(Map<String, Object> p) {
        return p.get("day_of_month") == null ? 1 : (int) Valores.dbl(p.get("day_of_month"));
    }

    public static void verificar(Map<String, Object> p) {
        String f = frequencia(p);
        if (!List.of("minute", "hour", "day", "week", "month").contains(f)) throw new IllegalArgumentException("Frequência inválida");
        int intervalo = intervalo(p);
        if (f.equals("minute") && intervalo < MIN_MINUTOS) {
            throw new IllegalArgumentException("A cada no mínimo " + MIN_MINUTOS + " minutos");
        }
        horario(p.get("time"));
        if (f.equals("week")) {
            TreeSet<Integer> dias = diasDaSemana(p);
            if (dias.isEmpty() || dias.first() < 0 || dias.last() > 6) throw new IllegalArgumentException("Escolha ao menos um dia da semana");
        }
        if (f.equals("month") && (diaDoMes(p) < 1 || diaDoMes(p) > 28)) {
            throw new IllegalArgumentException("Dia do mês entre 1 e 28");
        }
    }

    private static List<ZonedDateTime> doDia(Map<String, Object> p, LocalDate dia) {
        String f = frequencia(p);
        int[] hm = horario(p.get("time"));
        int intervalo = intervalo(p);
        ZonedDateTime meiaNoite = dia.atStartOfDay(Valores.BRASILIA);
        List<ZonedDateTime> slots = new ArrayList<>();
        switch (f) {
            case "minute" -> {
                int passo = Math.max(MIN_MINUTOS, intervalo);
                for (int m = 0; m < 1440; m += passo) slots.add(meiaNoite.plusMinutes(m));
            }
            case "hour" -> {
                for (int h = 0; h < 24; h += intervalo) slots.add(meiaNoite.plusHours(h).plusMinutes(hm[1]));
            }
            case "day" -> {
                if (ChronoUnit.DAYS.between(ANCORA, dia) % intervalo == 0) slots.add(meiaNoite.plusHours(hm[0]).plusMinutes(hm[1]));
            }
            case "week" -> {
                TreeSet<Integer> dias = diasDaSemana(p);
                if (dias.isEmpty()) dias.add(0);
                if (dias.contains(dia.getDayOfWeek().getValue() - 1)) slots.add(meiaNoite.plusHours(hm[0]).plusMinutes(hm[1]));
            }
            default -> {
                if (dia.getDayOfMonth() == diaDoMes(p)) slots.add(meiaNoite.plusHours(hm[0]).plusMinutes(hm[1]));
            }
        }
        return slots;
    }

    public static OffsetDateTime ultimo(Map<String, Object> p, OffsetDateTime agora) {
        ZonedDateTime local = agora.atZoneSameInstant(Valores.BRASILIA);
        for (int i = 0; i < 40; i++) {
            List<ZonedDateTime> slots = doDia(p, local.toLocalDate().minusDays(i)).stream().filter(s -> !s.isAfter(local)).toList();
            if (!slots.isEmpty()) return slots.get(slots.size() - 1).toOffsetDateTime();
        }
        return null;
    }

    public static OffsetDateTime proximo(Map<String, Object> p, OffsetDateTime agora) {
        ZonedDateTime local = agora.atZoneSameInstant(Valores.BRASILIA);
        for (int i = 0; i < 40; i++) {
            List<ZonedDateTime> slots = doDia(p, local.toLocalDate().plusDays(i)).stream().filter(s -> s.isAfter(local)).toList();
            if (!slots.isEmpty()) return slots.get(0).toOffsetDateTime();
        }
        return null;
    }

    /** Quanto atraso ainda vale disparar (o servidor pode ter estado fora do ar). */
    public static Duration tolerancia(Map<String, Object> p) {
        return switch (frequencia(p)) {
            case "minute" -> Duration.ofMinutes(Math.max(MIN_MINUTOS, intervalo(p)));
            case "hour" -> Duration.ofHours(1);
            default -> Duration.ofHours(6);
        };
    }

    public static String descrever(Map<String, Object> p) {
        String f = frequencia(p);
        int intervalo;
        int[] hm;
        try {
            intervalo = intervalo(p);
            hm = horario(p.get("time"));
        } catch (IllegalArgumentException e) {
            return "Agenda incompleta";
        }
        String as = String.format("%02d:%02d", hm[0], hm[1]);
        return switch (f) {
            case "minute" -> "A cada " + intervalo + " minutos";
            case "hour" -> intervalo == 1 ? "A cada hora" : "A cada " + intervalo + " horas";
            case "day" -> intervalo == 1 ? "Todo dia às " + as : "A cada " + intervalo + " dias às " + as;
            case "week" -> {
                TreeSet<Integer> dias = diasDaSemana(p);
                if (dias.isEmpty()) dias.add(0);
                String nomes = dias.equals(new TreeSet<>(List.of(0, 1, 2, 3, 4))) ? "dia útil"
                        : String.join(", ", dias.stream().filter(d -> d >= 0 && d < 7).map(d -> DIAS[d]).toList());
                yield "Toda " + nomes + " às " + as;
            }
            default -> "Todo dia " + diaDoMes(p) + " do mês às " + as;
        };
    }
}
