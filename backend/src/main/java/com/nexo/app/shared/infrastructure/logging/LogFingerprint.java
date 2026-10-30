package com.nexo.app.shared.infrastructure.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Produz uma correlação estável para valores livres sem copiá-los para o log.
 *
 * <p>Somente 64 bits do SHA-256 são exibidos: o bastante para correlacionar
 * tentativas operacionais, sem transformar o log em repositório de chaves,
 * tokens ou texto controlado pelo cliente. Como a saída contém apenas
 * caracteres ASCII fixos, CR/LF e demais caracteres de controle não chegam ao
 * sink de logging.</p>
 */
public final class LogFingerprint {

    private static final int HEX_EXIBIDO = 16;

    private LogFingerprint() {
    }

    public static String de(String valor) {
        if (valor == null) {
            return "sha256=ausente;length=0";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            String hexadecimal = HexFormat.of().formatHex(digest);
            return "sha256=" + hexadecimal.substring(0, HEX_EXIBIDO)
                    + ";length=" + valor.length();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }
}
