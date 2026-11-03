package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.application.dto.EquipeFilter;
import com.nexo.app.modules.rh.application.dto.EquipeMembroRequest;
import com.nexo.app.modules.rh.application.dto.EquipeRequest;
import com.nexo.app.modules.rh.application.mappers.EquipeMapper;
import com.nexo.app.modules.rh.domain.*;
import com.nexo.app.modules.rh.infrastructure.persistence.*;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.events.EquipeDesativadaEvent;
import com.nexo.app.shared.domain.events.EquipeCriadaEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "equipes")
public class EquipeUseCases implements EquipeApi {

    private final EquipeRepository repository;
    private final TipoEquipeRepository tipoEquipeRepository;
    private final VeiculoRepository veiculoRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final CoordenadorRepository coordenadorRepository;
    private final SupervisorRepository supervisorRepository;
    private final BaseOperacionalRepository baseOperacionalRepository;
    private final ProcessoNegocioRepository processoRepository;
    private final EquipeMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final MembroEquipeUseCase membroEquipeUseCase;
    private final EquipeMembroRepository equipeMembroRepository;
    private final ContratoApi contratoApi;
    private final MetaFaturamentoUseCases metaFaturamentoUseCases;

    private EquipeResponse responder(Equipe equipe) {
        return metaFaturamentoUseCases.comMetaVigente(mapper.toResponse(equipe));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public EquipeResponse criar(EquipeRequest request) {
        if (repository.existsByCodigoIgnoreCase(request.codigo())) {
            throw new InvarianteVioladaException("Já existe uma equipe com este código: " + request.codigo());
        }

        if (request.membros() == null || request.membros().isEmpty()) {
            throw new InvarianteVioladaException("A equipe deve possuir pelo menos um colaborador alocado.");
        }

        TipoEquipe tipoEquipe = tipoEquipeRepository.findById(request.idTipoEquipe())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de Equipe não encontrado"));

        BaseOperacional baseOperacional = baseOperacionalRepository.findById(request.idBaseOperacional())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base Operacional não encontrada"));

        Veiculo veiculo = veiculoRepository.findById(request.idVeiculo())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado"));

        Coordenador coordenador = coordenadorRepository.buscarPorIdComDetalhes(request.idCoordenador())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Coordenador não encontrado"));

        Supervisor supervisor = supervisorRepository.buscarPorIdComDetalhes(request.idSupervisor())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Supervisor não encontrado"));

        ProcessoNegocio processo = processoRepository.findById(request.idProcesso())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado"));

        Equipe equipe = new Equipe(
                request.codigo(),
                tipoEquipe,
                baseOperacional,
                veiculo,
                coordenador,
                supervisor,
                processo
        );

        // Aloca os membros iniciais ANTES de salvar (Garante atomicidade e validação de membros)
        for (var membroReq : request.membros()) {
            Colaborador colaborador = colaboradorRepository.findById(membroReq.idColaborador())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado: " + membroReq.idColaborador()));

            membroEquipeUseCase.processarAlocacaoUnica(equipe, colaborador, membroReq.funcao());
        }

        aplicarVinculoContrato(equipe, request.idContrato());
        equipe.ajustarJornada(request.jornadaMinutos(), request.plataformaMinutos());
        Equipe equipeSalva = repository.save(equipe);
        eventPublisher.publishEvent(new EquipeCriadaEvent(
                equipeSalva.getId(),
                equipeSalva.getCodigo(),
                equipeSalva.getBaseOperacional().getId()));
        return responder(equipeSalva);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(allEntries = true),
            @CacheEvict(cacheNames = "membros-equipes", allEntries = true)
    })
    public EquipeResponse atualizar(UUID id, EquipeRequest request) {
        Equipe equipe = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe não encontrada"));

        repository.findByCodigoIgnoreCase(request.codigo())
                .ifPresent(existente -> {
                    if (!existente.getId().equals(id)) {
                        throw new InvarianteVioladaException("Já existe outra equipe com este código: " + request.codigo());
                    }
                });

        TipoEquipe tipoEquipe = tipoEquipeRepository.findById(request.idTipoEquipe())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tipo de Equipe não encontrado"));

        BaseOperacional baseOperacional = baseOperacionalRepository.findById(request.idBaseOperacional())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Base Operacional não encontrada"));

        Veiculo veiculo = veiculoRepository.findById(request.idVeiculo())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo não encontrado"));

        Coordenador coordenador = coordenadorRepository.buscarPorIdComDetalhes(request.idCoordenador())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Coordenador não encontrado"));

        Supervisor supervisor = supervisorRepository.buscarPorIdComDetalhes(request.idSupervisor())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Supervisor não encontrado"));

        ProcessoNegocio processo = processoRepository.findById(request.idProcesso())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado"));

        equipe.atualizarCodigo(request.codigo());
        equipe.atualizarTipoEquipe(tipoEquipe);
        equipe.atualizarBaseOperacional(baseOperacional);
        equipe.vincularVeiculo(veiculo);
        equipe.vincularCoordenador(coordenador);
        equipe.vincularSupervisor(supervisor);
        equipe.vincularProcesso(processo);

        aplicarVinculoContrato(equipe, request.idContrato());
        equipe.ajustarJornada(request.jornadaMinutos(), request.plataformaMinutos());

        sincronizarMembros(equipe, request.membros());

        return responder(repository.save(equipe));
    }

