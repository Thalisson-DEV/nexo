package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.application.dto.MunicipioFilter;
import com.nexo.app.modules.rh.application.dto.MunicipioRequest;
import com.nexo.app.modules.rh.MunicipioResponse;
import com.nexo.app.modules.rh.application.mappers.MunicipioMapper;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.Municipio;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.MunicipioRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.MunicipioSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "municipios")
public class MunicipioUseCases implements MunicipioApi {

    private final MunicipioRepository repository;
    private final BaseOperacionalRepository baseOperacionalRepository;
    private final MunicipioMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public MunicipioResponse criar(MunicipioRequest request) {
        BaseOperacional base = baseOperacionalRepository.findById(request.idBaseOperacional())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base operacional não encontrada"));
        Municipio municipio = new Municipio(request.nome(), base);
        return mapper.toResponse(repository.save(municipio));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public MunicipioResponse atualizar(UUID id, MunicipioRequest request) {
        Municipio municipio = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Município não encontrado"));
        BaseOperacional base = baseOperacionalRepository.findById(request.idBaseOperacional())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base operacional não encontrada"));
        municipio.atualizar(request.nome(), base);
        return mapper.toResponse(repository.save(municipio));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        Municipio municipio = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Município não encontrado"));

        municipio.ativar();
        repository.save(municipio);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        Municipio municipio = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Município não encontrado"));
        municipio.desativar();
        repository.save(municipio);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public MunicipioResponse buscarPorId(UUID id) {
        Municipio municipio = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Município não encontrado"));
        return mapper.toResponse(municipio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MunicipioResponse> buscarPorIdResponse(UUID id) {
        return repository.findById(id).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<MunicipioResponse> listar(MunicipioFilter filter, Pageable pageable) {
        Specification<Municipio> spec = MunicipioSpecification.filterBy(filter);
        return PageResponse.fromPage(
                repository.findAll(spec, pageable).map(mapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> buscarIdPorNome(String nome) {
        if (nome == null || nome.isBlank()) return Optional.empty();
        return repository.findByNomeIgnoreCase(nome.trim()).map(Municipio::getId);
    }
}
