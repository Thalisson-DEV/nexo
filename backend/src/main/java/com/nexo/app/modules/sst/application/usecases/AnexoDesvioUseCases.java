package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.AnexoDesvioResponse;
import com.nexo.app.modules.sst.application.dto.EvidenciaDownload;
import com.nexo.app.modules.sst.domain.AnexoDesvio;
import com.nexo.app.modules.sst.domain.ContextoAnexo;
import com.nexo.app.modules.sst.domain.DesvioSeguranca;
import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.INSPECOES_SEGURANCA;

/**
 * Casos de uso para anexar, baixar e remover evidências (fotos/documentos) de um
 * {@link DesvioSeguranca}. O conteúdo binário trafega pelo backend e é persistido no
 * armazenamento de objetos (R2); o banco guarda apenas os metadados. Anexos são opcionais.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = INSPECOES_SEGURANCA)
public class AnexoDesvioUseCases {

    private final InspecaoSegurancaRepository repository;
    private final ObjectStorageService objectStorage;

    @Transactional
    @CacheEvict(allEntries = true)
    public AnexoDesvioResponse anexar(UUID idInspecao, UUID idDesvio, ContextoAnexo contexto, MultipartFile arquivo) {
        InspecaoSeguranca inspecao = buscarInspecao(idInspecao);
        DesvioSeguranca desvio = buscarDesvio(inspecao, idDesvio);

        ArquivoEvidencia.Recebido recebido = ArquivoEvidencia.receber(
                arquivo, ArquivoEvidencia.FOTOS_E_DOCUMENTOS, ArquivoEvidencia.RECUSA_ANEXO_DESVIO);
        String objectKey = ArquivoEvidencia.chave(
                "sst/desvios/" + idDesvio + "/" + contexto.name().toLowerCase(), recebido.extensao());

        try (InputStream conteudo = arquivo.getInputStream()) {
            objectStorage.enviar(objectKey, conteudo, arquivo.getSize(), recebido.contentType());
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao ler o arquivo enviado.", e);
        }

        AnexoDesvio anexo = desvio.adicionarAnexo(contexto, recebido.nomeArquivo(), recebido.contentType(),
                arquivo.getSize(), objectKey);
        repository.saveAndFlush(inspecao);
        return toResponse(anexo);
    }

    @Transactional(readOnly = true)
    public EvidenciaDownload baixar(UUID idInspecao, UUID idDesvio, UUID idAnexo) {
        InspecaoSeguranca inspecao = buscarInspecao(idInspecao);
        DesvioSeguranca desvio = buscarDesvio(inspecao, idDesvio);
        AnexoDesvio anexo = buscarAnexo(desvio, idAnexo);

        StorageObject objeto = objectStorage.baixar(anexo.getObjectKey());
        return new EvidenciaDownload(objeto.conteudo(), anexo.getContentType(), anexo.getTamanhoBytes(), anexo.getNomeArquivo());
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void remover(UUID idInspecao, UUID idDesvio, UUID idAnexo) {
        InspecaoSeguranca inspecao = buscarInspecao(idInspecao);
        DesvioSeguranca desvio = buscarDesvio(inspecao, idDesvio);
        AnexoDesvio anexo = buscarAnexo(desvio, idAnexo);
        String objectKey = anexo.getObjectKey();

        desvio.removerAnexo(anexo);
        repository.saveAndFlush(inspecao);

        // O registro já foi removido. A exclusão do objeto é best-effort: uma falha
        // transitória no R2 não deve impedir a remoção (no pior caso, objeto órfão).
        try {
            objectStorage.remover(objectKey);
        } catch (RuntimeException e) {
            log.warn("Anexo {} removido do banco, mas falhou a exclusão do objeto '{}' no R2: {}",
                    idAnexo, objectKey, e.getMessage());
        }
    }

    // --------------------------------------------------------------------- //
    //  Apoio
    // --------------------------------------------------------------------- //

    private InspecaoSeguranca buscarInspecao(UUID idInspecao) {
        return repository.findByIdAndAtivoTrue(idInspecao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inspeção de segurança não encontrada com ID: " + idInspecao));
    }

    private DesvioSeguranca buscarDesvio(InspecaoSeguranca inspecao, UUID idDesvio) {
        return inspecao.getDesvios().stream()
                .filter(d -> d.getId().equals(idDesvio))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Desvio não encontrado com ID: " + idDesvio));
    }

    private AnexoDesvio buscarAnexo(DesvioSeguranca desvio, UUID idAnexo) {
        return desvio.getAnexos().stream()
                .filter(a -> a.getId().equals(idAnexo))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado com ID: " + idAnexo));
    }

    private AnexoDesvioResponse toResponse(AnexoDesvio anexo) {
        return new AnexoDesvioResponse(
                anexo.getId(), anexo.getContexto(), anexo.getTipo(), anexo.getNomeArquivo(),
                anexo.getContentType(), anexo.getTamanhoBytes(), anexo.getDataCriacao());
    }
}
