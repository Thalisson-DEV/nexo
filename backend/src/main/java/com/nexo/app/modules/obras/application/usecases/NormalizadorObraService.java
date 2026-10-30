package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.domain.TipoObra;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Serviço utilitário responsável pela normalização, sanitização e conversão de dados do domínio de Obras.
 */
@Service
public class NormalizadorObraService {

    public static final DateTimeFormatter FORMATTER_DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Normaliza o número da obra:
     * - Remove espaços nas extremidades.
     * - Se inicia com prefixo "B-" ou "B" seguido puramente de números, remove o prefixo (ex.: "B-1228694" -> "1228694").
     * - Preserva zeros à esquerda como texto (ex.: "0994094" -> "0994094").
     * - Códigos com exceções alfanuméricas (ex.: "B-SOL1290", "SOL1290") são padronizados em caixa alta.
     */
    public String normalizarNumero(String numero) {
        if (numero == null || numero.isBlank()) {
            return null;
        }
        String limpo = numero.trim();
        if (limpo.matches("(?i)^B-?\\d+$")) {
            return limpo.replaceFirst("(?i)^B-?", "");
        }
        return limpo.toUpperCase(Locale.ROOT);
    }

    /**
     * Normaliza o título da obra:
     * - Converte para CAIXA ALTA.
     * - Remove espaços duplicados e espaços nas pontas.
     */
    public String normalizarTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            return null;
        }
        return titulo.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /**
     * Converte o tipo operacional informado. Arquivos e payloads legados sem o campo
     * continuam representando uma obra comum.
     */
    public TipoObra parseTipoObra(String tipoObra) {
        if (tipoObra == null || tipoObra.isBlank()) {
            return TipoObra.OBRA;
        }
        String normalizado = java.text.Normalizer.normalize(tipoObra.trim(), java.text.Normalizer.Form.NFKC)
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return TipoObra.valueOf(normalizado);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Tipo de obra inválido: '" + tipoObra + "'. Valores aceitos: OBRA, CORRETIVA, PREVENTIVA ou LPT.");
        }
    }

    /**
     * Faz o parsing de datas no formato padrão brasileiro (dd/MM/yyyy) ou ISO (yyyy-MM-dd).
     */
    public LocalDate parseData(String dataStr) {
        if (dataStr == null || dataStr.isBlank()) {
            return null;
        }
        String s = dataStr.trim();
        try {
            return LocalDate.parse(s, FORMATTER_DATA_BR);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("d/M/yyyy"));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida: '" + dataStr + "'. Formato esperado: dd/MM/yyyy.");
        }
    }

    /**
     * Faz o parsing de coordenadas GPS (latitude/longitude), tratando nulos, zeros ou formatos inválidos.
     */
    public Double parseCoordenada(String coordStr) {
        if (coordStr == null || coordStr.isBlank()) {
            return null;
        }
        String s = coordStr.trim().replace(',', '.');
        if (s.equals("0") || s.equals("0.0") || s.equals("-00.00000") || s.equals("0.00000") || s.equals("-0.0")) {
            return null;
        }
        // Descarta strings com múltiplos pontos (máscaras corrompidas de importações legadas)
        if (s.chars().filter(ch -> ch == '.').count() > 1) {
            return null;
        }
        try {
            double val = Double.parseDouble(s);
            if (Double.isNaN(val) || Double.isInfinite(val) || Math.abs(val) > 180.0) {
                return null;
            }
            return val;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Faz o parsing de valores monetários (ex.: "15000.00", "15.000,00", "R$ 15.000,00").
     */
    public BigDecimal parseValor(String valorStr) {
        if (valorStr == null || valorStr.isBlank()) {
            return null;
        }
        String limpo = valorStr.replaceAll("[R$\\s]", "");
        if (limpo.contains(",") && limpo.contains(".")) {
            limpo = limpo.replace(".", "").replace(",", ".");
        } else if (limpo.contains(",")) {
            limpo = limpo.replace(",", ".");
        }
        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor monetário inválido: '" + valorStr + "'.");
        }
    }
}
