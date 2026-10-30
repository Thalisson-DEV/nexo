package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.ConsultaTurnosFilter;
import com.nexo.app.modules.dashboard.application.dto.DetalhamentoTurnoResponse.Composicao;
import com.nexo.app.modules.dashboard.application.dto.TurnoConsultaResponse;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.ConsultaTurnos;
import com.nexo.app.modules.execucao.application.dto.ProducaoDoTurno;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.CronogramaMetaFaturamento;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.MetaFaturamentoApi;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.application.SelecaoMultipla;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCase.media;
import static com.nexo.app.modules.dashboard.domain.CalendarioOperacional.percentual;

/**
 * Consulta paginada de turnos, no mesmo molde da consulta de execuções: filtros por
 * equipe, base, processo, situação e período de abertura, com a produção de cada turno
 * e a composição confirmada na abertura.
 */
@Service
public class ConsultarTurnosUseCase {

    /** Colunas que o banco ordena; as demais são calculadas e não ordenam no servidor. */
    static final Set<String> ORDENAVEIS = Set.of("dataInicio", "dataFim", "status");

    private static final Sort ORDEM_PADRAO = Sort.by(Sort.Direction.DESC, "dataInicio");

    private final EquipeApi equipeApi;
    private final TurnoApi turnoApi;
    private final ExecucaoComercialApi execucaoApi;
    private final ComposicaoDosTurnos composicaoDosTurnos;
    private final MetaFaturamentoApi metaFaturamentoApi;
    private final Clock clock;

    @Autowired
    public ConsultarTurnosUseCase(EquipeApi equipeApi, TurnoApi turnoApi, ExecucaoComercialApi execucaoApi,
                                  ComposicaoDosTurnos composicaoDosTurnos, MetaFaturamentoApi metaFaturamentoApi) {
        this(equipeApi, turnoApi, execucaoApi, composicaoDosTurnos, metaFaturamentoApi,
                Clock.system(CalendarioOperacional.FUSO));
    }

    ConsultarTurnosUseCase(EquipeApi equipeApi, TurnoApi turnoApi, ExecucaoComercialApi execucaoApi,
                           ComposicaoDosTurnos composicaoDosTurnos, MetaFaturamentoApi metaFaturamentoApi,
                           Clock clock) {
        this.metaFaturamentoApi = metaFaturamentoApi;
        this.equipeApi = equipeApi;
        this.turnoApi = turnoApi;
        this.execucaoApi = execucaoApi;
        this.composicaoDosTurnos = composicaoDosTurnos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TurnoConsultaResponse> executar(ConsultaTurnosFilter filter, Pageable pageable) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        LocalDate hoje = CalendarioOperacional.diaDe(agora);
        List<EquipeResponse> equipesAtivas = equipeApi.listarEquipesAtivas();

        boolean apenasHoje = Boolean.TRUE.equals(filter.apenasHoje());
        ConsultaTurnos consulta = new ConsultaTurnos(
                equipesAceitas(filter, equipesAtivas),
                filter.status(),
                filter.aberturaDe() != null ? CalendarioOperacional.inicioDoDia(filter.aberturaDe()) : null,
                filter.aberturaAte() != null ? CalendarioOperacional.inicioDoDia(filter.aberturaAte().plusDays(1)) : null,
                apenasHoje ? CalendarioOperacional.inicioDoDia(hoje) : null,
                apenasHoje ? CalendarioOperacional.inicioDoDia(hoje.plusDays(1)) : null);

        Page<TurnoResponse> pagina = turnoApi.consultar(consulta, ordenacaoSegura(pageable));
        List<TurnoResponse> turnos = pagina.getContent();

        Map<UUID, ProducaoDoTurno> producao = execucaoApi
                .resumirPorTurnos(turnos.stream().map(TurnoResponse::id).toList()).stream()
                .collect(Collectors.toMap(ProducaoDoTurno::idTurno, Function.identity()));
        Equipes equipes = new Equipes(equipesAtivas);
        ComposicaoDosTurnos.Resolvedor resolvedor = composicaoDosTurnos.para(turnos);
        OffsetDateTime inicioHoje = CalendarioOperacional.inicioDoDia(hoje);
        Map<UUID, CronogramaMetaFaturamento> metas = metaFaturamentoApi.cronogramas(turnos.stream()
                .map(TurnoResponse::idEquipe).filter(Objects::nonNull).distinct().toList());

        return PageResponse.fromPage(pagina.map(turno -> linha(turno, equipes.de(turno.idEquipe()),
                metas.getOrDefault(turno.idEquipe(), CronogramaMetaFaturamento.vazio()),
                producao.get(turno.id()), resolvedor, agora, inicioHoje)));
    }

