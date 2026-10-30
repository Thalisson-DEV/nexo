package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.DocumentoSstDownload;
import com.nexo.app.modules.sst.domain.ArquivoPrivadoDocumentoSst;
import com.nexo.app.modules.sst.domain.DocumentoSst;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Valida o conteudo real e mantem a chave privada fora da API publica. */
@Component
@RequiredArgsConstructor
public class ArquivoDocumentoSstService {

    static final long TAMANHO_MAXIMO_BYTES = 15L * 1024 * 1024;
    private static final int MAXIMO_ENTRADAS_ZIP = 2_000;

    private final ObjectStorageService storageService;

    public ArquivoProcessado processar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Envie um arquivo não vazio.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new InvarianteVioladaException("O arquivo excede o limite de 15 MB.");
        }

        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException exception) {
            throw new ArmazenamentoException("Não foi possível ler o arquivo enviado.", exception);
        }
        if (conteudo.length == 0) {
            throw new InvarianteVioladaException("Envie um arquivo não vazio.");
        }
        if (conteudo.length > TAMANHO_MAXIMO_BYTES) {
            throw new InvarianteVioladaException("O arquivo excede o limite de 15 MB.");
        }

        TipoDetectado tipo = detectar(conteudo);
        validarDeclaracao(arquivo.getContentType(), tipo.contentType());
        String nomeSeguro = nomeSeguro(arquivo.getOriginalFilename(), tipo.extensao());
        String objectKey = "sst/governanca/documentos/" + UUID.randomUUID() + "." + tipo.extensao();
        return new ArquivoProcessado(conteudo, new ArquivoPrivadoDocumentoSst(
                nomeSeguro, objectKey, tipo.contentType(), conteudo.length, sha256(conteudo)));
    }

    public void enviar(ArquivoProcessado arquivo) {
        storageService.enviar(arquivo.metadados().objectKey(),
                new ByteArrayInputStream(arquivo.conteudo()), arquivo.conteudo().length,
                arquivo.metadados().contentType());
    }

    public DocumentoSstDownload baixar(DocumentoSst documento) {
        StorageObject objeto = storageService.baixar(documento.getObjectKey());
        return new DocumentoSstDownload(objeto.conteudo(), documento.getContentType(),
                documento.getTamanhoBytes(), documento.getNomeArquivo());
    }

    public void remover(String objectKey) {
        storageService.remover(objectKey);
    }

    static TipoDetectado detectar(byte[] bytes) {
        if (comecaCom(bytes, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            return new TipoDetectado("application/pdf", "pdf");
        }
        if (bytes.length >= 3 && unsigned(bytes[0]) == 0xff && unsigned(bytes[1]) == 0xd8
                && unsigned(bytes[2]) == 0xff) {
            return new TipoDetectado("image/jpeg", "jpg");
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (comecaCom(bytes, png)) {
            return new TipoDetectado("image/png", "png");
        }
        if (bytes.length >= 12 && ascii(bytes, 0, 4).equals("RIFF")
                && ascii(bytes, 8, 4).equals("WEBP")) {
            return new TipoDetectado("image/webp", "webp");
        }
        if (bytes.length >= 4 && unsigned(bytes[0]) == 0x50 && unsigned(bytes[1]) == 0x4b
                && (unsigned(bytes[2]) == 0x03 || unsigned(bytes[2]) == 0x05
                || unsigned(bytes[2]) == 0x07)) {
            return detectarOfficeAberto(bytes);
        }
        throw new InvarianteVioladaException(
                "O conteúdo não corresponde a um formato aceito. Envie PDF, JPEG, PNG, WebP, DOCX ou XLSX.");
    }

    private static TipoDetectado detectarOfficeAberto(byte[] bytes) {
        boolean contentTypes = false;
        boolean word = false;
        boolean excel = false;
        int entradas = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entradas > MAXIMO_ENTRADAS_ZIP) {
                    throw new InvarianteVioladaException("O pacote Office possui entradas em excesso.");
                }
                String nome = entry.getName();
                contentTypes |= "[Content_Types].xml".equals(nome);
                word |= nome.startsWith("word/");
                excel |= nome.startsWith("xl/");
                if (contentTypes && (word || excel)) {
                    break;
                }
            }
        } catch (IOException exception) {
            throw new InvarianteVioladaException("O pacote Office enviado está corrompido.");
        }
        if (!contentTypes || word == excel) {
            throw new InvarianteVioladaException("O arquivo ZIP não é um documento DOCX ou XLSX válido.");
        }
        return word
                ? new TipoDetectado("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx")
                : new TipoDetectado("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
    }

    private static void validarDeclaracao(String declarado, String detectado) {
        if (!StringUtils.hasText(declarado)) {
            return;
        }
        String normalizado = declarado.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (normalizado.equals("application/octet-stream") || normalizado.equals(detectado)) {
            return;
        }
        throw new InvarianteVioladaException(
                "O tipo declarado do arquivo não corresponde ao conteúdo detectado.");
    }

    private static String nomeSeguro(String original, String extensao) {
        String nome = StringUtils.hasText(original)
                ? StringUtils.getFilename(StringUtils.cleanPath(original)) : null;
        if (!StringUtils.hasText(nome) || nome.contains("..")) {
            nome = "documento." + extensao;
        }
        nome = nome.replaceAll("[\\r\\n\\t\\u0000-\\u001f]", "_");
        return nome.length() <= 255 ? nome : nome.substring(nome.length() - 255);
    }

    private static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException exception) {
            throw new ArmazenamentoException("SHA-256 não está disponível na JVM.", exception);
        }
    }

    private static boolean comecaCom(byte[] bytes, byte[] prefixo) {
        if (bytes.length < prefixo.length) return false;
        for (int i = 0; i < prefixo.length; i++) {
            if (bytes[i] != prefixo[i]) return false;
        }
        return true;
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static String ascii(byte[] bytes, int inicio, int tamanho) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream(tamanho);
        saida.write(bytes, inicio, tamanho);
        return saida.toString(java.nio.charset.StandardCharsets.US_ASCII);
    }

    public record ArquivoProcessado(byte[] conteudo, ArquivoPrivadoDocumentoSst metadados) {
        public ArquivoProcessado {
            conteudo = conteudo.clone();
        }

        @Override
        public byte[] conteudo() {
            return conteudo.clone();
        }
    }

    record TipoDetectado(String contentType, String extensao) {
    }
}
