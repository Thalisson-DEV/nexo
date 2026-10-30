package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.application.dto.CatalogoFrotaFilter;
import com.nexo.app.modules.frota.application.dto.TipoImplementoRequest;
import com.nexo.app.modules.frota.application.dto.TipoImplementoResponse;
import com.nexo.app.modules.frota.application.mappers.CatalogoFrotaMapper;
import com.nexo.app.modules.frota.domain.TipoImplemento;
import com.nexo.app.modules.frota.infrastructure.persistence.CatalogoFrotaSpecification;
import com.nexo.app.modules.frota.infrastructure.persistence.TipoImplementoRepository;
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

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "frota_tipos_implemento")
public class TipoImplementoUseCases {

    private final TipoImplementoRepository repository;
    private final CatalogoFrotaMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoImplementoResponse criar(TipoImplementoRequest request) {
        if (repository.existsByNomeIgnoreCase(request.nome().trim())) {
            throw new InvarianteVioladaException("Já existe um tipo de implemento com este nome.");
        }
        return mapper.toResponse(repository.save(new TipoImplemento(request.nome(), request.descricao())));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoImplementoResponse atualizar(UUID id, TipoImplementoRequest request) {
        var tipo = buscarEntidade(id);
        if (repository.existsByNomeIgnoreCaseAndIdNot(request.nome().trim(), id)) {
            throw new InvarianteVioladaException("Já existe um tipo de implemento com este nome.");
        }
        tipo.atualizar(request.nome(), request.descricao());
        return mapper.toResponse(repository.save(tipo));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        var tipo = buscarEntidade(id);
        tipo.desativar();
        repository.save(tipo);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        var tipo = buscarEntidade(id);
        tipo.ativar();
        repository.save(tipo);
    }

    @Transactional(readOnly = true)
    public TipoImplementoResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort + '-' + (#filtro != null ? #filtro.hashCode() : 'sem-filtro')")
    public PageResponse<TipoImplementoResponse> listar(CatalogoFrotaFilter filtro, Pageable pageable) {
        var spec = CatalogoFrotaSpecification.<TipoImplemento>of(
                filtro == null ? null : filtro.busca(), filtro == null ? null : filtro.ativo());
        return PageResponse.fromPage(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    TipoImplemento buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de implemento não encontrado."));
    }
}