    private TurnoConsultaResponse linha(TurnoResponse turno, EquipeResponse equipe, CronogramaMetaFaturamento metas,
                                        ProducaoDoTurno producao,
                                        ComposicaoDosTurnos.Resolvedor resolvedor, OffsetDateTime agora,
                                        OffsetDateTime inicioHoje) {
        long notas = producao != null && producao.notas() != null ? producao.notas() : 0;
        BigDecimal total = producao != null && producao.producao() != null
                ? producao.producao().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        BigDecimal metaDiaria = equipe != null
                ? metas.metaDiaria(CalendarioOperacional.diaDe(turno.dataInicio()))
                : null;
        Composicao composicao = resolvedor.composicao(turno, equipe);
        boolean aberto = turno.status() == StatusTurno.ABERTO;
        OffsetDateTime fim = turno.dataFim() != null ? turno.dataFim() : agora;

        return new TurnoConsultaResponse(
                turno.id(),
                turno.idEquipe(),
                equipe != null ? equipe.codigo() : null,
                equipe != null && equipe.baseOperacional() != null ? equipe.baseOperacional().nome() : null,
                equipe != null && equipe.processo() != null ? equipe.processo().nome() : null,
                turno.status(),
                turno.dataInicio(),
                turno.dataFim(),
                Math.max(0, Duration.between(turno.dataInicio(), fim).toMinutes()),
                aberto && turno.dataInicio().isBefore(inicioHoje),
                composicao.abertoPor(),
                composicao.placa(),
                turno.idsColaboradores() != null ? turno.idsColaboradores().size() : 0,
                notas,
                total,
                media(total, notas),
                metaDiaria,
                percentual(total, metaDiaria),
                producao != null ? producao.primeiroLancamento() : null,
                producao != null ? producao.ultimoLancamento() : null,
                turno.encerradoAutomaticamente(),
                turno.motivoEncerramento());
    }

    /**
     * Equipes que o filtro aceita: {@code null} quando nenhum filtro de equipe foi
     * informado (todas, inclusive desativadas), vazio quando nenhuma combina.
     */
    static Collection<UUID> equipesAceitas(ConsultaTurnosFilter filter, List<EquipeResponse> equipesAtivas) {
        boolean porRecorte = !filter.idBaseOperacional().isEmpty() || !filter.idProcesso().isEmpty()
                || StringUtils.hasText(filter.buscaGlobal());
        if (!porRecorte) {
            return filter.idEquipe() != null ? Set.of(filter.idEquipe()) : null;
        }
        String busca = StringUtils.hasText(filter.buscaGlobal())
                ? filter.buscaGlobal().trim().toLowerCase(Locale.ROOT) : null;
        return equipesAtivas.stream()
                .filter(e -> filter.idEquipe() == null || filter.idEquipe().equals(e.id()))
                .filter(e -> SelecaoMultipla.contem(filter.idBaseOperacional(),
                        e.baseOperacional() != null ? e.baseOperacional().id() : null))
                .filter(e -> SelecaoMultipla.contem(filter.idProcesso(), e.processo() != null ? e.processo().id() : null))
                .filter(e -> busca == null
                        || (e.codigo() != null && e.codigo().toLowerCase(Locale.ROOT).contains(busca)))
                .map(EquipeResponse::id)
                .collect(Collectors.toSet());
    }

    /** Mantém só ordenações por colunas do banco; sem nenhuma válida, abertura mais recente primeiro. */
    static Pageable ordenacaoSegura(Pageable pageable) {
        Sort sort = Sort.by(pageable.getSort().stream()
                .filter(ordem -> ORDENAVEIS.contains(ordem.getProperty()))
                .toList());
        Sort efetiva = sort.isSorted() ? sort : ORDEM_PADRAO;
        return pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), efetiva)
                : Pageable.unpaged(efetiva);
    }

    /** Equipes ativas indexadas; uma desativada é buscada uma vez, se aparecer num turno antigo. */
    private final class Equipes {
        private final Map<UUID, Optional<EquipeResponse>> porId = new HashMap<>();

        private Equipes(List<EquipeResponse> ativas) {
            ativas.forEach(e -> porId.put(e.id(), Optional.of(e)));
        }

        EquipeResponse de(UUID idEquipe) {
            return porId.computeIfAbsent(idEquipe, equipeApi::buscarPorIdResponse).orElse(null);
        }
    }
}
