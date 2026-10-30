package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.CentroInvestimentoFilter;
import com.nexo.app.modules.estoque.application.dto.CentroInvestimentoRequest;
import com.nexo.app.modules.estoque.CentroInvestimentoResponse;
import com.nexo.app.modules.estoque.application.mappers.CentroInvestimentoMapper;
import com.nexo.app.modules.estoque.domain.CentroInvestimento;
import com.nexo.app.modules.estoque.infrastructure.persistence.CentroInvestimentoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.CentroInvestimentoSpecification;
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

import static com.nexo.app.shared.infrastructure.config.CacheNames.CENTROS_INVESTIMENTO;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = CENTROS_INVESTIMENTO)
public class CentroInvestimentoUseCases {

    private final CentroInvestimentoRepository repository;
    private final CentroInvestimentoMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public CentroInvestimentoResponse criar(CentroInvestimentoRequest request) {
        validarUnicidade(request.codigo());
        CentroInvestimento entity = new CentroInvestimento(request.codigo());
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public CentroInvestimentoResponse atualizar(UUID id, CentroInvestimentoRequest request) {
        CentroInvestimento entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Centro de investimento não encontrado"));

        if (!entity.getCodigo().equalsIgnoreCase(request.codigo())) {
            validarUnicidade(request.codigo());
        }

        entity.atualizarCodigo(request.codigo());
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<CentroInvestimentoResponse> listar(CentroInvestimentoFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(CentroInvestimentoSpecification.filterBy(filter), pageable)
                        .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public CentroInvestimentoResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Centro de investimento não encontrado"));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        CentroInvestimento entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Centro de investimento não encontrado"));
        entity.ativar();
        repository.save(entity);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        CentroInvestimento entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Centro de investimento não encontrado"));
        entity.desativar();
        repository.save(entity);
    }

    private void validarUnicidade(String codigo) {
        if (repository.existsByCodigoIgnoreCase(codigo)) {
            throw new InvarianteVioladaException("Já existe um centro de investimento com este código");
        }
    }
}
