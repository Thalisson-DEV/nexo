package com.nexo.app.shared.application.exportacao;

import org.apache.commons.csv.CSVPrinter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/** Destino CSV de {@link LinhaExportacao}: datas no padrao brasileiro, demais valores como texto. */
public final class CsvExportacao {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CsvExportacao() {
    }

    public static LinhaExportacao para(CSVPrinter printer) {
        return valores -> {
            Object[] formatados = new Object[valores.length];
            for (int i = 0; i < valores.length; i++) formatados[i] = formatar(valores[i]);
            printer.printRecord(formatados);
        };
    }

    static Object formatar(Object valor) {
        return switch (valor) {
            case null -> "";
            case OffsetDateTime data -> data.format(DATA_HORA);
            case LocalDateTime data -> data.format(DATA_HORA);
            case LocalDate data -> data.format(DATA);
            default -> valor;
        };
    }
}
