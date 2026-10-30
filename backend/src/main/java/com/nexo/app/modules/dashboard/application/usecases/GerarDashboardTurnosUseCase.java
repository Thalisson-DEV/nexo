package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.DashboardTurnosFilter;
import com.nexo.app.modules.dashboard.application.dto.DashboardTurnosResponse;
import com.nexo.app.modules.dashboard.application.dto.ProducaoPorTipoServico;
import com.nexo.app.modules.dashboard.application.dto.DashboardTurnosResponse.Equipe;
import com.nexo.app.modules.dashboard.application.dto.DashboardTurnosResponse.Lancamento;
import com.nexo.app.modules.dashboard.application.dto.DashboardTurnosResponse.Resumo;
import com.nexo.app.modules.dashboard.application.dto.DashboardTurnosResponse.SituacaoTurno;
import com.nexo.app.modules.dashboard.application.dto.DetalhamentoTurnoResponse.Composicao;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.LancamentoResumido;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.rh.CronogramaMetaFaturamento;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.MetaFaturamentoApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCase.media;
import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCase.soma;
import static com.nexo.app.modules.dashboard.domain.CalendarioOperacional.percentual;

/**
 * Monta o dashboard de turnos de um dia: situação de cada equipe (em turno,
 * encerrado, sem turno), composição confirmada na abertura, produção do dia contra a
 * meta diária e há quanto tempo a equipe em turno não lança uma nota.
 */
@Service
public class GerarDashboardTurnosUseCase {

    /** Equipe em turno sem lançar há este tempo ou mais é destacada no painel. */
    static final int LIMITE_SEM_LANCAMENTO_MINUTOS = 120;

    private final EquipesDoRecorte equipesDoRecorte;
    private final ExecucaoComercialApi execucaoApi;
    private final TurnoApi turnoApi;
    private final ComposicaoDosTurnos composicaoDosTurnos;
    private final TiposServicoDosLancamentos tiposServico;
    private final MetaFaturamentoApi metaFaturamentoApi;
    private final Clock clock;

    @Autowired
    public GerarDashboardTurnosUseCase(EquipesDoRecorte equipesDoRecorte, ExecucaoComercialApi execucaoApi,
                                       TurnoApi turnoApi, ComposicaoDosTurnos composicaoDosTurnos, TiposServicoDosLancamentos tiposServico,
                                       MetaFaturamentoApi metaFaturamentoApi) {
        this(equipesDoRecorte, execucaoApi, turnoApi, composicaoDosTurnos, tiposServico, metaFaturamentoApi,
                Clock.system(CalendarioOperacional.FUSO));
    }