    /**
     * Converge os membros ativos da equipe para a lista enviada na edição: encerra quem saiu,
     * aloca quem entrou (como parceiro) e, por fim, transfere a responsabilidade quando o
     * responsável indicado for diferente do atual. Lista nula mantém a composição atual.
     */
    private void sincronizarMembros(Equipe equipe, List<EquipeMembroRequest> membros) {
        if (membros == null) {
            return;
        }
        if (membros.isEmpty()) {
            throw new InvarianteVioladaException("A equipe deve possuir pelo menos um colaborador alocado.");
        }

        Map<UUID, EquipeMembro.FuncaoMembro> desejados = new LinkedHashMap<>();
        for (var membroReq : membros) {
            if (desejados.put(membroReq.idColaborador(), membroReq.funcao()) != null) {
                throw new InvarianteVioladaException("Colaborador informado mais de uma vez na equipe.");
            }
        }

        List<UUID> responsaveis = desejados.entrySet().stream()
                .filter(e -> e.getValue() == EquipeMembro.FuncaoMembro.RESPONSAVEL)
                .map(Map.Entry::getKey)
                .toList();
        if (responsaveis.size() != 1) {
            throw new InvarianteVioladaException("A equipe deve possuir exatamente um responsável.");
        }

        List<EquipeMembro> ativos = equipe.getMembros().stream()
                .filter(EquipeMembro::isVinculoAtivo)
                .toList();

        ativos.stream()
                .map(EquipeMembro::getColaborador)
                .filter(c -> !desejados.containsKey(c.getId()))
                .toList()
                .forEach(equipe::encerrarVinculoMembro);

        Set<UUID> idsAtivos = ativos.stream()
                .map(m -> m.getColaborador().getId())
                .collect(Collectors.toSet());
        for (UUID idColaborador : desejados.keySet()) {
            if (idsAtivos.contains(idColaborador)) {
                continue;
            }
            Colaborador colaborador = buscarColaborador(idColaborador);
            membroEquipeUseCase.processarAlocacaoUnica(equipe, colaborador, EquipeMembro.FuncaoMembro.PARCEIRO);
        }

        equipe.transferirResponsabilidade(buscarColaborador(responsaveis.get(0)));
    }

    private Colaborador buscarColaborador(UUID idColaborador) {
        return colaboradorRepository.findById(idColaborador)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado: " + idColaborador));
    }

