package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Regras comuns de recebimento dos arquivos de evidência do SST: whitelist de
 * formatos, limite de tamanho, sanitização do nome e composição da chave do objeto.
 *
 * <p>São duas whitelists porque as evidências não provam a mesma coisa: o anexo de um
 * desvio pode ser um laudo em PDF, enquanto a evidência que comprova a realização da
 * inspeção precisa ser uma fotografia — um documento não atesta que o inspetor esteve
 * no local.</p>
 */
final class ArquivoEvidencia {

    /** Limite por arquivo (deve refletir spring.servlet.multipart.max-file-size). */
    static final long TAMANHO_MAXIMO_BYTES = 15L * 1024 * 1024;

    /** Fotografias → extensão canônica usada na chave do objeto. */
    static final Map<String, String> FOTOS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/heic", "heic",
            "image/heif", "heif");

    /** Fotografias e documentos (PDF/Word). */
    static final Map<String, String> FOTOS_E_DOCUMENTOS = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/heic", "heic"),
            Map.entry("image/heif", "heif"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"));

    static final String RECUSA_FOTO =
            "A evidência da inspeção deve ser uma fotografia. Aceitos: JPEG, PNG, WebP e HEIC.";

    static final String RECUSA_ANEXO_DESVIO =
            "Tipo de arquivo não suportado. Aceitos: imagens (JPEG, PNG, WebP, HEIC), PDF e Word (DOC, DOCX).";

    private ArquivoEvidencia() {
    }

    /** Arquivo já validado, pronto para virar entidade de anexo. */
    record Recebido(String contentType, String extensao, String nomeArquivo) {
    }

    /**
     * Valida o arquivo enviado contra a whitelist informada e devolve seus metadados
     * saneados. Não toca no armazenamento: recusar aqui evita subir lixo para o R2.
     */
    static Recebido receber(MultipartFile arquivo, Map<String, String> permitidos, String mensagemDeRecusa) {
        String contentType = resolverContentType(arquivo, permitidos, mensagemDeRecusa);
        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new InvarianteVioladaException("O arquivo excede o limite de 15 MB.");
        }
        String extensao = permitidos.get(contentType);
        return new Recebido(contentType, extensao, nomeSeguro(arquivo.getOriginalFilename(), extensao));
    }

    static String chave(String prefixo, String extensao) {
        return prefixo + "/" + UUID.randomUUID() + "." + extensao;
    }

    private static String resolverContentType(MultipartFile arquivo, Map<String, String> permitidos,
                                              String mensagemDeRecusa) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Envie um arquivo não vazio.");
        }
        String contentType = normalizar(arquivo.getContentType());
        if (contentType == null || !permitidos.containsKey(contentType)) {
            // Câmeras de celular e navegadores nem sempre declaram o tipo corretamente;
            // a extensão é a segunda tentativa antes de recusar o envio.
            contentType = inferirPorExtensao(arquivo.getOriginalFilename());
        }
        if (contentType == null || !permitidos.containsKey(contentType)) {
            throw new InvarianteVioladaException(mensagemDeRecusa);
        }
        return contentType;
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
        int separador = contentType.indexOf(';'); // remove parâmetros (ex.: "; charset=...")
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
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> null;
        };
    }
}
