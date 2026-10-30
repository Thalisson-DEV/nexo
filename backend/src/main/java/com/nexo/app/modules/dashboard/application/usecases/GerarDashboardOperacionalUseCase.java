package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalFilter;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.Equipe;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.ProducaoDia;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.Resumo;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.SituacaoMeta;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.LancamentoResumido;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
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
 * Monta o dashboard operacional: produção do período por equipe, comparada à meta
 * proporcional e ao ritmo necessário para fechá-la.
 *
 * <p>Os lançamentos chegam numa projeção leve (sem hidratar ações e materiais) e são
 * agrupados por dia de São Paulo aqui mesmo — agrupar no banco exigiria fixar o fuso
 * na consulta e não reduziria o volume de forma relevante para os recortes de até um
 * ano que a tela permite.</p>
 */
@Service
public class GerarDashboardOperacionalUseCase {

    static final int PERIODO_MAXIMO_DIAS = 366;

    private final EquipesDoRecorte equipesDoRecorte;
    private final ExecucaoComercialApi execucaoApi;
    private final TurnoApi turnoApi;
    private final TiposServicoDosLancamentos tiposServico;
    private final MetaFaturamentoApi metaFaturamentoApi;
    private final Clock clock;

    @Autowired
    public GerarDashboardOperacionalUseCase(EquipesDoRecorte equipesDoRecorte, ExecucaoComercialApi execucaoApi,
                                            TurnoApi turnoApi, TiposServicoDosLancamentos tiposServico,
                                            MetaFaturamentoApi metaFaturamentoApi) {
        this(equipesDoRecorte, execucaoApi, turnoApi, tiposServico, metaFaturamentoApi,
                Clock.system(CalendarioOperacional.FUSO));
    }

