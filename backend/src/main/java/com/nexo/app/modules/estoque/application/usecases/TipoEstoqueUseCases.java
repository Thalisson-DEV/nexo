package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.TipoEstoqueFilter;
import com.nexo.app.modules.estoque.application.dto.TipoEstoqueRequest;
import com.nexo.app.modules.estoque.application.dto.TipoEstoqueResponse;
import com.nexo.app.modules.estoque.application.mappers.TipoEstoqueMapper;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.TipoEstoqueSpecification;
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

import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.TIPOS_ESTOQUE;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = TIPOS_ESTOQUE)
public class TipoEstoqueUseCases {

    private final TipoEstoqueRepository repository;
    private final TipoEstoqueMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoEstoqueResponse criar(TipoEstoqueRequest request) {
        validarUnicidade(request.nome());
        TipoEstoque entity = new TipoEstoque(request.nome());
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoEstoqueResponse atualizar(UUID id, TipoEstoqueRequest request) {
        TipoEstoque entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de estoque não encontrado"));

        if (!entity.getNome().equalsIgnoreCase(request.nome())) {
            validarUnicidade(request.nome());
        }

        entity.atualizarNome(request.nome());
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<TipoEstoqueResponse> listar(TipoEstoqueFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(TipoEstoqueSpecification.filterBy(filter), pageable)
                        .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public TipoEstoqueResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de estoque não encontrado"));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        TipoEstoque entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de estoque não encontrado"));
        entity.ativar();
        repository.save(entity);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        TipoEstoque entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de estoque não encontrado"));
        entity.desativar();
        repository.save(entity);
    }

    private void validarUnicidade(String nome) {
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new InvarianteVioladaException("Já existe um tipo de estoque com este nome");
        }
    }
}
