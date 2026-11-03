package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.domain.OperacaoItemKit;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Planilha orçado × faturado do contrato: abas de materiais orçados, mão de obra orçada e faturado. Os cabeçalhos são
 * os do modelo; a ordem das colunas não importa. Linha com problema sai da leitura e volta como erro, com a obra
 * quando ela foi informada, para bloquear só o relatório daquela obra.
 */
final class PlanilhaOrcadoFaturado {

    static final int LIMITE_LINHAS = 20_000;
    private static final int LINHAS_DO_MODELO = 500;
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final String ABA_MATERIAIS = "Materiais orçados";
    static final String ABA_MAO_DE_OBRA = "Mão de obra orçada";
    static final String ABA_FATURADO = "Faturado";

    private static final String[] CABECALHO_ORCADO = {"Obra", "Operação", "Código", "Descrição", "Quantidade", "Valor unitário (R$)",
            "Valor total (R$)", "Valor unitário atualizado (R$)", "Valor total atualizado (R$)", "Unidade"};
    private static final String[] CABECALHO_FATURADO = {"Obra", "Data da medição", "Operação", "Código", "Descrição", "Quantidade",
            "Valor total (R$)", "Situação", "Débito/crédito"};

    /** Uma linha do orçamento; o valor vigente é o atualizado, quando houver. */
    record LinhaOrcada(int linha, String obra, OperacaoItemKit operacao, String codigo, String descricao, String unidade,
                       BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorTotal,
                       BigDecimal valorUnitarioAtualizado, BigDecimal valorTotalAtualizado) {

        BigDecimal valorVigente() {
            return valorTotalAtualizado != null ? valorTotalAtualizado : valorTotal;
        }
    }

    /** {@code situacao} "A" é aprovado; crédito entra subtraindo. */
    record LinhaFaturada(int linha, String obra, LocalDate data, OperacaoItemKit operacao, String codigo, String descricao,
                         BigDecimal quantidade, BigDecimal valorTotal, String situacao, boolean credito) {

        boolean aprovada() {
            return "A".equals(situacao);
        }

        /** Entra no faturado: aprovada; o crédito volta com sinal trocado. */
        BigDecimal valorConsiderado() {
            return !aprovada() ? BigDecimal.ZERO : credito ? valorTotal.negate() : valorTotal;
        }

        BigDecimal quantidadeConsiderada() {
            return !aprovada() ? BigDecimal.ZERO : credito ? quantidade.negate() : quantidade;
        }

        /** Precisa de conferência antes de emitir: situação diferente de aprovada ou crédito. */
        boolean paraConferir() {
            return !aprovada() || credito;
        }
    }

    /** Erro de leitura; {@code obra} nula quando a linha não informou a obra. */
    record Erro(String aba, int linha, String obra, String mensagem) {

        String texto() {
            return aba + ", linha " + linha + ": " + mensagem;
        }
    }

    record Conteudo(List<LinhaOrcada> materiais, List<LinhaOrcada> maoDeObra, List<LinhaFaturada> faturado, List<Erro> erros) {
    }

    private PlanilhaOrcadoFaturado() {
    }

