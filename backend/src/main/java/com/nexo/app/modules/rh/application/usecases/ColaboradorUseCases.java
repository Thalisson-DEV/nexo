package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorAtivoResumo;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.ContextoOrganizacional;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.application.mappers.ColaboradorMapper;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.Funcao;
import com.nexo.app.modules.rh.domain.ProcessoNegocio;
import com.nexo.app.modules.rh.infrastructure.persistence.*;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.events.AcessoColaboradorSolicitadoEvent;
import com.nexo.app.shared.domain.events.ColaboradorDesativadoEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.config.CacheNames;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orquestra o registro de um novo colaborador, garantindo as invariantes de negócio.
 */
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "colaboradores")
public class ColaboradorUseCases implements ColaboradorApi {

    private final ColaboradorRepository repository;
    private final ProcessoNegocioRepository processoRepository;
    private final BaseOperacionalRepository baseRepository;
    private final FuncaoRepository funcaoRepository;
    private final ColaboradorMapper mapper;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @CacheEvict(allEntries = true)
    public ColaboradorResponse criar(ColaboradorRequest request) {
        validarUnicidadeCpf(request.cpf());

        ProcessoNegocio processo = processoRepository.findById(request.idProcesso())
            .orElseThrow(() -> new RecursoNaoEncontradoException("Processo de Negócio não encontrado"));

        BaseOperacional base = baseRepository.findById(request.idBaseOperacional())
            .orElseThrow(() -> new RecursoNaoEncontradoException("Base Operacional não encontrada"));

        Funcao funcao = funcaoRepository.findById(request.idFuncao())
            .orElseThrow(() -> new RecursoNaoEncontradoException("Função do Colaborador não encontrada"));

        Colaborador colaborador = new Colaborador(
            request.primeiroNome(),
            request.ultimoNome(),
            request.nomeCompleto(),
            request.cpf(),
            processo,
            base,
            funcao,
            request.dataContratacao()
        );

        return mapper.toResponse(repository.save(colaborador));
    }

