package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.PermissaoFilter;
import com.nexo.app.modules.iam.application.dto.PermissaoRequest;
import com.nexo.app.modules.iam.application.dto.PermissaoResponse;
import com.nexo.app.modules.iam.application.mappers.PermissaoMapper;
import com.nexo.app.modules.iam.domain.Permissao;
import com.nexo.app.modules.iam.infrastructure.persistence.PermissaoRepository;
import com.nexo.app.modules.iam.infrastructure.persistence.PermissaoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
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
@CacheConfig(cacheNames = CacheNames.PERMISSOES)
public class PermissaoUseCases {

    private final PermissaoRepository repository;
    private final PermissaoMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public PermissaoResponse criar(PermissaoRequest request) {
        validarUnicidade(request.nome());
        Permissao permissao = new Permissao(request.nome());
        return mapper.toResponse(repository.save(permissao));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public PermissaoResponse atualizar(UUID id, PermissaoRequest request) {
        Permissao permissao = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Permissão não encontrada"));

        if (!permissao.getNome().equalsIgnoreCase(request.nome())) {
            validarUnicidade(request.nome());
        }

        permissao.atualizarNome(request.nome());
        return mapper.toResponse(repository.save(permissao));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void alterarStatus(UUID id, boolean ativo) {
        Permissao permissao = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Permissão não encontrada"));

        if (ativo) {
            permissao.ativar();
        } else {
            permissao.desativar();
        }

        repository.save(permissao);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<PermissaoResponse> listar(PermissaoFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(PermissaoSpecification.filterBy(filter), pageable)
                        .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public PermissaoResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Permissão não encontrada"));
    }

    private void validarUnicidade(String nome) {
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new InvarianteVioladaException("Já existe uma permissão com este nome");
        }
    }
}