    static Conteudo ler(byte[] conteudo) {
        if (conteudo == null || conteudo.length < 2 || conteudo[0] != 'P' || conteudo[1] != 'K') {
            throw new InvarianteVioladaException("Envie a planilha orçado × faturado em XLSX.");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(conteudo))) {
            Sheet materiais = aba(workbook, "MATERIA");
            Sheet maoDeObra = aba(workbook, "MAO_DE_OBRA");
            Sheet faturado = aba(workbook, "FATURADO");
            if (materiais == null && maoDeObra == null) {
                throw new InvarianteVioladaException("A planilha precisa das abas \"" + ABA_MATERIAIS + "\" e \"" + ABA_MAO_DE_OBRA
                        + "\". Baixe o modelo e mantenha os nomes das abas.");
            }
            List<Erro> erros = new ArrayList<>();
            int[] total = {0};
            List<LinhaOrcada> linhasMateriais = materiais == null ? List.of() : linhas(materiais, ABA_MATERIAIS, erros, total, PlanilhaOrcadoFaturado::orcada);
            List<LinhaOrcada> linhasMaoDeObra = maoDeObra == null ? List.of() : linhas(maoDeObra, ABA_MAO_DE_OBRA, erros, total, PlanilhaOrcadoFaturado::orcada);
            List<LinhaFaturada> linhasFaturado = faturado == null ? List.of() : linhas(faturado, ABA_FATURADO, erros, total, PlanilhaOrcadoFaturado::faturada);
            return new Conteudo(linhasMateriais, linhasMaoDeObra, linhasFaturado, erros);
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvarianteVioladaException invariante) {
                throw invariante;
            }
            throw new InvarianteVioladaException("Não foi possível ler a planilha. Confira se é o XLSX do modelo.");
        }
    }

    /** Aba cujo nome normalizado contém o trecho; "Mão de obra orçado" e "Mão de obra orçada" valem igual. */
    private static Sheet aba(Workbook workbook, String trecho) {
        for (Sheet aba : workbook) {
            if (LeitorPlanilhaImportacao.normalizarCabecalho(aba.getSheetName()).contains(trecho)) {
                return aba;
            }
        }
        return null;
    }

    private interface Conversor<T> {
        T converter(Celulas celulas, int linha);
    }

    private static <T> List<T> linhas(Sheet aba, String nome, List<Erro> erros, int[] total, Conversor<T> conversor) {
        Row cabecalho = aba.getRow(aba.getFirstRowNum());
        List<T> linhas = new ArrayList<>();
        if (cabecalho == null) {
            return linhas;
        }
        Map<String, Integer> colunas = new HashMap<>();
        for (Cell celula : cabecalho) {
            String texto = celula.getCellType() == CellType.STRING ? celula.getStringCellValue() : null;
            if (texto != null && !texto.isBlank()) {
                colunas.putIfAbsent(LeitorPlanilhaImportacao.normalizarCabecalho(texto), celula.getColumnIndex());
            }
        }
        for (int r = aba.getFirstRowNum() + 1; r <= aba.getLastRowNum(); r++) {
            Row row = aba.getRow(r);
            Celulas celulas = new Celulas(row, colunas);
            if (row == null || celulas.vazia()) {
                continue;
            }
            if (++total[0] > LIMITE_LINHAS) {
                throw new InvarianteVioladaException("A planilha passa do limite de " + LIMITE_LINHAS + " linhas. Envie só as obras do relatório.");
            }
            try {
                linhas.add(conversor.converter(celulas, r + 1));
            } catch (InvarianteVioladaException e) {
                erros.add(new Erro(nome, r + 1, celulas.obraOuNula(), e.getMessage()));
            }
        }
        return linhas;
    }

    private static LinhaOrcada orcada(Celulas c, int linha) {
        String obra = c.obra();
        OperacaoItemKit operacao = c.operacao();
        String codigo = c.obrigatorio("Código", "CODIGO");
        BigDecimal quantidade = c.numero("Quantidade", "QUANTIDADE");
        BigDecimal unitario = c.numero("Valor unitário", "VALOR_UNITARIO_R", "VALOR_UNITARIO");
        BigDecimal total = c.numero("Valor total", "VALOR_TOTAL_R", "VALOR_TOTAL");
        if (quantidade == null) {
            throw new InvarianteVioladaException("informe a quantidade.");
        }
        if (total == null && unitario == null) {
            throw new InvarianteVioladaException("informe o valor total ou o valor unitário.");
        }
        BigDecimal unitarioAtualizado = c.numero("Valor unitário atualizado", "VALOR_UNITARIO_ATUALIZADO_R", "VALOR_UNITARIO_ATUALIZADO");
        BigDecimal totalAtualizado = c.numero("Valor total atualizado", "VALOR_TOTAL_ATUALIZADO_R", "VALOR_TOTAL_ATUALIZADO");
        if (totalAtualizado == null && unitarioAtualizado != null) {
            totalAtualizado = unitarioAtualizado.multiply(quantidade);
        }
        return new LinhaOrcada(linha, obra, operacao, codigo, c.texto("DESCRICAO"), c.texto("UNIDADE"), quantidade, unitario,
                dinheiro(total != null ? total : unitario.multiply(quantidade)), unitarioAtualizado, totalAtualizado == null ? null : dinheiro(totalAtualizado));
    }

    private static LinhaFaturada faturada(Celulas c, int linha) {
        String obra = c.obra();
        LocalDate data = c.data("Data da medição", "DATA_DA_MEDICAO", "DATA_MEDICAO", "DATA");
        OperacaoItemKit operacao = c.operacao();
        String codigo = c.obrigatorio("Código", "CODIGO");
        BigDecimal quantidade = c.numero("Quantidade", "QUANTIDADE");
        BigDecimal valor = c.numero("Valor total", "VALOR_TOTAL_R", "VALOR_TOTAL");
        if (data == null) {
            throw new InvarianteVioladaException("informe a data da medição.");
        }
        if (quantidade == null || valor == null) {
            throw new InvarianteVioladaException("informe a quantidade e o valor total faturados.");
        }
        String situacao = c.texto("SITUACAO");
        String debitoCredito = c.texto("DEBITO_CREDITO", "DEBITO_OU_CREDITO");
        String dc = debitoCredito == null ? "D" : debitoCredito.trim().toUpperCase(Locale.ROOT);
        if (!dc.startsWith("D") && !dc.startsWith("C")) {
            throw new InvarianteVioladaException("débito/crédito deve ser D ou C.");
        }
        return new LinhaFaturada(linha, obra, data, operacao, codigo, c.texto("DESCRICAO"), quantidade.abs(), dinheiro(valor.abs()),
                situacao == null ? "A" : situacao.trim().toUpperCase(Locale.ROOT), dc.startsWith("C"));
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /** Células da linha pelo cabeçalho normalizado. Número e data vêm do tipo da célula; texto aceita o formato brasileiro. */
    private record Celulas(Row row, Map<String, Integer> colunas) {

        boolean vazia() {
            for (int indice : colunas.values()) {
                if (texto(row.getCell(indice)) != null) {
                    return false;
                }
            }
            return true;
        }

        String obra() {
            return obrigatorio("Obra", "OBRA", "NUMERO_DA_OBRA", "NUMERO_OBRA");
        }

        String obraOuNula() {
            return texto("OBRA", "NUMERO_DA_OBRA", "NUMERO_OBRA");
        }

        OperacaoItemKit operacao() {
            String valor = obrigatorio("Operação", "OPERACAO");
            String normalizado = LeitorPlanilhaImportacao.normalizarCabecalho(valor);
            return switch (normalizado) {
                case "I", "INSTALAR", "INSTALACAO" -> OperacaoItemKit.INSTALAR;
                case "R", "RETIRAR", "RETIRADA" -> OperacaoItemKit.RETIRAR;
                default -> throw new InvarianteVioladaException("operação \"" + valor + "\" inválida; use Instalar ou Retirar.");
            };
        }

        String obrigatorio(String rotulo, String... nomes) {
            String valor = texto(nomes);
            if (valor == null) {
                throw new InvarianteVioladaException("informe " + rotulo.toLowerCase(Locale.ROOT) + ".");
            }
            return valor;
        }

        String texto(String... nomes) {
            Cell celula = celula(nomes);
            return celula == null ? null : texto(celula);
        }

        BigDecimal numero(String rotulo, String... nomes) {
            Cell celula = celula(nomes);
            if (celula == null) {
                return null;
            }
            CellType tipo = tipo(celula);
            if (tipo == CellType.NUMERIC) {
                return BigDecimal.valueOf(celula.getNumericCellValue()).stripTrailingZeros();
            }
            String texto = texto(celula);
            if (texto == null) {
                return null;
            }
            try {
                return LeitorPlanilhaImportacao.interpretarDecimal(texto);
            } catch (InvarianteVioladaException e) {
                throw new InvarianteVioladaException(rotulo.toLowerCase(Locale.ROOT) + " \"" + texto + "\" não é um número.");
            }
        }

        LocalDate data(String rotulo, String... nomes) {
            Cell celula = celula(nomes);
            if (celula == null) {
                return null;
            }
            if (tipo(celula) == CellType.NUMERIC) {
                return DateUtil.getLocalDateTime(celula.getNumericCellValue()).toLocalDate();
            }
            String texto = texto(celula);
            if (texto == null) {
                return null;
            }
            try {
                return texto.contains("/") ? LocalDate.parse(texto, DATA_BR) : LocalDate.parse(texto);
            } catch (DateTimeParseException e) {
                throw new InvarianteVioladaException(rotulo.toLowerCase(Locale.ROOT) + " \"" + texto + "\" inválida; use dd/mm/aaaa.");
            }
        }

        private Cell celula(String... nomes) {
            if (row == null) {
                return null;
            }
            for (String nome : nomes) {
                Integer indice = colunas.get(nome);
                if (indice != null && row.getCell(indice) != null) {
                    return row.getCell(indice);
                }
            }
            return null;
        }

        private static CellType tipo(Cell celula) {
            return celula.getCellType() == CellType.FORMULA ? celula.getCachedFormulaResultType() : celula.getCellType();
        }

        /** Número inteiro (código, obra) sai sem ",0"; texto sai aparado. */
        private static String texto(Cell celula) {
            String valor = switch (tipo(celula)) {
                case STRING -> celula.getStringCellValue();
                case NUMERIC -> {
                    BigDecimal numero = BigDecimal.valueOf(celula.getNumericCellValue()).stripTrailingZeros();
                    yield numero.scale() <= 0 ? numero.toBigInteger().toString() : numero.toPlainString();
                }
                case BOOLEAN -> String.valueOf(celula.getBooleanCellValue());
                default -> null;
            };
            return valor == null || valor.isBlank() ? null : valor.trim();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Modelo
    // ═══════════════════════════════════════════════════════════════════════

    /** Modelo com instruções, as três abas vazias e as listas de operação, situação e débito/crédito. */
    static byte[] modelo() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            CellStyle titulo = estilo(workbook, true, false);
            CellStyle cabecalho = estilo(workbook, true, true);
            Sheet instrucoes = workbook.createSheet("Como preencher");
            String[][] texto = {
                    {"Relatório de medição detalhado", null, null},
                    {"1. Preencha as abas", "Materiais orçados, Mão de obra orçada e Faturado, a partir da linha 2. Mantenha os cabeçalhos.", null},
                    {"2. Use os dados da sua base", "Copie códigos, quantidades e valores do orçamento e do faturamento do contrato.", null},
                    {"3. Envie no sistema", "Em Obras › Medição e Fechamento › Relatório Detalhado. As obras são reconhecidas pelo número.", null},
                    {"4. Escolha as obras", "Informe a data de corte, o fiscal e o responsável e gere o pacote com Excel, PDF e fotos.", null},
                    {"Sem faturamento", "Deixe a aba Faturado só com os cabeçalhos.", null},
                    {"Buscado no sistema", "Execução, medições geradas e fotos do book não precisam ser preenchidas.", null},
                    {"Linhas repetidas", "No orçamento, linhas do mesmo item, obra e operação são somadas. No faturado, não copie a mesma medição duas vezes.", null},
                    {null, null, null},
                    {"Campo", "Como preencher", "Exemplo fictício"},
                    {"Obra", "Número da obra cadastrada.", "500000001"},
                    {"Operação", "Instalar ou Retirar (I e R também valem).", "Instalar"},
                    {"Código", "Código do material ou serviço. Preserve zeros à esquerda digitando o código como texto.", "1030"},
                    {"Descrição", "Descrição do material ou serviço.", "Serviço de exemplo"},
                    {"Quantidade", "Quantidade orçada ou faturada, como número.", "10"},
                    {"Valor unitário (R$)", "Preço unitário orçado. Opcional quando houver valor total.", "25"},
                    {"Valor total (R$)", "Total orçado ou faturado, como número.", "250"},
                    {"Valores atualizados (R$)", "Opcionais. Quando preenchidos, são o orçamento vigente.", ""},
                    {"Unidade", "Opcional, no orçamento.", "UN"},
                    {"Data da medição", "Data do faturamento (dd/mm/aaaa). Só entra o que for até a data de corte.", "20/09/2026"},
                    {"Situação", "A = aprovado. Outras situações ficam fora do faturado e aparecem para conferência.", "A"},
                    {"Débito/crédito", "D = débito; C = crédito. Crédito subtrai do faturado e aparece para conferência.", "D"},
            };
            for (int i = 0; i < texto.length; i++) {
                Row row = instrucoes.createRow(i);
                for (int c = 0; c < 3; c++) {
                    if (texto[i][c] != null) {
                        Cell celula = row.createCell(c);
                        celula.setCellValue(texto[i][c]);
                        if (i == 0 || i == 9 || (c == 0 && i < 9)) {
                            celula.setCellStyle(i == 9 ? cabecalho : titulo);
                        }
                    }
                }
            }
            instrucoes.setColumnWidth(0, 26 * 256);
            instrucoes.setColumnWidth(1, 95 * 256);
            instrucoes.setColumnWidth(2, 20 * 256);
            abaDoModelo(workbook, ABA_MATERIAIS, CABECALHO_ORCADO, cabecalho, Map.of(1, new String[]{"Instalar", "Retirar"}));
            abaDoModelo(workbook, ABA_MAO_DE_OBRA, CABECALHO_ORCADO, cabecalho, Map.of(1, new String[]{"Instalar", "Retirar"}));
            abaDoModelo(workbook, ABA_FATURADO, CABECALHO_FATURADO, cabecalho,
                    Map.of(2, new String[]{"Instalar", "Retirar"}, 8, new String[]{"D", "C"}));
            workbook.write(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o modelo orçado × faturado.", e);
        }
    }

    private static void abaDoModelo(XSSFWorkbook workbook, String nome, String[] colunas, CellStyle estilo, Map<Integer, String[]> listas) {
        Sheet aba = workbook.createSheet(nome);
        Row row = aba.createRow(0);
        CellStyle textoPuro = workbook.createCellStyle();
        textoPuro.setDataFormat(workbook.createDataFormat().getFormat("@"));
        for (int c = 0; c < colunas.length; c++) {
            Cell celula = row.createCell(c);
            celula.setCellValue(colunas[c]);
            celula.setCellStyle(estilo);
            aba.setColumnWidth(c, Math.max(14, colunas[c].length() + 4) * 256);
            if (colunas[c].equals("Obra") || colunas[c].equals("Código")) {
                aba.setDefaultColumnStyle(c, textoPuro);
            }
        }
        aba.createFreezePane(0, 1);
        DataValidationHelper ajuda = aba.getDataValidationHelper();
        listas.forEach((coluna, valores) -> aba.addValidationData(ajuda.createValidation(ajuda.createExplicitListConstraint(valores),
                new CellRangeAddressList(1, LINHAS_DO_MODELO, coluna, coluna))));
    }

    private static CellStyle estilo(Workbook workbook, boolean negrito, boolean fundo) {
        CellStyle estilo = workbook.createCellStyle();
        Font fonte = workbook.createFont();
        fonte.setBold(negrito);
        estilo.setFont(fonte);
        if (fundo) {
            estilo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return estilo;
    }

    static <T> Map<String, List<T>> porObra(List<T> linhas, Function<T, String> obra) {
        Map<String, List<T>> mapa = new HashMap<>();
        for (T linha : linhas) {
            mapa.computeIfAbsent(obra.apply(linha).toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(linha);
        }
        return mapa;
    }
}
