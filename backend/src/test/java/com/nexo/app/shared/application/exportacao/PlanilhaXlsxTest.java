package com.nexo.app.shared.application.exportacao;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanilhaXlsxTest {

    private static final OffsetDateTime REALIZACAO = OffsetDateTime.of(2026, 9, 14, 9, 30, 0, 0, ZoneOffset.ofHours(-3));

    @Test
    void preservaTiposCongelaCabecalhoERegistraProcedencia() throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (PlanilhaXlsx planilha = new PlanilhaXlsx("Inspeções", "ID", "Data", "Qtd", "Valor", "Ativo", "Prazo", "Obs")) {
            LinhaExportacao linha = planilha::linha;
            linha.escrever(UUID.fromString("00000000-0000-0000-0000-000000000001"), REALIZACAO, 3, new BigDecimal("10.5"), true,
                    LocalDate.of(2026, 10, 1), null);
            linha.escrever("x");
            Map<String, String> info = new LinkedHashMap<>();
            info.put("Registros", "2");
            planilha.procedencia(info);
            assertThat(planilha.linhasEscritas()).isEqualTo(2);
            planilha.escrever(saida);
        }

        try (XSSFWorkbook lido = new XSSFWorkbook(new ByteArrayInputStream(saida.toByteArray()))) {
            Sheet aba = lido.getSheet("Inspeções");
            assertThat(aba.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Qtd");
            assertThat(aba.getPaneInformation().isFreezePane()).isTrue();
            Row dados = aba.getRow(1);
            assertThat(dados.getCell(0).getStringCellValue()).isEqualTo("00000000-0000-0000-0000-000000000001");
            assertThat(DateUtil.isCellDateFormatted(dados.getCell(1))).isTrue();
            assertThat(dados.getCell(1).getLocalDateTimeCellValue()).isEqualTo(REALIZACAO.toLocalDateTime());
            assertThat(dados.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(dados.getCell(3).getNumericCellValue()).isEqualTo(10.5);
            assertThat(dados.getCell(4).getBooleanCellValue()).isTrue();
            assertThat(dados.getCell(5).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(dados.getCell(6) == null || dados.getCell(6).getCellType() == CellType.BLANK).isTrue();
            assertThat(lido.getSheet("Sobre").getRow(0).getCell(1).getStringCellValue()).isEqualTo("2");
        }
    }

    @Test
    void csvMantemOFormatoBrasileiroDeDatasEVazioParaNulo() throws Exception {
        StringWriter texto = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(texto, CSVFormat.Builder.create().setDelimiter(';').build())) {
            CsvExportacao.para(printer).escrever(REALIZACAO, LocalDate.of(2026, 10, 1), null, 7, "ok");
        }
        assertThat(texto.toString().trim()).isEqualTo("14/09/2026 09:30:00;01/10/2026;;7;ok");
    }
}
