package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.sst.application.dto.AcaoGovernancaSstFilter;
import com.nexo.app.modules.sst.application.dto.AcaoGovernancaSstRequest;
import com.nexo.app.modules.sst.application.dto.AcaoGovernancaSstResponse;
import com.nexo.app.modules.sst.domain.AcaoGovernancaSst;
import com.nexo.app.modules.sst.domain.ConflitoGovernancaSstException;
import com.nexo.app.modules.sst.infrastructure.persistence.AcaoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.AcaoGovernancaSstSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcaoGovernancaSstUseCases {

    private final AcaoGovernancaSstRepository repository;
    private final ColaboradorApi colaboradorApi;
    private final BaseOperacionalApi baseOperacionalApi;

    @Transactional
    public AcaoGovernancaSstResponse criar(AcaoGovernancaSstRequest request) {
        if (request.clientRequestId() != null) {
            var existente = repository.findByIdCorpoAndClientRequestId(
                    request.idCorpo(), request.clientRequestId());
            if (existente.isPresent()) return montar(existente.get(), Map.of());
        }
        validarReferencias(request.idBaseOperacional(), request.idResponsavel());
        AcaoGovernancaSst acao = nova(request, request.idCorpo(), request.idBaseOperacional(),
                request.idReuniao(), request.idExecucaoChecklist());
        return montar(repository.save(acao), Map.of());
    }

    AcaoGovernancaSst nova(AcaoGovernancaSstRequest request, UUID idCorpo, UUID idBase,
                           UUID idReuniao, UUID idExecucao) {
        return new AcaoGovernancaSst(idCorpo, idBase, idReuniao, idExecucao,
                request.clientRequestId(), request.titulo(), request.descricao(), request.tipo(),
                request.prioridade(), request.idResponsavel(), request.prazo());
    }

    @Transactional
    public AcaoGovernancaSstResponse atualizar(UUID id, AcaoGovernancaSstRequest request) {
        AcaoGovernancaSst acao = buscar(id);
        conferirVersao(acao, request.versaoEsperada());
        validarReferencias(acao.getIdBaseOperacional(), request.idResponsavel());
        acao.atualizar(request.titulo(), request.descricao(), request.tipo(),
                request.prioridade(), request.idResponsavel(), request.prazo());
        return montar(repository.save(acao), Map.of());
    }

    @Transactional
    public AcaoGovernancaSstResponse iniciar(UUID id, Long versaoEsperada) {
        AcaoGovernancaSst acao = buscar(id);
        conferirVersao(acao, versaoEsperada);
        acao.iniciar(OffsetDateTime.now());
        return montar(repository.save(acao), Map.of());
    }

    @Transactional
    public AcaoGovernancaSstResponse concluir(UUID id, String resultado,
                                              Long versaoEsperada, UUID usuarioId) {
        AcaoGovernancaSst acao = buscar(id);
        conferirVersao(acao, versaoEsperada);
        acao.concluir(resultado, usuarioId, OffsetDateTime.now());
        return montar(repository.save(acao), Map.of());
    }

    @Transactional
    public AcaoGovernancaSstResponse cancelar(UUID id, String motivo,
                                              Long versaoEsperada, UUID usuarioId) {
        AcaoGovernancaSst acao = buscar(id);
        conferirVersao(acao, versaoEsperada);
        acao.cancelar(motivo, usuarioId, OffsetDateTime.now());
        return montar(repository.save(acao), Map.of());
    }

    @Transactional(readOnly = true)
    public AcaoGovernancaSstResponse buscarPorId(UUID id) {
        AcaoGovernancaSst acao = buscar(id);
        return montar(acao, colaboradores(List.of(acao)));
    }

    @Transactional(readOnly = true)
    public PageResponse<AcaoGovernancaSstResponse> listar(AcaoGovernancaSstFilter filtro,
                                                         Pageable pageable) {
        Page<AcaoGovernancaSst> pagina = repository.findAll(
                AcaoGovernancaSstSpecification.comFiltros(filtro, LocalDate.now()), pageable);
        Map<UUID, ResumoColaborador> pessoas = colaboradores(pagina.getContent());
        return PageResponse.fromPage(pagina.map(item -> montar(item, pessoas)));
    }

    AcaoGovernancaSst buscar(UUID id) {
        return repository.findById(id).filter(AcaoGovernancaSst::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ação de governança não encontrada."));
    }

    private void validarReferencias(UUID idBase, UUID idResponsavel) {
        if (!baseOperacionalApi.existeBase(idBase)) {
            throw new RecursoNaoEncontradoException("Base operacional não encontrada ou inativa.");
        }
        if (!colaboradorApi.existeColaborador(idResponsavel)) {
            throw new InvarianteVioladaException("Selecione um responsável ativo no RH.");
        }
    }

    private static void conferirVersao(AcaoGovernancaSst acao, Long esperada) {
        if (esperada == null || !Objects.equals(acao.getVersao(), esperada)) {
            throw new ConflitoGovernancaSstException(
                    "A ação foi alterada por outra pessoa. Atualize os dados antes de tentar novamente.");
        }
    }

    private Map<UUID, ResumoColaborador> colaboradores(Collection<AcaoGovernancaSst> acoes) {
        Set<UUID> ids = acoes.stream().map(AcaoGovernancaSst::getIdResponsavel)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return ids.isEmpty() ? Map.of() : colaboradorApi.buscarResumosPorIds(ids);
    }

    private AcaoGovernancaSstResponse montar(AcaoGovernancaSst acao,
                                             Map<UUID, ResumoColaborador> pessoas) {
        ResumoColaborador responsavel = pessoas.get(acao.getIdResponsavel());
        if (responsavel == null) {
            responsavel = colaboradorApi.buscarResumosPorIds(List.of(acao.getIdResponsavel()))
                    .get(acao.getIdResponsavel());
        }
        return new AcaoGovernancaSstResponse(
                acao.getId(), acao.getIdCorpo(), acao.getIdBaseOperacional(),
                acao.getIdReuniao(), acao.getIdExecucaoChecklist(), acao.getTitulo(),
                acao.getDescricao(), acao.getTipo(), acao.getPrioridade(),
                acao.getIdResponsavel(), responsavel == null ? null : responsavel.nomeCompleto(),
                acao.getPrazo(), acao.getStatus(), acao.statusPrazo(LocalDate.now()),
                acao.getResultado(), acao.getMotivoCancelamento(), acao.getDataInicio(),
                acao.getDataConclusao(), acao.getDataCancelamento(), acao.getVersao(),
                acao.getDataCriacao());
    }
}
