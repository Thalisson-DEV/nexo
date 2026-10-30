package com.nexo.app.modules.desconto.application.usecases;

import com.nexo.app.modules.desconto.application.dto.MotivoDescontoRequest;
import com.nexo.app.modules.desconto.application.dto.MotivoDescontoResponse;
import com.nexo.app.modules.desconto.application.mappers.MotivoDescontoMapper;
import com.nexo.app.modules.desconto.domain.MotivoDesconto;
import com.nexo.app.modules.desconto.infrastructure.persistence.MotivoDescontoRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MotivoDescontoUseCases {

    private final MotivoDescontoRepository repository;
    private final MotivoDescontoMapper mapper;

    @Transactional
    @CacheEvict(value = "motivos_desconto", allEntries = true)
    public MotivoDescontoResponse criar(MotivoDescontoRequest request) {
        var entity = new MotivoDesconto(request.descricao(), request.tetoParcelas());
        repository.save(entity);
        return mapper.toResponse(entity);
    }

    @Transactional
    @CacheEvict(value = "motivos_desconto", allEntries = true)
    public MotivoDescontoResponse atualizar(UUID id, MotivoDescontoRequest request) {
        var entity = buscarEntity(id);
        entity.atualizar(request.descricao(), request.tetoParcelas());
        repository.save(entity);
        return mapper.toResponse(entity);
    }

    @Transactional
    @CacheEvict(value = "motivos_desconto", allEntries = true)
    public void desativar(UUID id) {
        var entity = buscarEntity(id);
        entity.desativar();
        repository.save(entity);
    }

    @Transactional
    @CacheEvict(value = "motivos_desconto", allEntries = true)
    public void ativar(UUID id) {
        var entity = buscarEntity(id);
        entity.ativar();
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public MotivoDescontoResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntity(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "motivos_desconto", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + (#filtro != null ? #filtro.hashCode() : 'no-filter')")
    public PageResponse<MotivoDescontoResponse> listar(com.nexo.app.modules.desconto.application.dto.MotivoDescontoFilter filtro, Pageable pageable) {
        Page<com.nexo.app.modules.desconto.domain.MotivoDesconto> page = repository.findAll(com.nexo.app.modules.desconto.infrastructure.persistence.MotivoDescontoSpecification.of(filtro), pageable);
        return PageResponse.fromPage(page.map(mapper::toResponse));
    }

    protected MotivoDesconto buscarEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo de desconto não encontrado."));
    }
}
