package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.rh.CronogramaMetaFaturamento;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.JanelaMetaFaturamento;
import com.nexo.app.modules.rh.MetaFaturamentoApi;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoFilter;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoRequest;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoResponse;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoUpdateRequest;
import com.nexo.app.modules.rh.application.mappers.MetaFaturamentoMapper;
import com.nexo.app.modules.rh.domain.Equipe;
import com.nexo.app.modules.rh.domain.MetaFaturamentoEquipe;
import com.nexo.app.modules.rh.infrastructure.persistence.EquipeRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.MetaFaturamentoEquipeRepository;
import com.nexo.app.modules.rh.infrastructure.persistence.MetaFaturamentoEquipeSpecification;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nexo.app.shared.infrastructure.config.CacheNames.EQUIPES;
import static com.nexo.app.shared.infrastructure.config.CacheNames.METAS_FATURAMENTO;

/**
 * Motor de cadastro das metas de faturamento das equipes.
 *
 * <p>Uma meta vale por janela — o ciclo comercial (26 a 25) ou o mês civil — e tem
 * vigência contada em janelas inteiras. O cadastro aceita várias equipes de uma vez
 * (uma meta por equipe, tudo ou nada) e pode encerrar a meta sem prazo que a equipe
 * já tem, para reajustar o valor a partir de um ciclo sem apagar o histórico.</p>
 *
 * <p>Duas metas ativas da mesma equipe nunca cobrem o mesmo dia. A equipe inteira
 * ({@link EquipeResponse}) também expõe a meta da janela vigente, por isso as escritas
 * aqui invalidam o cache de equipes.</p>
 */
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = METAS_FATURAMENTO)
public class MetaFaturamentoUseCases implements MetaFaturamentoApi {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final MetaFaturamentoEquipeRepository repository;
    private final EquipeRepository equipeRepository;
    private final MetaFaturamentoMapper mapper;

