package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.SaldoFilter;
import com.nexo.app.modules.estoque.application.dto.SaldoResponse;
import com.nexo.app.modules.estoque.application.dto.SaldoSerieFilter;
import com.nexo.app.modules.estoque.application.dto.SaldoSerieResponse;
import com.nexo.app.modules.estoque.application.mappers.SaldoMapper;
import com.nexo.app.modules.estoque.application.mappers.SaldoSerieMapper;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSerieSpecification;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SALDOS;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = SALDOS)
public class SaldoUseCases {

    private final SaldoRepository repository;
    private final SaldoMapper mapper;
    
    private final SaldoSerieRepository serieRepository;
    private final SaldoSerieMapper serieMapper;

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<SaldoResponse> listar(SaldoFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(SaldoSpecification.filterBy(filter), pageable)
                        .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<SaldoSerieResponse> listarSeriais(SaldoSerieFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                serieRepository.findAll(SaldoSerieSpecification.filterBy(filter), pageable)
                        .map(serieMapper::toResponse)
        );
    }

    /** Séries já ativas em algum estoque (a entrada avulsa recusa série repetida no sistema). */
    @Transactional(readOnly = true)
    public List<SaldoSerieResponse> verificarSeries(List<String> series) {
        List<String> normalizadas = series.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (normalizadas.isEmpty()) return List.of();
        return serieRepository.findAllByNumSerieInAndAtivoTrue(normalizadas).stream()
                .map(serieMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public SaldoResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Saldo não encontrado"));
    }
}
