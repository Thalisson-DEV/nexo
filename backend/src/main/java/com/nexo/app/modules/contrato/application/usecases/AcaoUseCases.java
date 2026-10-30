package com.nexo.app.modules.contrato.application.usecases;

import com.nexo.app.modules.contrato.application.dto.AcaoFilter;
import com.nexo.app.modules.contrato.application.dto.AcaoRequest;
import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.application.mappers.AcaoMapper;
import com.nexo.app.modules.contrato.domain.Acao;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoRepository;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "acoes")
public class AcaoUseCases {
    private final AcaoRepository repository;
    private final AcaoMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public AcaoResponse criar(AcaoRequest request) {
        if (repository.existsByCodigoIgnoreCase(request.codigo())) {
            throw new InvarianteVioladaException("Já existe uma ação com este código: " + request.codigo());
        }
        Acao acao = new Acao(request.codigo(), request.nome());
        return mapper.toResponse(repository.save(acao));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public AcaoResponse atualizar(UUID id, AcaoRequest request) {
        Acao acao = repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Ação não encontrada"));

        repository.findByCodigoIgnoreCase(request.codigo()).ifPresent(existente -> {
            if (!existente.getId().equals(id)) throw new InvarianteVioladaException("Código já em uso por outra ação");
        });

        acao.atualizar(request.codigo(), request.nome());
        return mapper.toResponse(repository.save(acao));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        Acao acao = repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Ação não encontrada"));
        acao.desativar();
        repository.save(acao);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        Acao acao = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ação não encontrada"));

        acao.ativar();
        repository.save(acao);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public AcaoResponse buscarPorId(UUID id) {
        Acao acao = repository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Ação não encontrada"));
        return mapper.toResponse(acao);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<AcaoResponse> listar(AcaoFilter filter, Pageable pageable) {
        Specification<Acao> spec = AcaoSpecification.filterBy(filter);
        return PageResponse.fromPage(
                repository.findAll(spec, pageable).map(mapper::toResponse)
        );
    }
}
