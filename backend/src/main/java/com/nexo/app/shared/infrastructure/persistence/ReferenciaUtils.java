package com.nexo.app.shared.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public class ReferenciaUtils {

    private static final Map<String, Integer> MESES_MAP = Map.ofEntries(
            Map.entry("JAN", 1), Map.entry("FEV", 2), Map.entry("MAR", 3),
            Map.entry("ABR", 4), Map.entry("MAI", 5), Map.entry("JUN", 6),
            Map.entry("JUL", 7), Map.entry("AGO", 8), Map.entry("SET", 9),
            Map.entry("OUT", 10), Map.entry("NOV", 11), Map.entry("DEZ", 12)
    );

    public static OffsetDateTime converterParaDataInicio(String referencia) {
        OffsetDateTime base = converterReferencia(referencia);
        if (base == null) return null;
        return base.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    public static OffsetDateTime converterParaDataFim(String referencia) {
        OffsetDateTime base = converterReferencia(referencia);
        if (base == null) return null;
        return base.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0).minusNanos(1000000); // minus 1ms
    }

    private static OffsetDateTime converterReferencia(String referencia) {
        if (referencia == null) return null;
        try {
            String[] partes = referencia.split("[/-]");
            if (partes.length != 2) return null;

            String mesStr = partes[0].trim().toUpperCase();
            String anoStr = partes[1].trim();

            int mes;
            if (mesStr.matches("\\d+")) {
                mes = Integer.parseInt(mesStr);
            } else {
                mes = MESES_MAP.getOrDefault(mesStr, 0);
            }

            int ano = Integer.parseInt(anoStr);

            if (mes >= 1 && mes <= 12) {
                return OffsetDateTime.of(ano, mes, 1, 0, 0, 0, 0, ZoneOffset.UTC);
            }
        } catch (Exception e) {
            // Ignora
        }
        return null;
    }
}
