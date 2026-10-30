package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.sst.application.dto.*;
import com.nexo.app.modules.sst.domain.*;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaSpecification;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
import com.nexo.app.shared.application.SelecaoMultipla;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gera os dashboards executivos do domínio de SST: um focado em INSPEÇÕES e outro
 * focado em DESVIOS (não-conformidades). As métricas são consolidadas em memória a
 * partir das inspeções do período — adequado para volumes de gestão; caso o volume
 * cresça muito, migrar para agregações nativas (JPQL/SQL).
 */
@Service
@RequiredArgsConstructor
public class DashboardSstUseCases {

    private static final int SCALE = 2;
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String[] MESES_ABREVIADOS = {
            "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};

    private final InspecaoSegurancaRepository repository;
    private final FormularioInspecaoRepository formularioRepository;
    private final TipoInspecaoRepository tipoInspecaoRepository;
    private final ApurarMetasPeriodoUseCase apurarMetas;
    private final ColaboradorApi colaboradorApi;
    private final ProcessoNegocioApi processoApi;
    private final EquipeApi equipeApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final UsuarioApi usuarioApi;

    private record DesvioContexto(InspecaoSeguranca inspecao, DesvioSeguranca desvio) {}

    // ===================================================================== //
    //  Dashboard de INSPEÇÕES
    // ===================================================================== //

    @Transactional(readOnly = true)
    public DashboardInspecoesResponse gerarDashboardInspecoes(DashboardSstFilter filter) {
        DashboardSstFilter efetivo = filtroOuVazio(filter);
        OffsetDateTime inicio = inicioOuPadrao(efetivo);
        OffsetDateTime fim = fimOuPadrao(efetivo);
        validarPeriodo(inicio, fim);

        LocalDate primeiroDia = inicio.toLocalDate();
        LocalDate ultimoDia = fim.toLocalDate();
        long diasPeriodo = Math.max(1, ChronoUnit.DAYS.between(primeiroDia, ultimoDia) + 1);
        GranularidadeDashboard granularidade = efetivo.granularidade() != null
                ? efetivo.granularidade()
                : GranularidadeDashboard.automatica(diasPeriodo);

        PeriodoDashboard periodo = periodo(inicio, fim);
        InspecaoSegurancaFilter escopo = escopoDeInspecoes(efetivo);
        List<InspecaoSeguranca> inspecoes = carregar(escopo, inicio, fim);
        List<InspecaoSeguranca> anteriores = carregar(escopo, periodo.comparacaoInicio(), periodo.comparacaoFim());
        ApuracaoMetaPeriodo apuracao = apurarMetas.apurar(primeiroDia, ultimoDia,
                efetivo.idInspetor(), efetivo.idBaseOperacional(), efetivo.idCentroCusto(), efetivo.idTipoInspecao());

        long total = inspecoes.size();
        long comDesvio = inspecoes.stream().filter(i -> !i.getDesvios().isEmpty()).count();
        long totalDesvios = inspecoes.stream().mapToLong(i -> i.getDesvios().size()).sum();
        long somaColaboradores = inspecoes.stream().mapToLong(i -> i.getColaboradoresInspecionados().size()).sum();
        long colaboradoresDistintos = inspecoes.stream()
                .flatMap(i -> i.getColaboradoresInspecionados().stream())
                .map(ColaboradorInspecionado::getIdColaborador)
                .distinct().count();
        long inspetoresDistintos = inspecoes.stream()
                .map(InspecaoSeguranca::getIdInspetor).distinct().count();

        ResumoInspecoesDashboard resumo = new ResumoInspecoesDashboard(
                total, anteriores.size(),
                variacaoPercentual(BigDecimal.valueOf(total), BigDecimal.valueOf(anteriores.size())),
                comDesvio, total - comDesvio, percent(total - comDesvio, total),
                totalDesvios, media(totalDesvios, total),
                colaboradoresDistintos, media(somaColaboradores, total),
                inspetoresDistintos, media(total, inspetoresDistintos), media(total, diasPeriodo));

        Map<UUID, ProcessoNegocioResponse> processoPorInspetor = new HashMap<>();
        List<InspetorMetaItem> porInspetor = compararInspetoresComMeta(inspecoes, apuracao, total);
        List<ColaboradorInspecionadoItem> porColaborador = detalharColaboradores(inspecoes, ultimoDia);

        return new DashboardInspecoesResponse(
                inicio, fim, granularidade, OffsetDateTime.now(DASHBOARD_ZONE),
                resumo,
                resumoMeta(apuracao, total, porInspetor),
                resumoColaboradores(porColaborador, efetivo, somaColaboradores),
                serieComMeta(inspecoes, apuracao, granularidade, primeiroDia, ultimoDia),
                porInspetor,
                detalharEquipes(inspecoes, total, ultimoDia),
                porColaborador,
                ranking(agruparContagem(
                        inspecoes.stream().filter(i -> i.getIdProcesso() != null).toList(),
                        InspecaoSeguranca::getIdProcesso), total, this::nomeProcesso),
                ranking(agruparContagem(
                        inspecoes.stream().filter(i -> resolverIdProcessoInspetor(i, processoPorInspetor) != null).toList(),
                        i -> resolverIdProcessoInspetor(i, processoPorInspetor)), total, this::nomeProcesso),
                ranking(agruparContagem(
                        inspecoes.stream().filter(i -> i.getIdBaseOperacional() != null).toList(),
                        InspecaoSeguranca::getIdBaseOperacional), total, this::nomeBaseOperacional),
                ranking(agruparContagem(inspecoes, InspecaoSeguranca::getIdFormulario), total, this::nomeFormulario),
                ranking(agruparContagem(inspecoes, InspecaoSeguranca::getIdTipoInspecao), total, this::nomeTipoInspecao)
        );
    }

    /**
     * Indicadores por equipe: além do volume, quanto foi encontrado nela e há quantos
     * dias ela não recebe uma inspeção — o intervalo sem inspeção costuma explicar
     * uma equipe "sem desvios".
     */
    private List<EquipeInspecaoItem> detalharEquipes(List<InspecaoSeguranca> inspecoes, long total,
                                                     LocalDate dataReferencia) {
        return inspecoes.stream()
                .filter(i -> i.getIdEquipe() != null)
                .collect(Collectors.groupingBy(InspecaoSeguranca::getIdEquipe))
                .entrySet().stream()
                .map(entrada -> {
                    List<InspecaoSeguranca> daEquipe = entrada.getValue();
                    long quantidade = daEquipe.size();
                    long desvios = daEquipe.stream().mapToLong(i -> i.getDesvios().size()).sum();
                    long semDesvio = daEquipe.stream().filter(i -> i.getDesvios().isEmpty()).count();
                    long colaboradores = daEquipe.stream()
                            .flatMap(i -> i.getColaboradoresInspecionados().stream())
                            .map(ColaboradorInspecionado::getIdColaborador)
                            .distinct().count();
                    LocalDate ultima = ultimaRealizacao(daEquipe);
                    return new EquipeInspecaoItem(entrada.getKey(), nomeEquipe(entrada.getKey()),
                            quantidade, percent(quantidade, total),
                            desvios, media(desvios, quantidade), percent(semDesvio, quantidade),
                            colaboradores, ultima, ChronoUnit.DAYS.between(ultima, dataReferencia));
                })
                .sorted(Comparator.comparingLong(EquipeInspecaoItem::inspecoes).reversed())
                .toList();
    }

    /**
     * Indicadores por colaborador inspecionado: com que frequência foi alcançado e
     * quantos desvios lhe foram atribuídos no período.
     */
    private List<ColaboradorInspecionadoItem> detalharColaboradores(List<InspecaoSeguranca> inspecoes,
                                                                    LocalDate dataReferencia) {
        Map<UUID, List<InspecaoSeguranca>> porColaborador = new HashMap<>();
        inspecoes.forEach(inspecao -> inspecao.getColaboradoresInspecionados().forEach(vinculo ->
                porColaborador.computeIfAbsent(vinculo.getIdColaborador(), id -> new ArrayList<>()).add(inspecao)));

        Map<UUID, Long> desviosPorColaborador = new HashMap<>();
        inspecoes.stream().flatMap(i -> i.getDesvios().stream())
                .forEach(desvio -> desvio.getColaboradores().forEach(vinculo ->
                        desviosPorColaborador.merge(vinculo.getIdColaborador(), 1L, Long::sum)));

        return porColaborador.entrySet().stream()
                .map(entrada -> {
                    LocalDate ultima = ultimaRealizacao(entrada.getValue());
                    return new ColaboradorInspecionadoItem(
                            entrada.getKey(), nomeColaborador(entrada.getKey()),
                            entrada.getValue().size(),
                            desviosPorColaborador.getOrDefault(entrada.getKey(), 0L),
                            ultima, ChronoUnit.DAYS.between(ultima, dataReferencia));
                })
                .sorted(Comparator.comparingLong(ColaboradorInspecionadoItem::vezesInspecionado).reversed())
                .toList();
    }

    /**
     * Cobertura do efetivo: o denominador é o total de colaboradores ativos, restrito
     * às bases operacionais e aos centros de custo quando eles são filtrados.
     */
    private ResumoColaboradoresDashboard resumoColaboradores(List<ColaboradorInspecionadoItem> colaboradores,
                                                             DashboardSstFilter filter, long somaVinculos) {
        long inspecionados = colaboradores.size();
        long ativos = colaboradorApi.contarAtivos(filter.idBaseOperacional(), filter.idCentroCusto());
        long comDesvio = colaboradores.stream().filter(c -> c.desviosRecebidos() > 0).count();
        long reincidentes = colaboradores.stream().filter(c -> c.desviosRecebidos() > 1).count();

        return new ResumoColaboradoresDashboard(
                inspecionados, ativos, percent(inspecionados, ativos),
                Math.max(0, ativos - inspecionados),
                media(somaVinculos, inspecionados),
                comDesvio, percent(comDesvio, inspecionados), reincidentes);
    }

    private LocalDate ultimaRealizacao(List<InspecaoSeguranca> inspecoes) {
        return inspecoes.stream()
                .map(i -> i.getDataRealizacao().toLocalDate())
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private ResumoMetaDashboard resumoMeta(ApuracaoMetaPeriodo apuracao, long realizado,
                                           List<InspetorMetaItem> porInspetor) {
        if (!apuracao.possuiMeta()) {
            return new ResumoMetaDashboard(false, BigDecimal.ZERO, realizado, BigDecimal.ZERO, BigDecimal.ZERO,
                    0, 0, BigDecimal.ZERO, apuracao.colaboradoresSemAcesso());
        }

        BigDecimal meta = arredondar(apuracao.metaTotal());
        long comMeta = porInspetor.stream().filter(i -> i.meta().signum() > 0).count();
        long naMeta = porInspetor.stream().filter(InspetorMetaItem::atingiuMeta).count();

        return new ResumoMetaDashboard(
                true, meta, realizado,
                percentualDe(BigDecimal.valueOf(realizado), meta),
                arredondar(meta.subtract(BigDecimal.valueOf(realizado)).max(BigDecimal.ZERO)),
                apuracao.colaboradoresComMeta(), naMeta, percent(naMeta, comMeta),
                apuracao.colaboradoresSemAcesso());
    }

    /**
     * Junta os dois lados do comparativo: quem lançou inspeções e quem tinha meta.
     * Inspetores sem meta entram com meta zero; quem tinha meta e não lançou nada
     * aparece com realizado zero, que é justamente o caso a ser cobrado.
     */
    private List<InspetorMetaItem> compararInspetoresComMeta(List<InspecaoSeguranca> inspecoes,
                                                             ApuracaoMetaPeriodo apuracao, long total) {
        Map<UUID, List<InspecaoSeguranca>> porInspetor = inspecoes.stream()
                .collect(Collectors.groupingBy(InspecaoSeguranca::getIdInspetor));

        Set<UUID> inspetores = new LinkedHashSet<>(porInspetor.keySet());
        inspetores.addAll(apuracao.metaPorInspetor().keySet());

        return inspetores.stream()
                .map(id -> {
                    List<InspecaoSeguranca> doInspetor = porInspetor.getOrDefault(id, List.of());
                    long realizado = doInspetor.size();
                    long desvios = doInspetor.stream().mapToLong(i -> i.getDesvios().size()).sum();
                    BigDecimal meta = arredondar(
                            apuracao.metaPorInspetor().getOrDefault(id, BigDecimal.ZERO));
                    boolean temMeta = meta.signum() > 0;
                    return new InspetorMetaItem(id, nomeInspetor(id), realizado, meta,
                            temMeta ? percentualDe(BigDecimal.valueOf(realizado), meta) : null,
                            temMeta && BigDecimal.valueOf(realizado).compareTo(meta) >= 0,
                            desvios, percent(realizado, total));
                })
                .sorted(Comparator.comparingLong(InspetorMetaItem::realizado).reversed()
                        .thenComparing(item -> item.nomeInspetor() == null ? "" : item.nomeInspetor()))
                .toList();
    }

    /**
     * Série de realizado x meta no recorte escolhido, sem buracos: todo balde do
     * período aparece, mesmo zerado.
     */
    private List<SerieMetaItem> serieComMeta(List<InspecaoSeguranca> inspecoes, ApuracaoMetaPeriodo apuracao,
                                             GranularidadeDashboard granularidade,
                                             LocalDate primeiroDia, LocalDate ultimoDia) {
        Map<LocalDate, List<InspecaoSeguranca>> porBalde = inspecoes.stream()
                .collect(Collectors.groupingBy(i ->
                        granularidade.inicioDoBalde(i.getDataRealizacao().toLocalDate())));

        Map<LocalDate, BigDecimal> metaPorBalde = new HashMap<>();
        apuracao.metaPorDia().forEach((dia, valor) ->
                metaPorBalde.merge(granularidade.inicioDoBalde(dia), valor, BigDecimal::add));

        List<SerieMetaItem> serie = new ArrayList<>();
        LocalDate balde = granularidade.inicioDoBalde(primeiroDia);
        while (!balde.isAfter(ultimoDia)) {
            List<InspecaoSeguranca> doBalde = porBalde.getOrDefault(balde, List.of());
            long realizado = doBalde.size();
            BigDecimal meta = arredondar(metaPorBalde.getOrDefault(balde, BigDecimal.ZERO));
            serie.add(new SerieMetaItem(
                    chaveBalde(balde, granularidade), rotuloBalde(balde, granularidade), balde,
                    realizado, meta,
                    meta.signum() > 0 ? percentualDe(BigDecimal.valueOf(realizado), meta) : BigDecimal.ZERO,
                    doBalde.stream().mapToLong(i -> i.getDesvios().size()).sum()));
            balde = granularidade.proximoBalde(balde);
        }
        return serie;
    }

    private String chaveBalde(LocalDate balde, GranularidadeDashboard granularidade) {
        return switch (granularidade) {
            case DIA -> balde.toString();
            case MES -> String.format("%04d-%02d", balde.getYear(), balde.getMonthValue());
            case ANO -> String.valueOf(balde.getYear());
        };
    }

    private String rotuloBalde(LocalDate balde, GranularidadeDashboard granularidade) {
        return switch (granularidade) {
            case DIA -> String.format("%02d/%02d", balde.getDayOfMonth(), balde.getMonthValue());
            case MES -> MESES_ABREVIADOS[balde.getMonthValue() - 1] + "/" + balde.getYear();
            case ANO -> String.valueOf(balde.getYear());
        };
    }

    /** Percentual de atingimento (realizado sobre meta), sem teto — 120% é informação útil. */
    private BigDecimal percentualDe(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal arredondar(BigDecimal valor) {
        return valor.setScale(SCALE, RoundingMode.HALF_UP);
    }

    // ===================================================================== //
    //  Dashboard de DESVIOS
    // ===================================================================== //

    @Transactional(readOnly = true)
    public DashboardDesviosResponse gerarDashboardDesvios(DashboardSstFilter filter) {
        filter = filtroOuVazio(filter);
        OffsetDateTime inicio = inicioOuPadrao(filter);
        OffsetDateTime fim = fimOuPadrao(filter);
        validarPeriodo(inicio, fim);
        LocalDate primeiroDia = inicio.toLocalDate();
        LocalDate ultimoDia = fim.toLocalDate();
        long diasPeriodo = Math.max(1, ChronoUnit.DAYS.between(primeiroDia, ultimoDia) + 1);
        GranularidadeDashboard granularidade = filter.granularidade() != null
                ? filter.granularidade()
                : GranularidadeDashboard.automatica(diasPeriodo);
        PeriodoDashboard periodo = periodo(inicio, fim);
        InspecaoSegurancaFilter escopo = escopoDeInspecoes(filter);
        List<InspecaoSeguranca> inspecoes = carregar(escopo, inicio, fim);
        List<InspecaoSeguranca> anteriores = carregar(escopo, periodo.comparacaoInicio(), periodo.comparacaoFim());
        List<InspecaoSeguranca> historico = carregar(escopo, null, fim);

        List<DesvioContexto> desvios = contextos(inspecoes, filter);

        long total = desvios.size();

        Map<GravidadeDesvio, Long> porGravidade = new EnumMap<>(GravidadeDesvio.class);
        for (GravidadeDesvio g : GravidadeDesvio.values()) porGravidade.put(g, 0L);
        Map<StatusDesvio, Long> porStatus = new EnumMap<>(StatusDesvio.class);
        for (StatusDesvio s : StatusDesvio.values()) porStatus.put(s, 0L);
        desvios.forEach(dc -> {
            porGravidade.merge(dc.desvio().getGravidade(), 1L, Long::sum);
            porStatus.merge(dc.desvio().getStatus(), 1L, Long::sum);
        });

        long criticos = porGravidade.get(GravidadeDesvio.ALTA) + porGravidade.get(GravidadeDesvio.CRITICA);
        long resolvidos = porStatus.get(StatusDesvio.RESOLVIDO);
        long pendentes = total - resolvidos;

        LocalDate hoje = fim.toLocalDate();
        long vencidos = desvios.stream().map(DesvioContexto::desvio)
                .filter(d -> d.getPrazo() != null && d.getPrazo().isBefore(hoje) && d.getStatus() != StatusDesvio.RESOLVIDO)
                .count();
        long semPrazo = desvios.stream().map(DesvioContexto::desvio)
                .filter(d -> d.getStatus() != StatusDesvio.RESOLVIDO && d.getPrazo() == null).count();
        long noPrazo = desvios.stream().map(DesvioContexto::desvio)
                .filter(d -> d.getStatus() != StatusDesvio.RESOLVIDO && d.getPrazo() != null
                        && !d.getPrazo().isBefore(hoje)).count();

        // Atribuições por colaborador (um desvio pode citar vários colaboradores)
        Map<UUID, Long> desviosPorColaborador = new HashMap<>();
        desvios.forEach(dc -> dc.desvio().getColaboradores()
                .forEach(c -> desviosPorColaborador.merge(c.getIdColaborador(), 1L, Long::sum)));
        long atribuicoes = desviosPorColaborador.values().stream().mapToLong(Long::longValue).sum();
        long colaboradoresEnvolvidos = desviosPorColaborador.size();
        long reincidentes = desviosPorColaborador.values().stream().filter(q -> q > 1).count();

        long inspecoesComDesvio = desvios.stream().map(dc -> dc.inspecao()).distinct().count();

        Map<UUID, ProcessoNegocioResponse> processoPorInspetor = new HashMap<>();
        Map<UUID, Long> porPergunta = desvios.stream()
                .collect(Collectors.groupingBy(dc -> dc.desvio().getIdPergunta(), Collectors.counting()));
        Map<UUID, Long> porProcesso = desvios.stream()
                .filter(dc -> dc.inspecao().getIdProcesso() != null)
                .collect(Collectors.groupingBy(dc -> dc.inspecao().getIdProcesso(), Collectors.counting()));
        Map<UUID, Long> porProcessoInspetor = desvios.stream()
                .filter(dc -> resolverIdProcessoInspetor(dc.inspecao(), processoPorInspetor) != null)
                .collect(Collectors.groupingBy(dc -> resolverIdProcessoInspetor(dc.inspecao(), processoPorInspetor), Collectors.counting()));
        Map<UUID, Long> porEquipe = desvios.stream()
                .filter(dc -> dc.inspecao().getIdEquipe() != null)
                .collect(Collectors.groupingBy(dc -> dc.inspecao().getIdEquipe(), Collectors.counting()));
        Map<UUID, Long> porBase = desvios.stream()
                .filter(dc -> dc.inspecao().getIdBaseOperacional() != null)
                .collect(Collectors.groupingBy(dc -> dc.inspecao().getIdBaseOperacional(), Collectors.counting()));
        Map<UUID, Long> porInspetor = desvios.stream()
                .collect(Collectors.groupingBy(dc -> dc.inspecao().getIdInspetor(), Collectors.counting()));

        Map<UUID, String> enunciados = carregarEnunciados(inspecoes);
        List<DesvioContexto> desviosAnteriores = contextos(anteriores, filter);
        List<DesvioContexto> desviosHistoricos = contextos(historico, filter);
        OffsetDateTime atualizadoEm = OffsetDateTime.now(DASHBOARD_ZONE);
        Map<String, IndicadorComparativo> indicadores = indicadoresDesvios(
                desvios, desviosAnteriores, desviosHistoricos,
                fim.toLocalDate(), periodo.comparacaoFim().toLocalDate());
        long[] resolucao = duracoesResolucaoDias(desvios);

        return new DashboardDesviosResponse(
                inicio, fim,
                total, pendentes,
                porStatus.get(StatusDesvio.ABERTO),
                porStatus.get(StatusDesvio.EM_TRATAMENTO),
                resolvidos,
                criticos, vencidos, noPrazo, semPrazo,
                colaboradoresEnvolvidos, reincidentes, inspecoesComDesvio,
                porGravidade, porStatus,
                percent(resolvidos, total),
                percent(criticos, total),
                percent(vencidos, total),
                percent(reincidentes, colaboradoresEnvolvidos),
                media(atribuicoes, colaboradoresEnvolvidos),
                media(total, inspecoes.size()),
                tempoMedioResolucao(desvios),
                ranking(desviosPorColaborador, total, this::nomeColaborador),
                ranking(porPergunta, total, id -> enunciados.getOrDefault(id, null)),
                ranking(porProcesso, total, this::nomeProcesso),
                ranking(porProcessoInspetor, total, this::nomeProcesso),
                ranking(porEquipe, total, this::nomeEquipe),
                ranking(porBase, total, this::nomeBaseOperacional),
                ranking(porInspetor, total, this::nomeInspetor),
                pendenciasAgrupadas(desvios, dc -> dc.inspecao().getIdProcesso(), this::nomeProcesso, fim.toLocalDate()),
                pendenciasAgrupadas(desvios, dc -> resolverIdProcessoInspetor(dc.inspecao(), processoPorInspetor), this::nomeProcesso, fim.toLocalDate()),
                pendenciasAgrupadas(desvios, dc -> dc.inspecao().getIdBaseOperacional(), this::nomeBaseOperacional, fim.toLocalDate()),
                pendenciasAgrupadas(desvios, dc -> dc.inspecao().getIdEquipe(), this::nomeEquipe, fim.toLocalDate()),
                desempenhoPorColaborador(desvios, desviosPorColaborador),
                serieTemporal(desvios.stream()
                        .collect(Collectors.groupingBy(dc -> dc.inspecao().getDataRealizacao().toLocalDate(), Collectors.counting()))),
                periodo, atualizadoEm, qualidadeDados(inspecoes, atualizadoEm), indicadores,
                percentil(resolucao, 0.50), percentil(resolucao, 0.90),
                backlogPorIdade(desviosHistoricos, fim.toLocalDate()),
                volumePorGravidade(desvios, desviosAnteriores, total),
                exposicao(inspecoes, anteriores, InspecaoSeguranca::getIdProcesso, this::nomeProcesso, fim.toLocalDate()),
                exposicao(inspecoes, anteriores, i -> resolverIdProcessoInspetor(i, processoPorInspetor), this::nomeProcesso, fim.toLocalDate()),
                exposicao(inspecoes, anteriores, InspecaoSeguranca::getIdEquipe, this::nomeEquipe, fim.toLocalDate()),
                exposicao(inspecoes, anteriores, InspecaoSeguranca::getIdBaseOperacional, this::nomeBaseOperacional, fim.toLocalDate()),
                itensCriticos(inspecoes, anteriores, enunciados),
                prioridades(desviosHistoricos, fim.toLocalDate()),
                serieDesvios(desviosHistoricos, primeiroDia, ultimoDia),
                granularidade,
                tendenciaDesvios(desvios, granularidade, primeiroDia, ultimoDia)
        );
    }

    private List<ColaboradorDesvioKpi> desempenhoPorColaborador(List<DesvioContexto> desvios, Map<UUID, Long> totaisPorColaborador) {
        return totaisPorColaborador.keySet().stream()
                .map(idColaborador -> {
                    List<DesvioSeguranca> doColaborador = desvios.stream()
                            .map(DesvioContexto::desvio)
                            .filter(d -> d.getColaboradores().stream().anyMatch(c -> c.getIdColaborador().equals(idColaborador)))
                            .toList();
                    long abertos = doColaborador.stream().filter(d -> d.getStatus() == StatusDesvio.ABERTO).count();
                    long emTratamento = doColaborador.stream().filter(d -> d.getStatus() == StatusDesvio.EM_TRATAMENTO).count();
                    long resolvidos = doColaborador.stream().filter(d -> d.getStatus() == StatusDesvio.RESOLVIDO).count();
                    long criticos = doColaborador.stream()
                            .filter(d -> d.getGravidade() == GravidadeDesvio.ALTA || d.getGravidade() == GravidadeDesvio.CRITICA)
                            .count();
                    return new ColaboradorDesvioKpi(idColaborador, nomeColaborador(idColaborador),
                            doColaborador.size(), abertos, emTratamento, resolvidos, criticos);
                })
                .sorted(Comparator.comparingLong(ColaboradorDesvioKpi::totalDesvios).reversed())
                .toList();
    }

    private BigDecimal tempoMedioResolucao(List<DesvioContexto> desvios) {
        var dias = desvios.stream()
                .map(DesvioContexto::desvio)
                .filter(d -> d.getStatus() == StatusDesvio.RESOLVIDO
                        && d.getDataCriacao() != null && dataResolucao(d) != null)
                .mapToLong(d -> ChronoUnit.DAYS.between(d.getDataCriacao(), dataResolucao(d)))
                .toArray();
        if (dias.length == 0) {
            return BigDecimal.ZERO;
        }
        long soma = Arrays.stream(dias).sum();
        return media(soma, dias.length);
    }

    private DashboardSstFilter filtroOuVazio(DashboardSstFilter filter) {
        return filter == null ? DashboardSstFilter.vazio() : filter;
    }

    private void validarPeriodo(OffsetDateTime inicio, OffsetDateTime fim) {
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("O fim do período não pode ser anterior ao início.");
        }
    }

    private PeriodoDashboard periodo(OffsetDateTime inicio, OffsetDateTime fim) {
        boolean mesCalendarioCompleto = inicio.toLocalTime().equals(LocalTime.MIDNIGHT)
                && fim.toLocalDate().equals(inicio.toLocalDate().withDayOfMonth(inicio.toLocalDate().lengthOfMonth()))
                && fim.toLocalTime().compareTo(LocalTime.of(23, 59, 59)) >= 0;
        if (mesCalendarioCompleto) {
            return new PeriodoDashboard(inicio, fim, DASHBOARD_ZONE.getId(),
                    inicio.minusMonths(1), inicio.minusNanos(1));
        }
        Duration duracao = Duration.between(inicio, fim);
        OffsetDateTime comparacaoFim = inicio.minusNanos(1);
        OffsetDateTime comparacaoInicio = comparacaoFim.minus(duracao);
        return new PeriodoDashboard(inicio, fim, DASHBOARD_ZONE.getId(), comparacaoInicio, comparacaoFim);
    }

    private IndicadorComparativo indicador(long valor, long anterior, BigDecimal meta, String definicao) {
        return indicador(BigDecimal.valueOf(valor), BigDecimal.valueOf(anterior), meta, definicao);
    }

    private IndicadorComparativo indicador(BigDecimal valor, BigDecimal anterior, BigDecimal meta, String definicao) {
        BigDecimal variacao = variacaoPercentual(valor, anterior);
        String status = meta == null ? "SEM_META" : valor.compareTo(meta) <= 0 ? "DENTRO_META" : "FORA_META";
        return new IndicadorComparativo(valor, anterior, variacao, meta, status, definicao);
    }

    private BigDecimal variacaoPercentual(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return atual.subtract(anterior).multiply(BigDecimal.valueOf(100))
                .divide(anterior.abs(), SCALE, RoundingMode.HALF_UP);
    }

    private Map<String, IndicadorComparativo> indicadoresDesvios(
            List<DesvioContexto> atual, List<DesvioContexto> anterior, List<DesvioContexto> historico,
            LocalDate dataReferencia, LocalDate dataComparacao) {
        long backlog = historico.stream().filter(dc -> !dataAbertura(dc).isAfter(dataReferencia)
                && pendenteEm(dc.desvio(), dataReferencia)).count();
        long backlogAnterior = historico.stream().filter(dc -> !dataAbertura(dc).isAfter(dataComparacao)
                && pendenteEm(dc.desvio(), dataComparacao)).count();
        long resolvidos = atual.stream().filter(dc -> dc.desvio().getStatus() == StatusDesvio.RESOLVIDO).count();
        long resolvidosAnterior = anterior.stream().filter(dc -> dc.desvio().getStatus() == StatusDesvio.RESOLVIDO).count();
        long criticos = atual.stream().map(DesvioContexto::desvio).filter(this::isCritico).count();
        long criticosAnterior = anterior.stream().map(DesvioContexto::desvio).filter(this::isCritico).count();
        long inspecoesComDesvio = atual.stream().map(DesvioContexto::inspecao).distinct().count();
        long inspecoesComDesvioAnterior = anterior.stream().map(DesvioContexto::inspecao).distinct().count();
        Map<String, IndicadorComparativo> result = new LinkedHashMap<>();
        result.put("totalDesvios", indicador(atual.size(), anterior.size(), null,
                "Todos os desvios apontados nas inspeções da coorte, independentemente do status atual"));
        result.put("desviosCriticos", indicador(criticos, criticosAnterior, null,
                "Desvios de gravidade ALTA ou CRITICA apontados nas inspeções da coorte"));
        result.put("inspecoesComDesvio", indicador(inspecoesComDesvio, inspecoesComDesvioAnterior, null,
                "Inspeções da coorte que apontaram ao menos um desvio"));
        result.put("taxaResolucao", indicador(percent(resolvidos, atual.size()),
                percent(resolvidosAnterior, anterior.size()), null,
                "Desvios atualmente resolvidos / total de desvios da coorte"));
        result.put("backlog", indicador(backlog, backlogAnterior, null,
                "Indicador operacional: desvios ABERTO ou EM_TRATAMENTO no fim do período"));
        result.put("resolvidosNaCoorte", indicador(percent(resolvidos, atual.size()),
                percent(resolvidosAnterior, anterior.size()), null,
                "Indicador legado: desvios resolvidos da coorte / total de desvios da mesma coorte"));
        result.put("vencidos", indicador(historico.stream().filter(dc -> !dataAbertura(dc).isAfter(dataReferencia)
                        && vencidoEm(dc.desvio(), dataReferencia)).count(),
                historico.stream().filter(dc -> !dataAbertura(dc).isAfter(dataComparacao)
                        && vencidoEm(dc.desvio(), dataComparacao)).count(), null,
                "Indicador operacional: pendências com prazo anterior à data de referência"));
        return result;
    }

    private List<DesvioContexto> contextos(List<InspecaoSeguranca> inspecoes, DashboardSstFilter filter) {
        return inspecoes.stream().flatMap(i -> i.getDesvios().stream()
                .filter(d -> filtro(filter, d)).map(d -> new DesvioContexto(i, d))).toList();
    }

    private QualidadeDadosDashboard qualidadeDados(List<InspecaoSeguranca> inspecoes, OffsetDateTime atualizadoEm) {
        List<DesvioSeguranca> desvios = inspecoes.stream().flatMap(i -> i.getDesvios().stream()).toList();
        List<DesvioSeguranca> pendentes = desvios.stream()
                .filter(d -> d.getStatus() != StatusDesvio.RESOLVIDO).toList();
        long semPrazo = pendentes.stream().filter(d -> d.getPrazo() == null).count();
        long semResponsavel = pendentes.stream().filter(d -> d.getColaboradores().isEmpty()).count();
        long camposEsperados = pendentes.size() * 2L;
        long preenchidos = pendentes.stream().mapToLong(d ->
                (d.getPrazo() == null ? 0 : 1) + (d.getColaboradores().isEmpty() ? 0 : 1)).sum();
        OffsetDateTime ultimaCarga = inspecoes.stream()
                .map(i -> i.getDataUltimaAlteracao() != null ? i.getDataUltimaAlteracao() : i.getDataCriacao())
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(atualizadoEm);
        return new QualidadeDadosDashboard(ultimaCarga, semPrazo, semResponsavel,
                percent(preenchidos, camposEsperados));
    }

    private List<ExposicaoNormalizadaItem> exposicao(
            List<InspecaoSeguranca> atual, List<InspecaoSeguranca> anterior,
            Function<InspecaoSeguranca, UUID> chave, Function<UUID, String> nome,
            LocalDate dataReferencia) {
        Map<UUID, List<InspecaoSeguranca>> grupos = atual.stream().filter(i -> chave.apply(i) != null)
                .collect(Collectors.groupingBy(chave));
        Map<UUID, List<InspecaoSeguranca>> gruposAnteriores = anterior.stream().filter(i -> chave.apply(i) != null)
                .collect(Collectors.groupingBy(chave));
        return grupos.entrySet().stream().map(e -> {
            long desvios = e.getValue().stream().mapToLong(i -> i.getDesvios().size()).sum();
            long inspecoesAnteriores = gruposAnteriores.getOrDefault(e.getKey(), List.of()).size();
            long desviosAnteriores = gruposAnteriores.getOrDefault(e.getKey(), List.of()).stream()
                    .mapToLong(i -> i.getDesvios().size()).sum();
            BigDecimal taxa = porCem(desvios, e.getValue().size());
            BigDecimal taxaAnterior = porCem(desviosAnteriores, inspecoesAnteriores);
            long criticos = e.getValue().stream().flatMap(i -> i.getDesvios().stream())
                    .filter(d -> d.getStatus() != StatusDesvio.RESOLVIDO && isCritico(d)).count();
            long vencidos = e.getValue().stream().flatMap(i -> i.getDesvios().stream())
                    .filter(d -> vencidoEm(d, dataReferencia)).count();
            return new ExposicaoNormalizadaItem(e.getKey(), nome.apply(e.getKey()), e.getValue().size(),
                    desvios, taxa, criticos, vencidos, variacaoPercentual(taxa, taxaAnterior));
        }).sorted(Comparator.comparing(ExposicaoNormalizadaItem::desviosPor100Inspecoes).reversed()).toList();
    }

    private BigDecimal porCem(long parte, long exposicao) {
        return percent(parte, exposicao);
    }

    private List<ItemCriticoRecorrente> itensCriticos(
            List<InspecaoSeguranca> atual, List<InspecaoSeguranca> anterior, Map<UUID, String> nomes) {
        Map<UUID, Long> respostas = atual.stream().flatMap(i -> i.getRespostas().stream())
                .collect(Collectors.groupingBy(RespostaInspecao::getIdPergunta, Collectors.counting()));
        Map<UUID, List<DesvioSeguranca>> desvios = atual.stream().flatMap(i -> i.getDesvios().stream())
                .collect(Collectors.groupingBy(DesvioSeguranca::getIdPergunta));
        Map<UUID, Long> respostasAnt = anterior.stream().flatMap(i -> i.getRespostas().stream())
                .collect(Collectors.groupingBy(RespostaInspecao::getIdPergunta, Collectors.counting()));
        Map<UUID, Long> desviosAnt = anterior.stream().flatMap(i -> i.getDesvios().stream())
                .collect(Collectors.groupingBy(DesvioSeguranca::getIdPergunta, Collectors.counting()));
        long totalDesvios = desvios.values().stream().mapToLong(List::size).sum();
        List<Map.Entry<UUID, List<DesvioSeguranca>>> ordenados = desvios.entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID, List<DesvioSeguranca>>>comparingLong(e -> e.getValue().size()).reversed())
                .toList();
        long acumulado = 0;
        List<ItemCriticoRecorrente> result = new ArrayList<>();
        for (var e : ordenados) {
            long reprovacoes = e.getValue().size();
            acumulado += reprovacoes;
            BigDecimal taxa = percent(reprovacoes, respostas.getOrDefault(e.getKey(), 0L));
            BigDecimal taxaAnt = percent(desviosAnt.getOrDefault(e.getKey(), 0L), respostasAnt.getOrDefault(e.getKey(), 0L));
            GravidadeDesvio maior = e.getValue().stream().map(DesvioSeguranca::getGravidade)
                    .max(Comparator.comparingInt(Enum::ordinal)).orElse(GravidadeDesvio.BAIXA);
            result.add(new ItemCriticoRecorrente(e.getKey(), nomes.get(e.getKey()),
                    respostas.getOrDefault(e.getKey(), 0L), reprovacoes, taxa, maior,
                    variacaoPercentual(taxa, taxaAnt), percent(acumulado, totalDesvios)));
        }
        return result;
    }

    private long[] duracoesResolucaoDias(List<DesvioContexto> desvios) {
        return desvios.stream().map(DesvioContexto::desvio)
                .filter(d -> d.getDataCriacao() != null && dataResolucao(d) != null)
                .mapToLong(d -> Math.max(0, ChronoUnit.DAYS.between(d.getDataCriacao(), dataResolucao(d))))
                .sorted().toArray();
    }

    private BigDecimal percentil(long[] valores, double percentil) {
        if (valores.length == 0) return BigDecimal.ZERO;
        double posicao = percentil * (valores.length - 1);
        int inferior = (int) Math.floor(posicao);
        int superior = (int) Math.ceil(posicao);
        double valor = valores[inferior] + (valores[superior] - valores[inferior]) * (posicao - inferior);
        return BigDecimal.valueOf(valor).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private List<FaixaBacklog> backlogPorIdade(List<DesvioContexto> desvios, LocalDate referencia) {
        record Faixa(String nome, int min, Integer max) {}
        List<Faixa> faixas = List.of(new Faixa("0-7", 0, 7), new Faixa("8-15", 8, 15),
                new Faixa("16-30", 16, 30), new Faixa("31-60", 31, 60), new Faixa(">60", 61, null));
        List<DesvioContexto> pendentes = desvios.stream()
                .filter(dc -> pendenteEm(dc.desvio(), referencia)).toList();
        return faixas.stream().map(f -> {
            List<DesvioContexto> itens = pendentes.stream().filter(dc -> {
                long idade = idadeDias(dc, referencia);
                return idade >= f.min() && (f.max() == null || idade <= f.max());
            }).toList();
            Map<GravidadeDesvio, Long> porGravidade = new EnumMap<>(GravidadeDesvio.class);
            for (GravidadeDesvio g : GravidadeDesvio.values()) porGravidade.put(g, 0L);
            itens.forEach(dc -> porGravidade.merge(dc.desvio().getGravidade(), 1L, Long::sum));
            return new FaixaBacklog(f.nome(), f.min(), f.max(), itens.size(), porGravidade);
        }).toList();
    }

    private List<VolumePorGravidade> volumePorGravidade(
            List<DesvioContexto> atual, List<DesvioContexto> anterior, long totalAtual) {
        Map<GravidadeDesvio, Long> porGravidadeAtual = new EnumMap<>(GravidadeDesvio.class);
        Map<GravidadeDesvio, Long> porGravidadeAnterior = new EnumMap<>(GravidadeDesvio.class);
        for (GravidadeDesvio g : GravidadeDesvio.values()) {
            porGravidadeAtual.put(g, 0L);
            porGravidadeAnterior.put(g, 0L);
        }
        atual.forEach(dc -> porGravidadeAtual.merge(dc.desvio().getGravidade(), 1L, Long::sum));
        anterior.forEach(dc -> porGravidadeAnterior.merge(dc.desvio().getGravidade(), 1L, Long::sum));

        return Arrays.stream(GravidadeDesvio.values()).map(g -> {
            long total = porGravidadeAtual.get(g);
            long totalAnt = porGravidadeAnterior.get(g);
            BigDecimal percentual = percent(total, totalAtual);
            BigDecimal variacao = variacaoPercentual(BigDecimal.valueOf(total), BigDecimal.valueOf(totalAnt));
            return new VolumePorGravidade(g, total, percentual, totalAnt, variacao);
        }).toList();
    }

    private List<PrioridadeDashboard> prioridades(List<DesvioContexto> desvios, LocalDate referencia) {
        List<PrioridadeDashboard> result = new ArrayList<>();
        long criticosVencidos = desvios.stream().filter(dc -> isCritico(dc.desvio())
                && vencidoEm(dc.desvio(), referencia)).count();
        if (criticosVencidos > 0) {
            long semAtualizacao = desvios.stream().filter(dc -> isCritico(dc.desvio())
                    && vencidoEm(dc.desvio(), referencia) && diasSemAtualizacao(dc, referencia) > 15).count();
            result.add(new PrioridadeDashboard("SLA_CRITICO", GravidadeDesvio.CRITICA,
                    criticosVencidos + " desvios críticos vencidos",
                    semAtualizacao + " estão há mais de 15 dias sem atualização",
                    "Revisar tratativa, prazo e responsável hoje",
                    Map.of("status", List.of("ABERTO", "EM_TRATAMENTO"), "vencido", true,
                            "gravidade", List.of("ALTA", "CRITICA"))));
        }
        long semPrazo = desvios.stream().filter(dc -> dc.desvio().getStatus() != StatusDesvio.RESOLVIDO
                && dc.desvio().getPrazo() == null).count();
        if (semPrazo > 0) {
            result.add(new PrioridadeDashboard("SEM_PRAZO", GravidadeDesvio.ALTA,
                    semPrazo + " pendências sem prazo", "Não é possível monitorar o SLA desses registros",
                    "Definir prazo conforme a gravidade", Map.of("semPrazo", true)));
        }
        long riscoEstouro = desvios.stream().filter(dc -> previsaoEstouro(dc, referencia)).count();
        if (riscoEstouro > 0) {
            result.add(new PrioridadeDashboard("RISCO_ESTOURO_SLA", GravidadeDesvio.ALTA,
                    riscoEstouro + " pendências com risco de estouro de SLA",
                    "Prazo em até 3 dias e nenhuma atualização nos últimos 7 dias",
                    "Revisar avanço e impedimentos", Map.of("riscoEstouroSla", true)));
        }
        return result;
    }

    private List<SerieDesviosItem> serieDesvios(
            List<DesvioContexto> desvios, LocalDate inicio, LocalDate fim) {
        return inicio.datesUntil(fim.plusDays(1)).map(data -> {
            long abertos = desvios.stream().filter(dc -> dataAbertura(dc).equals(data)).count();
            long resolvidos = desvios.stream().map(DesvioContexto::desvio).filter(d ->
                    dataResolucao(d) != null && dataResolucao(d).toLocalDate().equals(data)).count();
            long backlog = desvios.stream().filter(dc -> pendenteEm(dc.desvio(), data)
                    && !dataAbertura(dc).isAfter(data)).count();
            long vencidos = desvios.stream().filter(dc -> vencidoEm(dc.desvio(), data)
                    && !dataAbertura(dc).isAfter(data)).count();
            return new SerieDesviosItem(data, abertos, resolvidos, backlog, vencidos);
        }).toList();
    }

    /**
     * Tendência gerencial de ocorrências. Um desvio pertence para sempre ao balde
     * da inspeção que o apontou; seu status atual não desloca nem apaga esse fato.
     */
    private List<TendenciaDesviosItem> tendenciaDesvios(
            List<DesvioContexto> desvios, GranularidadeDashboard granularidade,
            LocalDate primeiroDia, LocalDate ultimoDia) {
        Map<LocalDate, Long> porBalde = desvios.stream().collect(Collectors.groupingBy(
                dc -> granularidade.inicioDoBalde(dc.inspecao().getDataRealizacao().toLocalDate()),
                Collectors.counting()));

        List<TendenciaDesviosItem> serie = new ArrayList<>();
        LocalDate balde = granularidade.inicioDoBalde(primeiroDia);
        while (!balde.isAfter(ultimoDia)) {
            serie.add(new TendenciaDesviosItem(
                    chaveBalde(balde, granularidade), rotuloBalde(balde, granularidade), balde,
                    porBalde.getOrDefault(balde, 0L)));
            balde = granularidade.proximoBalde(balde);
        }
        return serie;
    }

    private boolean isCritico(DesvioSeguranca d) {
        return d.getGravidade() == GravidadeDesvio.ALTA || d.getGravidade() == GravidadeDesvio.CRITICA;
    }

    private OffsetDateTime dataResolucao(DesvioSeguranca d) {
        return d.getDataResolucao() != null ? d.getDataResolucao()
                : d.getStatus() == StatusDesvio.RESOLVIDO ? d.getDataUltimaAlteracao() : null;
    }

    private boolean vencidoEm(DesvioSeguranca d, LocalDate data) {
        return d.getPrazo() != null && d.getPrazo().isBefore(data) && pendenteEm(d, data);
    }

    private boolean pendenteEm(DesvioSeguranca d, LocalDate data) {
        return dataResolucao(d) == null || dataResolucao(d).toLocalDate().isAfter(data);
    }

    private LocalDate dataAbertura(DesvioContexto dc) {
        return dc.desvio().getDataCriacao() != null ? dc.desvio().getDataCriacao().toLocalDate()
                : dc.inspecao().getDataRealizacao().toLocalDate();
    }

    private long idadeDias(DesvioContexto dc, LocalDate referencia) {
        return Math.max(0, ChronoUnit.DAYS.between(dataAbertura(dc), referencia));
    }

    private long diasSemAtualizacao(DesvioContexto dc, LocalDate referencia) {
        OffsetDateTime atualizacao = dc.desvio().getDataUltimaAlteracao() != null
                ? dc.desvio().getDataUltimaAlteracao() : dc.desvio().getDataCriacao();
        LocalDate data = atualizacao != null ? atualizacao.toLocalDate() : dataAbertura(dc);
        return Math.max(0, ChronoUnit.DAYS.between(data, referencia));
    }

    private boolean previsaoEstouro(DesvioContexto dc, LocalDate referencia) {
        DesvioSeguranca d = dc.desvio();
        if (d.getStatus() == StatusDesvio.RESOLVIDO || d.getPrazo() == null || d.getPrazo().isBefore(referencia)) return false;
        long folga = ChronoUnit.DAYS.between(referencia, d.getPrazo());
        return folga <= 3 && diasSemAtualizacao(dc, referencia) >= 7;
    }

    // ===================================================================== //
    //  Helpers
    // ===================================================================== //

    /**
     * Traduz o filtro do dashboard no filtro da listagem de inspeções. O recorte de
     * gravidade/status fica de fora de propósito: no dashboard ele seleciona quais
     * desvios entram nas métricas, não quais inspeções são carregadas.
     */
    private InspecaoSegurancaFilter escopoDeInspecoes(DashboardSstFilter filter) {
        return new InspecaoSegurancaFilter(
                filter.idFormulario(), filter.idTipoInspecao(), filter.idProcesso(), filter.idProcessoInspetor(), filter.idInspetor(), null,
                filter.idEquipe(), filter.idBaseOperacional(), filter.idCentroCusto(),
                null, null, null, null, null, null, null);
    }

    private List<InspecaoSeguranca> carregar(InspecaoSegurancaFilter escopo, OffsetDateTime inicio, OffsetDateTime fim) {
        return repository.findAll(InspecaoSegurancaSpecification.filterBy(escopo.noPeriodo(inicio, fim)));
    }

    private boolean filtro(DashboardSstFilter filter, DesvioSeguranca desvio) {
        return SelecaoMultipla.contem(filter.gravidade(), desvio.getGravidade())
                && SelecaoMultipla.contem(filter.status(), desvio.getStatus());
    }

    private Map<UUID, Long> agruparContagem(List<InspecaoSeguranca> inspecoes, Function<InspecaoSeguranca, UUID> chave) {
        return inspecoes.stream().collect(Collectors.groupingBy(chave, Collectors.counting()));
    }

    private List<RankingItem> ranking(Map<UUID, Long> contagens, long total, Function<UUID, String> nomeResolver) {
        return contagens.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .map(e -> new RankingItem(e.getKey(), nomeResolver.apply(e.getKey()), e.getValue(), percent(e.getValue(), total)))
                .toList();
    }

    private List<SerieTemporalItem> serieTemporal(Map<LocalDate, Long> porDia) {
        return porDia.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new SerieTemporalItem(e.getKey(), e.getValue()))
                .toList();
    }

    private Map<UUID, String> carregarEnunciados(List<InspecaoSeguranca> inspecoes) {
        Set<UUID> formularioIds = inspecoes.stream()
                .map(InspecaoSeguranca::getIdFormulario)
                .collect(Collectors.toSet());
        if (formularioIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> enunciados = new HashMap<>();
        formularioRepository.findAllById(formularioIds).forEach(form ->
                form.getGrupos().forEach(grupo ->
                        grupo.getPerguntas().forEach(p -> enunciados.put(p.getId(), p.getEnunciado()))));
        return enunciados;
    }

    private String nomeInspetor(UUID idInspetor) {
        return colaboradorApi.buscarNomeCompletoPorUsuario(idInspetor)
                .orElseGet(() -> usuarioApi.buscarNomePorId(idInspetor).orElse(null));
    }

    private String nomeColaborador(UUID idColaborador) {
        return colaboradorApi.buscarPorIdResponse(idColaborador)
                .map(com.nexo.app.modules.rh.ColaboradorResponse::nomeCompleto)
                .orElse(null);
    }

    private String nomeProcesso(UUID idProcesso) {
        return processoApi.buscarPorIdResponse(idProcesso)
                .map(com.nexo.app.modules.rh.ProcessoNegocioResponse::nome)
                .orElse(null);
    }

    private UUID resolverIdProcessoInspetor(InspecaoSeguranca inspecao, Map<UUID, ProcessoNegocioResponse> cache) {
        if (inspecao == null) return null;
        ProcessoNegocioResponse proc = resolverProcessoInspetor(inspecao, cache);
        return proc != null ? proc.id() : null;
    }

    private ProcessoNegocioResponse resolverProcessoInspetor(InspecaoSeguranca inspecao, Map<UUID, ProcessoNegocioResponse> cache) {
        if (inspecao == null || inspecao.getIdInspetor() == null) {
            return null;
        }
        return cache.computeIfAbsent(inspecao.getIdInspetor(), idInsp ->
                colaboradorApi.buscarPorUsuarioResponse(idInsp)
                        .or(() -> colaboradorApi.buscarPorIdResponse(idInsp))
                        .map(com.nexo.app.modules.rh.ColaboradorResponse::processo)
                        .orElse(null)
        );
    }

    private String nomeEquipe(UUID idEquipe) {
        return equipeApi.buscarPorIdResponse(idEquipe)
                .map(com.nexo.app.modules.rh.EquipeResponse::codigo)
                .orElse(null);
    }

    private String nomeBaseOperacional(UUID idBaseOperacional) {
        return baseOperacionalApi.buscarPorIdResponse(idBaseOperacional)
                .map(com.nexo.app.modules.rh.BaseOperacionalResponse::nome)
                .orElse(null);
    }

    private List<PendenciaAgrupadaResponse> pendenciasAgrupadas(
            List<DesvioContexto> desvios,
            Function<DesvioContexto, UUID> chave,
            Function<UUID, String> nomeResolver,
            LocalDate dataReferencia) {
        return desvios.stream()
                .filter(dc -> dc.desvio().getStatus() != StatusDesvio.RESOLVIDO)
                .filter(dc -> chave.apply(dc) != null)
                .collect(Collectors.groupingBy(chave))
                .entrySet().stream()
                .map(entry -> {
                    List<DesvioContexto> itens = entry.getValue();
                    long abertos = itens.stream().filter(dc -> dc.desvio().getStatus() == StatusDesvio.ABERTO).count();
                    long emTratamento = itens.stream().filter(dc -> dc.desvio().getStatus() == StatusDesvio.EM_TRATAMENTO).count();
                    long vencidos = itens.stream().filter(dc -> dc.desvio().getPrazo() != null
                            && dc.desvio().getPrazo().isBefore(dataReferencia)).count();
                    long criticos = itens.stream().filter(dc -> dc.desvio().getGravidade() == GravidadeDesvio.ALTA
                            || dc.desvio().getGravidade() == GravidadeDesvio.CRITICA).count();
                    return new PendenciaAgrupadaResponse(entry.getKey(), nomeResolver.apply(entry.getKey()),
                            itens.size(), abertos, emTratamento, vencidos, criticos);
                })
                .sorted(Comparator.comparingLong(PendenciaAgrupadaResponse::totalPendentes).reversed())
                .toList();
    }

    private String nomeFormulario(UUID idFormulario) {
        return formularioRepository.findById(idFormulario)
                .map(FormularioInspecao::getTitulo)
                .orElse(null);
    }

    private String nomeTipoInspecao(UUID idTipoInspecao) {
        if (idTipoInspecao == null) {
            return null;
        }
        return tipoInspecaoRepository.findById(idTipoInspecao)
                .map(TipoInspecao::getNome)
                .orElse(null);
    }

    private OffsetDateTime inicioOuPadrao(DashboardSstFilter filter) {
        if (filter != null && filter.inicio() != null) {
            return filter.inicio();
        }
        return OffsetDateTime.now(DASHBOARD_ZONE).withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private OffsetDateTime fimOuPadrao(DashboardSstFilter filter) {
        if (filter != null && filter.fim() != null) {
            return filter.fim();
        }
        return OffsetDateTime.now(DASHBOARD_ZONE);
    }

    private BigDecimal percent(long parte, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(parte)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal media(long soma, long quantidade) {
        if (quantidade == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(soma).divide(BigDecimal.valueOf(quantidade), SCALE, RoundingMode.HALF_UP);
    }
}
