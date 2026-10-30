package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.LoteImportacaoSolicitacao.Formato;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class LeitorArquivoImportacaoSolicitacaoTecnica {

    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
    public static final int MAXIMO_LINHAS = 2000;

    public ArquivoLido ler(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw erroArquivo("O arquivo de importacao e obrigatorio.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw erroArquivo("O arquivo excede o limite de 5 MiB.");
        }
        String nome = arquivo.getOriginalFilename() == null ? "importacao" : arquivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!nome.endsWith(".csv")) {
            throw erroArquivo("Formato nao suportado. Envie somente um arquivo .csv UTF-8 delimitado por ponto e virgula (;).");
        }
        try {
            byte[] bytes = arquivo.getBytes();
            return lerCsv(bytes);
        } catch (PreenchimentoInvalidoException ex) {
            throw ex;
        } catch (IOException ex) {
            throw erroArquivo("Nao foi possivel ler o arquivo enviado.");
        }
    }

    public static final List<String> CABECALHOS_LEGADO = List.of(
            "Chave Solicitacao", "Numero Obra", "Ocorrencia OC OS",
            "Almoxarifado Atendimento", "Codigo Equipe", "Data Prevista",
            "Observacao", "Tipo Movimentacao", "Codigo Material", "Quantidade",
            "Numero Reserva", "Data Criacao Reserva", "Agente Responsavel"
    );

    private ArquivoLido lerCsv(byte[] bytes) {
        String conteudo = decodificarUtf8(bytes);
        if (conteudo.startsWith("\uFEFF")) {
            conteudo = conteudo.substring(1);
        }
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setDelimiter(';')
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(false)
                .setCommentMarker('#')
                .build()
                .parse(new StringReader(conteudo))) {
            List<String> colunasEsperadas = validarCabecalhos(parser.getHeaderNames());
            List<LinhaArquivo> linhas = new ArrayList<>();
            for (CSVRecord registro : parser) {
                if (registro.size() != colunasEsperadas.size()) {
                    throw erroArquivo("A linha " + registro.getRecordNumber()
                            + " possui quantidade de colunas diferente do cabecalho.");
                }
                if (linhas.size() >= MAXIMO_LINHAS) {
                    throw erroArquivo("O arquivo excede o limite de 2.000 linhas de dados.");
                }
                Map<String, String> valores = new LinkedHashMap<>();
                Set<String> formulas = new LinkedHashSet<>();
                for (String cabecalho : ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS) {
                    String valor = registro.isMapped(cabecalho) ? registro.get(cabecalho) : "";
                    valores.put(cabecalho, valor);
                    if (pareceFormula(valor)) {
                        formulas.add(cabecalho);
                    }
                }
                linhas.add(new LinhaArquivo((int) registro.getRecordNumber() + 1, valores, formulas));
            }
            return new ArquivoLido(Formato.CSV, linhas);
        } catch (PreenchimentoInvalidoException ex) {
            throw ex;
        } catch (Exception ex) {
            throw erroArquivo("O CSV esta corrompido ou usa separador diferente de ponto e virgula.");
        }
    }

    private List<String> validarCabecalhos(List<String> recebidos) {
        List<String> esperados = ModeloImportacaoSolicitacaoTecnicaService.CABECALHOS;
        List<String> normalizados = recebidos == null ? List.of()
                : recebidos.stream().map(valor -> valor == null ? "" : valor.trim()).toList();
        if (esperados.equals(normalizados)) {
            return esperados;
        }
        if (CABECALHOS_LEGADO.equals(normalizados)) {
            return CABECALHOS_LEGADO;
        }
        throw erroArquivo("Cabecalhos invalidos. Use o modelo oficial sem renomear ou reordenar colunas.");
    }

    private String decodificarUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw erroArquivo("O CSV deve usar codificacao UTF-8.");
        }
    }

    private boolean pareceFormula(String valor) {
        if (valor == null) return false;
        String texto = valor.stripLeading();
        return !texto.isEmpty() && "=+-@".indexOf(texto.charAt(0)) >= 0;
    }

    private PreenchimentoInvalidoException erroArquivo(String mensagem) {
        return new PreenchimentoInvalidoException(Map.of("arquivo", mensagem));
    }

    public record ArquivoLido(Formato formato, List<LinhaArquivo> linhas) {
        public ArquivoLido {
            linhas = linhas == null ? List.of() : List.copyOf(linhas);
        }
    }

    public record LinhaArquivo(int numero, Map<String, String> valores, Set<String> camposFormula) {
        public LinhaArquivo {
            valores = valores == null ? Map.of() : Map.copyOf(valores);
            camposFormula = camposFormula == null ? Set.of() : Set.copyOf(camposFormula);
        }

        public String valor(String campo) {
            return valores.get(campo);
        }
    }
}
