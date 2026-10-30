package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Validação de tamanho, MIME declarado e assinatura dos arquivos privados da reunião. */
final class ArquivoPrivadoReuniao {
    private static final long LIMITE = 15L * 1024 * 1024;
    private static final Map<String, String> FOTOS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp",
            "image/heic", "heic", "image/heif", "heif");
    private static final Map<String, String> DOCUMENTOS = Map.ofEntries(
            Map.entry("application/pdf", "pdf"),
            Map.entry("image/jpeg", "jpg"), Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"), Map.entry("image/heic", "heic"),
            Map.entry("image/heif", "heif"));

    private ArquivoPrivadoReuniao() {}

    static Recebido foto(MultipartFile arquivo, String prefixo) {
        return receber(arquivo, prefixo, FOTOS,
                "Envie uma fotografia JPEG, PNG, WebP ou HEIC válida.");
    }

    static Recebido documentoAssinado(MultipartFile arquivo, String prefixo) {
        return receber(arquivo, prefixo, DOCUMENTOS,
                "Envie a cópia assinada em PDF, JPEG, PNG, WebP ou HEIC.");
    }

    static Recebido evidenciaComunicacao(MultipartFile arquivo, String prefixo) {
        return receber(arquivo, prefixo, DOCUMENTOS,
                "Envie a evidência em PDF, JPEG, PNG, WebP ou HEIC.");
    }

    private static Recebido receber(MultipartFile arquivo, String prefixo,
                                     Map<String, String> permitidos, String recusa) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Envie um arquivo não vazio.");
        }
        if (arquivo.getSize() > LIMITE) {
            throw new InvarianteVioladaException("O arquivo excede o limite de 15 MB.");
        }
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao ler o arquivo enviado.", e);
        }
        String declarado = normalizar(arquivo.getContentType());
        String detectado = detectar(bytes);
        if (detectado == null || !permitidos.containsKey(detectado)
                || (declarado != null && permitidos.containsKey(declarado) && !declarado.equals(detectado))) {
            throw new InvarianteVioladaException(recusa);
        }
        String extensao = permitidos.get(detectado);
        String nome = nomeSeguro(arquivo.getOriginalFilename(), extensao);
        String chave = prefixo + "/" + UUID.randomUUID() + "." + extensao;
        return new Recebido(bytes, nome, detectado, chave, sha256(bytes));
    }

    private static String detectar(byte[] b) {
        if (b.length >= 4 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff)
            return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 0x50 && b[2] == 0x4e
                && b[3] == 0x47 && b[4] == 0x0d && b[5] == 0x0a && b[6] == 0x1a && b[7] == 0x0a)
            return "image/png";
        if (b.length >= 12 && ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WEBP"))
            return "image/webp";
        if (b.length >= 5 && ascii(b, 0, 5).equals("%PDF-")) return "application/pdf";
        if (b.length >= 12 && ascii(b, 4, 4).equals("ftyp")) {
            String marca = ascii(b, 8, 4).toLowerCase(Locale.ROOT);
            if (marca.startsWith("heic") || marca.startsWith("heix") || marca.startsWith("hevc")
                    || marca.startsWith("hevx") || marca.equals("mif1") || marca.equals("msf1")) {
                return marca.startsWith("hei") || marca.startsWith("hev") ? "image/heic" : "image/heif";
            }
        }
        return null;
    }

    private static String ascii(byte[] bytes, int inicio, int tamanho) {
        return new String(bytes, inicio, tamanho, StandardCharsets.US_ASCII);
    }

    private static String nomeSeguro(String original, String extensao) {
        String nome = StringUtils.hasText(original)
                ? StringUtils.getFilename(StringUtils.cleanPath(original)) : "arquivo." + extensao;
        if (!StringUtils.hasText(nome)) nome = "arquivo." + extensao;
        return nome.length() > 255 ? nome.substring(nome.length() - 255) : nome;
    }

    private static String normalizar(String tipo) {
        if (!StringUtils.hasText(tipo)) return null;
        int ponto = tipo.indexOf(';');
        return (ponto < 0 ? tipo : tipo.substring(0, ponto)).trim().toLowerCase(Locale.ROOT);
    }

    static String sha256(String texto) {
        return sha256(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }

    record Recebido(byte[] bytes, String nomeArquivo, String contentType,
                    String objectKey, String sha256) {
        long tamanho() { return bytes.length; }
    }
}
