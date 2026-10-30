package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lê planilhas de importação de Obras em XLSX (primeira aba) ou CSV (separador ";" ou ","), devolvendo as linhas
 * com o cabeçalho normalizado: maiúsculas, sem acento, espaços e pontuação viram "_" (ex.: "Código Kit" →
 * "CODIGO_KIT"). Aliases de coluna são resolvidos por quem consome.
 */
@Component
public class LeitorPlanilhaImportacao {

    static final int LIMITE_LINHAS = 20_000;

    public record Linha(int numero, Map<String, String> valores) {

        public String valor(String... colunas) {
            for (String coluna : colunas) {
                String valor = valores.get(coluna);
                if (valor != null && !valor.isBlank()) {
                    return valor.trim();
                }
            }
            return null;
        }

        boolean vazia() {
            return valores.values().stream().allMatch(v -> v == null || v.isBlank());
        }
    }

    public List<Linha> ler(byte[] conteudo, String nomeArquivo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new InvarianteVioladaException("Selecione uma planilha preenchida.");
        }
        boolean xlsx = conteudo.length > 1 && conteudo[0] == 'P' && conteudo[1] == 'K';
        try {
            List<Linha> linhas = xlsx ? lerXlsx(conteudo) : lerCsv(conteudo);
            if (linhas.isEmpty()) {
                throw new InvarianteVioladaException("A planilha não tem linhas para importar.");
            }
            return linhas;
        } catch (IOException e) {
            throw new InvarianteVioladaException("Não foi possível ler a planilha " + nomeArquivo + ": " + e.getMessage());
        }
    }

    /**
     * Interpreta número em formato brasileiro ("1.234,56") ou internacional ("1234.56"); vazio vira null.
     */
    public static java.math.BigDecimal interpretarDecimal(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.trim().replace("R$", "").replace(" ", "");
        if (limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(',', '.');
        }
        try {
            return new java.math.BigDecimal(limpo);
        } catch (NumberFormatException e) {
            throw new InvarianteVioladaException("Valor numérico inválido: " + valor);
        }
    }

    public static String normalizarCabecalho(String cabecalho) {
        if (cabecalho == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(cabecalho.replace("﻿", "").trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        return semAcento.replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private List<Linha> lerXlsx(byte[] conteudo) throws IOException {
        List<Linha> linhas = new ArrayList<>();
        DataFormatter formatador = new DataFormatter(Locale.of("pt", "BR"));
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(conteudo))) {
            Sheet aba = workbook.getSheetAt(0);
            Row cabecalho = aba.getRow(aba.getFirstRowNum());
            if (cabecalho == null) {
                return linhas;
            }
            List<String> colunas = new ArrayList<>();
            for (int c = 0; c < cabecalho.getLastCellNum(); c++) {
                Cell celula = cabecalho.getCell(c);
                colunas.add(normalizarCabecalho(celula != null ? formatador.formatCellValue(celula) : ""));
            }
            for (int r = aba.getFirstRowNum() + 1; r <= aba.getLastRowNum(); r++) {
                Row row = aba.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> valores = new LinkedHashMap<>();
                for (int c = 0; c < colunas.size(); c++) {
                    Cell celula = row.getCell(c);
                    valores.put(colunas.get(c), celula != null ? formatador.formatCellValue(celula) : null);
                }
                adicionar(linhas, new Linha(r + 1, valores));
            }
        }
        return linhas;
    }

    private List<Linha> lerCsv(byte[] conteudo) throws IOException {
        String texto = new String(conteudo, StandardCharsets.UTF_8);
        String primeiraLinha = texto.lines().findFirst().orElse("");
        char separador = primeiraLinha.chars().filter(ch -> ch == ';').count()
                >= primeiraLinha.chars().filter(ch -> ch == ',').count() ? ';' : ',';
        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setDelimiter(separador)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setAllowMissingColumnNames(true)
                .setIgnoreSurroundingSpaces(true)
                .build();
        List<Linha> linhas = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(conteudo), StandardCharsets.UTF_8);
             CSVParser parser = formato.parse(reader)) {
            List<String> colunas = parser.getHeaderNames().stream()
                    .map(LeitorPlanilhaImportacao::normalizarCabecalho)
                    .toList();
            for (CSVRecord registro : parser) {
                Map<String, String> valores = new LinkedHashMap<>();
                for (int c = 0; c < colunas.size() && c < registro.size(); c++) {
                    valores.put(colunas.get(c), registro.get(c));
                }
                adicionar(linhas, new Linha((int) registro.getRecordNumber() + 1, valores));
            }
        }
        return linhas;
    }

    private void adicionar(List<Linha> linhas, Linha linha) {
        if (linha.vazia()) {
            return;
        }
        if (linhas.size() >= LIMITE_LINHAS) {
            throw new InvarianteVioladaException("A planilha passa do limite de " + LIMITE_LINHAS
                    + " linhas. Divida o arquivo e importe em partes.");
        }
        linhas.add(linha);
    }
}
