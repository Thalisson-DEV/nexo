package com.nexo.app.modules.assistente.application.results;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TabularExportTest {

    private final TabularData dados = new TabularData(
            List.of(new TabularData.Column("material", TabularData.ColumnType.TEXTO),
                    new TabularData.Column("quantidade", TabularData.ColumnType.NUMERO),
                    new TabularData.Column("data", TabularData.ColumnType.DATA),
                    new TabularData.Column("ativo", TabularData.ColumnType.BOOLEANO)),
            List.of(Arrays.asList("Cabo; 10mm \"flex\"", 12.5, "2026-09-25", true),
                    Arrays.asList("Poste", 3, null, false)),
            2, false);

    @Test
    void csvTecnicoUsaVirgulaPontoDecimalEAspasQuandoNecessario() {
        String csv = new String(TabularExport.csvTecnico(dados), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("material,quantidade,data,ativo\n")
                .contains("\"Cabo; 10mm \"\"flex\"\"\",12.5,2026-09-25,true")
                .contains("Poste,3,,false");
    }

    @Test
    void csvParaExcelTemBomPontoEVirgulaEVirgulaDecimal() {
        byte[] bytes = TabularExport.csvExcel(dados);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("material;quantidade;data;ativo\n").contains(";12,5;2026-09-25;Sim");
    }

    @Test
    void xlsxPreservaTiposECriaAbaSobre() throws Exception {
        byte[] xlsx = TabularExport.xlsx(List.of(new TabularExport.Aba("Saidas de setembro", dados, "r1 · INTERNO")),
                "Exportacao de teste", List.of("Observacao importante"));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            Sheet aba = workbook.getSheetAt(0);
            assertThat(aba.getSheetName()).isEqualTo("Saidas de setembro");
            assertThat(aba.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Material");
            assertThat(aba.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(aba.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(12.5);
            assertThat(aba.getRow(1).getCell(2).getLocalDateTimeCellValue().toLocalDate()).hasToString("2026-09-25");
            assertThat(aba.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Sim");
            assertThat(workbook.getSheetAt(1).getSheetName()).isEqualTo("Sobre");
            assertThat(workbook.getSheetAt(1).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Exportacao de teste");
        }
    }
}
