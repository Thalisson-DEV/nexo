package com.nexo.app.modules.assistente.application.files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.assistente.application.results.TabularData;
import com.nexo.app.modules.assistente.domain.ArquivoAssistente;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Extracao de conteudo de anexos (pipeline inspirado no extract_file_text do Onyx): PDF (PDFBox), XLSX em
 * streaming SAX (memoria constante), XLS, CSV com deteccao de separador e codificacao, JSON, DOCX, texto,
 * HTML e imagens. Arquivos grandes nunca vao inteiros ao contexto: o agente le por trechos, busca ou usa Python.
 */
@Component
public class ArquivoParser {

    private static final Pattern INTEIRO = Pattern.compile("-?\\d{1,18}");
    private static final Pattern DECIMAL_PONTO = Pattern.compile("-?\\d+\\.\\d+");
    private static final Pattern DECIMAL_BR = Pattern.compile("-?\\d{1,3}(\\.\\d{3})*,\\d+|-?\\d+,\\d+");
    private static final Pattern MILHAR_BR = Pattern.compile("-?\\d{1,3}(\\.\\d{3})+");
    private static final Pattern DATA_ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern DATA_HORA_ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2})?.*");
    private static final Pattern DATA_BR = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");

    private final ObjectMapper objectMapper;

    public ArquivoParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static ArquivoAssistente.Categoria categoria(String nome, String tipoConteudo, byte[] bytes) {
        String extensao = extensao(nome);
        if (comeca(bytes, "%PDF".getBytes(StandardCharsets.US_ASCII))) return ArquivoAssistente.Categoria.PDF;
        if (comeca(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G'}) || comeca(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})
                || comeca(bytes, "GIF8".getBytes(StandardCharsets.US_ASCII))
                || (comeca(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII)) && "webp".equals(extensao))) {
            return ArquivoAssistente.Categoria.IMAGEM;
        }
        return switch (extensao) {
            case "xlsx", "xlsm", "xls" -> ArquivoAssistente.Categoria.PLANILHA;
            case "csv", "tsv" -> ArquivoAssistente.Categoria.CSV;
            case "json" -> ArquivoAssistente.Categoria.JSON;
            case "txt", "md", "log", "xml", "yml", "yaml", "sql", "html", "htm" -> ArquivoAssistente.Categoria.TEXTO;
            case "docx" -> ArquivoAssistente.Categoria.DOCUMENTO;
            case "png", "jpg", "jpeg", "gif", "webp" -> ArquivoAssistente.Categoria.IMAGEM;
            case "pdf" -> ArquivoAssistente.Categoria.PDF;
            default -> tipoConteudo != null && tipoConteudo.startsWith("text/")
                    ? ArquivoAssistente.Categoria.TEXTO : ArquivoAssistente.Categoria.OUTRO;
        };
    }

    public ArquivoExtraido extrair(String nome, byte[] bytes, int maxChars, int maxLinhas) throws IOException {
        ArquivoAssistente.Categoria categoria = categoria(nome, null, bytes);
        String extensao = extensao(nome);
        return switch (categoria) {
            case PDF -> pdf(bytes, maxChars);
            case PLANILHA -> "xls".equals(extensao) || comeca(bytes, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0})
                    ? xls(bytes, maxLinhas) : xlsx(bytes, maxLinhas);
            case CSV -> csv(nome, bytes, maxLinhas, maxChars);
            case JSON -> json(bytes, maxChars, maxLinhas);
            case DOCUMENTO -> docx(bytes, maxChars);
            case TEXTO -> texto(extensao, bytes, maxChars);
            case IMAGEM -> imagem(bytes);
            case OUTRO -> new ArquivoExtraido(categoria, null, List.of(),
                    Map.of("aviso", "Formato sem extracao automatica; use executar_python se necessario."));
        };
    }

    // ------------------------------------------------------------------------------------------ PDF

    private ArquivoExtraido pdf(byte[] bytes, int maxChars) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int paginas = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder texto = new StringBuilder();
            boolean truncado = false;
            for (int pagina = 1; pagina <= paginas; pagina++) {
                stripper.setStartPage(pagina);
                stripper.setEndPage(pagina);
                String conteudo = stripper.getText(document).strip();
                if (conteudo.isEmpty()) continue;
                texto.append("\n[Pagina ").append(pagina).append("]\n").append(conteudo).append('\n');
                if (texto.length() > maxChars) {
                    truncado = true;
                    break;
                }
            }
            Map<String, Object> metadados = new LinkedHashMap<>();
            metadados.put("paginas", paginas);
            String extraido = limitar(texto.toString().strip(), maxChars);
            metadados.put("caracteres", extraido.length());
            if (truncado) metadados.put("texto_truncado", true);
            if (extraido.isBlank()) {
                metadados.put("aviso", "PDF sem texto extraivel (provavelmente digitalizado).");
            }
            return new ArquivoExtraido(ArquivoAssistente.Categoria.PDF, extraido, List.of(), metadados);
        }
    }

    // ----------------------------------------------------------------------------------------- XLSX

    private ArquivoExtraido xlsx(byte[] bytes, int maxLinhas) throws IOException {
        List<ArquivoExtraido.Planilha> planilhas = new ArrayList<>();
        try (OPCPackage pacote = OPCPackage.open(new ByteArrayInputStream(bytes))) {
            XSSFReader reader = new XSSFReader(pacote);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pacote);
            StylesTable estilos = reader.getStylesTable();
            XSSFReader.SheetIterator folhas = reader.getSheetIterator();
            while (folhas.hasNext() && planilhas.size() < 30) {
                try (InputStream folha = folhas.next()) {
                    ColetorLinhas coletor = new ColetorLinhas(maxLinhas + 1);
                    XMLReader xml = XMLHelper.newXMLReader();
                    xml.setContentHandler(new XSSFSheetXMLHandler(estilos, strings, coletor, new FormatadorBruto(), false));
                    try {
                        xml.parse(new InputSource(folha));
                    } catch (LimiteAtingido ignored) {
                        // leitura interrompida ao atingir o limite de linhas
                    }
                    planilhas.add(new ArquivoExtraido.Planilha(folhas.getSheetName(), tabela(coletor.linhas, maxLinhas, coletor.total())));
                }
            }
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Nao foi possivel ler a planilha XLSX: " + exception.getMessage(), exception);
        }
        return planilhas(planilhas);
    }

    private static final class LimiteAtingido extends RuntimeException {
        LimiteAtingido() {
            super(null, null, false, false);
        }
    }

    private static final class ColetorLinhas implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final int limite;
        private final List<List<String>> linhas = new ArrayList<>();
        private List<String> atual;
        private int contadas;

        ColetorLinhas(int limite) {
            this.limite = limite;
        }

        @Override
        public void startRow(int rowNum) {
            atual = new ArrayList<>();
        }

        @Override
        public void endRow(int rowNum) {
            contadas++;
            if (atual.stream().anyMatch(valor -> valor != null && !valor.isBlank())) linhas.add(atual);
            if (linhas.size() >= limite) throw new LimiteAtingido();
        }

        @Override
        public void cell(String referencia, String valor, XSSFComment comentario) {
            int coluna = referencia == null ? atual.size() : new CellReference(referencia).getCol();
            while (atual.size() < coluna) atual.add(null);
            atual.add(valor);
        }

        int total() {
            return Math.max(contadas, linhas.size());
        }
    }

    /** Devolve numeros sem formatacao local e datas em ISO-8601 para analise confiavel. */
    private static final class FormatadorBruto extends DataFormatter {
        FormatadorBruto() {
            super(Locale.ROOT);
        }

        @Override
        public String formatRawCellContents(double valor, int indice, String formato) {
            return bruto(valor, indice, formato);
        }

        @Override
        public String formatRawCellContents(double valor, int indice, String formato, boolean use1904) {
            return bruto(valor, indice, formato);
        }

        private static String bruto(double valor, int indice, String formato) {
            if (DateUtil.isADateFormat(indice, formato) && DateUtil.isValidExcelDate(valor)) {
                LocalDateTime data = DateUtil.getLocalDateTime(valor);
                return data.toLocalTime().toSecondOfDay() == 0 ? data.toLocalDate().toString() : data.toString();
            }
            return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString();
        }
    }

    // ------------------------------------------------------------------------------------------ XLS

    private ArquivoExtraido xls(byte[] bytes, int maxLinhas) throws IOException {
        List<ArquivoExtraido.Planilha> planilhas = new ArrayList<>();
        try (Workbook workbook = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for (int i = 0; i < workbook.getNumberOfSheets() && i < 30; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                List<List<String>> linhas = new ArrayList<>();
                int total = 0;
                for (Row row : sheet) {
                    total++;
                    if (linhas.size() > maxLinhas) continue;
                    List<String> valores = new ArrayList<>();
                    for (int c = 0; c < Math.max(0, row.getLastCellNum()); c++) {
                        Cell cell = row.getCell(c);
                        valores.add(cell == null ? null : valorCelula(cell));
                    }
                    if (valores.stream().anyMatch(valor -> valor != null && !valor.isBlank())) linhas.add(valores);
                }
                planilhas.add(new ArquivoExtraido.Planilha(sheet.getSheetName(), tabela(linhas, maxLinhas, total)));
            }
        }
        return planilhas(planilhas);
    }

    private static String valorCelula(Cell cell) {
        CellType tipo = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (tipo) {
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalTime().toSecondOfDay() == 0
                        ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                        : cell.getLocalDateTimeCellValue().toString()
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case STRING -> cell.getStringCellValue();
            default -> null;
        };
    }

    // ------------------------------------------------------------------------------------------ CSV

    private ArquivoExtraido csv(String nome, byte[] bytes, int maxLinhas, int maxChars) throws IOException {
        String texto = decodificar(bytes);
        if (texto.startsWith("\uFEFF")) texto = texto.substring(1);
        char separador = "tsv".equals(extensao(nome)) ? '\t' : detectarSeparador(texto);
        List<List<String>> linhas = new ArrayList<>();
        int total = 0;
        CSVFormat formato = CSVFormat.DEFAULT.builder().setDelimiter(separador).setIgnoreEmptyLines(true)
                .setTrim(true).build();
        try (CSVParser parser = CSVParser.parse(new StringReader(texto), formato)) {
            for (CSVRecord registro : parser) {
                total++;
                if (linhas.size() > maxLinhas) continue;
                List<String> valores = new ArrayList<>();
                registro.forEach(valores::add);
                linhas.add(valores);
            }
        }
        ArquivoExtraido extraido = planilhas(List.of(new ArquivoExtraido.Planilha(baseNome(nome), tabela(linhas, maxLinhas, total))));
        Map<String, Object> metadados = new LinkedHashMap<>(extraido.metadados());
        metadados.put("separador", String.valueOf(separador));
        return new ArquivoExtraido(ArquivoAssistente.Categoria.CSV, null, extraido.planilhas(), metadados);
    }

    private static char detectarSeparador(String texto) {
        String primeira = texto.lines().filter(linha -> !linha.isBlank()).findFirst().orElse("");
        char[] candidatos = {';', ',', '\t', '|'};
        char melhor = ',';
        long maior = -1;
        for (char candidato : candidatos) {
            long quantidade = primeira.chars().filter(c -> c == candidato).count();
            if (quantidade > maior) {
                maior = quantidade;
                melhor = candidato;
            }
        }
        return melhor;
    }

    // ----------------------------------------------------------------------------------------- JSON

    private ArquivoExtraido json(byte[] bytes, int maxChars, int maxLinhas) throws IOException {
        String texto = decodificar(bytes);
        JsonNode raiz = objectMapper.readTree(texto);
        List<ArquivoExtraido.Planilha> planilhas = new ArrayList<>();
        JsonNode lista = raiz.isArray() ? raiz : null;
        if (lista == null && raiz.isObject()) {
            Iterator<JsonNode> valores = raiz.elements();
            while (valores.hasNext()) {
                JsonNode valor = valores.next();
                if (valor.isArray() && valor.size() > 0 && valor.get(0).isObject()) {
                    lista = valor;
                    break;
                }
            }
        }
        if (lista != null && lista.size() > 0 && lista.get(0).isObject()) {
            List<Map<String, Object>> linhas = new ArrayList<>();
            for (JsonNode item : lista) {
                if (linhas.size() >= maxLinhas) break;
                Map<String, Object> linha = new LinkedHashMap<>();
                item.fields().forEachRemaining(entry -> linha.put(entry.getKey(),
                        entry.getValue().isValueNode() ? valorJson(entry.getValue()) : entry.getValue().toString()));
                linhas.add(linha);
            }
            TabularData dados = TabularData.fromMaps(linhas, lista.size() > maxLinhas);
            planilhas.add(new ArquivoExtraido.Planilha("dados", new TabularData(dados.columns(), dados.rows(), lista.size(),
                    lista.size() > maxLinhas)));
        }
        String bonito = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(raiz);
        Map<String, Object> metadados = new LinkedHashMap<>();
        metadados.put("caracteres", bonito.length());
        if (!planilhas.isEmpty()) metadados.put("registros", planilhas.getFirst().dados().totalRows());
        return new ArquivoExtraido(ArquivoAssistente.Categoria.JSON, limitar(bonito, maxChars), planilhas, metadados);
    }

    private static Object valorJson(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isIntegralNumber()) return node.asLong();
        if (node.isNumber()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        return node.asText();
    }

    // ------------------------------------------------------------------------------ DOCX / TEXTO

    private ArquivoExtraido docx(byte[] bytes, int maxChars) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String texto = limitar(extractor.getText().strip(), maxChars);
            return new ArquivoExtraido(ArquivoAssistente.Categoria.DOCUMENTO, texto, List.of(),
                    Map.of("caracteres", texto.length()));
        }
    }

    private ArquivoExtraido texto(String extensao, byte[] bytes, int maxChars) {
        String texto = decodificar(bytes);
        if ("html".equals(extensao) || "htm".equals(extensao)) texto = Jsoup.parse(texto).text();
        texto = limitar(texto.strip(), maxChars);
        return new ArquivoExtraido(ArquivoAssistente.Categoria.TEXTO, texto, List.of(), Map.of("caracteres", texto.length()));
    }

    private ArquivoExtraido imagem(byte[] bytes) {
        Map<String, Object> metadados = new LinkedHashMap<>();
        try {
            BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(bytes));
            if (imagem != null) {
                metadados.put("largura", imagem.getWidth());
                metadados.put("altura", imagem.getHeight());
            }
        } catch (IOException ignored) {
            // metadados de imagem sao opcionais
        }
        return new ArquivoExtraido(ArquivoAssistente.Categoria.IMAGEM, null, List.of(), metadados);
    }

    // ------------------------------------------------------------------------------ utilitarios

    private ArquivoExtraido planilhas(List<ArquivoExtraido.Planilha> planilhas) {
        Map<String, Object> metadados = new LinkedHashMap<>();
        metadados.put("planilhas", planilhas.stream().map(planilha -> Map.of(
                "nome", planilha.nome(),
                "linhas", planilha.dados().totalRows(),
                "colunas", planilha.dados().columns().stream().map(TabularData.Column::name).toList())).toList());
        return new ArquivoExtraido(ArquivoAssistente.Categoria.PLANILHA, null, planilhas, metadados);
    }

    /** Primeira linha nao vazia vira cabecalho; tipos inferidos por coluna. */
    static TabularData tabela(List<List<String>> linhasBrutas, int maxLinhas, int totalBruto) {
        if (linhasBrutas.isEmpty()) return new TabularData(List.of(), List.of(), 0, false);
        List<String> cabecalho = linhasBrutas.getFirst();
        int largura = linhasBrutas.stream().mapToInt(List::size).max().orElse(0);
        List<String> nomes = new ArrayList<>();
        Set<String> usados = new HashSet<>();
        for (int i = 0; i < largura; i++) {
            String bruto = i < cabecalho.size() && cabecalho.get(i) != null && !cabecalho.get(i).isBlank()
                    ? cabecalho.get(i) : "coluna_" + (i + 1);
            String nome = nomeColuna(bruto);
            String unico = nome;
            int sufixo = 2;
            while (!usados.add(unico)) unico = nome + "_" + sufixo++;
            nomes.add(unico);
        }
        List<List<String>> dados = linhasBrutas.subList(1, Math.min(linhasBrutas.size(), maxLinhas + 1));
        List<TabularData.ColumnType> tipos = new ArrayList<>();
        for (int i = 0; i < largura; i++) tipos.add(inferir(dados, i));
        List<List<Object>> linhas = new ArrayList<>();
        for (List<String> bruta : dados) {
            List<Object> linha = new ArrayList<>(largura);
            for (int i = 0; i < largura; i++) linha.add(converter(i < bruta.size() ? bruta.get(i) : null, tipos.get(i)));
            linhas.add(linha);
        }
        List<TabularData.Column> colunas = new ArrayList<>();
        for (int i = 0; i < largura; i++) colunas.add(new TabularData.Column(nomes.get(i), tipos.get(i)));
        int total = Math.max(linhas.size(), totalBruto - 1);
        return new TabularData(colunas, linhas, total, total > linhas.size());
    }

    private static String nomeColuna(String bruto) {
        String semAcento = java.text.Normalizer.normalize(bruto.trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String nome = semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (nome.isEmpty()) nome = "coluna";
        if (Character.isDigit(nome.charAt(0))) nome = "c_" + nome;
        return nome.length() > 60 ? nome.substring(0, 60) : nome;
    }

    private static TabularData.ColumnType inferir(List<List<String>> linhas, int indice) {
        boolean algum = false;
        boolean inteiro = true;
        boolean numero = true;
        boolean data = true;
        for (List<String> linha : linhas) {
            String valor = indice < linha.size() ? linha.get(indice) : null;
            if (valor == null || valor.isBlank()) continue;
            algum = true;
            String v = valor.trim();
            if (!INTEIRO.matcher(v).matches() && !MILHAR_BR.matcher(v).matches()) inteiro = false;
            if (!INTEIRO.matcher(v).matches() && !DECIMAL_PONTO.matcher(v).matches() && !DECIMAL_BR.matcher(v).matches()
                    && !MILHAR_BR.matcher(v).matches()) numero = false;
            if (!DATA_ISO.matcher(v).matches() && !DATA_BR.matcher(v).matches() && !DATA_HORA_ISO.matcher(v).matches()) data = false;
            if (!inteiro && !numero && !data) break;
        }
        if (!algum) return TabularData.ColumnType.TEXTO;
        if (inteiro) return TabularData.ColumnType.INTEIRO;
        if (numero) return TabularData.ColumnType.NUMERO;
        if (data) return TabularData.ColumnType.DATA;
        return TabularData.ColumnType.TEXTO;
    }

    private static Object converter(String valor, TabularData.ColumnType tipo) {
        if (valor == null || valor.isBlank()) return null;
        String v = valor.trim();
        try {
            return switch (tipo) {
                case INTEIRO -> Long.valueOf(v.replace(".", ""));
                case NUMERO -> {
                    if (DECIMAL_BR.matcher(v).matches()) yield Double.valueOf(v.replace(".", "").replace(",", "."));
                    if (MILHAR_BR.matcher(v).matches()) yield Double.valueOf(v.replace(".", ""));
                    yield Double.valueOf(v);
                }
                case DATA -> DATA_BR.matcher(v).matches()
                        ? v.substring(6, 10) + "-" + v.substring(3, 5) + "-" + v.substring(0, 2) : v;
                default -> v;
            };
        } catch (NumberFormatException exception) {
            return v;
        }
    }

    static String decodificar(byte[] bytes) {
        CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return utf8.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    public static String extensao(String nome) {
        if (nome == null) return "";
        int ponto = nome.lastIndexOf('.');
        return ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }

    private static String baseNome(String nome) {
        if (nome == null) return "dados";
        int ponto = nome.lastIndexOf('.');
        return ponto <= 0 ? nome : nome.substring(0, ponto);
    }

    private static boolean comeca(byte[] bytes, byte[] prefixo) {
        if (bytes == null || bytes.length < prefixo.length) return false;
        for (int i = 0; i < prefixo.length; i++) if (bytes[i] != prefixo[i]) return false;
        return true;
    }

    private static String limitar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