    /**
     * Nome, função e base do colaborador também aparecem — como contexto — na listagem
     * de usuários do IAM. Por isso o cache de usuários cai junto: sem esse segundo
     * evict, um colaborador renomeado continuaria sendo apresentado pelo nome antigo na
     * tela de acessos até o TTL expirar, e o operador não teria como saber por quê.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.COLABORADORES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.USUARIOS, allEntries = true)
    })
    public ColaboradorResponse atualizar(UUID id, ColaboradorUpdateRequest request) {
        Colaborador colaborador = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado"));

        colaborador.validarStatus();

        colaborador.atualizarDadosPessoais(request.primeiroNome(), request.ultimoNome(), request.nomeCompleto());

        if (request.idFuncao() != null) {
            Funcao funcao = funcaoRepository.findById(request.idFuncao())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Função não encontrada"));
            colaborador.atualizarFuncao(funcao);
        }

        if (request.idProcesso() != null) {
            ProcessoNegocio processo = processoRepository.findById(request.idProcesso())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Processo de Negócio não encontrado"));
            colaborador.atualizarProcesso(processo);
        }

        if (request.idBaseOperacional() != null) {
            BaseOperacional base = baseRepository.findById(request.idBaseOperacional())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Base Operacional não encontrada"));
            colaborador.atualizarBaseOperacional(base);
        }

        return mapper.toResponse(repository.save(colaborador));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public ColaboradorResponse gerarAcesso(VincularAcessoRequest request) {

        Colaborador colaborador = repository.findById(request.colaboradorId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado"));

        if (colaborador.getIdUsuario() != null) {
            throw new InvarianteVioladaException("Este colaborador já possui um acesso vinculado");
        }

        colaborador.validarStatus();

        eventPublisher.publishEvent(new AcessoColaboradorSolicitadoEvent(
                colaborador.getId(),
                request.usuario().login(),
                request.usuario().email(),
                request.usuario().telefone(),
                request.usuario().senha(),
                request.usuario().idPerfil(),
                request.usuario().tipo()
        ));

        return mapper.toResponse(colaborador);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        Colaborador colaborador = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado"));

        colaborador.desativar();
        repository.save(colaborador);

        if (colaborador.getIdUsuario() != null) {
            eventPublisher.publishEvent(new ColaboradorDesativadoEvent(
                    colaborador.getId(),
                    colaborador.getIdUsuario()
            ));
        }
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        Colaborador entity = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado"));

        entity.ativar();
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<ColaboradorResponse> listar(ColaboradorFilter filter, Pageable pageable) {
        return PageResponse.fromPage(
                repository.findAll(ColaboradorSpecification.filterBy(filter), pageable)
                    .map(mapper::toResponse)
        );
    }

    @Deprecated(since = "v1.1.0", forRemoval = true)
    @Transactional(readOnly = true)
    public List<ColaboradorMinResponse> listarCoordenadoresESupervisores() {
        return repository.findAll().stream()
                .filter(c -> c.isAtivo() &&
                        (c.getFuncao().getNome().equalsIgnoreCase("COORDENADOR") ||
                                c.getFuncao().getNome().equalsIgnoreCase("SUPERVISOR")))
                .map(c -> new ColaboradorMinResponse(c.getId(), c.getNomeCompleto()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public ColaboradorResponse buscarPorId(UUID id) {
        Colaborador colaborador = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado"));

        return mapper.toResponse(colaborador);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> buscarNomeCompletoPorUsuario(UUID idUsuario) {
        return repository.findByIdUsuario(idUsuario)
                .map(Colaborador::getNomeCompleto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ColaboradorResponse> buscarPorUsuarioResponse(UUID idUsuario) {
        return repository.findByIdUsuario(idUsuario).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeColaborador(UUID id) {
        return repository.existsByIdAndAtivoTrue(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ColaboradorResponse> buscarPorIdResponse(UUID id) {
        return repository.findById(id).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ColaboradorResponse> buscarPorNomeCompletoResponse(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) {
            return List.of();
        }
        String nomeHash = HashUtils.gerarBlindIndex(nomeCompleto.trim().toUpperCase());
        return repository.findAllByNomeCompletoHash(nomeHash).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ResumoColaborador> buscarResumosPorIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> distintos = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distintos.isEmpty()) {
            return Map.of();
        }
        return repository.buscarComVinculosPorIds(distintos).stream()
                .collect(Collectors.toMap(Colaborador::getId, ColaboradorUseCases::resumir));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> buscarIdsPorTrechoDoNome(String trecho) {
        String termo = SpecificationUtils.normalizarTermo(trecho);
        if (termo == null || termo.isBlank()) {
            return List.of();
        }
        return repository.buscarIdsPorNomeBusca("%" + termo + "%");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ContextoOrganizacional> buscarContextoOrganizacional(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<UUID> distintos = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distintos.isEmpty()) {
            return Map.of();
        }
        return repository.buscarComEstruturaPorIds(distintos).stream()
                .collect(Collectors.toMap(Colaborador::getId, ColaboradorUseCases::contextualizar));
    }

    private static ContextoOrganizacional contextualizar(Colaborador colaborador) {
        var base = colaborador.getBaseOperacional();
        var centro = base.getCentroFaturamento();
        return new ContextoOrganizacional(
                colaborador.getId(),
                colaborador.getNomeCompleto(),
                colaborador.getFuncao().getId(),
                colaborador.getFuncao().getNome(),
                base.getId(),
                base.getNome(),
                centro.getId(),
                centro.getNome(),
                colaborador.getProcesso().getId(),
                colaborador.getProcesso().getNome(),
                colaborador.isAtivo()
        );
    }

    private static ResumoColaborador resumir(Colaborador colaborador) {
        return new ResumoColaborador(
                colaborador.getId(),
                colaborador.getNomeCompleto(),
                colaborador.getFuncao().getNome(),
                colaborador.getBaseOperacional().getNome(),
                colaborador.isAtivo()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long contarAtivos(Collection<UUID> idsBaseOperacional, Collection<UUID> idsCentroCusto) {
        return repository.count(ColaboradorSpecification.ativosNoEscopo(idsBaseOperacional, idsCentroCusto));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ColaboradorResponse> listarAtivosPorFuncao(UUID idFuncao) {
        if (idFuncao == null) {
            return List.of();
        }
        return repository.findByAtivoTrueAndFuncaoId(idFuncao).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ColaboradorAtivoResumo> listarAtivosPorEscopo(
            Collection<UUID> idsBaseOperacional,
            Collection<UUID> idsFuncao) {
        Set<UUID> bases = idsDistintos(idsBaseOperacional);
        Set<UUID> funcoes = idsDistintos(idsFuncao);
        if (bases.isEmpty() && funcoes.isEmpty()) {
            return List.of();
        }

        List<Colaborador> colaboradores;
        if (!bases.isEmpty() && !funcoes.isEmpty()) {
            colaboradores = repository.buscarAtivosPorBasesEFuncoes(bases, funcoes);
        } else if (!bases.isEmpty()) {
            colaboradores = repository.buscarAtivosPorBases(bases);
        } else {
            colaboradores = repository.buscarAtivosPorFuncoes(funcoes);
        }
        return colaboradores.stream().map(ColaboradorUseCases::resumirAtivo).toList();
    }

    private static Set<UUID> idsDistintos(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static ColaboradorAtivoResumo resumirAtivo(Colaborador colaborador) {
        return new ColaboradorAtivoResumo(
                colaborador.getId(),
                colaborador.getNomeCompleto(),
                colaborador.getFuncao().getId(),
                colaborador.getFuncao().getNome(),
                colaborador.getBaseOperacional().getId(),
                colaborador.getBaseOperacional().getNome());
    }

    private void validarUnicidadeCpf(String cpf) {
        String cpfFormatado = cpf.replace("-", "").replace(".", "").trim();
        String cpfHash = HashUtils.gerarBlindIndex(cpfFormatado);
        if (repository.existsByCpfHash(cpfHash)) {
            throw new InvarianteVioladaException("Já existe um colaborador cadastrado com este CPF");
        }
    }
}
