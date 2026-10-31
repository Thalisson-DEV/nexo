package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.AnexoObraResponse;
import com.nexo.app.modules.obras.application.dto.DownloadAnexoObraDTO;
import com.nexo.app.modules.obras.application.mappers.ObraMapper;
import com.nexo.app.modules.obras.domain.AnexoObra;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.TipoAnexoObra;
import com.nexo.app.modules.obras.infrastructure.persistence.AnexoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AnexoObraUseCases {

    private final ObraRepository obraRepository;
    private final AnexoObraRepository anexoRepository;
    private final TipoAnexoObraUseCases tipoAnexoUseCases;
    private final ObjectStorageService objectStorage;
    private final ObraMapper mapper;

    @Transactional
    public AnexoObraResponse anexar(UUID idObra, UUID idTipoAnexo, String observacoes, MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new InvarianteVioladaException("O arquivo para upload não pode estar vazio.");
        }

        Obra obra = obraRepository.findById(idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra não encontrada com o ID: " + idObra));

        TipoAnexoObra tipoAnexo = tipoAnexoUseCases.buscarEntidade(idTipoAnexo);

        String nomeOriginal = sanitizarNomeArquivo(arquivo.getOriginalFilename());
        String contentType = arquivo.getContentType() != null ? arquivo.getContentType() : "application/octet-stream";
        long tamanho = arquivo.getSize();

        String chaveStorage = "obras/" + idObra + "/anexos/" + UUID.randomUUID() + "_" + nomeOriginal;

        try (InputStream stream = arquivo.getInputStream()) {
            objectStorage.enviar(chaveStorage, stream, tamanho, contentType);
        } catch (IOException e) {
            log.error("Erro ao ler conteúdo do arquivo '{}' para obra ID {}", nomeOriginal, idObra, e);
            throw new ArmazenamentoException("Falha ao processar arquivo para envio ao armazenamento.", e);
        }

        AnexoObra anexo = obra.adicionarAnexo(tipoAnexo, nomeOriginal, contentType, tamanho, chaveStorage, observacoes);
        // O merge em cascata grava outra instância do anexo: o id gerado fica na que voltou da obra salva.
        AnexoObra salvo = obraRepository.saveAndFlush(obra).getAnexos().stream()
                .filter(a -> chaveStorage.equals(a.getChaveStorage()))
                .findFirst()
                .orElse(anexo);

        return mapper.toAnexoResponse(salvo);
    }

    @Transactional(readOnly = true)
    public DownloadAnexoObraDTO baixar(UUID idObra, UUID idAnexo) {
        AnexoObra anexo = anexoRepository.findByIdAndObraIdAndAtivoTrue(idAnexo, idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado com o ID: " + idAnexo + " para a obra informada."));

        StorageObject obj = objectStorage.baixar(anexo.getChaveStorage());
        return new DownloadAnexoObraDTO(obj.conteudo(), anexo.getNomeArquivo(), anexo.getContentType(), anexo.getTamanhoBytes());
    }

    @Transactional
    public void remover(UUID idObra, UUID idAnexo) {
        Obra obra = obraRepository.findById(idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra não encontrada com o ID: " + idObra));

        AnexoObra anexo = anexoRepository.findByIdAndObraIdAndAtivoTrue(idAnexo, idObra)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado com o ID: " + idAnexo));

        try {
            objectStorage.remover(anexo.getChaveStorage());
        } catch (Exception e) {
            log.warn("Falha ao remover arquivo do storage: chave={}", anexo.getChaveStorage(), e);
        }

        obra.removerAnexo(idAnexo);
        obraRepository.save(obra);
    }

    @Transactional(readOnly = true)
    public List<AnexoObraResponse> listarPorObra(UUID idObra) {
        return mapper.toAnexoResponseList(anexoRepository.findByObraIdAndAtivoTrue(idObra));
    }

    private String sanitizarNomeArquivo(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            return "arquivo_sem_nome";
        }
        return nomeOriginal.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
