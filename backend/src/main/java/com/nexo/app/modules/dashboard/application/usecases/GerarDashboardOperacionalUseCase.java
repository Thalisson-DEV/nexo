package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalFilter;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.Equipe;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.ProducaoDia;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.ProducaoPorObra;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.Resumo;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.SituacaoMeta;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.LancamentoResumido;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.obras.ExecucaoObrasApi;
import com.nexo.app.modules.obras.ExecucaoObrasApi.ExecutadoNoDia;
import com.nexo.app.modules.rh.CronogramaMetaFaturamento;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.MetaFaturamentoApi;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nexo.app.modules.dashboard.domain.CalendarioOperacional.percentual;

/**
 * Monta o dashboard operacional do processo de obras: produção do período por equipe (o executado apontado nas
 * obras, o mesmo que o boletim soma), comparada à meta proporcional e ao ritmo necessário para fechá-la.
 *
 * <p>Entram só as equipes dos processos que têm obras. O executado chega já somado por obra, equipe e dia; o
 * resto da agregação é feito aqui.</p>
 */
@Service
public class GerarDashboardOperacionalUseCase {

    static final int PERIODO_MAXIMO_DIAS = 366;

    private final EquipesDoRecorte equipesDoRecorte;
    private final ExecucaoObrasApi execucaoObrasApi;
    private final TurnoApi turnoApi;
    private final MetaFaturamentoApi metaFaturamentoApi;
    private final Clock clock;

    @Autowired
    public GerarDashboardOperacionalUseCase(EquipesDoRecorte equipesDoRecorte, ExecucaoObrasApi execucaoObrasApi,
                                            TurnoApi turnoApi, MetaFaturamentoApi metaFaturamentoApi) {
        this(equipesDoRecorte, execucaoObrasApi, turnoApi, metaFaturamentoApi, Clock.system(CalendarioOperacional.FUSO));
    }

