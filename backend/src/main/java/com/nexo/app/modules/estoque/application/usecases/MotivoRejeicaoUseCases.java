package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.MotivoRejeicaoFilter;
import com.nexo.app.modules.estoque.application.dto.MotivoRejeicaoRequest;
import com.nexo.app.modules.estoque.application.dto.MotivoRejeicaoResponse;
import com.nexo.app.modules.estoque.application.mappers.MotivoRejeicaoMapper;
import com.nexo.app.modules.estoque.domain.MotivoRejeicao;
import com.nexo.app.modules.estoque.infrastructure.persistence.MotivoRejeicaoRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.MotivoRejeicaoSpecification;
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

import static com.nexo.app.shared.infrastructure.config.CacheNames.MOTIVOS_REJEICAO;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = MOTIVOS_REJEICAO)
public class MotivoRejeicaoUseCases {

    private final MotivoRejeicaoRepository repository;
    private final MotivoRejeicaoMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public MotivoRejeicaoResponse criar(MotivoRejeicaoRequest request) {
        validarUnicidade(request.nome());
        MotivoRejeicao entity = new MotivoRejeicao(request.nome(), request.descricao());
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public MotivoRejeicaoResponse atualizar(UUID id, MotivoRejeicaoRequest request) {
        MotivoRejeicao entity = buscarEntidadePorId(id);

        if (!entity.getNome().equalsIgnoreCase(request.nome())) {
            validarUnicidade(request.nome());
        }

        entity.atualizar(request.nome(), request.descricao());
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<MotivoRejeicaoResponse> listar(MotivoRejeicaoFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(MotivoRejeicaoSpecification.filterBy(filter), pageable)
                        .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public MotivoRejeicaoResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo de rejeição não encontrado"));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        MotivoRejeicao entity = buscarEntidadePorId(id);
        entity.ativar();
        repository.save(entity);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        MotivoRejeicao entity = buscarEntidadePorId(id);
        entity.desativar();
        repository.save(entity);
    }

    public MotivoRejeicao buscarEntidadePorId(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Motivo de rejeição não encontrado"));
    }

    private void validarUnicidade(String nome) {
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw new InvarianteVioladaException("Já existe um motivo de rejeição com este nome");
        }
    }
}