    GerarDashboardOperacionalUseCase(EquipesDoRecorte equipesDoRecorte, ExecucaoComercialApi execucaoApi,
                                     TurnoApi turnoApi, TiposServicoDosLancamentos tiposServico,
                                     MetaFaturamentoApi metaFaturamentoApi, Clock clock) {
        this.metaFaturamentoApi = metaFaturamentoApi;
        this.equipesDoRecorte = equipesDoRecorte;
        this.execucaoApi = execucaoApi;
        this.turnoApi = turnoApi;
        this.tiposServico = tiposServico;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardOperacionalResponse executar(DashboardOperacionalFilter filter) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate inicio = filter.inicio() != null ? filter.inicio() : hoje.withDayOfMonth(1);
        LocalDate fim = filter.fim() != null ? filter.fim() : hoje;
        validarPeriodo(inicio, fim);

        List<EquipeResponse> equipes = equipesDoRecorte.listar(filter.idBaseOperacional(), filter.idProcesso());
        Set<UUID> ids = equipes.stream().map(EquipeResponse::id).collect(Collectors.toSet());

        OffsetDateTime de = CalendarioOperacional.inicioDoDia(inicio);
        OffsetDateTime ate = CalendarioOperacional.inicioDoDia(fim.plusDays(1));
        List<LancamentoResumido> lancamentos = execucaoApi.listarLancamentosResumidos(de, ate, ids);
        List<TurnoResponse> turnos = turnoApi.listarSobrepostosAoPeriodo(de, ate, ids);

        long diasNoPeriodo = ChronoUnit.DAYS.between(inicio, fim) + 1;
        BigDecimal producaoAnterior = soma(execucaoApi.listarLancamentosResumidos(
                CalendarioOperacional.inicioDoDia(inicio.minusDays(diasNoPeriodo)), de, ids));

        // Dias úteis já encerrados: até ontem, sem passar do fim do período.
        LocalDate ultimoDiaEncerrado = minimo(fim, hoje.minusDays(1));
        int diasUteisPeriodo = CalendarioOperacional.diasUteis(inicio, fim);
        int diasUteisEncerrados = CalendarioOperacional.diasUteis(inicio, ultimoDiaEncerrado);
        int diasUteisFuturos = CalendarioOperacional.diasUteis(maximo(inicio, hoje.plusDays(1)), fim);

        Map<UUID, List<LancamentoResumido>> lancamentosPorEquipe = lancamentos.stream()
                .collect(Collectors.groupingBy(LancamentoResumido::idEquipe));
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
        TiposServicoDosLancamentos.Agrupador porTipo = tiposServico.agrupador();
        List<Equipe> resumosEquipes = equipes.stream()
                .map(equipe -> resumirEquipe(equipe,
                        metas.getOrDefault(equipe.id(), CronogramaMetaFaturamento.vazio()),
                        lancamentosPorEquipe.getOrDefault(equipe.id(), List.of()),
                        diasComTurnoPorEquipe.getOrDefault(equipe.id(), Set.of()).size(),
                        contexto, porTipo))
                .sorted(Comparator.comparing(Equipe::codigo, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        return new DashboardOperacionalResponse(
                inicio,
                fim,
                OffsetDateTime.now(clock),
                diasUteisPeriodo,
                diasUteisEncerrados,
                resumir(resumosEquipes, lancamentos, producaoAnterior),
                producaoDiariaGeral(lancamentos, resumosEquipes, inicio, fim),
                porTipo.agrupar(lancamentos),
                resumosEquipes);
    }

    private Equipe resumirEquipe(EquipeResponse equipe, CronogramaMetaFaturamento metas,
                                 List<LancamentoResumido> lancamentos, long diasComTurno,
                                 Contexto contexto, TiposServicoDosLancamentos.Agrupador porTipo) {
        BigDecimal producao = soma(lancamentos);
        long notas = lancamentos.size();
        BigDecimal metaPeriodo = metas.metaProporcional(contexto.inicio(), contexto.fim());
        List<ProducaoDia> diaria = producaoDiaria(lancamentos, contexto.inicio(), contexto.fim(),
                metaPeriodo == null ? null : metas);
        long diasComProducao = diaria.stream().filter(d -> d.notas() > 0).count();

        // Meta da janela (ciclo comercial ou mês) vigente na data de referência.
        BigDecimal metaDaJanela = metas.valorDaJanelaEm(contexto.referencia());
        BigDecimal metaEsperada = metaPeriodo == null ? null
                : metas.metaProporcional(contexto.inicio(), contexto.ultimoDiaEncerrado());
        BigDecimal producaoAteOntem = soma(lancamentos.stream()
                .filter(l -> !CalendarioOperacional.diaDe(l.dataLancamento()).isAfter(contexto.ultimoDiaEncerrado()))
                .toList());

        return new Equipe(
                equipe.id(),
                equipe.codigo(),
                equipe.baseOperacional() != null ? equipe.baseOperacional().nome() : null,
                equipe.processo() != null ? equipe.processo().nome() : null,
                equipe.tipoEquipe() != null ? equipe.tipoEquipe().nome() : null,
                equipe.veiculo() != null ? equipe.veiculo().placa() : null,
                producao,
                notas,
                media(producao, notas),
                diasComTurno,
                diasComProducao,
                media(producao, diasComProducao),
                metaDaJanela,
                metaPeriodo,
                metaEsperada,
                percentual(producao, metaPeriodo),
                projetar(producao, producaoAteOntem, contexto),
                situacao(producao, metaPeriodo, metaEsperada),
                lancamentos.isEmpty() ? null : lancamentos.getLast().dataLancamento(),
                diaria,
                porTipo.agrupar(lancamentos));
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

    private static Resumo resumir(List<Equipe> equipes, List<LancamentoResumido> lancamentos,
                                  BigDecimal producaoAnterior) {
        BigDecimal producao = soma(lancamentos);
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
                lancamentos.size(),
                media(producao, lancamentos.size()),
                equipes.size(),
                (int) equipes.stream().filter(e -> e.notas() > 0).count(),
                comMeta.size(),
                metaPeriodo,
                producaoComMeta,
                percentual(producaoComMeta, metaPeriodo),
                projecaoComMeta,
                (int) equipes.stream().filter(e -> e.situacao() == SituacaoMeta.ABAIXO_DO_RITMO).count());
    }

    /** Produção por dia; sem cronograma de metas, a meta de cada dia fica nula. */
    private static List<ProducaoDia> producaoDiaria(List<LancamentoResumido> lancamentos, LocalDate inicio,
                                                    LocalDate fim, CronogramaMetaFaturamento metas) {
        Map<LocalDate, List<LancamentoResumido>> porDia = lancamentos.stream()
                .collect(Collectors.groupingBy(l -> CalendarioOperacional.diaDe(l.dataLancamento())));
        List<ProducaoDia> dias = new ArrayList<>();
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            List<LancamentoResumido> doDia = porDia.getOrDefault(dia, List.of());
            BigDecimal meta = metas == null ? null
                    : Objects.requireNonNullElse(metas.metaDiaria(dia), BigDecimal.ZERO);
            dias.add(new ProducaoDia(dia, doDia.size(), soma(doDia), meta));
        }
        return dias;
    }

    /** Série do recorte inteiro: produção de todas as equipes e meta somada das equipes com meta. */
    private static List<ProducaoDia> producaoDiariaGeral(List<LancamentoResumido> lancamentos, List<Equipe> equipes,
                                                         LocalDate inicio, LocalDate fim) {
        List<ProducaoDia> producao = producaoDiaria(lancamentos, inicio, fim, null);
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
            dias.add(new ProducaoDia(dia.data(), dia.notas(), dia.valor(), meta));
        }
        return dias;
    }

    private static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) {
            throw new InvarianteVioladaException("A data final do período não pode ser anterior à inicial.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new InvarianteVioladaException("O período do dashboard pode ter no máximo " + PERIODO_MAXIMO_DIAS + " dias.");
        }
    }

    static BigDecimal soma(List<LancamentoResumido> lancamentos) {
        return lancamentos.stream()
                .map(LancamentoResumido::valor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
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
