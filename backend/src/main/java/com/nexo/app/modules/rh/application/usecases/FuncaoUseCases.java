package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.FuncaoFilter;
import com.nexo.app.modules.rh.application.dto.FuncaoRequest;
import com.nexo.app.modules.rh.FuncaoApi;
import com.nexo.app.modules.rh.FuncaoResponse;
import com.nexo.app.modules.rh.application.mappers.FuncaoMapper;
import com.nexo.app.modules.rh.domain.Funcao;
import com.nexo.app.modules.rh.infrastructure.persistence.FuncaoRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.FuncaoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Orquestra o registro de uma nova função administrativa/operacional.
 */
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "funcoes")
public class FuncaoUseCases implements FuncaoApi {

    private final FuncaoRepository repository;
    private final FuncaoMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public FuncaoResponse criar(FuncaoRequest request) {
        if (repository.existsByNomeIgnoreCase(request.nome())) {
            throw new InvarianteVioladaException("Já existe uma função cadastrada com este nome.");
        }

        Funcao funcao = new Funcao(request.nome(), request.descricao());
        return mapper.toResponse(repository.save(funcao));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public FuncaoResponse atualizar(UUID id, FuncaoRequest request) {
        Funcao funcao = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Função não encontrada"));

        // Verifica unicidade se o nome mudou
        if (!funcao.getNome().equalsIgnoreCase(request.nome()) && repository.existsByNomeIgnoreCase(request.nome())) {
            throw new InvarianteVioladaException("Já existe uma função cadastrada com este nome.");
        }

        funcao.atualizar(request.nome(), request.descricao());
        return mapper.toResponse(repository.save(funcao));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        Funcao funcao = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Função não encontrada"));
        funcao.ativar();
        repository.save(funcao);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        Funcao funcao = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Função não encontrada"));
        funcao.desativar();
        repository.save(funcao);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public FuncaoResponse buscarPorId(UUID id) {
        Funcao funcao = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Função não encontrada"));
        return mapper.toResponse(funcao);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<FuncaoResponse> listar(FuncaoFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(FuncaoSpecification.filterBy(filter), pageable)
                    .map(mapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeFuncao(UUID id) {
        return id != null && repository.existsByIdAndAtivoTrue(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FuncaoResponse> buscarPorIdResponse(UUID id) {
        return repository.findById(id).map(mapper::toResponse);
    }
}
