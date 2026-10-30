package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.CentroFaturamentoApi;
import com.nexo.app.modules.rh.application.dto.CentroFaturamentoFilter;
import com.nexo.app.modules.rh.application.dto.CentroFaturamentoRequest;
import com.nexo.app.modules.rh.CentroFaturamentoResponse;
import com.nexo.app.modules.rh.application.mappers.CentroFaturamentoMapper;
import com.nexo.app.modules.rh.domain.CentroFaturamento;
import com.nexo.app.modules.rh.infrastructure.persistence.CentroFaturamentoRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CentroFaturamentoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "centros-faturamento")
public class CentroFaturamentoUseCases implements CentroFaturamentoApi {

    private final CentroFaturamentoRepository repository;
    private final CentroFaturamentoMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public CentroFaturamentoResponse criar(CentroFaturamentoRequest request) {
        if (repository.existsByNomeIgnoreCase(request.nome())) {
            throw new InvarianteVioladaException("Já existe um centro de faturamento com este nome");
        }

        CentroFaturamento centro = new CentroFaturamento(request.nome());
        return mapper.toResponse(repository.save(centro));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public CentroFaturamentoResponse atualizar(UUID id, CentroFaturamentoRequest request) {
        CentroFaturamento centro = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Centro de faturamento não encontrado"));

        repository.findByNomeIgnoreCase(request.nome())
                .ifPresent(existente -> {
                    if (!existente.getId().equals(id)) {
                        throw new InvarianteVioladaException("Já existe outro centro de faturamento com este nome");
                    }
                });

        centro.atualizar(request.nome());
        return mapper.toResponse(repository.save(centro));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        CentroFaturamento entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Centro de faturamento não encontrado"));

        entity.ativar();
        repository.save(entity);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        CentroFaturamento centro = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Centro de faturamento não encontrado"));

        centro.desativar();
        repository.save(centro);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<CentroFaturamentoResponse> listar(CentroFaturamentoFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(CentroFaturamentoSpecification.filterBy(filter), pageable)
                    .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public CentroFaturamentoResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Centro de faturamento não encontrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existePorId(UUID id) {
        return id != null && repository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CentroFaturamentoResponse> buscarPorIdResponse(UUID id) {
        if (id == null) return Optional.empty();
        return repository.findById(id).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> buscarIdPorNome(String nome) {
        if (nome == null || nome.isBlank()) return Optional.empty();
        return repository.findByNomeIgnoreCase(nome.trim()).map(CentroFaturamento::getId);
    }
}
