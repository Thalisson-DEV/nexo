package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.DetalhamentoTurnoResponse;
import com.nexo.app.modules.dashboard.application.dto.DetalhamentoTurnoResponse.Historico;
import com.nexo.app.modules.dashboard.application.dto.DetalhamentoTurnoResponse.Indicadores;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.ConsultaTurnos;
import com.nexo.app.modules.execucao.application.dto.ExecucaoComercialResponse;
import com.nexo.app.modules.execucao.application.dto.LancamentoDoTurno;
import com.nexo.app.modules.execucao.application.dto.ProducaoDoTurno;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.execucao.domain.StatusExecucao;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.MetaFaturamentoApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCase.media;
import static com.nexo.app.modules.dashboard.domain.CalendarioOperacional.percentual;

/**
 * Monta o relatório de um turno: composição confirmada na abertura, notas lançadas,
 * indicadores de ritmo e meta e a comparação com os turnos anteriores da equipe.
 */
@Service
public class DetalharTurnoUseCase {

    /** Janela, em dias antes da abertura, dos turnos que servem de referência para a equipe. */
    static final int DIAS_HISTORICO = 30;

    private final TurnoApi turnoApi;
    private final ExecucaoComercialApi execucaoApi;
    private final EquipeApi equipeApi;
    private final ComposicaoDosTurnos composicaoDosTurnos;
    private final MetaFaturamentoApi metaFaturamentoApi;
    private final Clock clock;

    @Autowired
    public DetalharTurnoUseCase(TurnoApi turnoApi, ExecucaoComercialApi execucaoApi, EquipeApi equipeApi,
                                ComposicaoDosTurnos composicaoDosTurnos, MetaFaturamentoApi metaFaturamentoApi) {
        this(turnoApi, execucaoApi, equipeApi, composicaoDosTurnos, metaFaturamentoApi,
                Clock.system(CalendarioOperacional.FUSO));
    }

    DetalharTurnoUseCase(TurnoApi turnoApi, ExecucaoComercialApi execucaoApi, EquipeApi equipeApi,
                         ComposicaoDosTurnos composicaoDosTurnos, MetaFaturamentoApi metaFaturamentoApi,
                         Clock clock) {
        this.metaFaturamentoApi = metaFaturamentoApi;
        this.turnoApi = turnoApi;
        this.execucaoApi = execucaoApi;
        this.equipeApi = equipeApi;
        this.composicaoDosTurnos = composicaoDosTurnos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DetalhamentoTurnoResponse executar(UUID idTurno) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        TurnoResponse turno = turnoApi.buscarPorId(idTurno);
        List<ExecucaoComercialResponse> execucoes = execucaoApi.listarPorTurno(idTurno).stream()
                .sorted(Comparator.comparing(ExecucaoComercialResponse::dataLancamento))
                .toList();
        EquipeResponse equipe = equipeApi.buscarPorIdResponse(turno.idEquipe()).orElse(null);
        var composicao = composicaoDosTurnos.para(List.of(turno)).composicao(turno, equipe);

        BigDecimal metaDiaria = equipe != null
                ? metaFaturamentoApi.cronograma(equipe.id()).metaDiaria(CalendarioOperacional.diaDe(turno.dataInicio()))
                : null;
        Indicadores indicadores = indicadores(turno, execucaoApi.listarLancamentosDoTurno(idTurno), metaDiaria, agora);

        return new DetalhamentoTurnoResponse(turno, composicao, execucoes, indicadores, historico(turno));
    }

