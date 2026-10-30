package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.AgenteResponsavelFilter;
import com.nexo.app.modules.obras.application.dto.AgenteResponsavelRequest;
import com.nexo.app.modules.obras.application.dto.AgenteResponsavelResponse;
import com.nexo.app.modules.obras.application.mappers.AgenteResponsavelMapper;
import com.nexo.app.modules.obras.domain.AgenteResponsavel;
import com.nexo.app.modules.obras.infrastructure.persistence.AgenteResponsavelRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.AgenteResponsavelSpecification;
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
@CacheConfig(cacheNames = CacheNames.OBRAS_AGENTES)
public class AgenteResponsavelUseCases {

    private final AgenteResponsavelRepository repository;
    private final AgenteResponsavelMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public AgenteResponsavelResponse criar(AgenteResponsavelRequest request) {
        String nome = request.nome().trim().toUpperCase();
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new InvarianteVioladaException("Já existe um agente responsável com o nome informado.");
        }
        AgenteResponsavel agente = new AgenteResponsavel(nome, request.email(), request.telefone(), request.empresaOrgao());
        return mapper.toResponse(repository.save(agente));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public AgenteResponsavelResponse atualizar(UUID id, AgenteResponsavelRequest request) {
        AgenteResponsavel agente = buscarEntidade(id);
        String nome = request.nome().trim().toUpperCase();
        if (repository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw new InvarianteVioladaException("Já existe outro agente responsável com o nome informado.");
        }
        agente.atualizar(nome, request.email(), request.telefone(), request.empresaOrgao());
        return mapper.toResponse(repository.save(agente));
    }

    @Transactional(readOnly = true)
    public AgenteResponsavelResponse buscarPorId(UUID id) {
        return mapper.toResponse(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<AgenteResponsavelResponse> listar(AgenteResponsavelFilter filter, Pageable pageable) {
        Page<AgenteResponsavel> page = repository.findAll(AgenteResponsavelSpecification.filterBy(filter), pageable);
        return PageResponse.fromPage(page.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "'todos-ativos'")
    public List<AgenteResponsavelResponse> listarTodosAtivos() {
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
        AgenteResponsavel agente = buscarEntidade(id);
        agente.desativar();
        repository.save(agente);
    }

    public AgenteResponsavel buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agente responsável não encontrado com o ID: " + id));
    }
}
