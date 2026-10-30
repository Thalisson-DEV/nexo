package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.EvidenciaDownload;
import com.nexo.app.modules.sst.application.dto.EvidenciaInspecaoResponse;
import com.nexo.app.modules.sst.domain.EvidenciaInspecao;
import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import com.nexo.app.modules.sst.infrastructure.persistence.EvidenciaInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
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
 * Envio, download e remoção das evidências fotográficas que comprovam a realização de
 * uma inspeção.
 *
 * <p>O envio acontece <b>antes</b> do lançamento: cada foto sobe em sua própria
 * requisição e devolve um ID, que o cliente reúne em {@code evidencias} no registro da
 * inspeção. Isso mantém o lançamento um POST pequeno e atômico e deixa o upload — a
 * parte cara e sujeita a queda de sinal em campo — retentável foto a foto.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = INSPECOES_SEGURANCA)
public class EvidenciaInspecaoUseCases {

    private static final String PREFIXO_OBJETO = "sst/inspecoes/evidencias";

    private final EvidenciaInspecaoRepository evidenciaRepository;
    private final InspecaoSegurancaRepository inspecaoRepository;
    private final ObjectStorageService objectStorage;

    /**
     * Recebe uma foto avulsa, ainda sem inspeção. Fica pendente até ser usada em um
     * lançamento; se nunca for, o expurgo a recolhe.
     */
    @Transactional
    public EvidenciaInspecaoResponse enviar(MultipartFile arquivo, UUID idUsuario) {
        EvidenciaInspecao evidencia = armazenar(arquivo, idUsuario);
        return toResponse(evidenciaRepository.save(evidencia));
    }

    /**
     * Acrescenta uma foto a uma inspeção já lançada — o inspetor pode complementar a
     * comprovação depois, mas nunca lançar sem nenhuma.
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public EvidenciaInspecaoResponse anexar(UUID idInspecao, MultipartFile arquivo, UUID idUsuario) {
        InspecaoSeguranca inspecao = buscarInspecao(idInspecao);
        EvidenciaInspecao evidencia = armazenar(arquivo, idUsuario);

        inspecao.vincularEvidencia(evidencia);
        inspecaoRepository.saveAndFlush(inspecao);
        return toResponse(evidencia);
    }

    @Transactional(readOnly = true)
    public EvidenciaDownload baixar(UUID idInspecao, UUID idEvidencia) {
        EvidenciaInspecao evidencia = buscarEvidencia(buscarInspecao(idInspecao), idEvidencia);

        StorageObject objeto = objectStorage.baixar(evidencia.getObjectKey());
        return new EvidenciaDownload(objeto.conteudo(), evidencia.getContentType(),
                evidencia.getTamanhoBytes(), evidencia.getNomeArquivo());
    }

    /**
     * Remove uma foto da inspeção. A última é barrada pelo agregado.
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public void remover(UUID idInspecao, UUID idEvidencia) {
        InspecaoSeguranca inspecao = buscarInspecao(idInspecao);
        EvidenciaInspecao evidencia = buscarEvidencia(inspecao, idEvidencia);
        String objectKey = evidencia.getObjectKey();

        inspecao.removerEvidencia(evidencia);
        inspecaoRepository.saveAndFlush(inspecao);

        removerObjetoSilenciosamente(objectKey);
    }

    /**
     * Descarta uma foto ainda pendente — a que o inspetor tirou errado e não vai usar.
     * Só quem enviou pode descartar, e nunca uma já vinculada a uma inspeção.
     */
    @Transactional
    public void descartar(UUID idEvidencia, UUID idUsuario) {
        EvidenciaInspecao evidencia = evidenciaRepository.findById(idEvidencia)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Evidência fotográfica não encontrada com ID: " + idEvidencia));

        if (!evidencia.foiEnviadaPor(idUsuario)) {
            throw new InvarianteVioladaException("Só é possível descartar uma evidência enviada por você.");
        }
        if (evidencia.estaVinculada()) {
            throw new InvarianteVioladaException(
                    "A evidência já pertence a uma inspeção. Remova-a pela própria inspeção.");
        }

        String objectKey = evidencia.getObjectKey();
        evidenciaRepository.delete(evidencia);
        removerObjetoSilenciosamente(objectKey);
    }

    // --------------------------------------------------------------------- //
    //  Apoio
    // --------------------------------------------------------------------- //

    private EvidenciaInspecao armazenar(MultipartFile arquivo, UUID idUsuario) {
        ArquivoEvidencia.Recebido recebido =
                ArquivoEvidencia.receber(arquivo, ArquivoEvidencia.FOTOS, ArquivoEvidencia.RECUSA_FOTO);
        String objectKey = ArquivoEvidencia.chave(PREFIXO_OBJETO, recebido.extensao());

        try (InputStream conteudo = arquivo.getInputStream()) {
            objectStorage.enviar(objectKey, conteudo, arquivo.getSize(), recebido.contentType());
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao ler o arquivo enviado.", e);
        }

        return new EvidenciaInspecao(idUsuario, recebido.nomeArquivo(), recebido.contentType(),
                arquivo.getSize(), objectKey);
    }

    /**
     * O vínculo já saiu do banco; derrubar a transação por causa do storage devolveria
     * ao usuário uma foto que ele mandou excluir e continua aparecendo. No pior caso
     * sobra um objeto órfão no bucket.
     */
    private void removerObjetoSilenciosamente(String objectKey) {
        try {
            objectStorage.remover(objectKey);
        } catch (RuntimeException e) {
            log.warn("Evidência removida do banco, mas falhou a exclusão do objeto '{}' no R2: {}",
                    objectKey, e.getMessage());
        }
    }

    private InspecaoSeguranca buscarInspecao(UUID idInspecao) {
        return inspecaoRepository.findByIdAndAtivoTrue(idInspecao)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Inspeção de segurança não encontrada com ID: " + idInspecao));
    }

    private EvidenciaInspecao buscarEvidencia(InspecaoSeguranca inspecao, UUID idEvidencia) {
        return inspecao.getEvidencias().stream()
                .filter(e -> e.getId().equals(idEvidencia))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Evidência fotográfica não encontrada com ID: " + idEvidencia));
    }

    private EvidenciaInspecaoResponse toResponse(EvidenciaInspecao evidencia) {
        return new EvidenciaInspecaoResponse(evidencia.getId(), evidencia.getNomeArquivo(),
                evidencia.getContentType(), evidencia.getTamanhoBytes(), evidencia.getDataCriacao());
    }
}
