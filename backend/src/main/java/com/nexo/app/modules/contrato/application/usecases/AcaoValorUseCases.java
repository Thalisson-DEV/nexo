package com.nexo.app.modules.contrato.application.usecases;

import com.nexo.app.modules.contrato.application.dto.AcaoValorFilter;
import com.nexo.app.modules.contrato.application.dto.AcaoValorRequest;
import com.nexo.app.modules.contrato.application.dto.AcaoValorResponse;
import com.nexo.app.modules.contrato.application.mappers.AcaoValorMapper;
import com.nexo.app.modules.contrato.domain.Acao;
import com.nexo.app.modules.contrato.domain.AcaoValor;
import com.nexo.app.modules.contrato.domain.Contrato;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoRepository;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoValorRepository;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoValorSpecification;
import com.nexo.app.modules.contrato.infrastructure.persistence.ContratoRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "acoes-valores")
public class AcaoValorUseCases {

    private final AcaoValorRepository repository;
    private final AcaoRepository acaoRepository;
    private final ContratoRepository contratoRepository;
    private final AcaoValorMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public AcaoValorResponse criar(AcaoValorRequest request) {
        Acao acao = acaoRepository.findById(request.idAcao())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ação não encontrada"));

        Contrato contrato = contratoRepository.findById(request.idContrato())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contrato não encontrado"));

        repository.findByAcaoIdAndContratoIdAndDataVigencia(request.idAcao(), request.idContrato(), request.dataVigencia())
                .ifPresent(v -> {
                    throw new InvarianteVioladaException("Já existe um valor definido para esta ação, contrato e data de vigência");
                });

        AcaoValor acaoValor = new AcaoValor(acao, contrato, request.valorBase(), request.dataVigencia());
        return mapper.toResponse(repository.save(acaoValor));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public AcaoValorResponse atualizar(UUID id, AcaoValorRequest request) {
        AcaoValor acaoValor = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Valor da ação não encontrado"));
        acaoValor.atualizarValor(request.valorBase());
        return mapper.toResponse(repository.save(acaoValor));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        AcaoValor acaoValor = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Valor da ação não encontrado"));
        acaoValor.desativar();
        repository.save(acaoValor);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        AcaoValor acaoValor = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Valor de ação não encontrado"));

        acaoValor.ativar();
        repository.save(acaoValor);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public AcaoValorResponse buscarPorId(UUID id) {
        AcaoValor acaoValor = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Valor da ação não encontrado"));
        return mapper.toResponse(acaoValor);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<AcaoValorResponse> listar(AcaoValorFilter filter, Pageable pageable) {
        Specification<AcaoValor> spec = AcaoValorSpecification.filterBy(filter);
        return PageResponse.fromPage(
                repository.findAll(spec, pageable).map(mapper::toResponse)
        );
    }
}
