package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.application.dto.ProcessoNegocioFilter;
import com.nexo.app.modules.rh.application.dto.ProcessoNegocioRequest;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.rh.application.mappers.ProcessoNegocioMapper;
import com.nexo.app.modules.rh.domain.ProcessoNegocio;
import com.nexo.app.modules.rh.infrastructure.persistence.ProcessoNegocioRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.ProcessoNegocioSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.LOOKUP_BOOTSTRAP;
import static com.nexo.app.shared.infrastructure.config.CacheNames.PROCESSOS_NEGOCIO;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = PROCESSOS_NEGOCIO)
public class ProcessoNegocioUseCases implements ProcessoNegocioApi {

    private final ProcessoNegocioRepository repository;
    private final ProcessoNegocioMapper mapper;

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = PROCESSOS_NEGOCIO, allEntries = true),
            @CacheEvict(cacheNames = LOOKUP_BOOTSTRAP, allEntries = true)
    })
    public ProcessoNegocioResponse criar(ProcessoNegocioRequest request) {
        validarUnicidadeNome(request.nome());

        ProcessoNegocio processo = new ProcessoNegocio(request.nome());

        return mapper.toResponse(repository.save(processo));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = PROCESSOS_NEGOCIO, allEntries = true),
            @CacheEvict(cacheNames = LOOKUP_BOOTSTRAP, allEntries = true)
    })
    public ProcessoNegocioResponse atualizar(UUID id, ProcessoNegocioRequest request) {
        ProcessoNegocio processo = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo de Negócio não encontrado"));

        validarUnicidadeNomeEId(request.nome(), id);

        processo.atualizarNome(request.nome());

        return mapper.toResponse(repository.save(processo));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = PROCESSOS_NEGOCIO, allEntries = true),
            @CacheEvict(cacheNames = LOOKUP_BOOTSTRAP, allEntries = true)
    })
    public void desativar(UUID id) {
        repository.findById(id)
                .ifPresentOrElse(
                        processo -> {
                            processo.desativar();
                            repository.save(processo);
                        },
                        () -> { throw new RecursoNaoEncontradoException("Processo de Negócio não encontrado"); }
                );
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = PROCESSOS_NEGOCIO, allEntries = true),
            @CacheEvict(cacheNames = LOOKUP_BOOTSTRAP, allEntries = true)
    })
    public void ativar(UUID id) {
        ProcessoNegocio entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo de negócio não encontrado"));

        entity.ativar();
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public ProcessoNegocioResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo de Negócio não encontrado"));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<ProcessoNegocioResponse> listar(ProcessoNegocioFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(ProcessoNegocioSpecification.filterBy(filter), pageable)
                    .map(mapper::toResponse)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeProcesso(UUID id) {
        return repository.existsByIdAndAtivoTrue(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessoNegocioResponse> buscarPorIdResponse(UUID id) {
        return repository.findById(id).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> buscarIdPorNome(String nome) {
        return repository.findByNomeIgnoreCase(nome)
                .filter(ProcessoNegocio::isAtivo)
                .map(ProcessoNegocio::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> buscarIdsPorTrecho(String trecho) {
        String termo = SpecificationUtils.normalizarTermo(trecho);
        return termo == null || termo.isBlank() ? List.of() : repository.buscarIdsPorTrecho(termo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LookupOptionDto> listarOpcoesAtivas() {
        return repository.listarOpcoesAtivas().stream()
                .map(processo -> new LookupOptionDto(processo.getId().toString(), processo.getLabel()))
                .toList();
    }

    private void validarUnicidadeNomeEId(String nome, UUID id) {
        repository.findByNomeIgnoreCase(nome)
                .filter(p -> !p.getId().equals(id))
                .ifPresent(p -> {
                    throw new InvarianteVioladaException("Já existe outro processo de negócio com este nome");
                });
    }

    private void validarUnicidadeNome(String nome) {
        repository.findByNomeIgnoreCase(nome).ifPresent(p -> {
            throw new InvarianteVioladaException("Já existe um processo de negócio com este nome");
        });
    }
}
