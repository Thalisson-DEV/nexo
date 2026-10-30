package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.StatusObraFilter;
import com.nexo.app.modules.obras.application.dto.StatusObraRequest;
import com.nexo.app.modules.obras.application.dto.StatusObraResponse;
import com.nexo.app.modules.obras.application.mappers.StatusObraMapper;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.infrastructure.persistence.StatusObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.StatusObraSpecification;
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
@CacheConfig(cacheNames = CacheNames.OBRAS_STATUS)
public class StatusObraUseCases {

    private final StatusObraRepository repository;
    private final StatusObraMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public StatusObraResponse criar(StatusObraRequest request) {
        String nome = request.nome().trim().toUpperCase();
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new InvarianteVioladaException("Já existe um status de obra com o nome informado.");
        }
        StatusObra status = new StatusObra(nome, request.ordem(), Boolean.TRUE.equals(request.concluida()));
        return mapper.toResponse(repository.save(status));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public StatusObraResponse atualizar(UUID id, StatusObraRequest request) {
        StatusObra status = buscarEntidade(id);
        String nome = request.nome().trim().toUpperCase();
        if (repository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new InvarianteVioladaException("Já existe outro status de obra com o nome informado.");
        }
        status.atualizar(nome, request.ordem(), Boolean.TRUE.equals(request.concluida()));
        return mapper.toResponse(repository.save(status));
    }

    @Transactional(readOnly = true)
    public StatusObraResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<StatusObraResponse> listar(StatusObraFilter filter, Pageable pageable) {
        Page<StatusObra> page = repository.findAll(StatusObraSpecification.filterBy(filter), pageable);
        return PageResponse.fromPage(page.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "'todas-ativas'")
    public List<StatusObraResponse> listarTodasAtivas() {
        return mapper.toResponseList(
                repository.findAll(
                        (root, query, cb) -> cb.isTrue(root.get("ativo")),
                        Sort.by(Sort.Direction.ASC, "ordem", "nome")
                )
        );
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        StatusObra status = buscarEntidade(id);
        status.desativar();
        repository.save(status);
    }

    public StatusObra buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Status de obra não encontrado com o ID: " + id));
    }
}