    private void aplicarVinculoContrato(Equipe equipe, UUID idContrato) {
        if (idContrato == null) {
            equipe.desvincularContrato();
            return;
        }
        if (!contratoApi.existeContratoAtivoNaData(idContrato, LocalDate.now())) {
            throw new InvarianteVioladaException("Contrato inválido ou inativo na data atual: " + idContrato);
        }
        equipe.vincularContrato(idContrato);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        Equipe entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe não encontrada"));

        entity.ativar();
        repository.save(entity);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        Equipe equipe = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe não encontrada"));

        equipe.desativar();
        repository.save(equipe);

        // Notifica o IAM para desativar o usuário da equipe, se houver
        if (equipe.getIdUsuario() != null) {
            eventPublisher.publishEvent(new EquipeDesativadaEvent(
                    equipe.getId(),
                    equipe.getIdUsuario()
            ));
        }
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void transferirResponsabilidade(UUID idEquipe, UUID idNovoResponsavel) {
        Equipe equipe = repository.findById(idEquipe)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe não encontrada"));

        Colaborador novoResponsavel = colaboradorRepository.findById(idNovoResponsavel)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado"));

        equipe.transferirResponsabilidade(novoResponsavel);
        repository.save(equipe);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<EquipeResponse> listar(EquipeFilter filter, Pageable pageable) {
        var pagina = repository.findAll(EquipeSpecification.filterBy(filter), pageable).map(mapper::toResponse);
        List<EquipeResponse> comMeta = metaFaturamentoUseCases.comMetaVigente(pagina.getContent());
        return PageResponse.fromPage(new org.springframework.data.domain.PageImpl<>(
                comMeta, pagina.getPageable(), pagina.getTotalElements()));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public EquipeResponse buscarPorId(UUID id) {
        Equipe equipe = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Equipe não encontrada"));
        return responder(equipe);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EquipeResponse> buscarPorIdResponse(UUID id) {
        return repository.findById(id).map(this::responder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EquipeResponse> buscarPorIdsResponse(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return metaFaturamentoUseCases.comMetaVigente(mapper.toResponseList(repository.findAllById(ids)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EquipeApi.EquipePublica> buscarPublicaPorId(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id).map(equipe -> new EquipeApi.EquipePublica(
                equipe.getId(),
                equipe.getCodigo(),
                equipe.isAtivo(),
                equipe.getBaseOperacional() != null ? equipe.getBaseOperacional().getId() : null,
                equipe.getProcesso() != null ? equipe.getProcesso().getId() : null,
                equipe.getIdUsuario(),
                equipe.getIdContrato()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existePorCodigo(String codigo) {
        return repository.existsByCodigoIgnoreCase(codigo);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> buscarIdPorCodigo(String codigo) {
        return repository.findByCodigoIgnoreCase(codigo)
                .map(Equipe::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<UUID> buscarIdsPorFiltros(UUID idBaseOperacional, UUID idProcesso) {
        return repository.findIdsByFiltros(idBaseOperacional, idProcesso);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> buscarContratoDaEquipe(UUID idEquipe) {
        return repository.findById(idEquipe)
                .map(Equipe::getIdContrato)
                .filter(java.util.Objects::nonNull);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> buscarIdEquipePorUsuario(UUID idUsuario) {
        return repository.findEquipePorUsuario(idUsuario).map(Equipe::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<EquipeResponse> listarEquipesAtivas() {
        return metaFaturamentoUseCases.comMetaVigente(repository.findAllByAtivoTrue().stream()
                .map(mapper::toResponse)
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<UUID> buscarIdsPorBases(java.util.List<UUID> idBases) {
        if (idBases == null || idBases.isEmpty()) {
            return java.util.List.of();
        }
        return repository.findIdsByIdBaseOperacionalIn(idBases);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMembroAtivo(UUID idEquipe, UUID idColaborador) {
        if (idEquipe == null || idColaborador == null) {
            return false;
        }
        return equipeMembroRepository.isMembroAtivo(idEquipe, idColaborador);
    }
}
