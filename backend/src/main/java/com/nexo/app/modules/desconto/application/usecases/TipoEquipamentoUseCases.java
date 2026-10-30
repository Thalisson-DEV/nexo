package com.nexo.app.modules.desconto.application.usecases;

import com.nexo.app.modules.desconto.application.dto.TipoEquipamentoRequest;
import com.nexo.app.modules.desconto.application.dto.TipoEquipamentoResponse;
import com.nexo.app.modules.desconto.application.mappers.TipoEquipamentoMapper;
import com.nexo.app.modules.desconto.domain.TipoEquipamento;
import com.nexo.app.modules.desconto.infrastructure.persistence.TipoEquipamentoRepository;
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
public class TipoEquipamentoUseCases {

    private final TipoEquipamentoRepository repository;
    private final TipoEquipamentoMapper mapper;

    @Transactional
    @CacheEvict(value = "tipos_equipamento", allEntries = true)
    public TipoEquipamentoResponse criar(TipoEquipamentoRequest request) {
        var entity = new TipoEquipamento(request.descricao());
        repository.save(entity);
        return mapper.toResponse(entity);
    }

    @Transactional
    @CacheEvict(value = "tipos_equipamento", allEntries = true)
    public TipoEquipamentoResponse atualizar(UUID id, TipoEquipamentoRequest request) {
        var entity = buscarEntity(id);
        entity.atualizar(request.descricao());
        repository.save(entity);
        return mapper.toResponse(entity);
    }

    @Transactional
    @CacheEvict(value = "tipos_equipamento", allEntries = true)
    public void desativar(UUID id) {
        var entity = buscarEntity(id);
        entity.desativar();
        repository.save(entity);
    }

    @Transactional
    @CacheEvict(value = "tipos_equipamento", allEntries = true)
    public void ativar(UUID id) {
        var entity = buscarEntity(id);
        entity.ativar();
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public TipoEquipamentoResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntity(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "tipos_equipamento", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + (#filtro != null ? #filtro.hashCode() : 'no-filter')")
    public PageResponse<TipoEquipamentoResponse> listar(com.nexo.app.modules.desconto.application.dto.TipoEquipamentoFilter filtro, Pageable pageable) {
        Page<TipoEquipamento> page = repository.findAll(com.nexo.app.modules.desconto.infrastructure.persistence.TipoEquipamentoSpecification.of(filtro), pageable);
        return PageResponse.fromPage(page.map(mapper::toResponse));
    }

    protected TipoEquipamento buscarEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de equipamento não encontrado."));
    }
}
