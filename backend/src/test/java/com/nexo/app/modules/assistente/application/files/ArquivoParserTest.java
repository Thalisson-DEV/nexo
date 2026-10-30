package com.nexo.app.modules.assistente.application.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.domain.ArquivoAssistente;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ArquivoParserTest {

    private final ArquivoParser parser = new ArquivoParser(new ObjectMapper());

    @Test
    void csvBrasileiroComPontoEVirgulaMilharEDecimalVirgula() throws Exception {
        String csv = "\uFEFFCódigo;Descrição do material;Quantidade;Valor unitário;Data\n"
                + "1001;Cabo 10mm;1.200;12,50;25/09/2026\n"
                + "1002;Poste \"duplo T\";3;1.234,56;24/09/2026\n";
        ArquivoExtraido extraido = parser.extrair("inventario.csv", csv.getBytes(StandardCharsets.UTF_8), 10_000, 1_000);

        assertThat(extraido.categoria()).isEqualTo(ArquivoAssistente.Categoria.CSV);
        TabularData dados = extraido.planilhas().getFirst().dados();
        assertThat(dados.columns()).extracting(TabularData.Column::name)
                .containsExactly("codigo", "descricao_do_material", "quantidade", "valor_unitario", "data");
        assertThat(dados.columns()).extracting(TabularData.Column::type).containsExactly(TabularData.ColumnType.INTEIRO,
                TabularData.ColumnType.TEXTO, TabularData.ColumnType.INTEIRO, TabularData.ColumnType.NUMERO,
                TabularData.ColumnType.DATA);
        assertThat(dados.rows().get(0)).containsExactly(1001L, "Cabo 10mm", 1200L, 12.5, "2026-09-25");
        assertThat(dados.rows().get(1).get(3)).isEqualTo(1234.56);
        assertThat(extraido.metadados().get("separador")).isEqualTo(";");
    }

    @Test
    void xlsxComVariasAbasTiposEDatas() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet estoque = workbook.createSheet("Estoque");
            Row cabecalho = estoque.createRow(0);
            cabecalho.createCell(0).setCellValue("Material");
            cabecalho.createCell(1).setCellValue("Saldo");
            cabecalho.createCell(2).setCellValue("Atualizado em");
            Row linha = estoque.createRow(1);
            linha.createCell(0).setCellValue("Cabo");
            linha.createCell(1).setCellValue(42.5);
            var estilo = workbook.createCellStyle();
            estilo.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
            var data = linha.createCell(2);
            data.setCellValue(LocalDate.of(2026, 9, 20));
            data.setCellStyle(estilo);
            workbook.createSheet("Resumo").createRow(0).createCell(0).setCellValue("Total");
            workbook.write(out);
            xlsx = out.toByteArray();
        }
        ArquivoExtraido extraido = parser.extrair("estoque.xlsx", xlsx, 10_000, 1_000);

        assertThat(extraido.categoria()).isEqualTo(ArquivoAssistente.Categoria.PLANILHA);
        assertThat(extraido.planilhas()).extracting(ArquivoExtraido.Planilha::nome).containsExactly("Estoque", "Resumo");
        TabularData dados = extraido.planilhas().getFirst().dados();
        assertThat(dados.columns()).extracting(TabularData.Column::name).containsExactly("material", "saldo", "atualizado_em");
        assertThat(dados.rows().getFirst().get(1)).isEqualTo(42.5);
        assertThat(String.valueOf(dados.rows().getFirst().get(2))).startsWith("2026-09-20");
    }

    @Test
    void jsonComListaDeObjetosViraTabela() throws Exception {
        String json = "{\"itens\":[{\"codigo\":1,\"nome\":\"Cabo\",\"ativo\":true},{\"codigo\":2,\"nome\":\"Poste\",\"ativo\":false}]}";
        ArquivoExtraido extraido = parser.extrair("dados.json", json.getBytes(StandardCharsets.UTF_8), 10_000, 1_000);
        TabularData dados = extraido.planilhas().getFirst().dados();
        assertThat(dados.totalRows()).isEqualTo(2);
        assertThat(dados.columns()).extracting(TabularData.Column::type)
                .containsExactly(TabularData.ColumnType.INTEIRO, TabularData.ColumnType.TEXTO, TabularData.ColumnType.BOOLEANO);
    }

    @Test
    void pdfExtraiTextoPorPagina() throws Exception {
        byte[] pdf;
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String texto : new String[]{"Relatorio de inspecao", "Segunda pagina com desvios"}) {
                PDPage pagina = new PDPage();
                documento.addPage(pagina);
                try (PDPageContentStream cs = new PDPageContentStream(documento, pagina)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText(texto);
                    cs.endText();
                }
            }
            documento.save(out);
            pdf = out.toByteArray();
        }
        ArquivoExtraido extraido = parser.extrair("inspecao.pdf", pdf, 10_000, 0);
        assertThat(extraido.categoria()).isEqualTo(ArquivoAssistente.Categoria.PDF);
        assertThat(extraido.texto()).contains("[Pagina 1]", "Relatorio de inspecao", "[Pagina 2]", "Segunda pagina com desvios");
        assertThat(extraido.metadados().get("paginas")).isEqualTo(2);
    }
}
