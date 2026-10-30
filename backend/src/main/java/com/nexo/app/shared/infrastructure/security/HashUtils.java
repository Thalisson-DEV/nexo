package com.nexo.app.shared.infrastructure.security;

import org.springframework.util.DigestUtils;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Utilitário para geração de Blind Indices (Hashes determinísticos).
 * Permite a busca exata em campos criptografados sem revelar o conteúdo original.
 */
public class HashUtils {

    // Em produção, este salt (Pepper) deve vir de uma variável de ambiente segura.
    private static final String PEPPER = "NEXO_SECURE_PEPPER_2026";

    /**
     * Gera um hash SHA-256 determinístico para busca exata.
     */
    public static String gerarBlindIndex(String valor) {
        if (valor == null || valor.isBlank()) return null;
        
        // Normalizamos para evitar divergências por espaços ou caixa.
        String normalizado = valor.trim().toLowerCase(Locale.ROOT);
        String valorComPepper = normalizado + PEPPER;
        
        return DigestUtils.md5DigestAsHex(valorComPepper.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gera um conjunto delimitado de blind indices para o texto completo e seus
     * termos pesquisaveis. O valor original nao compoe a coluna de busca.
     */
    public static String gerarIndicesBusca(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        Set<String> termos = new LinkedHashSet<>();
        String normalizado = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        termos.add(normalizado);
        for (String termo : normalizado.split("[^\\p{L}\\p{N}@._+\\-]+")) {
            if (termo.length() >= 3) {
                termos.add(termo);
                String digitos = termo.replaceAll("\\D", "");
                if (digitos.length() >= 4) {
                    termos.add(digitos);
                }
            }
        }
        String todosOsDigitos = normalizado.replaceAll("\\D", "");
        if (todosOsDigitos.length() >= 4) {
            termos.add(todosOsDigitos);
        }
        return termos.stream()
                .map(HashUtils::gerarBlindIndex)
                .map(hash -> "|" + hash + "|")
                .reduce("", String::concat);
    }
}
