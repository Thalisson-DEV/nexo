package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.application.dto.BaseOperacionalFilter;
import com.nexo.app.modules.rh.application.dto.BaseOperacionalRequest;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.application.mappers.BaseOperacionalMapper;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.CentroFaturamento;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.BaseOperacionalSpecification;
import com.nexo.app.modules.rh.infrastructure.persistence.CentroFaturamentoRepository;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nexo.app.shared.infrastructure.config.CacheNames.BASES_OPERACIONAIS;
import static com.nexo.app.shared.infrastructure.config.CacheNames.LOOKUP_BOOTSTRAP;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = BASES_OPERACIONAIS)
public class BaseOperacionalUseCases implements BaseOperacionalApi {

    private final BaseOperacionalRepository repository;
    private final CentroFaturamentoRepository centroFaturamentoRepository;
    private final BaseOperacionalMapper mapper;

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = BASES_OPERACIONAIS, allEntries = true),
            @CacheEvict(cacheNames = LOOKUP_BOOTSTRAP, allEntries = true)
    })
    public BaseOperacionalResponse criar(BaseOperacionalRequest request) {
        validarUnicidadeNome(request.nome());

        CentroFaturamento centroFaturamento = centroFaturamentoRepository.findById(request.centroCustoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Centro de faturamento não encontrado"));

        BaseOperacional base = new BaseOperacional(
                request.nome(),
                request.uf(),
                request.endereco(),
                centroFaturamento
        );

        base.configurarLocalizacao(request.latitude(), request.longitude(), request.raio());

        return mapper.toResponse(repository.save(base));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = BASES_OPERACIONAIS, allEntries = true),
            @CacheEvict(cacheNames = LOOKUP_BOOTSTRAP, allEntries = true)
    })
    public BaseOperacionalResponse atualizar(UUID id, BaseOperacionalRequest request) {
        BaseOperacional base = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base Operacional não encontrada"));

        validarUnicidadeNome(request.nome(), id);

        CentroFaturamento centroFaturamento = centroFaturamentoRepository.findById(request.centroCustoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Centro de faturamento não encontrado"));

        base.atualizar(request.nome(), request.uf(), request.endereco(), centroFaturamento);
        base.configurarLocalizacao(request.latitude(), request.longitude(), request.raio());

        return mapper.toResponse(repository.save(base));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<BaseOperacionalResponse> listar(BaseOperacionalFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(BaseOperacionalSpecification.filterBy(filter), pageable)
                        .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public BaseOperacionalResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base Operacional não encontrada"));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = BASES_OPERACIONAIS, allEntries = true),
            @CacheEvict(cacheNames = LOOKUP_BOOTSTRAP, allEntries = true)
    })
    public void ativar(UUID id) {
        BaseOperacional entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base operacional não encontrada"));

        entity.ativar();
        repository.save(entity);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = BASES_OPERACIONAIS, allEntries = true),
            @CacheEvict(cacheNames = LOOKUP_BOOTSTRAP, allEntries = true)
    })
    public void desativar(UUID id) {
        repository.findById(id)
                .ifPresentOrElse(
                        base -> {
                            base.desativar();
                            repository.save(base);
                        },
                        () -> { throw new RecursoNaoEncontradoException("Base Operacional não encontrada"); }
                );
    }

    @Transactional(readOnly = true)
    public boolean existePorNome(String nome) {
        return repository.existsByNomeIgnoreCase(nome);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<UUID> buscarIdPorNome(String nome) {
        return repository.findByNomeIgnoreCase(nome)
                .map(BaseOperacional::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> buscarIdsPorTrecho(String trecho) {
        String termo = SpecificationUtils.normalizarTermo(trecho);
        return termo == null || termo.isBlank() ? List.of() : repository.buscarIdsPorTrecho(termo);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeBase(UUID id) {
        return id != null && repository.findById(id).filter(BaseOperacional::isAtivo).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<BaseOperacionalResponse> buscarPorIdResponse(UUID id) {
        return id == null ? java.util.Optional.empty() : repository.findById(id).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BaseOperacionalResponse> buscarPorIdsResponse(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> distintos = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distintos.isEmpty()) {
            return Map.of();
        }
        return repository.buscarComCentroPorIds(distintos).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toMap(BaseOperacionalResponse::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LookupOptionDto> listarOpcoesAtivas() {
        return repository.listarOpcoesAtivas().stream()
                .map(base -> new LookupOptionDto(base.getId().toString(), base.getLabel()))
                .toList();
    }

    private void validarUnicidadeNome(String nome) {
        if (existePorNome(nome)) {
            throw new InvarianteVioladaException("Já existe uma base operacional com este nome");
        }
    }

    private void validarUnicidadeNome(String nome, UUID idIgnorado) {
        repository.findByNomeIgnoreCase(nome)
                .ifPresent(base -> {
                    if (!base.getId().equals(idIgnorado)) {
                        throw new InvarianteVioladaException("Já existe uma base operacional com este nome");
                    }
                });
    }
}
