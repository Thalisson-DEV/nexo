package com.nexo.app.modules.assistente.application.results;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Exportacao de resultados tabulares: CSV tecnico (para o sandbox Python), CSV para Excel em portugues
 * (separador ";" e virgula decimal) e XLSX com tipos preservados, cabecalho destacado e aba de procedencia.
 */
public final class TabularExport {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final int XLSX_MAX_AUTOSIZE_ROWS = 2_000;

    private TabularExport() {
    }

    /** CSV UTF-8, separador virgula, ponto decimal e datas ISO: leitura direta por pandas. */
    public static byte[] csvTecnico(TabularData data) {
        return csv(data, ',', false, false);
    }

    /** CSV para Excel pt-BR: BOM UTF-8, separador ponto e virgula e virgula decimal. */
    public static byte[] csvExcel(TabularData data) {
        return csv(data, ';', true, true);
    }

    private static byte[] csv(TabularData data, char delimiter, boolean bom, boolean decimalVirgula) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if (bom) output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
            CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setDelimiter(delimiter)
                    .setHeader(data.columns().stream().map(TabularData.Column::name).toArray(String[]::new))
                    .setRecordSeparator("\n")
                    .build();
            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (List<Object> row : data.rows()) {
                    Object[] values = new Object[data.columns().size()];
                    for (int i = 0; i < values.length; i++) {
                        Object value = i < row.size() ? row.get(i) : null;
                        values[i] = csvValue(value, decimalVirgula);
                    }
                    printer.printRecord(values);
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String csvValue(Object value, boolean decimalVirgula) {
        if (value == null) return "";
        if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
            String plain = new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
            return decimalVirgula ? plain.replace('.', ',') : plain;
        }
        if (value instanceof Boolean bool && decimalVirgula) return bool ? "Sim" : "Nao";
        return value.toString();
    }

