package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.TreinamentoGovernancaSstFilter;
import com.nexo.app.modules.sst.application.dto.TreinamentoGovernancaSstRequest;
import com.nexo.app.modules.sst.application.dto.TreinamentoGovernancaSstResponse;
import com.nexo.app.modules.sst.domain.ConflitoGovernancaSstException;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TreinamentoGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.ExigenciaTreinamentoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TreinamentoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TreinamentoGovernancaSstSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TreinamentoGovernancaSstUseCases {

    private final TreinamentoGovernancaSstRepository repository;
    private final ExigenciaTreinamentoSstRepository exigenciaRepository;

    @Transactional
    public TreinamentoGovernancaSstResponse criar(TreinamentoGovernancaSstRequest request) {
        String codigo = request.codigo().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCodigoIgnoreCase(codigo)) {
            throw new ConflitoGovernancaSstException("Já existe treinamento com este código.");
        }
        String nomeNormalizado = TreinamentoGovernancaSst.normalizar(request.nome());
        if (repository.existsByNomeNormalizadoAndAtivoTrue(nomeNormalizado)) {
            throw new ConflitoGovernancaSstException("Já existe treinamento ativo com este nome.");
        }
        TreinamentoGovernancaSst treinamento = new TreinamentoGovernancaSst(
                codigo, request.nome(), request.descricao(),
                request.contextos().contains(TipoCorpoGovernancaSst.CIPA),
                request.contextos().contains(TipoCorpoGovernancaSst.BRIGADA));
        return montar(repository.save(treinamento));
    }

    @Transactional
    public TreinamentoGovernancaSstResponse atualizar(UUID id,
                                                      TreinamentoGovernancaSstRequest request) {
        TreinamentoGovernancaSst treinamento = buscar(id);
        if (!treinamento.getCodigo().equals(request.codigo().trim().toUpperCase(Locale.ROOT))) {
            throw new InvarianteVioladaException("O código do treinamento não pode ser alterado.");
        }
        String normalizado = TreinamentoGovernancaSst.normalizar(request.nome());
        if (!Objects.equals(normalizado, treinamento.getNomeNormalizado())
                && repository.existsByNomeNormalizadoAndAtivoTrue(normalizado)) {
            throw new ConflitoGovernancaSstException("Já existe treinamento ativo com este nome.");
        }
        treinamento.atualizar(request.nome(), request.descricao(),
                request.contextos().contains(TipoCorpoGovernancaSst.CIPA),
                request.contextos().contains(TipoCorpoGovernancaSst.BRIGADA));
        return montar(repository.save(treinamento));
    }

    @Transactional
    public void desativar(UUID id) {
        TreinamentoGovernancaSst treinamento = buscar(id);
        if (exigenciaRepository.existsByIdTreinamentoAndAtualTrueAndAtivoTrue(id)) {
            throw new ConflitoGovernancaSstException(
                    "Desative as exigências atuais antes de desativar o treinamento.");
        }
        treinamento.desativar();
        repository.save(treinamento);
    }

    @Transactional(readOnly = true)
    public TreinamentoGovernancaSstResponse buscarPorId(UUID id) {
        return montar(buscar(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TreinamentoGovernancaSstResponse> listar(
            TreinamentoGovernancaSstFilter filtro, Pageable pageable) {
        return PageResponse.fromPage(repository.findAll(
                TreinamentoGovernancaSstSpecification.comFiltros(filtro), pageable).map(this::montar));
    }

    private TreinamentoGovernancaSst buscar(UUID id) {
        return repository.findByIdAndAtivoTrue(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Treinamento não encontrado ou inativo."));
    }

    private TreinamentoGovernancaSstResponse montar(TreinamentoGovernancaSst treinamento) {
        Set<TipoCorpoGovernancaSst> contextos = EnumSet.noneOf(TipoCorpoGovernancaSst.class);
        if (treinamento.isAplicaCipa()) contextos.add(TipoCorpoGovernancaSst.CIPA);
        if (treinamento.isAplicaBrigada()) contextos.add(TipoCorpoGovernancaSst.BRIGADA);
        return new TreinamentoGovernancaSstResponse(treinamento.getId(), treinamento.getCodigo(),
                treinamento.getNome(), treinamento.getDescricao(), Set.copyOf(contextos),
                treinamento.isAtivo(), treinamento.getVersao());
    }
}