    @Transactional
    @Caching(evict = {
            @CacheEvict(allEntries = true),
            @CacheEvict(cacheNames = EQUIPES, allEntries = true)
    })
    public List<MetaFaturamentoResponse> criar(MetaFaturamentoRequest request) {
        Set<UUID> idsEquipes = new LinkedHashSet<>(request.idsEquipes());
        Map<UUID, Equipe> equipes = equipesAtivas(idsEquipes);

        List<MetaFaturamentoEquipe> novas = new ArrayList<>();
        for (UUID idEquipe : idsEquipes) {
            MetaFaturamentoEquipe meta = new MetaFaturamentoEquipe(idEquipe, request.valor(), request.periodicidade(),
                    request.vigenciaInicio(), request.vigenciaFim(), request.descricao());
            List<MetaFaturamentoEquipe> existentes = repository.findByAtivoTrueAndIdEquipe(idEquipe);
            if (request.substituirVigentes()) {
                encerrarSubstituidas(meta, existentes, equipes.get(idEquipe));
            }
            validarSobreposicao(meta, existentes, null, equipes.get(idEquipe));
            novas.add(meta);
        }

        List<MetaFaturamentoEquipe> salvas = repository.saveAll(novas);
        return hidratar(salvas, equipes);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(allEntries = true),
            @CacheEvict(cacheNames = EQUIPES, allEntries = true)
    })
    public MetaFaturamentoResponse atualizar(UUID id, MetaFaturamentoUpdateRequest request) {
        MetaFaturamentoEquipe meta = buscarEntidade(id);
        meta.atualizar(request.valor(), request.periodicidade(),
                request.vigenciaInicio(), request.vigenciaFim(), request.descricao());
        Equipe equipe = equipeRepository.findById(meta.getIdEquipe()).orElse(null);
        validarSobreposicao(meta, repository.findByAtivoTrueAndIdEquipe(meta.getIdEquipe()), id, equipe);
        return hidratar(repository.save(meta));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(allEntries = true),
            @CacheEvict(cacheNames = EQUIPES, allEntries = true)
    })
    public void desativar(UUID id) {
        MetaFaturamentoEquipe meta = buscarEntidade(id);
        meta.desativar();
        repository.save(meta);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(allEntries = true),
            @CacheEvict(cacheNames = EQUIPES, allEntries = true)
    })
    public void ativar(UUID id) {
        MetaFaturamentoEquipe meta = buscarEntidade(id);
        Equipe equipe = equipeRepository.findById(meta.getIdEquipe()).orElse(null);
        meta.ativar();
        validarSobreposicao(meta, repository.findByAtivoTrueAndIdEquipe(meta.getIdEquipe()), id, equipe);
        repository.save(meta);
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public MetaFaturamentoResponse buscarPorId(UUID id) {
        return hidratar(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<MetaFaturamentoResponse> listar(MetaFaturamentoFilter filter, Pageable pageable) {
        List<UUID> recorte = filter != null && (filter.idBaseOperacional() != null || filter.idProcesso() != null)
                ? equipeRepository.findIdsByFiltros(filter.idBaseOperacional(), filter.idProcesso())
                : null;
        Page<MetaFaturamentoEquipe> pagina = repository.findAll(
                MetaFaturamentoEquipeSpecification.filterBy(filter, recorte), pageable);
        Map<UUID, Equipe> equipes = equipeRepository.findAllById(
                        pagina.getContent().stream().map(MetaFaturamentoEquipe::getIdEquipe).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Equipe::getId, Function.identity()));
        return PageResponse.fromPage(pagina.map(meta -> hidratar(meta, equipes.get(meta.getIdEquipe()))));
    }

    // ------------------------------------------------------------ API pública

    @Override
    @Transactional(readOnly = true)
    public CronogramaMetaFaturamento cronograma(UUID idEquipe) {
        if (idEquipe == null) {
            return CronogramaMetaFaturamento.vazio();
        }
        return new CronogramaMetaFaturamento(repository.findByAtivoTrueAndIdEquipe(idEquipe).stream()
                .map(MetaFaturamentoEquipe::comoVigencia)
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CronogramaMetaFaturamento> cronogramas(Collection<UUID> idsEquipes) {
        if (idsEquipes == null || idsEquipes.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<CronogramaMetaFaturamento.Vigencia>> porEquipe = repository
                .findByAtivoTrueAndIdEquipeIn(idsEquipes).stream()
                .collect(Collectors.groupingBy(MetaFaturamentoEquipe::getIdEquipe,
                        Collectors.mapping(MetaFaturamentoEquipe::comoVigencia, Collectors.toList())));
        Map<UUID, CronogramaMetaFaturamento> resultado = new HashMap<>();
        for (UUID id : idsEquipes) {
            resultado.put(id, new CronogramaMetaFaturamento(porEquipe.getOrDefault(id, List.of())));
        }
        return resultado;
    }

    /**
     * Preenche na resposta da equipe a meta da janela vigente hoje e a meta diária
     * correspondente — o que o aplicativo de campo mostra ao encarregado.
     */
    @Transactional(readOnly = true)
    public EquipeResponse comMetaVigente(EquipeResponse equipe) {
        if (equipe == null) {
            return null;
        }
        return comMetaVigente(equipe, cronograma(equipe.id()));
    }

    @Transactional(readOnly = true)
    public List<EquipeResponse> comMetaVigente(List<EquipeResponse> equipes) {
        Map<UUID, CronogramaMetaFaturamento> cronogramas = cronogramas(
                equipes.stream().map(EquipeResponse::id).toList());
        return equipes.stream()
                .map(e -> comMetaVigente(e, cronogramas.getOrDefault(e.id(), CronogramaMetaFaturamento.vazio())))
                .toList();
    }

    /** Meta da janela vigente hoje e a meta diária dela; campos nulos sem meta vigente. */
    public record MetaVigente(BigDecimal valorDaJanela, BigDecimal diaria) {
    }

    @Transactional(readOnly = true)
    public MetaVigente metaVigente(UUID idEquipe) {
        return metaVigente(cronograma(idEquipe));
    }

    private static MetaVigente metaVigente(CronogramaMetaFaturamento cronograma) {
        LocalDate hoje = LocalDate.now(ZONA);
        Optional<JanelaMetaFaturamento> janela = cronograma.janelaEm(hoje);
        BigDecimal valor = cronograma.valorDaJanelaEm(hoje);
        BigDecimal diaria = valor == null || janela.isEmpty() || janela.get().diasUteis() == 0 ? null
                : valor.divide(BigDecimal.valueOf(janela.get().diasUteis()), 2, RoundingMode.HALF_UP);
        return new MetaVigente(valor, diaria);
    }

    private static EquipeResponse comMetaVigente(EquipeResponse equipe, CronogramaMetaFaturamento cronograma) {
        MetaVigente meta = metaVigente(cronograma);
        return equipe.comMeta(meta.valorDaJanela(), meta.diaria());
    }

    // ------------------------------------------------------------ Regras

    private Map<UUID, Equipe> equipesAtivas(Set<UUID> ids) {
        Map<UUID, Equipe> equipes = equipeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Equipe::getId, Function.identity()));
        for (UUID id : ids) {
            Equipe equipe = equipes.get(id);
            if (equipe == null) {
                throw new RecursoNaoEncontradoException("Equipe não encontrada com ID: " + id);
            }
            if (!equipe.isAtivo()) {
                throw new InvarianteVioladaException("A equipe " + equipe.getCodigo() + " está inativa.");
            }
        }
        return equipes;
    }

    /**
     * Encerra, no fim da janela anterior ao início da nova meta, as metas da equipe
     * que começaram antes dela e seguiriam vigentes depois — o reajuste de um ciclo
     * para o outro. Metas que começam junto ou depois da nova continuam conflitando.
     */
    private void encerrarSubstituidas(MetaFaturamentoEquipe nova, List<MetaFaturamentoEquipe> existentes,
                                      Equipe equipe) {
        LocalDate ultimoDiaAnterior = nova.getVigenciaInicio().minusDays(1);
        for (MetaFaturamentoEquipe existente : existentes) {
            boolean comecaAntes = existente.getVigenciaInicio().isBefore(nova.getVigenciaInicio());
            boolean seguiriaVigente = existente.getVigenciaFim() == null
                    || !existente.getVigenciaFim().isBefore(nova.getVigenciaInicio());
            if (comecaAntes && seguiriaVigente) {
                if (!existente.getPeriodicidade().encerraJanela(ultimoDiaAnterior)) {
                    throw new InvarianteVioladaException("A meta atual da equipe " + codigo(equipe)
                            + " é por " + rotulo(existente) + " e não termina em "
                            + ultimoDiaAnterior + ". Ajuste a vigência dela antes de cadastrar a nova.");
                }
                existente.encerrarEm(ultimoDiaAnterior);
            }
        }
    }

    private void validarSobreposicao(MetaFaturamentoEquipe meta, List<MetaFaturamentoEquipe> existentes,
                                     UUID idIgnorar, Equipe equipe) {
        boolean conflito = existentes.stream()
                .filter(outra -> idIgnorar == null || !idIgnorar.equals(outra.getId()))
                .anyMatch(meta::conflitaCom);
        if (conflito) {
            throw new InvarianteVioladaException("A equipe " + codigo(equipe)
                    + " já tem uma meta ativa com vigência sobreposta. "
                    + "Encerre ou ajuste a meta existente, ou marque a opção de substituir a meta vigente.");
        }
    }

    private static String codigo(Equipe equipe) {
        return equipe != null ? equipe.getCodigo() : "selecionada";
    }

    private static String rotulo(MetaFaturamentoEquipe meta) {
        return switch (meta.getPeriodicidade()) {
            case CICLO_COMERCIAL -> "ciclo comercial";
            case MENSAL -> "mês civil";
        };
    }

    // ------------------------------------------------------------ Respostas

    private List<MetaFaturamentoResponse> hidratar(List<MetaFaturamentoEquipe> metas, Map<UUID, Equipe> equipes) {
        return metas.stream().map(meta -> hidratar(meta, equipes.get(meta.getIdEquipe()))).toList();
    }

    private MetaFaturamentoResponse hidratar(MetaFaturamentoEquipe meta) {
        return hidratar(meta, equipeRepository.findById(meta.getIdEquipe()).orElse(null));
    }

    private MetaFaturamentoResponse hidratar(MetaFaturamentoEquipe meta, Equipe equipe) {
        MetaFaturamentoResponse base = mapper.toResponse(meta);
        LocalDate hoje = LocalDate.now(ZONA);
        Optional<JanelaMetaFaturamento> janela = meta.janelaEm(hoje);
        return new MetaFaturamentoResponse(
                base.id(), base.idEquipe(),
                equipe != null ? equipe.getCodigo() : null,
                equipe != null && equipe.getProcesso() != null ? equipe.getProcesso().getNome() : null,
                equipe != null && equipe.getBaseOperacional() != null ? equipe.getBaseOperacional().getNome() : null,
                base.valor(), base.periodicidade(), base.vigenciaInicio(), base.vigenciaFim(),
                base.descricao(), base.ativo(),
                janela.map(JanelaMetaFaturamento::inicio).orElse(null),
                janela.map(JanelaMetaFaturamento::fim).orElse(null),
                janela.map(JanelaMetaFaturamento::diasUteis).orElse(null),
                meta.metaDiariaEm(hoje).orElse(null));
    }

    private MetaFaturamentoEquipe buscarEntidade(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Meta de faturamento não encontrada com ID: " + id));
    }
}
