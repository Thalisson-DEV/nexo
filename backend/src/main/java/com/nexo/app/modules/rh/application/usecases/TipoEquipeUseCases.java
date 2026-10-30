package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.application.dto.TipoEquipeFilter;
import com.nexo.app.modules.rh.application.dto.TipoEquipeRequest;
import com.nexo.app.modules.rh.TipoEquipeResponse;
import com.nexo.app.modules.rh.application.mappers.TipoEquipeMapper;
import com.nexo.app.modules.rh.domain.TipoEquipe;
import com.nexo.app.modules.rh.infrastructure.persistence.TipoEquipeRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.TipoEquipeSpecification;
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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "tipo-equipes")
public class TipoEquipeUseCases {

    private final TipoEquipeRepository repository;
    private final TipoEquipeMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoEquipeResponse criar(TipoEquipeRequest request) {
        if (repository.existsByNomeIgnoreCase(request.nome())) {
            throw new InvarianteVioladaException("Já existe um tipo de equipe com este nome: " + request.nome());
        }

        TipoEquipe tipoEquipe = new TipoEquipe(request.nome());
        return mapper.toResponse(repository.save(tipoEquipe));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoEquipeResponse atualizar(UUID id, TipoEquipeRequest request) {
        TipoEquipe tipoEquipe = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de Equipe não encontrado"));

        repository.findByNomeIgnoreCase(request.nome())
                .ifPresent(existente -> {
                    if (!existente.getId().equals(id)) {
                        throw new InvarianteVioladaException("Já existe outro tipo de equipe com este nome: " + request.nome());
                    }
                });

        tipoEquipe.atualizarNome(request.nome());
        return mapper.toResponse(repository.save(tipoEquipe));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        TipoEquipe tipoEquipe = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de Equipe não encontrado"));
        tipoEquipe.desativar();
        repository.save(tipoEquipe);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        TipoEquipe entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de equipe não encontrado"));

        entity.ativar();
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<TipoEquipeResponse> listar(TipoEquipeFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(TipoEquipeSpecification.filterBy(filter), pageable)
                    .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public TipoEquipeResponse buscarPorId(UUID id) {
        TipoEquipe tipoEquipe = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de Equipe não encontrado"));
        return mapper.toResponse(tipoEquipe);
    }
}
