package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.*;
import com.nexo.app.modules.obras.application.mappers.ObraMapper;
import com.nexo.app.modules.obras.domain.AgenteResponsavel;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.domain.TipoVinculoProjeto;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraSpecification;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.CentroFaturamentoApi;
import com.nexo.app.modules.rh.CoordenadorApi;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.SupervisorApi;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = CacheNames.OBRAS)
public class ObraUseCases {

    private final ObraRepository repository;
    private final StatusObraUseCases statusUseCases;
    private final AgenteResponsavelUseCases agenteUseCases;
    private final NormalizadorObraService normalizador;
    private final ProcessoNegocioApi processoNegocioApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final MunicipioApi municipioApi;
    private final CentroFaturamentoApi centroFaturamentoApi;
    private final SupervisorApi supervisorApi;
    private final CoordenadorApi coordenadorApi;
    private final ObraMapper mapper;
    private final RelacionamentoProjetoUseCases relacionamentoProjetoUseCases;

    @Transactional
    @CacheEvict(allEntries = true)
    public ObraResponse criar(ObraRequest request) {
        String numeroNormalizado = normalizador.normalizarNumero(request.numero());
        String tituloNormalizado = normalizador.normalizarTitulo(request.titulo());

        if (repository.existsByNumeroIgnoreCaseAndAtivoTrue(numeroNormalizado)) {
            throw new InvarianteVioladaException("Já existe uma obra ativa cadastrada com o número '" + numeroNormalizado + "'.");
        }

        if (!supervisorApi.existeSupervisor(request.idSupervisor())) {
            throw new InvarianteVioladaException("Supervisor informado não foi encontrado ou não está ativo no cadastro de RH.");
        }

        if (!coordenadorApi.existeCoordenador(request.idCoordenador())) {
            throw new InvarianteVioladaException("Coordenador informado não foi encontrado ou não está ativo no cadastro de RH.");
        }

        StatusObra status = statusUseCases.buscarEntidade(request.idStatusObra());
        AgenteResponsavel agente = request.idAgenteResponsavel() != null
                ? agenteUseCases.buscarEntidade(request.idAgenteResponsavel())
                : null;

        Obra obra = new Obra(
                numeroNormalizado,
                tituloNormalizado,
                request.descricao(),
                request.notaObra(),
                request.valorProjeto(),
                request.dataInicio(),
                request.dataPrevisaoFim(),
                request.latitude(),
                request.longitude(),
                request.idProcesso(),
                request.idBaseOperacional(),
                request.idMunicipio(),
                request.idCentroCusto(),
                request.idSupervisor(),
                request.idCoordenador(),
                status,
                agente,
                request.equipes(),
                com.nexo.app.modules.obras.domain.TipoObra.ouPadrao(request.tipoObra())
        );

        if (request.dataFimEfetiva() != null || status.isConcluida()) {
            obra.alterarStatus(status, request.dataFimEfetiva());
        }

        if (request.idObraPai() != null) {
            Obra pai = buscarEntidade(request.idObraPai());
            obra.vincularObraPai(pai, request.tipoVinculoPai());
        }

        Obra salva = repository.save(obra);
        if (request.idObraPai() != null) {
            relacionamentoProjetoUseCases.garantirVinculoLegado(
                    salva.getId(),
                    new VinculoProjetoRequest(request.idObraPai(), converterTipoVinculo(request.tipoVinculoPai()))
            );
        }
        return montarObraResponse(salva);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public ObraResponse atualizar(UUID id, ObraRequest request) {
        Obra obra = buscarEntidade(id);
        UUID idObraPaiAnterior = obra.getObraPai() != null ? obra.getObraPai().getId() : null;

        String numeroNormalizado = normalizador.normalizarNumero(request.numero());
        String tituloNormalizado = normalizador.normalizarTitulo(request.titulo());

        if (repository.existsByNumeroIgnoreCaseAndIdNotAndAtivoTrue(numeroNormalizado, id)) {
            throw new InvarianteVioladaException("Já existe outra obra ativa cadastrada com o número '" + numeroNormalizado + "'.");
        }

        if (!supervisorApi.existeSupervisor(request.idSupervisor())) {
            throw new InvarianteVioladaException("Supervisor informado não foi encontrado ou não está ativo no cadastro de RH.");
        }

        if (!coordenadorApi.existeCoordenador(request.idCoordenador())) {
            throw new InvarianteVioladaException("Coordenador informado não foi encontrado ou não está ativo no cadastro de RH.");
        }

        StatusObra status = statusUseCases.buscarEntidade(request.idStatusObra());
        AgenteResponsavel agente = request.idAgenteResponsavel() != null
                ? agenteUseCases.buscarEntidade(request.idAgenteResponsavel())
                : null;

        obra.atualizarDados(
                numeroNormalizado,
                tituloNormalizado,
                request.descricao(),
                request.notaObra(),
                request.valorProjeto(),
                request.dataInicio(),
                request.dataPrevisaoFim(),
                request.latitude(),
                request.longitude(),
                request.idProcesso(),
                request.idBaseOperacional(),
                request.idMunicipio(),
                request.idCentroCusto(),
                request.idSupervisor(),
                request.idCoordenador(),
                status,
                agente,
                request.equipes(),
                request.tipoObra() != null ? request.tipoObra() : obra.getTipoObra()
        );

        if (request.dataFimEfetiva() != null || status.isConcluida()) {
            obra.alterarStatus(status, request.dataFimEfetiva());
        }

        if (request.idObraPai() != null) {
            Obra pai = buscarEntidade(request.idObraPai());
            obra.vincularObraPai(pai, request.tipoVinculoPai());
        } else {
            obra.desvincularObraPai();
        }

        Obra salva = repository.save(obra);
        if (idObraPaiAnterior != null && !idObraPaiAnterior.equals(request.idObraPai())) {
            relacionamentoProjetoUseCases.desvincular(id, idObraPaiAnterior);
        }
        if (request.idObraPai() != null) {
            relacionamentoProjetoUseCases.garantirVinculoLegado(
                    id,
                    new VinculoProjetoRequest(request.idObraPai(), converterTipoVinculo(request.tipoVinculoPai()))
            );
        }
        return montarObraResponse(salva);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public ObraResponse alterarStatus(UUID id, UUID idNovoStatus, LocalDate dataFimEfetiva) {
        Obra obra = buscarEntidade(id);
        StatusObra status = statusUseCases.buscarEntidade(idNovoStatus);
        obra.alterarStatus(status, dataFimEfetiva);
        return montarObraResponse(repository.save(obra));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    @Deprecated(forRemoval = true)
    public ObraResponse vincularSubprojeto(UUID idObraPai, SubprojetoVinculoRequest request) {
        Obra pai = buscarEntidade(idObraPai);
        Obra filha = buscarEntidade(request.idObraFilha());
        relacionamentoProjetoUseCases.garantirVinculoLegado(
                idObraPai,
                new VinculoProjetoRequest(request.idObraFilha(), converterTipoVinculo(request.tipoVinculo()))
        );
        filha.vincularObraPai(pai, request.tipoVinculo());
        repository.save(filha);
        return montarObraResponse(buscarEntidade(idObraPai));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    @Deprecated(forRemoval = true)
    public ObraResponse desvincularSubprojeto(UUID idObraPai, UUID idFilho) {
        Obra filha = buscarEntidade(idFilho);
        relacionamentoProjetoUseCases.desvincular(idObraPai, idFilho);
        if (filha.getObraPai() != null && idObraPai.equals(filha.getObraPai().getId())) {
            filha.desvincularObraPai();
            repository.save(filha);
        }
        return montarObraResponse(buscarEntidade(idObraPai));
    }

    private TipoVinculoProjeto converterTipoVinculo(
            com.nexo.app.modules.obras.domain.TipoVinculoSubprojeto tipoLegado) {
        return tipoLegado == null ? TipoVinculoProjeto.KIT : TipoVinculoProjeto.valueOf(tipoLegado.name());
    }

    @Transactional(readOnly = true)
    public ObraResponse buscarPorId(UUID id) {
        Obra obra = repository.findByIdWithDetails(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra não encontrada com o ID: " + id));
        return montarObraResponse(obra);
    }

    @Transactional(readOnly = true)
    public PageResponse<ObraResumoResponse> listar(ObraFilter filter, Pageable pageable) {
        Sort sort = pageable.getSort();
        Pageable paginationWithoutSort = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.unsorted())
                : Pageable.unpaged(Sort.unsorted());
        Page<Obra> page = repository.findAll(ObraSpecification.filterBy(filter, sort), paginationWithoutSort);
        return PageResponse.fromPage(page.map(this::montarObraResumoResponse));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        Obra obra = buscarEntidade(id);
        obra.desativar();
        repository.save(obra);
    }

    public Obra buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Obra não encontrada com o ID: " + id));
    }

    private ObraResponse montarObraResponse(Obra obra) {
        String processoNome = resolverProcessoNome(obra.getIdProcesso());
        String baseNome = resolverBaseNome(obra.getIdBaseOperacional());
        String municipioNome = resolverMunicipioNome(obra.getIdMunicipio());
        String centroCustoNome = resolverCentroCustoNome(obra.getIdCentroCusto());
        String supervisorNome = resolverSupervisorNome(obra.getIdSupervisor());
        String coordenadorNome = resolverCoordenadorNome(obra.getIdCoordenador());
        var projetosRelacionados = relacionamentoProjetoUseCases.listarSemValidarExistencia(obra.getId());
        return mapper.toResponse(obra, processoNome, baseNome, municipioNome, centroCustoNome,
                supervisorNome, coordenadorNome, projetosRelacionados);
    }

    private ObraResumoResponse montarObraResumoResponse(Obra obra) {
        String processoNome = resolverProcessoNome(obra.getIdProcesso());
        String baseNome = resolverBaseNome(obra.getIdBaseOperacional());
        String municipioNome = resolverMunicipioNome(obra.getIdMunicipio());
        String centroCustoNome = resolverCentroCustoNome(obra.getIdCentroCusto());
        String supervisorNome = resolverSupervisorNome(obra.getIdSupervisor());
        String coordenadorNome = resolverCoordenadorNome(obra.getIdCoordenador());
        int totalProjetosRelacionados = Math.toIntExact(relacionamentoProjetoUseCases.contarAtivos(obra.getId()));
        return mapper.toResumo(obra, processoNome, baseNome, municipioNome, centroCustoNome,
                supervisorNome, coordenadorNome, totalProjetosRelacionados);
    }

    private String resolverProcessoNome(UUID idProcesso) {
        if (idProcesso == null) return null;
        return processoNegocioApi.buscarPorIdResponse(idProcesso)
                .map(p -> p.nome())
                .orElse(null);
    }

    private String resolverBaseNome(UUID idBase) {
        if (idBase == null) return null;
        return baseOperacionalApi.buscarPorIdResponse(idBase)
                .map(b -> b.nome())
                .orElse(null);
    }

    private String resolverMunicipioNome(UUID idMunicipio) {
        if (idMunicipio == null) return null;
        return municipioApi.buscarPorIdResponse(idMunicipio)
                .map(m -> m.nome())
                .orElse(null);
    }

    private String resolverCentroCustoNome(UUID idCentro) {
        if (idCentro == null) return null;
        return centroFaturamentoApi.buscarPorIdResponse(idCentro)
                .map(c -> c.nome())
                .orElse(null);
    }

    private String resolverSupervisorNome(UUID idSupervisor) {
        if (idSupervisor == null) return null;
        return supervisorApi.buscarPorIdResponse(idSupervisor)
                .map(s -> s.colaborador() != null ? s.colaborador().nomeCompleto() : null)
                .orElse(null);
    }

    private String resolverCoordenadorNome(UUID idCoordenador) {
        if (idCoordenador == null) return null;
        return coordenadorApi.buscarPorIdResponse(idCoordenador)
                .map(c -> c.colaborador() != null ? c.colaborador().nomeCompleto() : null)
                .orElse(null);
    }
}
