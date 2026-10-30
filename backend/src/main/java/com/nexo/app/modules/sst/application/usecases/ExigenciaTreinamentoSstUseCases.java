package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.FuncaoApi;
import com.nexo.app.modules.sst.application.dto.ExigenciaTreinamentoSstFilter;
import com.nexo.app.modules.sst.application.dto.ExigenciaTreinamentoSstRequest;
import com.nexo.app.modules.sst.application.dto.ExigenciaTreinamentoSstResponse;
import com.nexo.app.modules.sst.domain.ExigenciaTreinamentoSst;
import com.nexo.app.modules.sst.domain.TreinamentoGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.ExigenciaTreinamentoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ExigenciaTreinamentoSstSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.TreinamentoGovernancaSstRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExigenciaTreinamentoSstUseCases {

    private final ExigenciaTreinamentoSstRepository repository;
    private final TreinamentoGovernancaSstRepository treinamentoRepository;
    private final FuncaoApi funcaoApi;
    private final BaseOperacionalApi baseOperacionalApi;

    @Transactional
    public ExigenciaTreinamentoSstResponse criar(ExigenciaTreinamentoSstRequest request,
                                                 UUID ator) {
        TreinamentoGovernancaSst treinamento = validarReferencias(request);
        ExigenciaTreinamentoSst exigencia = construir(request, ator);
        return montar(repository.save(exigencia), Map.of(treinamento.getId(), treinamento));
    }

    @Transactional
    public ExigenciaTreinamentoSstResponse versionar(UUID id,
                                                     ExigenciaTreinamentoSstRequest request,
                                                     UUID ator) {
        ExigenciaTreinamentoSst anterior = buscar(id);
        TreinamentoGovernancaSst treinamento = validarReferencias(request);
        ExigenciaTreinamentoSst proxima = anterior.criarProximaVersao(
                request.idTreinamento(), request.tipoCorpo(), request.modoAplicabilidade(),
                request.idFuncao(), request.papelCorpo(), request.idBase(),
                request.cargaHorariaMinutos(), request.politicaInstituicao(),
                request.instituicoesPermitidas(), request.modalidade(), request.recorrenciaMeses(),
                request.antecedenciaAlertaDias(), request.vigenciaInicio(), request.vigenciaFim(),
                request.tipoFonte(), request.jurisdicao(), request.identificadorFonte(),
                request.edicaoFonte(), request.urlFonte(), request.fonteConfirmada(),
                request.fonteConfirmada() ? exigirAtor(ator) : null, request.observacoes());
        repository.save(anterior);
        return montar(repository.save(proxima), Map.of(treinamento.getId(), treinamento));
    }

    @Transactional
    public ExigenciaTreinamentoSstResponse confirmarFonte(UUID id, UUID ator) {
        ExigenciaTreinamentoSst exigencia = buscar(id);
        exigencia.confirmarFonte(exigirAtor(ator));
        return montar(repository.save(exigencia), treinamentos(List.of(exigencia)));
    }

    @Transactional
    public void desativar(UUID id) {
        ExigenciaTreinamentoSst exigencia = buscar(id);
        exigencia.desativarExigencia();
        repository.save(exigencia);
    }

    @Transactional(readOnly = true)
    public ExigenciaTreinamentoSstResponse buscarPorId(UUID id) {
        ExigenciaTreinamentoSst exigencia = buscar(id);
        return montar(exigencia, treinamentos(List.of(exigencia)));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExigenciaTreinamentoSstResponse> listar(
            ExigenciaTreinamentoSstFilter filtro, Pageable pageable) {
        Page<ExigenciaTreinamentoSst> pagina = repository.findAll(
                ExigenciaTreinamentoSstSpecification.comFiltros(filtro), pageable);
        Map<UUID, TreinamentoGovernancaSst> treinamentos = treinamentos(pagina.getContent());
        return PageResponse.fromPage(pagina.map(item -> montar(item, treinamentos)));
    }

    private ExigenciaTreinamentoSst construir(ExigenciaTreinamentoSstRequest request, UUID ator) {
        return new ExigenciaTreinamentoSst(request.idTreinamento(), request.tipoCorpo(),
                request.modoAplicabilidade(), request.idFuncao(), request.papelCorpo(), request.idBase(),
                request.cargaHorariaMinutos(), request.politicaInstituicao(),
                request.instituicoesPermitidas(), request.modalidade(), request.recorrenciaMeses(),
                request.antecedenciaAlertaDias(), request.vigenciaInicio(), request.vigenciaFim(),
                request.tipoFonte(), request.jurisdicao(), request.identificadorFonte(),
                request.edicaoFonte(), request.urlFonte(), request.fonteConfirmada(),
                request.fonteConfirmada() ? exigirAtor(ator) : null, request.observacoes());
    }

    private TreinamentoGovernancaSst validarReferencias(ExigenciaTreinamentoSstRequest request) {
        TreinamentoGovernancaSst treinamento = treinamentoRepository
                .findByIdAndAtivoTrue(request.idTreinamento())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Treinamento não encontrado."));
        if (!treinamento.aplicaA(request.tipoCorpo())) {
            throw new InvarianteVioladaException("O treinamento não se aplica ao tipo de corpo informado.");
        }
        if (request.modoAplicabilidade().exigeFuncao()
                && !funcaoApi.existeFuncao(request.idFuncao())) {
            throw new RecursoNaoEncontradoException("Função não encontrada ou inativa no RH.");
        }
        if (request.idBase() != null && !baseOperacionalApi.existeBase(request.idBase())) {
            throw new RecursoNaoEncontradoException("Base operacional não encontrada ou inativa.");
        }
        if (request.papelCorpo() != null && !request.papelCorpo().pertenceA(request.tipoCorpo())) {
            throw new InvarianteVioladaException("O papel não pertence ao tipo de corpo selecionado.");
        }
        return treinamento;
    }

    private ExigenciaTreinamentoSst buscar(UUID id) {
        return repository.findByIdAndAtivoTrue(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Exigência de treinamento não encontrada."));
    }

    private Map<UUID, TreinamentoGovernancaSst> treinamentos(
            Collection<ExigenciaTreinamentoSst> exigencias) {
        List<UUID> ids = exigencias.stream().map(ExigenciaTreinamentoSst::getIdTreinamento)
                .distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return treinamentoRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(TreinamentoGovernancaSst::getId, Function.identity()));
    }

    private ExigenciaTreinamentoSstResponse montar(
            ExigenciaTreinamentoSst exigencia,
            Map<UUID, TreinamentoGovernancaSst> treinamentos) {
        TreinamentoGovernancaSst treinamento = treinamentos.get(exigencia.getIdTreinamento());
        return new ExigenciaTreinamentoSstResponse(
                exigencia.getId(), exigencia.getIdGrupoVersao(), exigencia.getRevisaoExigencia(),
                exigencia.getIdExigenciaAnterior(), exigencia.isAtual(), exigencia.getIdTreinamento(),
                treinamento == null ? null : treinamento.getCodigo(),
                treinamento == null ? null : treinamento.getNome(), exigencia.getTipoCorpo(),
                exigencia.getModoAplicabilidade(), exigencia.getIdFuncao(), exigencia.getPapelCorpo(),
                exigencia.getIdBase(), exigencia.getCargaHorariaMinutos(),
                exigencia.getPoliticaInstituicao(),
                new LinkedHashSet<>(exigencia.getInstituicoesPermitidas()), exigencia.getModalidade(),
                exigencia.getRecorrenciaMeses(), exigencia.getAntecedenciaAlertaDias(),
                exigencia.getVigenciaInicio(), exigencia.getVigenciaFim(), exigencia.getTipoFonte(),
                exigencia.getJurisdicao(), exigencia.getIdentificadorFonte(), exigencia.getEdicaoFonte(),
                exigencia.getUrlFonte(), exigencia.isFonteConfirmada(),
                exigencia.getDataConfirmacaoFonte(), exigencia.getObservacoes(), exigencia.isAtivo(),
                exigencia.getVersao());
    }

    private static UUID exigirAtor(UUID ator) {
        if (ator == null) throw new InvarianteVioladaException(
                "Não foi possível identificar o usuário autenticado.");
        return ator;
    }
}
