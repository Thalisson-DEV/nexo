package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ContextoOrganizacional;
import com.nexo.app.modules.sst.application.dto.AtivacaoCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstDetalheResponse;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstFilter;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstRequest;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstResponse;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstUpdateRequest;
import com.nexo.app.modules.sst.application.dto.EncerramentoCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.InclusaoMembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.MembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.MembroCorpoSstResponse;
import com.nexo.app.modules.sst.application.dto.SaidaMembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.SubstituicaoMembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.mappers.BaseAbrangidaCorpoSstMapper;
import com.nexo.app.modules.sst.application.mappers.CorpoGovernancaSstMapper;
import com.nexo.app.modules.sst.application.mappers.MembroCorpoSstMapper;
import com.nexo.app.modules.sst.domain.ConflitoGovernancaSstException;
import com.nexo.app.modules.sst.domain.CorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.MembroCorpoSst;
import com.nexo.app.modules.sst.domain.OrigemMembroCipa;
import com.nexo.app.modules.sst.domain.PerfilNormativoGovernancaSst;
import com.nexo.app.modules.sst.domain.SnapshotMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.modules.sst.infrastructure.persistence.CorpoGovernancaSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.CorpoGovernancaSstSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.MembroCorpoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.PerfilNormativoGovernancaSstRepository;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.UnidadeFederativa;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.nexo.app.shared.infrastructure.config.CacheNames.SST_GOVERNANCA_CORPOS;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = SST_GOVERNANCA_CORPOS)
public class CorpoGovernancaSstUseCases {

    private final CorpoGovernancaSstRepository repository;
    private final MembroCorpoSstRepository membroRepository;
    private final PerfilNormativoGovernancaSstRepository perfilRepository;
    private final BaseOperacionalApi baseOperacionalApi;
    private final ColaboradorApi colaboradorApi;
    private final CorpoGovernancaSstMapper mapper;
    private final MembroCorpoSstMapper membroMapper;
    private final BaseAbrangidaCorpoSstMapper baseMapper;