    static Indicadores indicadores(TurnoResponse turno, List<LancamentoDoTurno> lancamentos,
                                   BigDecimal metaDiaria, OffsetDateTime agora) {
        boolean emAndamento = turno.status() == StatusTurno.ABERTO;
        OffsetDateTime fim = turno.dataFim() != null ? turno.dataFim() : agora;
        long duracao = minutos(turno.dataInicio(), fim);

        List<LancamentoDoTurno> concluidos = lancamentos.stream()
                .filter(l -> l.status() == StatusExecucao.CONCLUIDO)
                .sorted(Comparator.comparing(LancamentoDoTurno::dataLancamento))
                .toList();
        BigDecimal producao = concluidos.stream()
                .map(LancamentoDoTurno::valor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long notas = concluidos.size();

        BigDecimal porHora = duracao > 0
                ? producao.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(duracao), 2, RoundingMode.HALF_UP)
                : null;

        Long atePrimeiro = null;
        Long aposUltimo = null;
        Long maiorIntervalo = null;
        if (!concluidos.isEmpty()) {
            atePrimeiro = minutos(turno.dataInicio(), concluidos.getFirst().dataLancamento());
            aposUltimo = minutos(concluidos.getLast().dataLancamento(), fim);
            List<OffsetDateTime> marcos = new ArrayList<>();
            marcos.add(turno.dataInicio());
            concluidos.forEach(l -> marcos.add(l.dataLancamento()));
            marcos.add(fim);
            long maior = 0;
            for (int i = 1; i < marcos.size(); i++) {
                maior = Math.max(maior, minutos(marcos.get(i - 1), marcos.get(i)));
            }
            maiorIntervalo = maior;
        }

        int semLocalizacao = (int) lancamentos.stream()
                .filter(l -> l.latitude() == null || l.longitude() == null)
                .count();
        OffsetDateTime inicioHoje = CalendarioOperacional.inicioDoDia(CalendarioOperacional.diaDe(agora));

        return new Indicadores(
                agora,
                emAndamento,
                emAndamento && turno.dataInicio().isBefore(inicioHoje),
                duracao,
                notas,
                producao,
                media(producao, notas),
                porHora,
                metaDiaria,
                percentual(producao, metaDiaria),
                atePrimeiro,
                aposUltimo,
                maiorIntervalo,
                semLocalizacao);
    }

    private Historico historico(TurnoResponse turno) {
        ConsultaTurnos consulta = new ConsultaTurnos(Set.of(turno.idEquipe()), StatusTurno.FECHADO,
                turno.dataInicio().minusDays(DIAS_HISTORICO), turno.dataInicio(), null, null);
        List<TurnoResponse> anteriores = turnoApi.consultar(consulta, Pageable.unpaged()).getContent();
        List<ProducaoDoTurno> producoes = anteriores.isEmpty()
                ? List.of()
                : execucaoApi.resumirPorTurnos(anteriores.stream().map(TurnoResponse::id).toList());
        return historico(anteriores, producoes);
    }

    static Historico historico(List<TurnoResponse> anteriores, List<ProducaoDoTurno> producoes) {
        int turnos = anteriores.size();
        if (turnos == 0) {
            return new Historico(DIAS_HISTORICO, 0, null, null, null, null);
        }
        long notas = producoes.stream().map(ProducaoDoTurno::notas).filter(Objects::nonNull)
                .mapToLong(Long::longValue).sum();
        BigDecimal producao = producoes.stream().map(ProducaoDoTurno::producao).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long duracaoTotal = anteriores.stream()
                .filter(t -> t.dataFim() != null)
                .mapToLong(t -> minutos(t.dataInicio(), t.dataFim()))
                .sum();
        long comFim = anteriores.stream().filter(t -> t.dataFim() != null).count();

        return new Historico(
                DIAS_HISTORICO,
                turnos,
                BigDecimal.valueOf(notas).divide(BigDecimal.valueOf(turnos), 1, RoundingMode.HALF_UP),
                producao.divide(BigDecimal.valueOf(turnos), 2, RoundingMode.HALF_UP),
                comFim > 0 ? duracaoTotal / comFim : null,
                notas > 0 ? producao.divide(BigDecimal.valueOf(notas), 2, RoundingMode.HALF_UP) : null);
    }

    private static long minutos(OffsetDateTime de, OffsetDateTime ate) {
        return Math.max(0, Duration.between(de, ate).toMinutes());
    }
}
