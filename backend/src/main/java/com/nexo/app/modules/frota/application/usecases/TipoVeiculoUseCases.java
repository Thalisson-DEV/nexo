package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.application.dto.CatalogoFrotaFilter;
import com.nexo.app.modules.frota.application.dto.TipoVeiculoRequest;
import com.nexo.app.modules.frota.application.dto.TipoVeiculoResponse;
import com.nexo.app.modules.frota.application.mappers.CatalogoFrotaMapper;
import com.nexo.app.modules.frota.domain.TipoVeiculo;
import com.nexo.app.modules.frota.infrastructure.persistence.CatalogoFrotaSpecification;
import com.nexo.app.modules.frota.infrastructure.persistence.TipoVeiculoRepository;
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
@CacheConfig(cacheNames = "frota_tipos_veiculo")
public class TipoVeiculoUseCases {

    private final TipoVeiculoRepository repository;
    private final CatalogoFrotaMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoVeiculoResponse criar(TipoVeiculoRequest request) {
        if (repository.existsByNomeIgnoreCase(request.nome().trim())) {
            throw new InvarianteVioladaException("Já existe um tipo de veículo com este nome.");
        }
        var tipo = new TipoVeiculo(request.nome(), request.descricao(), request.categoriaCnhMinima());
        return mapper.toResponse(repository.save(tipo));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoVeiculoResponse atualizar(UUID id, TipoVeiculoRequest request) {
        var tipo = buscarEntidade(id);
        if (repository.existsByNomeIgnoreCaseAndIdNot(request.nome().trim(), id)) {
            throw new InvarianteVioladaException("Já existe um tipo de veículo com este nome.");
        }
        tipo.atualizar(request.nome(), request.descricao(), request.categoriaCnhMinima());
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
    public TipoVeiculoResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort + '-' + (#filtro != null ? #filtro.hashCode() : 'sem-filtro')")
    public PageResponse<TipoVeiculoResponse> listar(CatalogoFrotaFilter filtro, Pageable pageable) {
        var spec = CatalogoFrotaSpecification.<TipoVeiculo>of(
                filtro == null ? null : filtro.busca(), filtro == null ? null : filtro.ativo());
        return PageResponse.fromPage(repository.findAll(spec, pageable).map(mapper::toResponse));
    }

    TipoVeiculo buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de veículo não encontrado."));
    }
}
