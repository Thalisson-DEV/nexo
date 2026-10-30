package com.nexo.app.modules.assistente.application.conexoes;

import com.nexo.app.shared.infrastructure.security.AcessoExterno;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Geracao e hash das credenciais das conexoes de IA. As credenciais tem 256 bits aleatorios, entao o
 * SHA-256 puro basta para guarda-las (nao ha o que adivinhar por dicionario). O prefixo identifica o
 * tipo e tira a credencial do caminho do JWT no filtro de seguranca.
 */
public final class CredenciaisIa {

    public static final String PREFIXO_ACESSO = AcessoExterno.PREFIXO + "at_";
    public static final String PREFIXO_RENOVACAO = AcessoExterno.PREFIXO + "rt_";
    public static final String PREFIXO_CHAVE = AcessoExterno.PREFIXO + "key_";
    public static final String PREFIXO_CODIGO = AcessoExterno.PREFIXO + "code_";
    public static final String PREFIXO_CLIENTE = "ces-app-";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CredenciaisIa() {
    }

    public static String gerar(String prefixo) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return prefixo + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String gerarClientId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return PREFIXO_CLIENTE + HexFormat.of().formatHex(bytes);
    }

    public static String hash(String credencial) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(credencial.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    /** O bastante da chave para a pessoa reconhecer qual e qual na lista. */
    public static String prefixoVisivel(String chave) {
        return chave.substring(0, Math.min(chave.length(), PREFIXO_CHAVE.length() + 4)) + "…";
    }
}