    GerarDashboardOperacionalUseCase(EquipesDoRecorte equipesDoRecorte, ExecucaoObrasApi execucaoObrasApi,
                                     TurnoApi turnoApi, MetaFaturamentoApi metaFaturamentoApi, Clock clock) {
        this.equipesDoRecorte = equipesDoRecorte;
        this.execucaoObrasApi = execucaoObrasApi;
        this.turnoApi = turnoApi;
        this.metaFaturamentoApi = metaFaturamentoApi;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardOperacionalResponse executar(DashboardOperacionalFilter filter) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicio = filter.inicio() != null ? filter.inicio() : hoje.withDayOfMonth(1);
        LocalDate fim = filter.fim() != null ? filter.fim() : hoje;
        validarPeriodo(inicio, fim);

        Set<UUID> processosDeObras = execucaoObrasApi.processosComObras();
        List<EquipeResponse> equipes = equipesDoRecorte.listar(filter.idBaseOperacional(), filter.idProcesso()).stream()
                .filter(e -> e.processo() != null && processosDeObras.contains(e.processo().id()))
                .toList();
        Set<UUID> ids = equipes.stream().map(EquipeResponse::id).collect(Collectors.toSet());

        List<ExecutadoNoDia> executado = execucaoObrasApi.executadoPorDia(ids, inicio, fim);
        OffsetDateTime de = CalendarioOperacional.inicioDoDia(inicio);
        OffsetDateTime ate = CalendarioOperacional.inicioDoDia(fim.plusDays(1));
        List<TurnoResponse> turnos = ids.isEmpty() ? List.of() : turnoApi.listarSobrepostosAoPeriodo(de, ate, ids);

        long diasNoPeriodo = ChronoUnit.DAYS.between(inicio, fim) + 1;
        BigDecimal producaoAnterior = somaExecutado(execucaoObrasApi.executadoPorDia(ids,
                inicio.minusDays(diasNoPeriodo), inicio.minusDays(1)));

        // Dias úteis já encerrados: até ontem, sem passar do fim do período.
        LocalDate ultimoDiaEncerrado = minimo(fim, hoje.minusDays(1));
        int diasUteisPeriodo = CalendarioOperacional.diasUteis(inicio, fim);
        int diasUteisEncerrados = CalendarioOperacional.diasUteis(inicio, ultimoDiaEncerrado);
        int diasUteisFuturos = CalendarioOperacional.diasUteis(maximo(inicio, hoje.plusDays(1)), fim);

        Map<UUID, List<ExecutadoNoDia>> executadoPorEquipe = executado.stream()
                .collect(Collectors.groupingBy(ExecutadoNoDia::idEquipe));
        Map<UUID, Set<LocalDate>> diasComTurnoPorEquipe = new HashMap<>();
        for (TurnoResponse turno : turnos) {
            LocalDate dia = CalendarioOperacional.diaDe(turno.dataInicio());
            if (!dia.isBefore(inicio) && !dia.isAfter(fim)) {
                diasComTurnoPorEquipe.computeIfAbsent(turno.idEquipe(), k -> new HashSet<>()).add(dia);
            }
        }

        // Dia que define a meta da janela exibida: hoje, limitado ao período.
        LocalDate referencia = minimo(fim, maximo(inicio, hoje));
        Contexto contexto = new Contexto(inicio, fim, ultimoDiaEncerrado, diasUteisEncerrados, diasUteisFuturos,
                referencia);
        Map<UUID, CronogramaMetaFaturamento> metas = metaFaturamentoApi.cronogramas(ids);
        List<Equipe> resumosEquipes = equipes.stream()
                .map(equipe -> resumirEquipe(equipe,
                        metas.getOrDefault(equipe.id(), CronogramaMetaFaturamento.vazio()),
                        executadoPorEquipe.getOrDefault(equipe.id(), List.of()),
                        diasComTurnoPorEquipe.getOrDefault(equipe.id(), Set.of()).size(),
                        contexto))
                .sorted(Comparator.comparing(Equipe::codigo, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        return new DashboardOperacionalResponse(
                inicio,
                fim,
                OffsetDateTime.now(clock),
                diasUteisPeriodo,
                diasUteisEncerrados,
                resumir(resumosEquipes, executado, producaoAnterior),
                producaoDiariaGeral(executado, resumosEquipes, inicio, fim),
                porObra(executado),
                resumosEquipes);
    }

    private Equipe resumirEquipe(EquipeResponse equipe, CronogramaMetaFaturamento metas,
                                 List<ExecutadoNoDia> executado, long diasComTurno, Contexto contexto) {
        BigDecimal producao = somaExecutado(executado);
        long obras = obrasDistintas(executado);
        BigDecimal metaPeriodo = metas.metaProporcional(contexto.inicio(), contexto.fim());
        List<ProducaoDia> diaria = producaoDiaria(executado, contexto.inicio(), contexto.fim(),
                metaPeriodo == null ? null : metas);
        long diasComProducao = diaria.stream().filter(d -> d.obras() > 0).count();

        // Meta da janela (ciclo ou mês) vigente na data de referência.
        BigDecimal metaDaJanela = metas.valorDaJanelaEm(contexto.referencia());
        BigDecimal metaEsperada = metaPeriodo == null ? null
                : metas.metaProporcional(contexto.inicio(), contexto.ultimoDiaEncerrado());
        BigDecimal producaoAteOntem = somaExecutado(executado.stream()
                .filter(e -> !e.data().isAfter(contexto.ultimoDiaEncerrado()))
                .toList());

        return new Equipe(
                equipe.id(),
                equipe.codigo(),
                equipe.baseOperacional() != null ? equipe.baseOperacional().nome() : null,
                equipe.processo() != null ? equipe.processo().nome() : null,
                equipe.tipoEquipe() != null ? equipe.tipoEquipe().nome() : null,
                equipe.veiculo() != null ? equipe.veiculo().placa() : null,
                producao,
                obras,
                media(producao, obras),
                servicos(executado),
                diasComTurno,
                diasComProducao,
                media(producao, diasComProducao),
                metaDaJanela,
                metaPeriodo,
                metaEsperada,
                percentual(producao, metaPeriodo),
                projetar(producao, producaoAteOntem, contexto),
                situacao(producao, metaPeriodo, metaEsperada),
                executado.stream().map(ExecutadoNoDia::data).max(Comparator.naturalOrder()).orElse(null),
                diaria,
                porObra(executado));
    }

    static SituacaoMeta situacao(BigDecimal producao, BigDecimal metaPeriodo, BigDecimal metaEsperada) {
        if (metaPeriodo == null) {
            return SituacaoMeta.SEM_META;
        }
        if (producao.compareTo(metaPeriodo) >= 0) {
            return SituacaoMeta.META_ATINGIDA;
        }
        if (metaEsperada == null || producao.compareTo(metaEsperada) >= 0) {
            return SituacaoMeta.NO_RITMO;
        }
        return SituacaoMeta.ABAIXO_DO_RITMO;
    }

    /**
     * Produção atual mais o ritmo dos dias úteis encerrados aplicado aos dias úteis
     * futuros. Sem dia útil encerrado não há ritmo para projetar; sem dias futuros a
     * projeção é a própria produção.
     */
    private static BigDecimal projetar(BigDecimal producao, BigDecimal producaoAteOntem, Contexto contexto) {
        if (contexto.diasUteisFuturos() == 0) {
            return producao;
        }
        if (contexto.diasUteisEncerrados() == 0) {
            return null;
        }
        BigDecimal ritmo = producaoAteOntem.divide(
                BigDecimal.valueOf(contexto.diasUteisEncerrados()), 6, RoundingMode.HALF_UP);
        return producao.add(ritmo.multiply(BigDecimal.valueOf(contexto.diasUteisFuturos())))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static Resumo resumir(List<Equipe> equipes, List<ExecutadoNoDia> executado,
                                  BigDecimal producaoAnterior) {
        BigDecimal producao = somaExecutado(executado);
        long obras = obrasDistintas(executado);
        List<Equipe> comMeta = equipes.stream().filter(e -> e.metaPeriodo() != null).toList();
        BigDecimal metaPeriodo = comMeta.isEmpty() ? null : comMeta.stream()
                .map(Equipe::metaPeriodo).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal producaoComMeta = comMeta.stream().map(Equipe::producao).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean projecaoCompleta = !comMeta.isEmpty() && comMeta.stream().allMatch(e -> e.projecao() != null);
        BigDecimal projecaoComMeta = projecaoCompleta
                ? comMeta.stream().map(Equipe::projecao).reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;

        BigDecimal variacao = producaoAnterior.signum() > 0
                ? producao.subtract(producaoAnterior).multiply(BigDecimal.valueOf(100))
                        .divide(producaoAnterior, 1, RoundingMode.HALF_UP)
                : null;

        return new Resumo(
                producao,
                producaoAnterior,
                variacao,
                obras,
                media(producao, obras),
                servicos(executado),
                equipes.size(),
                (int) equipes.stream().filter(e -> e.producao().signum() > 0 || e.servicos() > 0).count(),
                comMeta.size(),
                metaPeriodo,
                producaoComMeta,
                percentual(producaoComMeta, metaPeriodo),
                projecaoComMeta,
                (int) equipes.stream().filter(e -> e.situacao() == SituacaoMeta.ABAIXO_DO_RITMO).count());
    }

    /** Produção por dia; sem cronograma de metas, a meta de cada dia fica nula. */
    private static List<ProducaoDia> producaoDiaria(List<ExecutadoNoDia> executado, LocalDate inicio,
                                                    LocalDate fim, CronogramaMetaFaturamento metas) {
        Map<LocalDate, List<ExecutadoNoDia>> porDia = executado.stream()
                .collect(Collectors.groupingBy(ExecutadoNoDia::data));
        List<ProducaoDia> dias = new ArrayList<>();
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            List<ExecutadoNoDia> doDia = porDia.getOrDefault(dia, List.of());
            BigDecimal meta = metas == null ? null
                    : Objects.requireNonNullElse(metas.metaDiaria(dia), BigDecimal.ZERO);
            dias.add(new ProducaoDia(dia, obrasDistintas(doDia), servicos(doDia), somaExecutado(doDia), meta));
        }
        return dias;
    }

    /** Série do recorte inteiro: produção de todas as equipes e meta somada das equipes com meta. */
    private static List<ProducaoDia> producaoDiariaGeral(List<ExecutadoNoDia> executado, List<Equipe> equipes,
                                                         LocalDate inicio, LocalDate fim) {
        List<ProducaoDia> producao = producaoDiaria(executado, inicio, fim, null);
        List<Equipe> comMeta = equipes.stream().filter(e -> e.metaPeriodo() != null).toList();
        if (comMeta.isEmpty()) {
            return producao;
        }
        List<ProducaoDia> dias = new ArrayList<>(producao.size());
        for (int i = 0; i < producao.size(); i++) {
            int indice = i;
            BigDecimal meta = comMeta.stream()
                    .map(e -> e.producaoDiaria().get(indice).meta())
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            ProducaoDia dia = producao.get(i);
            dias.add(new ProducaoDia(dia.data(), dia.obras(), dia.servicos(), dia.valor(), meta));
        }
        return dias;
    }

    /** Obras do recorte, da maior produção para a menor (empate: número da obra). */
    static List<ProducaoPorObra> porObra(List<ExecutadoNoDia> executado) {
        if (executado.isEmpty()) {
            return List.of();
        }
        BigDecimal total = somaExecutado(executado);
        Map<UUID, List<ExecutadoNoDia>> porObra = executado.stream()
                .collect(Collectors.groupingBy(ExecutadoNoDia::idObra));
        return porObra.values().stream()
                .map(daObra -> {
                    ExecutadoNoDia primeiro = daObra.getFirst();
                    BigDecimal producao = somaExecutado(daObra);
                    return new ProducaoPorObra(
                            primeiro.idObra(),
                            primeiro.numeroObra(),
                            primeiro.tituloObra(),
                            (int) daObra.stream().map(ExecutadoNoDia::idEquipe).distinct().count(),
                            servicos(daObra),
                            daObra.stream().map(ExecutadoNoDia::data).distinct().count(),
                            producao,
                            total.signum() > 0 ? percentual(producao, total) : BigDecimal.ZERO,
                            daObra.stream().map(ExecutadoNoDia::data).max(Comparator.naturalOrder()).orElse(null));
                })
                .sorted(Comparator.comparing(ProducaoPorObra::producao, Comparator.reverseOrder())
                        .thenComparing(ProducaoPorObra::numero, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) {
            throw new InvarianteVioladaException("A data final do período não pode ser anterior à inicial.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new InvarianteVioladaException("O período do dashboard pode ter no máximo " + PERIODO_MAXIMO_DIAS + " dias.");
        }
    }

    static BigDecimal somaExecutado(List<ExecutadoNoDia> executado) {
        return executado.stream()
                .map(ExecutadoNoDia::valor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Soma de lançamentos de notas, usada pelos painéis do comercial. */
    static BigDecimal soma(List<LancamentoResumido> lancamentos) {
        return lancamentos.stream()
                .map(LancamentoResumido::valor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static long obrasDistintas(List<ExecutadoNoDia> executado) {
        return executado.stream().map(ExecutadoNoDia::idObra).distinct().count();
    }

    private static long servicos(List<ExecutadoNoDia> executado) {
        return executado.stream().mapToLong(ExecutadoNoDia::servicos).sum();
    }

    static BigDecimal media(BigDecimal total, long quantidade) {
        return quantidade > 0
                ? total.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
    }

    private static LocalDate minimo(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate maximo(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private record Contexto(LocalDate inicio, LocalDate fim, LocalDate ultimoDiaEncerrado,
                            int diasUteisEncerrados, int diasUteisFuturos, LocalDate referencia) {
    }
}
