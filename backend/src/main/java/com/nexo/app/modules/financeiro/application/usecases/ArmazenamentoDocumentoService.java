package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.DocumentoDownload;
import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Recebe, guarda e devolve os arquivos do repositório financeiro.
 *
 * <p>Todo documento — venha de uma compra, de um pagamento ou avulso da
 * contabilidade — corre os mesmos riscos, então a whitelist de formatos, o limite
 * de tamanho e a sanitização do nome ficam num lugar só; do contrário, apertar a
 * regra num fluxo deixaria os outros para trás.</p>
 *
 * <p>O conteúdo vai para o object storage e o download é servido pelo backend, sob
 * o mesmo JWT; a chave do objeto nunca chega ao cliente.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArmazenamentoDocumentoService {

    private static final long TAMANHO_MAXIMO_BYTES = 15L * 1024 * 1024;

    private static final Map<String, String> TIPOS_PERMITIDOS = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/heic", "heic"),
            Map.entry("image/heif", "heif"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/xml", "xml"),
            Map.entry("text/xml", "xml"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"));

    private final ObjectStorageService storageService;

    /**
     * Valida e grava o arquivo, devolvendo os metadados prontos para virar
     * documento.
     *
     * <p>O conteúdo é lido inteiro em memória antes de subir porque o hash exige
     * percorrer os bytes e o stream do multipart só pode ser consumido uma vez.
     * O teto de 15 MB é o que torna isso aceitável — e é o mesmo teto que a
     * validação acima já cobra.</p>
     *
     * @param prefixo caminho lógico dentro do bucket, ex.: {@code financeiro/documentos}
     */
    public DocumentoFinanceiro.Arquivo armazenar(String prefixo, MultipartFile arquivo) {
        String contentType = resolverContentType(arquivo);
        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new InvarianteVioladaException("O arquivo excede o limite de 15 MB.");
        }

        String extensao = TIPOS_PERMITIDOS.get(contentType);
        String nomeSeguro = nomeSeguro(arquivo.getOriginalFilename(), extensao);
        String chave = prefixo + "/" + UUID.randomUUID() + "." + extensao;

        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new ArmazenamentoException("Erro ao processar o arquivo do documento", e);
        }
        if (conteudo.length == 0) {
            throw new InvarianteVioladaException("Envie um arquivo não vazio.");
        }

        storageService.enviar(chave, new ByteArrayInputStream(conteudo), conteudo.length, contentType);
        return new DocumentoFinanceiro.Arquivo(nomeSeguro, chave, contentType,
                (long) conteudo.length, sha256(conteudo));
    }

    public DocumentoDownload baixar(DocumentoFinanceiro documento) {
        StorageObject objeto = storageService.baixar(documento.getObjectKey());
        return new DocumentoDownload(objeto.conteudo(), documento.getContentType(),
                documento.getTamanhoBytes(), documento.getNomeArquivo());
    }

    /**
     * Apaga o objeto do storage. A falha é registrada e engolida de propósito: o
     * documento já foi baixado no banco, e derrubar a transação por causa do
     * storage deixaria o usuário com um arquivo que ele mandou excluir e continua
     * aparecendo.
     */
    public void removerSilenciosamente(String objectKey) {
        try {
            storageService.remover(objectKey);
        } catch (RuntimeException e) {
            log.warn("Documento removido no banco, mas a exclusão do objeto '{}' falhou: {}",
                    objectKey, e.getMessage());
        }
    }

    private String resolverContentType(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Envie um arquivo não vazio.");
        }
        String contentType = normalizar(arquivo.getContentType());
        if (contentType == null || !TIPOS_PERMITIDOS.containsKey(contentType)) {
            // Navegadores e apps nem sempre declaram o tipo corretamente; a extensão
            // é a segunda tentativa antes de recusar o envio.
            contentType = inferirPorExtensao(arquivo.getOriginalFilename());
        }
        if (contentType == null || !TIPOS_PERMITIDOS.containsKey(contentType)) {
            throw new InvarianteVioladaException(
                    "Tipo de arquivo não suportado. Aceitos: imagens (JPEG, PNG, WebP, HEIC), PDF, XML, "
                            + "Word (DOC, DOCX) e Excel (XLS, XLSX).");
        }
        return contentType;
    }

    /**
     * SHA-256 do conteúdo. É o que reconhece o mesmo arquivo reenviado — a mesma
     * nota chegando duas vezes por caminhos diferentes é o começo de um pagamento
     * em duplicidade, e o nome do arquivo não serve para detectá-la.
     */
    private static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException e) {
            throw new ArmazenamentoException("Algoritmo de hash indisponível na JVM", e);
        }
    }

    /** Remove qualquer caminho embutido no nome enviado pelo cliente. */
    private static String nomeSeguro(String nomeOriginal, String extensaoPadrao) {
        String nome = StringUtils.hasText(nomeOriginal)
                ? StringUtils.getFilename(StringUtils.cleanPath(nomeOriginal))
                : null;
        if (!StringUtils.hasText(nome)) {
            nome = "arquivo." + extensaoPadrao;
        }
        return nome.length() > 255 ? nome.substring(nome.length() - 255) : nome;
    }

    private static String normalizar(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        int separador = contentType.indexOf(';');
        return (separador >= 0 ? contentType.substring(0, separador) : contentType).trim().toLowerCase();
    }

    private static String inferirPorExtensao(String nomeArquivo) {
        String extensao = normalizar(StringUtils.getFilenameExtension(nomeArquivo));
        if (extensao == null) {
            return null;
        }
        return switch (extensao) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            case "pdf" -> "application/pdf";
            case "xml" -> "application/xml";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> null;
        };
    }
}
