package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@RequiredArgsConstructor
public class ArmazenamentoEvidenciaGovernancaSstService {

    static final long TAMANHO_MAXIMO = 15L * 1024 * 1024;
    private static final String PREFIXO = "sst/governanca/evidencias";

    private final ObjectStorageService storage;

    public Arquivo armazenar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Envie um arquivo não vazio.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new InvarianteVioladaException("O arquivo excede o limite de 15 MB.");
        }
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new ArmazenamentoException("Não foi possível ler a evidência enviada.", e);
        }
        TipoDetectado tipo = detectar(bytes);
        String nome = nomeSeguro(arquivo.getOriginalFilename(), tipo.extensao());
        String chave = PREFIXO + "/" + UUID.randomUUID() + "." + tipo.extensao();
        storage.enviar(chave, new ByteArrayInputStream(bytes), bytes.length, tipo.mime());
        return new Arquivo(nome, chave, tipo.mime(), bytes.length, sha256(bytes));
    }

    public void remover(String objectKey) {
        storage.remover(objectKey);
    }

    private static TipoDetectado detectar(byte[] bytes) {
        if (bytes.length >= 4 && u(bytes[0]) == 0xFF && u(bytes[1]) == 0xD8 && u(bytes[2]) == 0xFF)
            return new TipoDetectado("image/jpeg", "jpg");
        if (bytes.length >= 8 && u(bytes[0]) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G' && u(bytes[4]) == 0x0D && u(bytes[5]) == 0x0A)
            return new TipoDetectado("image/png", "png");
        if (bytes.length >= 12 && ascii(bytes, 0, 4).equals("RIFF")
                && ascii(bytes, 8, 4).equals("WEBP"))
            return new TipoDetectado("image/webp", "webp");
        if (bytes.length >= 12 && ascii(bytes, 4, 4).equals("ftyp")) {
            String marca = ascii(bytes, 8, 4).toLowerCase(Locale.ROOT);
            if (marca.startsWith("hei") || marca.startsWith("hev") || marca.equals("mif1"))
                return new TipoDetectado("image/heic", "heic");
        }
        if (bytes.length >= 5 && ascii(bytes, 0, 5).equals("%PDF-"))
            return new TipoDetectado("application/pdf", "pdf");
        if (ehDocx(bytes))
            return new TipoDetectado(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        throw new InvarianteVioladaException(
                "Tipo de evidência não suportado. Aceitos: JPEG, PNG, WebP, HEIC, PDF e DOCX.");
    }

    private static boolean ehDocx(byte[] bytes) {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') return false;
        boolean contentTypes = false;
        boolean word = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int inspecionadas = 0;
            while ((entry = zip.getNextEntry()) != null && inspecionadas++ < 300) {
                String nome = entry.getName();
                if ("[Content_Types].xml".equals(nome)) contentTypes = true;
                if (nome.startsWith("word/")) word = true;
                if (contentTypes && word) return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new ArmazenamentoException("SHA-256 indisponível na JVM.", e);
        }
    }

    private static String nomeSeguro(String original, String extensao) {
        String nome = StringUtils.hasText(original)
                ? StringUtils.getFilename(StringUtils.cleanPath(original)) : null;
        if (!StringUtils.hasText(nome)) nome = "evidencia." + extensao;
        return nome.length() > 255 ? nome.substring(nome.length() - 255) : nome;
    }

    private static int u(byte valor) {
        return Byte.toUnsignedInt(valor);
    }

    private static String ascii(byte[] bytes, int inicio, int tamanho) {
        if (bytes.length < inicio + tamanho) return "";
        return new String(bytes, inicio, tamanho, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private record TipoDetectado(String mime, String extensao) {}

    public record Arquivo(String nomeArquivo, String objectKey, String contentType,
                          long tamanhoBytes, String hashConteudo) {}
}
