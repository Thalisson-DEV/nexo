package com.nexo.app.shared.application.exportacao;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Planilha XLSX em streaming (SXSSF: mantem poucas linhas em memoria) para exportacoes grandes. Preserva tipos
 * — numeros, datas e booleanos viram celulas nativas do Excel —, destaca e congela o cabecalho, liga o
 * autofiltro e registra a procedencia (periodo e filtros aplicados) numa aba "Sobre".
 */
public final class PlanilhaXlsx implements AutoCloseable {

    private static final int LINHAS_EM_MEMORIA = 200;
    private static final int LARGURA_MAXIMA = 60 * 256;

    private final SXSSFWorkbook workbook = new SXSSFWorkbook(LINHAS_EM_MEMORIA);
    private final CellStyle estiloCabecalho;
    private final CellStyle estiloData;
    private SXSSFSheet sheet;
    private int colunas;
    private int proximaLinha = 1;

    public PlanilhaXlsx(String aba, String... cabecalho) {
        estiloCabecalho = workbook.createCellStyle();
        Font negrito = workbook.createFont();
        negrito.setBold(true);
        estiloCabecalho.setFont(negrito);
        estiloCabecalho.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estiloCabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CreationHelper helper = workbook.getCreationHelper();
        estiloData = workbook.createCellStyle();
        estiloData.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));

        iniciarAba(aba, cabecalho);
    }

    /**
     * Fecha a aba atual (autofiltro e largura das colunas) e passa a escrever numa nova aba, com o
     * mesmo tratamento de cabecalho. Util para exportacoes com varias visoes (resumo, detalhe, rankings).
     */
    public void aba(String nome, String... cabecalho) {
        finalizarAba();
        iniciarAba(nome, cabecalho);
    }

    private void iniciarAba(String nome, String... cabecalho) {
        this.sheet = workbook.createSheet(nome);
        this.colunas = cabecalho.length;
        this.proximaLinha = 1;
        sheet.trackAllColumnsForAutoSizing();
        Row linha = sheet.createRow(0);
        for (int i = 0; i < cabecalho.length; i++) {
            Cell celula = linha.createCell(i);
            celula.setCellValue(cabecalho[i]);
            celula.setCellStyle(estiloCabecalho);
        }
        sheet.createFreezePane(0, 1);
    }

    private void finalizarAba() {
        if (proximaLinha > 1) sheet.setAutoFilter(new CellRangeAddress(0, proximaLinha - 1, 0, colunas - 1));
        for (int i = 0; i < colunas; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) > LARGURA_MAXIMA) sheet.setColumnWidth(i, LARGURA_MAXIMA);
        }
    }

    /** Uma linha de dados; valores alem do cabecalho sao ignorados. */
    public void linha(Object... valores) {
        Row linha = sheet.createRow(proximaLinha++);
        for (int i = 0; i < Math.min(colunas, valores.length); i++) {
            preencher(linha.createCell(i), valores[i]);
        }
    }

    /** Linhas de dados escritas na aba atual. */
    public int linhasEscritas() {
        return proximaLinha - 1;
    }

    /** Aba "Sobre" com a procedencia da exportacao (ex.: periodo, filtros, gerado em). */
    public void procedencia(Map<String, String> informacoes) {
        Sheet sobre = workbook.createSheet("Sobre");
        int indice = 0;
        for (Map.Entry<String, String> item : informacoes.entrySet()) {
            Row linha = sobre.createRow(indice++);
            linha.createCell(0).setCellValue(item.getKey());
            linha.createCell(1).setCellValue(item.getValue() == null ? "" : item.getValue());
        }
        sobre.setColumnWidth(0, 28 * 256);
        sobre.setColumnWidth(1, 80 * 256);
    }

    public void escrever(OutputStream saida) throws IOException {
        finalizarAba();
        workbook.write(saida);
    }

    @Override
    public void close() {
        workbook.dispose();
        try {
            workbook.close();
        } catch (IOException ignored) {
            // arquivos temporarios do SXSSF ja foram descartados por dispose()
        }
    }

    private void preencher(Cell celula, Object valor) {
        switch (valor) {
            case null -> celula.setBlank();
            case Number numero when numero instanceof BigDecimal decimal -> celula.setCellValue(decimal.doubleValue());
            case Number numero -> celula.setCellValue(numero.doubleValue());
            case Boolean booleano -> celula.setCellValue(booleano);
            // Hora local registrada (mesma leitura do CSV), sem conversao de fuso.
            case OffsetDateTime data -> data(celula, data.toLocalDateTime());
            case LocalDateTime data -> data(celula, data);
            case LocalDate data -> data(celula, data.atStartOfDay());
            default -> celula.setCellValue(String.valueOf(valor));
        }
    }

    private void data(Cell celula, LocalDateTime data) {
        celula.setCellValue(data);
        celula.setCellStyle(estiloData);
    }
}