    @Transactional
    @CacheEvict(allEntries = true)
    public CorpoGovernancaSstDetalheResponse criar(CorpoGovernancaSstRequest request) {
        Map<UUID, BaseOperacionalResponse> bases = resolverBases(
                request.idBaseOperacional(), request.idsBasesAbrangidas());
        BaseOperacionalResponse principal = bases.get(request.idBaseOperacional());
        String uf = validarUfDaBase(principal, request.uf());
        validarPerfil(request.idPerfilNormativo(), request.tipo(), request.inicioPeriodo(), uf);

        CorpoGovernancaSst corpo = new CorpoGovernancaSst(
                request.tipo(), request.regimeComposicao(), request.nome(), principal.id(),
                principal.nome(), request.unidadeGovernanca(), uf, request.inicioPeriodo(),
                request.fimPeriodo(), request.grauRisco(), request.idPerfilNormativo(),
                Boolean.TRUE.equals(request.hipoteseRepresentanteNomeadoConfirmada()));
        bases.values().stream().filter(base -> !base.id().equals(principal.id()))
                .forEach(base -> corpo.adicionarBaseAbrangida(base.id(), base.nome()));
        return detalhar(repository.save(corpo));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public CorpoGovernancaSstDetalheResponse atualizar(
            UUID id, CorpoGovernancaSstUpdateRequest request) {
        CorpoGovernancaSst corpo = buscarEntidade(id);
        exigirVersao(corpo, request.versao());
        BaseOperacionalResponse principal = resolverBaseAtiva(corpo.getIdBaseOperacional());
        String uf = validarUfDaBase(principal, request.uf());
        validarPerfil(request.idPerfilNormativo(), corpo.getTipo(), request.inicioPeriodo(), uf);
        corpo.atualizarPreparacao(request.nome(), request.unidadeGovernanca(), uf,
                request.inicioPeriodo(), request.fimPeriodo(), request.grauRisco(),
                request.idPerfilNormativo(),
                Boolean.TRUE.equals(request.hipoteseRepresentanteNomeadoConfirmada()));
        return detalhar(repository.save(corpo));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public MembroCorpoSstResponse adicionarMembro(UUID idCorpo, InclusaoMembroCorpoSstRequest request) {
        CorpoGovernancaSst corpo = buscarEntidade(idCorpo);
        exigirVersao(corpo, request.versao());
        SnapshotMembroGovernancaSst snapshot = resolverSnapshot(request.membro());
        MembroCorpoSst membro = adicionar(corpo, snapshot, request.membro());
        repository.save(corpo);
        return membroMapper.toResponse(membro);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public MembroCorpoSstResponse registrarSaida(
            UUID idCorpo, UUID idMembro, SaidaMembroCorpoSstRequest request) {
        CorpoGovernancaSst corpo = buscarEntidade(idCorpo);
        exigirVersao(corpo, request.versao());
        MembroCorpoSst membro = buscarMembroDoCorpo(idCorpo, idMembro);
        corpo.registrarSaida(membro, request.dataEfeito(), request.motivo(), ator());
        repository.save(corpo);
        return membroMapper.toResponse(membro);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public MembroCorpoSstResponse substituir(
            UUID idCorpo, UUID idMembro, SubstituicaoMembroCorpoSstRequest request) {
        CorpoGovernancaSst corpo = buscarEntidade(idCorpo);
        exigirVersao(corpo, request.versao());
        MembroCorpoSst sucedido = buscarMembroDoCorpo(idCorpo, idMembro);
        MembroCorpoSstRequest dados = request.sucessor();
        SnapshotMembroGovernancaSst snapshot = resolverSnapshot(dados);
        MembroCorpoSst novo = corpo.substituir(sucedido, snapshot, dados.papel(),
                dados.representacao(), dados.titularidade(), dados.origem(),
                dados.cicloEleitoral(), dados.classificacaoEleitoral(), dados.atoDesignacao(),
                dados.reeleito(), request.dataEfeito(), request.motivoSaida(),
                request.fundamentoProcedimental(), ator());
        verificarReeleicao(novo, dados);
        repository.save(corpo);
        return membroMapper.toResponse(novo);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public CorpoGovernancaSstDetalheResponse ativar(UUID id, AtivacaoCorpoSstRequest request) {
        CorpoGovernancaSst corpo = buscarEntidade(id);
        exigirVersao(corpo, request.versao());
        if (repository.existsByTipoAndIdBaseOperacionalAndUnidadeGovernancaNormalizadaAndStatusAndAtivoTrue(
                corpo.getTipo(), corpo.getIdBaseOperacional(), corpo.getUnidadeGovernancaNormalizada(),
                StatusCorpoGovernancaSst.ATIVO)) {
            throw conflitoAtivacao();
        }
        PerfilNormativoGovernancaSst perfil = perfil(corpo.getIdPerfilNormativo());
        corpo.ativar(perfil, ator());
        try {
            return detalhar(repository.saveAndFlush(corpo));
        } catch (DataIntegrityViolationException exception) {
            throw conflitoAtivacao();
        }
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public CorpoGovernancaSstDetalheResponse encerrar(
            UUID id, EncerramentoCorpoSstRequest request) {
        CorpoGovernancaSst corpo = buscarEntidade(id);
        exigirVersao(corpo, request.versao());
        corpo.encerrar(request.dataEfeito(), request.motivo(), request.justificativa(),
                request.referenciaEvidenciaAdministrativa(), ator());
        return detalhar(repository.save(corpo));
    }

    @Transactional(readOnly = true)
    public CorpoGovernancaSstDetalheResponse buscarPorId(UUID id) {
        return detalhar(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{'v2', #filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<CorpoGovernancaSstResponse> listar(
            CorpoGovernancaSstFilter filter, Pageable pageable) {
        return PageResponse.fromPage(repository.findAll(
                CorpoGovernancaSstSpecification.filterBy(filter), pageable).map(mapper::toResponse));
    }

    /** Consulta pública dentro do módulo SST para reuniões, sem expor a coleção JPA do corpo. */
    @Transactional(readOnly = true)
    public List<MembroCorpoSstResponse> listarMembrosAtivosEm(
            UUID idCorpo, java.time.LocalDate dataReferencia) {
        buscarEntidade(idCorpo);
        return membroRepository.buscarAtivosNoCorpoEm(idCorpo, dataReferencia).stream()
                .map(membroMapper::toResponse).toList();
    }

    private MembroCorpoSst adicionar(
            CorpoGovernancaSst corpo,
            SnapshotMembroGovernancaSst snapshot,
            MembroCorpoSstRequest request) {
        MembroCorpoSst membro = corpo.adicionarMembro(snapshot, request.papel(),
                request.representacao(), request.titularidade(), request.origem(),
                request.cicloEleitoral(), request.classificacaoEleitoral(), request.atoDesignacao(),
                request.reeleito(), request.inicioParticipacao());
        verificarReeleicao(membro, request);
        return membro;
    }

    private void verificarReeleicao(MembroCorpoSst membro, MembroCorpoSstRequest request) {
        if (!request.reeleito() || request.origem() != OrigemMembroCipa.ELEITO) {
            return;
        }
        List<MembroCorpoSst> anterior = membroRepository.buscarHistoricoEleitoralAnterior(
                request.idColaborador(), TipoCorpoGovernancaSst.CIPA, OrigemMembroCipa.ELEITO,
                request.inicioParticipacao(), PageRequest.of(0, 1));
        if (!anterior.isEmpty() && anterior.getFirst().isReeleito()) {
            membro.registrarDivergenciaNormativa("REELEICAO_CONSECUTIVA_EXCEDIDA");
        }
    }

    private SnapshotMembroGovernancaSst resolverSnapshot(MembroCorpoSstRequest request) {
        ContextoOrganizacional contexto = colaboradorApi
                .buscarContextoOrganizacional(Set.of(request.idColaborador()))
                .get(request.idColaborador());
        if (contexto == null || !contexto.ativo()) {
            throw new InvarianteVioladaException("O colaborador informado não existe ou está inativo.");
        }
        return new SnapshotMembroGovernancaSst(contexto.id(), contexto.nomeCompleto(),
                contexto.idFuncao(), contexto.funcao(), contexto.idBaseOperacional(),
                contexto.baseOperacional(), request.turnoDisponivel());
    }

    private Map<UUID, BaseOperacionalResponse> resolverBases(
            UUID principal, Collection<UUID> abrangidas) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(principal);
        if (abrangidas != null) {
            abrangidas.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        Map<UUID, BaseOperacionalResponse> bases = baseOperacionalApi.buscarPorIdsResponse(ids);
        List<UUID> invalidas = ids.stream().filter(id -> {
            BaseOperacionalResponse base = bases.get(id);
            return base == null || !base.ativo();
        }).toList();
        if (!invalidas.isEmpty()) {
            throw new InvarianteVioladaException(
                    "Uma ou mais bases informadas não existem ou estão inativas.");
        }
        return bases;
    }

    private BaseOperacionalResponse resolverBaseAtiva(UUID id) {
        BaseOperacionalResponse base = baseOperacionalApi.buscarPorIdResponse(id).orElseThrow(
                () -> new InvarianteVioladaException(
                        "A base operacional do corpo não existe ou está indisponível."));
        if (!base.ativo()) {
            throw new InvarianteVioladaException("A base operacional do corpo está inativa.");
        }
        return base;
    }

    private static String validarUfDaBase(BaseOperacionalResponse base, String ufInformada) {
        String uf = UnidadeFederativa.normalizar(ufInformada);
        if (base.uf() != null && !base.uf().isBlank()
                && !UnidadeFederativa.normalizar(base.uf()).equals(uf)) {
            throw new InvarianteVioladaException(
                    "A UF informada diverge da UF cadastrada para a base operacional.");
        }
        return uf;
    }

    private void validarPerfil(UUID id, TipoCorpoGovernancaSst tipo, java.time.LocalDate inicio, String uf) {
        if (id == null) {
            return;
        }
        PerfilNormativoGovernancaSst perfil = perfil(id);
        if (perfil.getTipoCorpo() != tipo) {
            throw new InvarianteVioladaException("O perfil normativo não se aplica ao tipo do corpo.");
        }
        if (perfil.isConfirmado() && !perfil.aplicaA(tipo, uf, inicio)) {
            throw new InvarianteVioladaException(
                    "O perfil confirmado não abrange a jurisdição ou o início do período informado.");
        }
    }

    private PerfilNormativoGovernancaSst perfil(UUID id) {
        if (id == null) {
            return null;
        }
        return perfilRepository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil normativo não encontrado."));
    }

    private CorpoGovernancaSst buscarEntidade(UUID id) {
        return repository.findByIdAndAtivoTrue(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Corpo de governança não encontrado."));
    }

    private MembroCorpoSst buscarMembroDoCorpo(UUID idCorpo, UUID idMembro) {
        return membroRepository.findByIdAndCorpoIdAndAtivoTrue(idMembro, idCorpo)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Corpo ou integrante não encontrado."));
    }

    private CorpoGovernancaSstDetalheResponse detalhar(CorpoGovernancaSst corpo) {
        return new CorpoGovernancaSstDetalheResponse(
                mapper.toResponse(corpo),
                corpo.getBasesAbrangidas().stream().map(baseMapper::toResponse).toList(),
                corpo.getMembros().stream().map(membroMapper::toResponse).toList());
    }

    private static UUID ator() {
        UUID ator = SecurityUtils.getUsuarioLogadoId();
        if (ator == null) {
            throw new InvarianteVioladaException("Não foi possível identificar o usuário autenticado.");
        }
        return ator;
    }

    private static void exigirVersao(CorpoGovernancaSst corpo, Long esperada) {
        if (!Objects.equals(corpo.getVersao(), esperada)) {
            throw new ConflitoGovernancaSstException(
                    "O corpo foi alterado por outro usuário. Recarregue os dados e tente novamente.");
        }
    }

    private static ConflitoGovernancaSstException conflitoAtivacao() {
        return new ConflitoGovernancaSstException(
                "Já existe um corpo ativo deste tipo para a unidade de governança informada.");
    }
}
