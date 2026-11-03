package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanilhaOrcadoFaturadoTest {

    @Test
    void modeloVoltaSemLinhasNemErros() {
        PlanilhaOrcadoFaturado.Conteudo conteudo = PlanilhaOrcadoFaturado.ler(PlanilhaOrcadoFaturado.modelo());

        assertThat(conteudo.materiais()).isEmpty();
        assertThat(conteudo.maoDeObra()).isEmpty();
        assertThat(conteudo.faturado()).isEmpty();
        assertThat(conteudo.erros()).isEmpty();
    }

    /** Layout de quem já preenchia a planilha: "Mão de obra orçado", obra numérica e operação abreviada. */
    @Test
    void lePlanilhaJaPreenchidaComCelulasNumericasEDatas() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet mo = cabecalho(workbook.createSheet("Mão de obra orçado"), "Obra", "Operação", "Código", "Descrição", "Quantidade",
                    "Valor unitário (R$)", "Valor total (R$)", "Valor unitário atualizado (R$)", "Valor total atualizado (R$)");
            linha(mo, 1, 500000001d, "I", 1030d, "Serviço A", 10d, 25d, null, 30d, null);
            linha(mo, 2, "500000001", "Retirar", "0042", "Serviço B", "2", "12,50", null, null, null);
            linha(mo, 3, "500000002", "Trocar", "77", "Inválida", 1d, 1d, 1d, null, null);
            linha(mo, 4, null, "I", "77", "Sem obra", 1d, 1d, 1d, null, null);
            cabecalho(workbook.createSheet("Materiais orçados"), "Obra", "Operação", "Código", "Descrição", "Quantidade",
                    "Valor unitário (R$)", "Valor total (R$)");
            Sheet fat = cabecalho(workbook.createSheet("Faturado"), "Obra", "Data da medição", "Operação", "Código", "Descrição",
                    "Quantidade", "Valor total (R$)", "Situação", "Débito/crédito");
            linha(fat, 1, "500000001", "20/09/2026", "I", "1030", "Serviço A", 2d, 60d, "A", "D");
            linha(fat, 2, "500000001", null, "I", "1030", "Serviço A", 1d, 30d, "A", "C");
            linha(fat, 3, "500000001", "21/09/2026", "I", "1030", "Serviço A", 1d, 30d, "P", "D");
            CellStyle data = workbook.createCellStyle();
            data.setDataFormat((short) 14);
            fat.getRow(2).createCell(1).setCellValue(LocalDate.of(2026, 9, 25));
            fat.getRow(2).getCell(1).setCellStyle(data);

            PlanilhaOrcadoFaturado.Conteudo conteudo = PlanilhaOrcadoFaturado.ler(bytes(workbook));

            assertThat(conteudo.maoDeObra()).hasSize(2);
            PlanilhaOrcadoFaturado.LinhaOrcada a = conteudo.maoDeObra().getFirst();
            assertThat(a.obra()).isEqualTo("500000001");
            assertThat(a.codigo()).isEqualTo("1030");
            assertThat(a.operacao()).isEqualTo(OperacaoItemKit.INSTALAR);
            assertThat(a.valorTotal()).isEqualByComparingTo("250");
            assertThat(a.valorVigente()).isEqualByComparingTo("300");
            PlanilhaOrcadoFaturado.LinhaOrcada b = conteudo.maoDeObra().get(1);
            assertThat(b.codigo()).isEqualTo("0042");
            assertThat(b.operacao()).isEqualTo(OperacaoItemKit.RETIRAR);
            assertThat(b.valorTotal()).isEqualByComparingTo("25");

            assertThat(conteudo.erros()).extracting(PlanilhaOrcadoFaturado.Erro::obra).containsExactly("500000002", null);
            assertThat(conteudo.erros().getFirst().texto()).contains("Mão de obra orçada, linha 4", "Trocar");

            assertThat(conteudo.faturado()).hasSize(3);
            assertThat(conteudo.faturado().get(1).data()).isEqualTo(LocalDate.of(2026, 9, 25));
            assertThat(conteudo.faturado()).extracting(PlanilhaOrcadoFaturado.LinhaFaturada::valorConsiderado)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("60"), new BigDecimal("-30"), BigDecimal.ZERO);
            assertThat(conteudo.faturado()).extracting(PlanilhaOrcadoFaturado.LinhaFaturada::paraConferir).containsExactly(false, true, true);
        }
    }

    @Test
    void recusaArquivoQueNaoEXlsxOuSemAsAbasDoOrcado() throws IOException {
        assertThatThrownBy(() -> PlanilhaOrcadoFaturado.ler("obra;codigo".getBytes()))
                .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("XLSX");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Planilha1");
            byte[] semAbas = bytes(workbook);
            assertThatThrownBy(() -> PlanilhaOrcadoFaturado.ler(semAbas))
                    .isInstanceOf(InvarianteVioladaException.class).hasMessageContaining("Mão de obra orçada");
        }
    }

    private static Sheet cabecalho(Sheet aba, String... colunas) {
        Row row = aba.createRow(0);
        for (int i = 0; i < colunas.length; i++) {
            row.createCell(i).setCellValue(colunas[i]);
        }
        return aba;
    }

    private static void linha(Sheet aba, int indice, Object... valores) {
        Row row = aba.createRow(indice);
        for (int i = 0; i < valores.length; i++) {
            switch (valores[i]) {
                case null -> { }
                case Double numero -> row.createCell(i).setCellValue(numero);
                default -> row.createCell(i).setCellValue(String.valueOf(valores[i]));
            }
        }
    }

    private static byte[] bytes(XSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            workbook.write(saida);
            return saida.toByteArray();
        }
    }
}
