package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.AvaliacaoMaterialFilter;
import com.nexo.app.modules.estoque.application.dto.AvaliacaoMaterialRequest;
import com.nexo.app.modules.estoque.AvaliacaoMaterialResponse;
import com.nexo.app.modules.estoque.application.mappers.AvaliacaoMaterialMapper;
import com.nexo.app.modules.estoque.domain.AvaliacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.AvaliacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.AvaliacaoMaterialSpecification;
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

import static com.nexo.app.shared.infrastructure.config.CacheNames.AVALIACOES_MATERIAL;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = AVALIACOES_MATERIAL)
public class AvaliacaoMaterialUseCases {

    private final AvaliacaoMaterialRepository repository;
    private final AvaliacaoMaterialMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public AvaliacaoMaterialResponse criar(AvaliacaoMaterialRequest request) {
        validarUnicidade(request.nome());
        AvaliacaoMaterial entity = new AvaliacaoMaterial(request.nome());
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public AvaliacaoMaterialResponse atualizar(UUID id, AvaliacaoMaterialRequest request) {
        AvaliacaoMaterial entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Avaliação de material não encontrada"));

        if (!entity.getNome().equalsIgnoreCase(request.nome())) {
            validarUnicidade(request.nome());
        }

        entity.atualizarNome(request.nome());
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<AvaliacaoMaterialResponse> listar(AvaliacaoMaterialFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(AvaliacaoMaterialSpecification.filterBy(filter), pageable)
                        .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public AvaliacaoMaterialResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Avaliação de material não encontrada"));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        AvaliacaoMaterial entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Avaliação de material não encontrada"));
        entity.ativar();
        repository.save(entity);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        AvaliacaoMaterial entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Avaliação de material não encontrada"));
        entity.desativar();
        repository.save(entity);
    }

    private void validarUnicidade(String nome) {
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new InvarianteVioladaException("Já existe uma avaliação de material com este nome");
        }
    }
}
