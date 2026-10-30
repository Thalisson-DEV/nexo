package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.TipoAnexoObraFilter;
import com.nexo.app.modules.obras.application.dto.TipoAnexoObraRequest;
import com.nexo.app.modules.obras.application.dto.TipoAnexoObraResponse;
import com.nexo.app.modules.obras.application.mappers.TipoAnexoObraMapper;
import com.nexo.app.modules.obras.domain.TipoAnexoObra;
import com.nexo.app.modules.obras.infrastructure.persistence.TipoAnexoObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.TipoAnexoObraSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = CacheNames.OBRAS_TIPOS_ANEXOS)
public class TipoAnexoObraUseCases {

    private final TipoAnexoObraRepository repository;
    private final TipoAnexoObraMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoAnexoObraResponse criar(TipoAnexoObraRequest request) {
        String nome = request.nome().trim().toUpperCase();
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new InvarianteVioladaException("Já existe um tipo de anexo com o nome informado.");
        }
        TipoAnexoObra tipo = new TipoAnexoObra(nome, request.descricao());
        return mapper.toResponse(repository.save(tipo));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public TipoAnexoObraResponse atualizar(UUID id, TipoAnexoObraRequest request) {
        TipoAnexoObra tipo = buscarEntidade(id);
        String nome = request.nome().trim().toUpperCase();
        if (repository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new InvarianteVioladaException("Já existe outro tipo de anexo com o nome informado.");
        }
        tipo.atualizar(nome, request.descricao());
        return mapper.toResponse(repository.save(tipo));
    }

    @Transactional(readOnly = true)
    public TipoAnexoObraResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TipoAnexoObraResponse> listar(TipoAnexoObraFilter filter, Pageable pageable) {
        Page<TipoAnexoObra> page = repository.findAll(TipoAnexoObraSpecification.filterBy(filter), pageable);
        return PageResponse.fromPage(page.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "'todos-ativos'")
    public List<TipoAnexoObraResponse> listarTodosAtivos() {
        return mapper.toResponseList(
                repository.findAll(
                        (root, query, cb) -> cb.isTrue(root.get("ativo")),
                        Sort.by(Sort.Direction.ASC, "nome")
                )
        );
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        TipoAnexoObra tipo = buscarEntidade(id);
        tipo.desativar();
        repository.save(tipo);
    }

    public TipoAnexoObra buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de anexo de obra não encontrado com o ID: " + id));
    }
}