    GerarDashboardTurnosUseCase(EquipesDoRecorte equipesDoRecorte, ExecucaoComercialApi execucaoApi,
                                TurnoApi turnoApi, ComposicaoDosTurnos composicaoDosTurnos,
                                TiposServicoDosLancamentos tiposServico, MetaFaturamentoApi metaFaturamentoApi,
                                Clock clock) {
        this.metaFaturamentoApi = metaFaturamentoApi;
        this.equipesDoRecorte = equipesDoRecorte;
        this.execucaoApi = execucaoApi;
        this.turnoApi = turnoApi;
        this.composicaoDosTurnos = composicaoDosTurnos;
        this.tiposServico = tiposServico;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardTurnosResponse executar(DashboardTurnosFilter filter) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        LocalDate hoje = CalendarioOperacional.diaDe(agora);
        LocalDate dia = filter.data() != null ? filter.data() : hoje;
        boolean ehHoje = dia.equals(hoje);

        List<EquipeResponse> equipes = equipesDoRecorte.listar(filter.idBaseOperacional(), filter.idProcesso());
        Set<UUID> ids = equipes.stream().map(EquipeResponse::id).collect(Collectors.toSet());

        OffsetDateTime inicioDia = CalendarioOperacional.inicioDoDia(dia);
        OffsetDateTime fimDia = CalendarioOperacional.inicioDoDia(dia.plusDays(1));
        List<TurnoResponse> turnos = turnoApi.listarSobrepostosAoPeriodo(inicioDia, fimDia, ids);
        List<LancamentoResumido> lancamentos = execucaoApi.listarLancamentosResumidos(inicioDia, fimDia, ids);

        Map<UUID, List<TurnoResponse>> turnosPorEquipe = turnos.stream()
                .collect(Collectors.groupingBy(TurnoResponse::idEquipe));
        Map<UUID, List<LancamentoResumido>> lancamentosPorEquipe = lancamentos.stream()
                .collect(Collectors.groupingBy(LancamentoResumido::idEquipe));
        ComposicaoDosTurnos.Resolvedor resolvedor = composicaoDosTurnos.para(turnos);

        Janela janela = new Janela(dia, inicioDia, fimDia, agora, ehHoje);
        TiposServicoDosLancamentos.Agrupador porTipo = tiposServico.agrupador();
        Map<UUID, CronogramaMetaFaturamento> metas = metaFaturamentoApi.cronogramas(ids);
        List<Equipe> resumos = equipes.stream()
                .map(equipe -> resumirEquipe(equipe,
                        metas.getOrDefault(equipe.id(), CronogramaMetaFaturamento.vazio()),
                        turnosPorEquipe.getOrDefault(equipe.id(), List.of()),
                        lancamentosPorEquipe.getOrDefault(equipe.id(), List.of()),
                        resolvedor, janela, porTipo))
                .sorted(Comparator.comparing(Equipe::codigo, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        return new DashboardTurnosResponse(
                dia,
                ehHoje,
                CalendarioOperacional.diaUtil(dia),
                agora,
                LIMITE_SEM_LANCAMENTO_MINUTOS,
                resumir(resumos, lancamentos),
                porTipo.agrupar(lancamentos),
                resumos);
    }

    private Equipe resumirEquipe(EquipeResponse equipe, CronogramaMetaFaturamento metas, List<TurnoResponse> turnos,
                                 List<LancamentoResumido> lancamentos, ComposicaoDosTurnos.Resolvedor resolvedor,
                                 Janela janela, TiposServicoDosLancamentos.Agrupador porTipo) {
        BigDecimal producao = soma(lancamentos);
        long notas = lancamentos.size();
        BigDecimal metaDiaria = metas.metaDiaria(janela.dia());
        OffsetDateTime primeiro = lancamentos.isEmpty() ? null : lancamentos.getFirst().dataLancamento();
        OffsetDateTime ultimo = lancamentos.isEmpty() ? null : lancamentos.getLast().dataLancamento();
        List<Lancamento> linhaDoTempo = lancamentos.stream()
                .map(l -> new Lancamento(l.idExecucao(), l.dataLancamento(), l.valor(),
                        porTipo.descricao(l.idTipoServico())))
                .toList();
        List<ProducaoPorTipoServico> doDiaPorTipo = porTipo.agrupar(lancamentos);

        String base = equipe.baseOperacional() != null ? equipe.baseOperacional().nome() : null;
        String processo = equipe.processo() != null ? equipe.processo().nome() : null;

        if (turnos.isEmpty()) {
            return new Equipe(equipe.id(), equipe.codigo(), base, processo, SituacaoTurno.SEM_TURNO,
                    null, 0, null, null, 0, false, null,
                    equipe.veiculo() != null ? equipe.veiculo().placa() : null, List.of(),
                    notas, producao, media(producao, notas), metaDiaria, percentual(producao, metaDiaria),
                    primeiro, ultimo, null, false, linhaDoTempo, doDiaPorTipo);
        }

        TurnoResponse aberto = turnos.stream().filter(t -> t.status() == StatusTurno.ABERTO)
                .max(Comparator.comparing(TurnoResponse::dataInicio)).orElse(null);
        TurnoResponse principal = aberto != null ? aberto
                : turnos.stream().max(Comparator.comparing(TurnoResponse::dataInicio)).orElseThrow();
        Composicao composicao = resolvedor.composicao(principal, equipe);

        OffsetDateTime abertura = turnos.stream().map(TurnoResponse::dataInicio)
                .min(Comparator.naturalOrder()).orElseThrow();
        OffsetDateTime fechamento = aberto != null ? null : turnos.stream().map(TurnoResponse::dataFim)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);

        Long minutosSemLancamento = null;
        if (aberto != null && janela.hoje()) {
            OffsetDateTime referencia = ultimo != null && ultimo.isAfter(aberto.dataInicio()) ? ultimo : aberto.dataInicio();
            minutosSemLancamento = Math.max(0, Duration.between(referencia, janela.agora()).toMinutes());
        }

        return new Equipe(
                equipe.id(),
                equipe.codigo(),
                base,
                processo,
                aberto != null ? SituacaoTurno.EM_TURNO : SituacaoTurno.ENCERRADO,
                principal.id(),
                turnos.size(),
                abertura,
                fechamento,
                turnos.stream().mapToLong(t -> minutosNoDia(t, janela)).sum(),
                aberto != null && aberto.dataInicio().isBefore(janela.inicioDia()),
                composicao.abertoPor(),
                composicao.placa(),
                composicao.membros(),
                notas,
                producao,
                media(producao, notas),
                metaDiaria,
                percentual(producao, metaDiaria),
                primeiro,
                ultimo,
                minutosSemLancamento,
                minutosSemLancamento != null && minutosSemLancamento >= LIMITE_SEM_LANCAMENTO_MINUTOS,
                linhaDoTempo,
                doDiaPorTipo);
    }

    /** Minutos do turno que caem dentro do dia analisado (e não depois de agora). */
    static long minutosNoDia(TurnoResponse turno, Janela janela) {
        OffsetDateTime de = turno.dataInicio().isAfter(janela.inicioDia()) ? turno.dataInicio() : janela.inicioDia();
        OffsetDateTime ate = turno.dataFim() != null ? turno.dataFim() : janela.agora();
        if (ate.isAfter(janela.fimDia())) {
            ate = janela.fimDia();
        }
        if (ate.isAfter(janela.agora())) {
            ate = janela.agora();
        }
        return ate.isAfter(de) ? Duration.between(de, ate).toMinutes() : 0;
    }

    private static Resumo resumir(List<Equipe> equipes, List<LancamentoResumido> lancamentos) {
        List<Equipe> comMeta = equipes.stream().filter(e -> e.metaDiaria() != null).toList();
        BigDecimal metaDiaria = comMeta.isEmpty() ? null
                : comMeta.stream().map(Equipe::metaDiaria).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal producaoComMeta = comMeta.stream().map(Equipe::producao).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Resumo(
                equipes.size(),
                contar(equipes, SituacaoTurno.EM_TURNO),
                contar(equipes, SituacaoTurno.ENCERRADO),
                contar(equipes, SituacaoTurno.SEM_TURNO),
                (int) equipes.stream().filter(Equipe::semLancamentoRecente).count(),
                (int) equipes.stream().filter(Equipe::abertoDesdeOutroDia).count(),
                lancamentos.size(),
                soma(lancamentos),
                comMeta.size(),
                metaDiaria,
                producaoComMeta,
                percentual(producaoComMeta, metaDiaria));
    }

    private static int contar(List<Equipe> equipes, SituacaoTurno situacao) {
        return (int) equipes.stream().filter(e -> e.situacao() == situacao).count();
    }

    record Janela(LocalDate dia, OffsetDateTime inicioDia, OffsetDateTime fimDia, OffsetDateTime agora,
                  boolean hoje) {
    }
}
