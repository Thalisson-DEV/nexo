package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.EvidenciaGovernancaDownload;
import com.nexo.app.modules.sst.application.dto.EvidenciaGovernancaResponse;
import com.nexo.app.modules.sst.domain.AcaoGovernancaSst;
import com.nexo.app.modules.sst.domain.EvidenciaGovernancaSst;
import com.nexo.app.modules.sst.domain.ExecucaoChecklistGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusAcaoGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusExecucaoChecklistGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.EvidenciaGovernancaSstRepository;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenciaGovernancaSstUseCases {

    private final EvidenciaGovernancaSstRepository repository;
    private final AcaoGovernancaSstUseCases acaoUseCases;
    private final ExecucaoChecklistGovernancaSstUseCases execucaoUseCases;
    private final ArmazenamentoEvidenciaGovernancaSstService armazenamento;
    private final ObjectStorageService storage;

    @Transactional
    public EvidenciaGovernancaResponse anexarAcao(UUID idAcao, MultipartFile arquivo, UUID usuarioId) {
        AcaoGovernancaSst acao = acaoUseCases.buscar(idAcao);
        if (acao.getStatus() == StatusAcaoGovernancaSst.CONCLUIDA
                || acao.getStatus() == StatusAcaoGovernancaSst.CANCELADA) {
            throw new com.nexo.app.shared.domain.exception.InvarianteVioladaException(
                    "A ação encerrada não aceita novas evidências.");
        }
        var recebido = armazenamento.armazenar(arquivo);
        try {
            return montar(repository.saveAndFlush(EvidenciaGovernancaSst.paraAcao(
                    idAcao, usuarioId, recebido.nomeArquivo(), recebido.objectKey(),
                    recebido.contentType(), recebido.tamanhoBytes(), recebido.hashConteudo())));
        } catch (RuntimeException e) {
            removerSilenciosamente(recebido.objectKey());
            throw e;
        }
    }

    @Transactional
    public EvidenciaGovernancaResponse anexarChecklist(UUID idExecucao, MultipartFile arquivo,
                                                        UUID usuarioId) {
        ExecucaoChecklistGovernancaSst execucao = execucaoUseCases.carregarDetalhada(idExecucao);
        if (execucao.getStatus() != StatusExecucaoChecklistGovernancaSst.RASCUNHO) {
            throw new com.nexo.app.shared.domain.exception.InvarianteVioladaException(
                    "O checklist concluído não aceita novas evidências.");
        }
        var recebido = armazenamento.armazenar(arquivo);
        try {
            return montar(repository.saveAndFlush(EvidenciaGovernancaSst.paraChecklist(
                    idExecucao, usuarioId, recebido.nomeArquivo(), recebido.objectKey(),
                    recebido.contentType(), recebido.tamanhoBytes(), recebido.hashConteudo())));
        } catch (RuntimeException e) {
            removerSilenciosamente(recebido.objectKey());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<EvidenciaGovernancaResponse> listarAcao(UUID idAcao) {
        acaoUseCases.buscar(idAcao);
        return repository.findByIdAcaoAndAtivoTrueOrderByDataCriacaoAsc(idAcao).stream()
                .map(this::montar).toList();
    }

    @Transactional(readOnly = true)
    public List<EvidenciaGovernancaResponse> listarChecklist(UUID idExecucao) {
        execucaoUseCases.carregarDetalhada(idExecucao);
        return repository.findByIdExecucaoAndAtivoTrueOrderByDataCriacaoAsc(idExecucao).stream()
                .map(this::montar).toList();
    }

    @Transactional(readOnly = true)
    public EvidenciaGovernancaDownload baixarAcao(UUID idAcao, UUID idEvidencia) {
        acaoUseCases.buscar(idAcao);
        EvidenciaGovernancaSst evidencia = buscar(idEvidencia);
        if (!idAcao.equals(evidencia.getIdAcao())) throw naoEncontrada();
        return baixar(evidencia);
    }

    @Transactional(readOnly = true)
    public EvidenciaGovernancaDownload baixarChecklist(UUID idExecucao, UUID idEvidencia) {
        execucaoUseCases.carregarDetalhada(idExecucao);
        EvidenciaGovernancaSst evidencia = buscar(idEvidencia);
        if (!idExecucao.equals(evidencia.getIdExecucao())) throw naoEncontrada();
        return baixar(evidencia);
    }

    @Transactional
    public void removerAcao(UUID idAcao, UUID idEvidencia, UUID usuarioId) {
        AcaoGovernancaSst acao = acaoUseCases.buscar(idAcao);
        if (acao.getStatus() == StatusAcaoGovernancaSst.CONCLUIDA
                || acao.getStatus() == StatusAcaoGovernancaSst.CANCELADA) {
            throw new com.nexo.app.shared.domain.exception.InvarianteVioladaException(
                    "Evidências de ação encerrada são somente leitura.");
        }
        EvidenciaGovernancaSst evidencia = buscar(idEvidencia);
        if (!idAcao.equals(evidencia.getIdAcao())) throw naoEncontrada();
        remover(evidencia, usuarioId);
    }

    @Transactional
    public void removerChecklist(UUID idExecucao, UUID idEvidencia, UUID usuarioId) {
        ExecucaoChecklistGovernancaSst execucao = execucaoUseCases.carregarDetalhada(idExecucao);
        if (execucao.getStatus() != StatusExecucaoChecklistGovernancaSst.RASCUNHO) {
            throw new com.nexo.app.shared.domain.exception.InvarianteVioladaException(
                    "Evidências do checklist concluído são somente leitura.");
        }
        EvidenciaGovernancaSst evidencia = buscar(idEvidencia);
        if (!idExecucao.equals(evidencia.getIdExecucao())) throw naoEncontrada();
        remover(evidencia, usuarioId);
    }

    private void remover(EvidenciaGovernancaSst evidencia, UUID usuarioId) {
        if (!usuarioId.equals(evidencia.getIdUsuarioEnvio())
                && !SecurityUtils.possuiAutoridade("sst:governanca:aprovar")) {
            throw new com.nexo.app.shared.domain.exception.InvarianteVioladaException(
                    "Somente o autor ou um aprovador pode retirar esta evidência.");
        }
        String chave = evidencia.getObjectKey();
        evidencia.desativar();
        repository.saveAndFlush(evidencia);
        removerSilenciosamente(chave);
    }

    private EvidenciaGovernancaDownload baixar(EvidenciaGovernancaSst evidencia) {
        StorageObject objeto = storage.baixar(evidencia.getObjectKey());
        return new EvidenciaGovernancaDownload(objeto.conteudo(), evidencia.getContentType(),
                evidencia.getTamanhoBytes(), evidencia.getNomeArquivo());
    }

    private EvidenciaGovernancaSst buscar(UUID id) {
        return repository.findByIdAndAtivoTrue(id).orElseThrow(this::naoEncontrada);
    }

    private RecursoNaoEncontradoException naoEncontrada() {
        return new RecursoNaoEncontradoException("Evidência de governança não encontrada.");
    }

    private EvidenciaGovernancaResponse montar(EvidenciaGovernancaSst evidencia) {
        return new EvidenciaGovernancaResponse(evidencia.getId(), evidencia.getNomeArquivo(),
                evidencia.getContentType(), evidencia.getTamanhoBytes(), evidencia.getHashConteudo(),
                evidencia.getDataCriacao());
    }

    private void removerSilenciosamente(String chave) {
        try {
            armazenamento.remover(chave);
        } catch (RuntimeException e) {
            log.warn("Falha ao limpar objeto órfão de evidência de governança ({}).", e.getClass().getSimpleName());
        }
    }
}
