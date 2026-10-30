package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.CoordenadorApi;
import com.nexo.app.modules.rh.CoordenadorResponse;
import com.nexo.app.modules.rh.application.dto.CoordenadorFilter;
import com.nexo.app.modules.rh.application.dto.CoordenadorMinResponse;
import com.nexo.app.modules.rh.application.dto.CoordenadorRequest;
import com.nexo.app.modules.rh.application.mappers.CoordenadorMapper;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Coordenador;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CoordenadorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CoordenadorSpecification;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.nexo.app.shared.infrastructure.security.HashUtils;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = CacheNames.COORDENADORES)
public class CoordenadorUseCases implements CoordenadorApi {

    private final CoordenadorRepository repository;
    private final ColaboradorRepository colaboradorRepository;
    private final CoordenadorMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public CoordenadorResponse criar(CoordenadorRequest request) {
        Colaborador colaborador = colaboradorRepository.findById(request.idColaborador())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado"));

        if (!colaborador.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular um colaborador desativado.");
        }

        if (repository.existsByColaboradorIdAndAtivoTrue(colaborador.getId())) {
            throw new InvarianteVioladaException("Este colaborador já possui um registro ativo de coordenador.");
        }

        Coordenador coordenador = new Coordenador(
                colaborador,
                request.dataInicio(),
                request.dataFim()
        );

        return mapper.toResponse(repository.save(coordenador));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public CoordenadorResponse atualizar(UUID id, CoordenadorRequest request) {
        Coordenador coordenador = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Coordenador não encontrado"));

        coordenador.validarStatus();

        if (!coordenador.getColaborador().getId().equals(request.idColaborador())) {
            Colaborador novoColaborador = colaboradorRepository.findById(request.idColaborador())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Novo colaborador não encontrado"));

            if (repository.existsByColaboradorIdAndAtivoTrueAndIdNot(novoColaborador.getId(), id)) {
                throw new InvarianteVioladaException("O novo colaborador já possui outro registro ativo de coordenador.");
            }
            coordenador.atualizarColaborador(novoColaborador);
        }

        coordenador.atualizar(request.dataInicio(), request.dataFim());

        return mapper.toResponse(repository.save(coordenador));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        Coordenador coordenador = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Coordenador não encontrado"));

        coordenador.ativar();
        repository.save(coordenador);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        Coordenador coordenador = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Coordenador não encontrado"));

        coordenador.desativar();
        repository.save(coordenador);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public CoordenadorResponse buscarPorId(UUID id) {
        return repository.buscarPorIdComDetalhes(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Coordenador não encontrado"));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<CoordenadorResponse> listar(CoordenadorFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(CoordenadorSpecification.filterBy(filter), pageable)
                        .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public List<CoordenadorMinResponse> listarAtivosMin() {
        return mapper.toMinResponseList(repository.listarAtivos());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeCoordenador(UUID id) {
        return id != null && repository.findById(id).filter(Coordenador::isAtivo).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CoordenadorResponse> buscarPorIdResponse(UUID id) {
        return id == null ? Optional.empty() : repository.buscarPorIdComDetalhes(id).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> buscarIdPorNomeColaborador(String nome) {
        if (nome == null || nome.isBlank()) {
            return Optional.empty();
        }
        String nomeHash = HashUtils.gerarBlindIndex(nome.trim().toUpperCase());
        return repository.findByColaboradorNomeCompletoHashAndAtivoTrue(nomeHash).map(Coordenador::getId);
    }
}