    /** Planilha de uma ou mais abas de dados, com cabecalho, filtro, painel congelado e aba "Sobre". */
    public static byte[] xlsx(List<Aba> abas, String titulo, List<String> notas) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(workbook);
            java.util.Set<String> usados = new java.util.HashSet<>();
            for (Aba aba : abas) {
                String nome = nomeAba(aba.nome(), usados);
                preencherAba(workbook.createSheet(nome), aba.dados(), estilos);
            }
            Sheet sobre = workbook.createSheet(nomeAba("Sobre", usados));
            int linha = 0;
            Row cabecalho = sobre.createRow(linha++);
            Cell tituloCell = cabecalho.createCell(0);
            tituloCell.setCellValue(titulo == null || titulo.isBlank() ? "Exportacao do Copiloto" : titulo);
            tituloCell.setCellStyle(estilos.titulo);
            sobre.createRow(linha++).createCell(0).setCellValue("Gerado pelo Copiloto (Nexo) em "
                    + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(LocalDateTime.now()));
            for (Aba aba : abas) {
                String descricao = aba.nome() + ": " + aba.dados().totalRows() + " linhas"
                        + (aba.procedencia() == null ? "" : " · " + aba.procedencia())
                        + (aba.dados().truncated() || aba.dados().rows().size() < aba.dados().totalRows()
                        ? " · amostra de " + aba.dados().rows().size() + " linhas" : "");
                sobre.createRow(linha++).createCell(0).setCellValue(descricao);
            }
            if (notas != null) {
                for (String nota : notas) {
                    if (nota != null && !nota.isBlank()) sobre.createRow(linha++).createCell(0).setCellValue(nota);
                }
            }
            sobre.setColumnWidth(0, 120 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void preencherAba(Sheet sheet, TabularData data, Estilos estilos) {
        Row header = sheet.createRow(0);
        for (int c = 0; c < data.columns().size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(rotulo(data.columns().get(c).name()));
            cell.setCellStyle(estilos.cabecalho);
        }
        int r = 1;
        for (List<Object> row : data.rows()) {
            Row excelRow = sheet.createRow(r++);
            for (int c = 0; c < data.columns().size(); c++) {
                Object value = c < row.size() ? row.get(c) : null;
                if (value == null) continue;
                Cell cell = excelRow.createCell(c);
                escrever(cell, value, data.columns().get(c).type(), estilos);
            }
        }
        if (!data.columns().isEmpty()) {
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, r - 1), 0, data.columns().size() - 1));
            boolean autosize = data.rows().size() <= XLSX_MAX_AUTOSIZE_ROWS;
            for (int c = 0; c < data.columns().size(); c++) {
                if (autosize) {
                    sheet.autoSizeColumn(c);
                    int width = Math.min(Math.max(sheet.getColumnWidth(c) + 512, 10 * 256), 60 * 256);
                    sheet.setColumnWidth(c, width);
                } else {
                    sheet.setColumnWidth(c, 18 * 256);
                }
            }
        }
    }

    private static void escrever(Cell cell, Object value, TabularData.ColumnType type, Estilos estilos) {
        switch (value) {
            case Number number -> {
                cell.setCellValue(number.doubleValue());
                cell.setCellStyle(type == TabularData.ColumnType.INTEIRO || number instanceof Long || number instanceof Integer
                        ? estilos.inteiro : estilos.decimal);
            }
            case Boolean bool -> cell.setCellValue(bool ? "Sim" : "Nao");
            case String text when type == TabularData.ColumnType.DATA -> {
                try {
                    cell.setCellValue(LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text));
                    cell.setCellStyle(estilos.data);
                } catch (RuntimeException exception) {
                    cell.setCellValue(text);
                }
            }
            case String text when type == TabularData.ColumnType.DATA_HORA -> {
                try {
                    LocalDateTime dateTime = text.contains("+") || text.endsWith("Z") || text.matches(".*-\\d{2}:\\d{2}$")
                            ? OffsetDateTime.parse(text).toLocalDateTime() : LocalDateTime.parse(text);
                    cell.setCellValue(dateTime);
                    cell.setCellStyle(estilos.dataHora);
                } catch (RuntimeException exception) {
                    cell.setCellValue(text);
                }
            }
            default -> {
                String text = value.toString();
                cell.setCellValue(text.length() > 32_000 ? text.substring(0, 32_000) : text);
            }
        }
    }

    /** "quantidade_disponivel" -> "Quantidade disponivel". */
    public static String rotulo(String coluna) {
        if (coluna == null || coluna.isBlank()) return "";
        String texto = coluna.replace('_', ' ').trim();
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static String nomeAba(String nome, java.util.Set<String> usados) {
        String base = (nome == null || nome.isBlank() ? "Dados" : nome).replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (base.isEmpty()) base = "Dados";
        if (base.length() > 28) base = base.substring(0, 28);
        String candidato = base;
        int sufixo = 2;
        while (!usados.add(candidato.toLowerCase(Locale.ROOT))) candidato = base + " " + sufixo++;
        return candidato;
    }

    /** Formata numero no padrao brasileiro (1.234,56). */
    public static String numeroBr(Object value, int casas) {
        if (!(value instanceof Number number)) return value == null ? "" : value.toString();
        DecimalFormat format = new DecimalFormat(casas <= 0 ? "#,##0" : "#,##0." + "0".repeat(casas),
                DecimalFormatSymbols.getInstance(PT_BR));
        return format.format(number.doubleValue());
    }

    public record Aba(String nome, TabularData dados, String procedencia) {
    }

    private static final class Estilos {
        final CellStyle cabecalho;
        final CellStyle titulo;
        final CellStyle inteiro;
        final CellStyle decimal;
        final CellStyle data;
        final CellStyle dataHora;

        Estilos(Workbook workbook) {
            DataFormat format = workbook.createDataFormat();
            Font negrito = workbook.createFont();
            negrito.setBold(true);
            negrito.setColor(IndexedColors.WHITE.getIndex());
            cabecalho = workbook.createCellStyle();
            cabecalho.setFont(negrito);
            cabecalho.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecalho.setVerticalAlignment(VerticalAlignment.CENTER);
            Font grande = workbook.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);
            titulo = workbook.createCellStyle();
            titulo.setFont(grande);
            inteiro = workbook.createCellStyle();
            inteiro.setDataFormat(format.getFormat("#,##0"));
            decimal = workbook.createCellStyle();
            decimal.setDataFormat(format.getFormat("#,##0.00"));
            data = workbook.createCellStyle();
            data.setDataFormat(format.getFormat("dd/mm/yyyy"));
            dataHora = workbook.createCellStyle();
            dataHora.setDataFormat(format.getFormat("dd/mm/yyyy hh:mm"));
        }
    }
}
