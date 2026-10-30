package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.AnexoEventoResponse;
import com.nexo.app.modules.sst.application.dto.EvidenciaDownload;
import com.nexo.app.modules.sst.domain.AnexoEventoSst;
import com.nexo.app.modules.sst.domain.ContextoAnexoEvento;
import com.nexo.app.modules.sst.domain.EventoSst;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoSstSpecification;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Evidências de um {@link EventoSst}: fotos do local, PDF da CAT, boletim de ocorrência,
 * comprovação da tratativa.
 *
 * <p>O envio acontece <b>depois</b> do registro, e não antes como na evidência de
 * inspeção. A inspeção é preenchida em campo, offline, onde um multipart de 30 MB morre
 * junto com a conexão; o acidente é registrado da base, no desktop, depois do fato. O
 * pré-upload aqui só produziria arquivo órfão e um job para varrer o que ficou.</p>
 *
 * <p>O binário vai para o armazenamento de objetos; o banco guarda metadados e chave.
 * Se o armazenamento falhar, nada é gravado — meia evidência é pior que nenhuma, porque
 * aparece na lista e não abre.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnexoEventoSstUseCases {

    private final EventoSstRepository repository;
    private final ObjectStorageService objectStorage;

    @Transactional
    public AnexoEventoResponse anexar(UUID idEvento, ContextoAnexoEvento contexto, MultipartFile arquivo) {
        EventoSst evento = buscarEvento(idEvento);

        ArquivoEvidencia.Recebido recebido = ArquivoEvidencia.receber(
                arquivo, ArquivoEvidencia.FOTOS_E_DOCUMENTOS, ArquivoEvidencia.RECUSA_ANEXO_DESVIO);
        String objectKey = ArquivoEvidencia.chave(
                "sst/eventos/" + idEvento + "/" + contexto.name().toLowerCase(), recebido.extensao());

        try (InputStream conteudo = arquivo.getInputStream()) {
            objectStorage.enviar(objectKey, conteudo, arquivo.getSize(), recebido.contentType());
        } catch (IOException e) {
            throw new ArmazenamentoException("Falha ao ler o arquivo enviado.", e);
        }

        AnexoEventoSst anexo = evento.adicionarAnexo(contexto, recebido.nomeArquivo(), recebido.contentType(),
                arquivo.getSize(), objectKey);
        // flush() e não save(): o agregado já está gerenciado, e o merge de save()
        // persistiria uma cópia do anexo, devolvendo esta instância sem id.
        repository.flush();

        log.info("Evidência anexada ao evento de SST {} no contexto {}", evento.getNumeroRegistro(), contexto);
        return toResponse(anexo);
    }

    @Transactional(readOnly = true)
    public List<AnexoEventoResponse> listar(UUID idEvento) {
        return buscarEvento(idEvento).getAnexos().stream().map(AnexoEventoSstUseCases::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EvidenciaDownload baixar(UUID idEvento, UUID idAnexo) {
        AnexoEventoSst anexo = buscarAnexo(buscarEvento(idEvento), idAnexo);

        StorageObject objeto = objectStorage.baixar(anexo.getObjectKey());
        return new EvidenciaDownload(objeto.conteudo(), anexo.getContentType(),
                anexo.getTamanhoBytes(), anexo.getNomeArquivo());
    }

    @Transactional
    public void remover(UUID idEvento, UUID idAnexo) {
        EventoSst evento = buscarEvento(idEvento);
        AnexoEventoSst anexo = buscarAnexo(evento, idAnexo);
        String objectKey = anexo.getObjectKey();

        evento.removerAnexo(anexo);
        repository.flush();

        // O registro já saiu. Apagar o objeto é best-effort: uma falha transitória no
        // R2 não deve impedir a remoção — no pior caso sobra um objeto órfão.
        try {
            objectStorage.remover(objectKey);
        } catch (RuntimeException e) {
            log.warn("Anexo {} removido do banco, mas falhou a exclusão do objeto '{}' no R2: {}",
                    idAnexo, objectKey, e.getMessage());
        }
    }

    private EventoSst buscarEvento(UUID idEvento) {
        return repository.findOne(EventoSstSpecification.porId(idEvento))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Evento de SST não encontrado com ID: " + idEvento));
    }

    private AnexoEventoSst buscarAnexo(EventoSst evento, UUID idAnexo) {
        return evento.getAnexos().stream()
                .filter(anexo -> anexo.getId().equals(idAnexo))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado com ID: " + idAnexo));
    }

    private static AnexoEventoResponse toResponse(AnexoEventoSst anexo) {
        return new AnexoEventoResponse(anexo.getId(), anexo.getContexto(), anexo.getTipo(), anexo.getNomeArquivo(),
                anexo.getContentType(), anexo.getTamanhoBytes(), anexo.getDataCriacao());
    }
}
