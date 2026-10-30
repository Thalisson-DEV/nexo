package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.SupervisorApi;
import com.nexo.app.modules.rh.SupervisorResponse;
import com.nexo.app.modules.rh.application.dto.SupervisorFilter;
import com.nexo.app.modules.rh.application.dto.SupervisorMinResponse;
import com.nexo.app.modules.rh.application.dto.SupervisorRequest;
import com.nexo.app.modules.rh.application.mappers.SupervisorMapper;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Coordenador;
import com.nexo.app.modules.rh.domain.Supervisor;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.CoordenadorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.SupervisorRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.SupervisorSpecification;
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
@CacheConfig(cacheNames = CacheNames.SUPERVISORES)
public class SupervisorUseCases implements SupervisorApi {

    private final SupervisorRepository repository;
    private final ColaboradorRepository colaboradorRepository;
    private final CoordenadorRepository coordenadorRepository;
    private final SupervisorMapper mapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public SupervisorResponse criar(SupervisorRequest request) {
        Colaborador colaborador = colaboradorRepository.findById(request.idColaborador())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado"));

        if (!colaborador.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular um colaborador desativado.");
        }

        Coordenador coordenador = coordenadorRepository.findById(request.idCoordenador())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Coordenador não encontrado"));

        if (!coordenador.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular a um coordenador desativado.");
        }

        if (colaborador.getId().equals(coordenador.getColaborador().getId())) {
            throw new InvarianteVioladaException("Um colaborador não pode ser supervisor de si mesmo sob sua própria coordenação.");
        }

        if (repository.existsByColaboradorIdAndAtivoTrue(colaborador.getId())) {
            throw new InvarianteVioladaException("Este colaborador já possui um registro ativo de supervisor.");
        }

        Supervisor supervisor = new Supervisor(
                colaborador,
                coordenador,
                request.dataInicio(),
                request.dataFim()
        );

        return mapper.toResponse(repository.save(supervisor));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public SupervisorResponse atualizar(UUID id, SupervisorRequest request) {
        Supervisor supervisor = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Supervisor não encontrado"));

        supervisor.validarStatus();

        if (!supervisor.getColaborador().getId().equals(request.idColaborador())) {
            Colaborador novoColaborador = colaboradorRepository.findById(request.idColaborador())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Novo colaborador não encontrado"));

            if (repository.existsByColaboradorIdAndAtivoTrueAndIdNot(novoColaborador.getId(), id)) {
                throw new InvarianteVioladaException("O novo colaborador já possui outro registro ativo de supervisor.");
            }
            supervisor.atualizarColaborador(novoColaborador);
        }

        Coordenador coordenador = coordenadorRepository.findById(request.idCoordenador())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Coordenador não encontrado"));

        supervisor.atualizar(coordenador, request.dataInicio(), request.dataFim());

        return mapper.toResponse(repository.save(supervisor));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        Supervisor supervisor = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Supervisor não encontrado"));

        supervisor.ativar();
        repository.save(supervisor);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        Supervisor supervisor = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Supervisor não encontrado"));

        supervisor.desativar();
        repository.save(supervisor);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public SupervisorResponse buscarPorId(UUID id) {
        return repository.buscarPorIdComDetalhes(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Supervisor não encontrado"));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<SupervisorResponse> listar(SupervisorFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(SupervisorSpecification.filterBy(filter), pageable)
                        .map(mapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public List<SupervisorMinResponse> listarAtivosMin() {
        return mapper.toMinResponseList(repository.listarAtivos());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeSupervisor(UUID id) {
        return id != null && repository.findById(id).filter(Supervisor::isAtivo).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupervisorResponse> buscarPorIdResponse(UUID id) {
        return id == null ? Optional.empty() : repository.buscarPorIdComDetalhes(id).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> buscarIdPorNomeColaborador(String nome) {
        if (nome == null || nome.isBlank()) {
            return Optional.empty();
        }
        String nomeHash = HashUtils.gerarBlindIndex(nome.trim().toUpperCase());
        return repository.findByColaboradorNomeCompletoHashAndAtivoTrue(nomeHash).map(Supervisor::getId);
    }
}
