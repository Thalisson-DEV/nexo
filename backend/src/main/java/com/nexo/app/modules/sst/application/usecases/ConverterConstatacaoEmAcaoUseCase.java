package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.sst.application.dto.AcaoGovernancaSstResponse;
import com.nexo.app.modules.sst.application.dto.ConverterConstatacaoEmAcaoRequest;
import com.nexo.app.modules.sst.domain.AcaoGovernancaSst;
import com.nexo.app.modules.sst.domain.ConstatacaoChecklistGovernancaSst;
import com.nexo.app.modules.sst.domain.ExecucaoChecklistGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusExecucaoChecklistGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.AcaoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ConstatacaoChecklistGovernancaSstRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConverterConstatacaoEmAcaoUseCase {

    private final ExecucaoChecklistGovernancaSstUseCases execucaoUseCases;
    private final ConstatacaoChecklistGovernancaSstRepository constatacaoRepository;
    private final AcaoGovernancaSstRepository acaoRepository;
    private final AcaoGovernancaSstUseCases acaoUseCases;
    private final ColaboradorApi colaboradorApi;

    @Transactional
    public AcaoGovernancaSstResponse converter(UUID idExecucao, UUID idConstatacao,
                                               ConverterConstatacaoEmAcaoRequest request) {
        ExecucaoChecklistGovernancaSst execucao = execucaoUseCases.carregarDetalhada(idExecucao);
        if (execucao.getStatus() != StatusExecucaoChecklistGovernancaSst.CONCLUIDA) {
            throw new InvarianteVioladaException(
                    "Conclua o checklist antes de transformar uma constatação em ação.");
        }
        ConstatacaoChecklistGovernancaSst constatacao = constatacaoRepository.findById(idConstatacao)
                .filter(item -> item.pertenceA(idExecucao))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Constatação não encontrada neste checklist."));
        if (constatacao.getIdAcao() != null) {
            return acaoUseCases.buscarPorId(constatacao.getIdAcao());
        }
        if (request.clientRequestId() != null) {
            var existente = acaoRepository.findByIdCorpoAndClientRequestId(
                    execucao.getIdCorpo(), request.clientRequestId());
            if (existente.isPresent()) {
                constatacao.vincularAcao(existente.get().getId());
                constatacaoRepository.save(constatacao);
                return acaoUseCases.buscarPorId(existente.get().getId());
            }
        }
        if (!colaboradorApi.existeColaborador(request.idResponsavel())) {
            throw new InvarianteVioladaException("Selecione um responsável ativo no RH.");
        }
        AcaoGovernancaSst acao = new AcaoGovernancaSst(
                execucao.getIdCorpo(), execucao.getIdBaseOperacional(), null, execucao.getId(),
                request.clientRequestId(), request.titulo(), request.descricao(), request.tipo(),
                request.prioridade(), request.idResponsavel(), request.prazo());
        acao = acaoRepository.saveAndFlush(acao);
        constatacao.vincularAcao(acao.getId());
        constatacaoRepository.save(constatacao);
        return acaoUseCases.buscarPorId(acao.getId());
    }
}
