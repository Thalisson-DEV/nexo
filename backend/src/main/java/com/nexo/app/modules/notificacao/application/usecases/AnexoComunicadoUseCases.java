package com.nexo.app.modules.notificacao.application.usecases;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.nexo.app.modules.notificacao.application.dto.AnexoComunicadoDownload;
import com.nexo.app.modules.notificacao.application.dto.AnexoComunicadoResponse;
import com.nexo.app.modules.notificacao.domain.AnexoComunicado;
import com.nexo.app.modules.notificacao.domain.Comunicado;
import com.nexo.app.modules.notificacao.infrastructure.persistence.ComunicadoRepository;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Anexos do comunicado: imagem e PDF, gravados no R2 com o binário passando pelo
 * backend (mesmo padrão das evidências de desvio do SST).
 *
 * <p>Vídeo não entra por aqui. Um arquivo de vídeo estoura o limite de 15 MB do
 * multipart e traria transcodificação e streaming junto — para changelog e dica de
 * uso, um link para YouTube/Vimeo no campo {@code videoUrl} do comunicado resolve o
 * mesmo problema sem nada disso.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnexoComunicadoUseCases {

    /** Deve refletir spring.servlet.multipart.max-file-size. */
    private static final long TAMANHO_MAXIMO_BYTES = 15L * 1024 * 1024;

    /** MIME types aceitos → extensão canônica usada na chave do objeto. */
    private static final Map<String, String> TIPOS_PERMITIDOS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "application/pdf", "pdf");

    private final ComunicadoRepository repository;
    private final ComunicadoUseCases comunicadoUseCases;
    private final ObjectStorageService objectStorage;

    @Transactional
    public AnexoComunicadoResponse anexar(UUID idComunicado, MultipartFile arquivo) {
        Comunicado comunicado = comunicadoUseCases.buscarEntity(idComunicado);

        String contentType = resolverContentType(arquivo);
        validarTamanho(arquivo);
        String nomeArquivo = nomeArquivoSeguro(arquivo, contentType);
        String objectKey = gerarObjectKey(idComunicado, contentType);

        try (InputStream conteudo = arquivo.getInputStream()) {
            objectStorage.enviar(objectKey, conteudo, arquivo.getSize(), contentType);
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao ler o arquivo enviado.", e);
        }

        AnexoComunicado anexo = comunicado.adicionarAnexo(
                nomeArquivo, contentType, arquivo.getSize(), objectKey);
        repository.saveAndFlush(comunicado);

        return toResponse(anexo);
    }

    @Transactional(readOnly = true)
    public AnexoComunicadoDownload baixar(UUID idComunicado, UUID idAnexo) {
        AnexoComunicado anexo = comunicadoUseCases.buscarEntity(idComunicado).buscarAnexo(idAnexo);
        StorageObject objeto = objectStorage.baixar(anexo.getObjectKey());
        return new AnexoComunicadoDownload(objeto.conteudo(), objeto.contentType(),
                objeto.tamanhoBytes(), anexo.getNomeArquivo());
    }

    @Transactional
    public void remover(UUID idComunicado, UUID idAnexo) {
        Comunicado comunicado = comunicadoUseCases.buscarEntity(idComunicado);
        AnexoComunicado anexo = comunicado.buscarAnexo(idAnexo);
        String objectKey = anexo.getObjectKey();

        comunicado.removerAnexo(idAnexo);
        repository.saveAndFlush(comunicado);

        try {
            objectStorage.remover(objectKey);
        } catch (ArmazenamentoException e) {
            // O registro já saiu do banco; um objeto órfão no bucket é preferível a
            // devolver erro para uma remoção que, do ponto de vista do usuário, deu certo.
            log.warn("Anexo {} removido do banco, mas falhou a exclusão do objeto '{}' no R2: {}",
                    idAnexo, objectKey, e.getMessage());
        }
    }

    private String resolverContentType(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("Envie um arquivo com conteúdo.");
        }
        String contentType = arquivo.getContentType();
        if (contentType == null || !TIPOS_PERMITIDOS.containsKey(contentType.toLowerCase())) {
            throw new InvarianteVioladaException(
                    "O comunicado aceita apenas imagens (JPEG, PNG, WEBP, GIF) e PDF. "
                            + "Para vídeo, informe o link no campo de vídeo do comunicado.");
        }
        return contentType.toLowerCase();
    }

    private void validarTamanho(MultipartFile arquivo) {
        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new InvarianteVioladaException("Cada anexo pode ter no máximo 15 MB.");
        }
    }

    private String nomeArquivoSeguro(MultipartFile arquivo, String contentType) {
        String original = StringUtils.getFilename(arquivo.getOriginalFilename());
        if (!StringUtils.hasText(original)) {
            return "anexo." + TIPOS_PERMITIDOS.get(contentType);
        }
        return original.length() > 255 ? original.substring(0, 255) : original;
    }

    private String gerarObjectKey(UUID idComunicado, String contentType) {
        return "notificacao/comunicados/%s/%s.%s".formatted(
                idComunicado, UUID.randomUUID(), TIPOS_PERMITIDOS.get(contentType));
    }

    private AnexoComunicadoResponse toResponse(AnexoComunicado anexo) {
        return new AnexoComunicadoResponse(anexo.getId(), anexo.getNomeArquivo(),
                anexo.getContentType(), anexo.getTamanhoBytes());
    }
}
