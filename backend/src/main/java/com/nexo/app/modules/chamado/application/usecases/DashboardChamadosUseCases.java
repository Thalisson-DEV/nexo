package com.nexo.app.modules.chamado.application.usecases;

import com.nexo.app.modules.chamado.application.dto.*;
import com.nexo.app.modules.chamado.domain.*;
import com.nexo.app.modules.chamado.infrastructure.persistence.ChamadoDashboardSpecification;
import com.nexo.app.modules.chamado.infrastructure.persistence.ChamadoRepository;
import com.nexo.app.modules.chamado.infrastructure.persistence.EquipeAtendimentoRepository;
import com.nexo.app.modules.chamado.infrastructure.persistence.TopicoChamadoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard gerencial e operacional do domínio de Chamados.
 * Consolida métricas em memória a partir da coorte consultada, janela comparativa
 * anterior e histórico de pendências acumuladas.
 */
@Service
@RequiredArgsConstructor
public class DashboardChamadosUseCases {

    private static final int SCALE = 2;
    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String[] MESES_ABREVIADOS = {
            "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"
    };

    private final ChamadoRepository repository;
    private final TopicoChamadoRepository topicoRepository;
    private final EquipeAtendimentoRepository equipeRepository;
    private final UsuarioApi usuarioApi;
    private final ColaboradorApi colaboradorApi;

    @Transactional(readOnly = true)
    public DashboardChamadosResponse gerar(DashboardChamadosFilter filter) {
        DashboardChamadosFilter efetivo = filtroOuPadrao(filter);
        OffsetDateTime inicio = inicioOuPadrao(efetivo);
        OffsetDateTime fim = fimOuPadrao(efetivo);
        validarPeriodo(inicio, fim);

        LocalDate primeiroDia = inicio.toLocalDate();
        LocalDate ultimoDia = fim.toLocalDate();
        long diasPeriodo = Math.max(1, ChronoUnit.DAYS.between(primeiroDia, ultimoDia) + 1);

        GranularidadeDashboard granularidade = efetivo.granularidade() != null
                ? efetivo.granularidade()
                : GranularidadeDashboard.automatica(diasPeriodo);

        PeriodoChamadoDashboard periodo = montarPeriodo(inicio, fim, diasPeriodo);

        // 1. Carregamento das três coortes fundamentais
        List<Chamado> chamados = repository.findAll(ChamadoDashboardSpecification.noEscopo(efetivo, inicio, fim));
        List<Chamado> anteriores = repository.findAll(ChamadoDashboardSpecification.noEscopo(efetivo, periodo.comparacaoInicio(), periodo.comparacaoFim()));
        List<Chamado> backlogHistorico = repository.findAll(ChamadoDashboardSpecification.backlogAte(efetivo, fim));
        List<Chamado> backlogAnterior = repository.findAll(ChamadoDashboardSpecification.backlogAte(efetivo, periodo.comparacaoFim()));

        // Mapeamento de reaberturas através de consulta única
        Set<UUID> idsChamados = chamados.stream().map(Chamado::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> idsReabertos = idsChamados.isEmpty() ? Set.of() : repository.findIdsComReabertura(idsChamados);

        long totalChamados = chamados.size();
        long totalAnteriores = anteriores.size();

        // 2. Contagens por status
        Map<StatusChamado, Long> totalPorStatus = new EnumMap<>(StatusChamado.class);
        for (StatusChamado s : StatusChamado.values()) totalPorStatus.put(s, 0L);
        chamados.forEach(c -> totalPorStatus.merge(c.getStatus(), 1L, Long::sum));

        long totalAbertos = totalPorStatus.get(StatusChamado.ABERTO);
        long totalEmAtendimento = totalPorStatus.get(StatusChamado.EM_ATENDIMENTO);
        long totalAguardandoSolicitante = totalPorStatus.get(StatusChamado.AGUARDANDO_SOLICITANTE);
        long totalResolvidos = totalPorStatus.get(StatusChamado.RESOLVIDO);
        long totalFechados = totalPorStatus.get(StatusChamado.FECHADO);
        long totalCancelados = totalPorStatus.get(StatusChamado.CANCELADO);
        long totalPendentes = totalAbertos + totalEmAtendimento + totalAguardandoSolicitante;
        long totalConcluidos = totalResolvidos + totalFechados;
        long totalReabertos = idsReabertos.size();

        // 3. Distribuição por prioridade
        Map<PrioridadeChamado, Long> totalPorPrioridade = new EnumMap<>(PrioridadeChamado.class);
        for (PrioridadeChamado p : PrioridadeChamado.values()) totalPorPrioridade.put(p, 0L);
        chamados.forEach(c -> totalPorPrioridade.merge(c.getPrioridade(), 1L, Long::sum));

        Map<PrioridadeChamado, Long> anterioresPorPrioridade = new EnumMap<>(PrioridadeChamado.class);
        for (PrioridadeChamado p : PrioridadeChamado.values()) anterioresPorPrioridade.put(p, 0L);
        anteriores.forEach(c -> anterioresPorPrioridade.merge(c.getPrioridade(), 1L, Long::sum));

        long totalCriticos = totalPorPrioridade.get(PrioridadeChamado.CRITICA) + totalPorPrioridade.get(PrioridadeChamado.ALTA);
        long criticosAnterior = anterioresPorPrioridade.get(PrioridadeChamado.CRITICA) + anterioresPorPrioridade.get(PrioridadeChamado.ALTA);

        List<VolumePrioridadeChamadoItem> volumePorPrioridade = Arrays.stream(PrioridadeChamado.values())
                .map(p -> {
                    long totalP = totalPorPrioridade.get(p);
                    long antP = anterioresPorPrioridade.get(p);
                    return new VolumePrioridadeChamadoItem(p, totalP, antP,
                            variacaoPercentual(BigDecimal.valueOf(totalP), BigDecimal.valueOf(antP)),
                            percent(totalP, totalChamados));
                }).toList();

        // 4. Métricas de SLA e Tempos de Ciclo
        List<Double> duracoesPrimeiraResposta = new ArrayList<>();
        long primeiraRespostaNoPrazo = 0;
        long primeiraRespostaVencida = 0;

        for (Chamado c : chamados) {
            if (c.getDataPrimeiraResposta() != null) {
                double horas = Math.max(0, Duration.between(c.getDataAbertura(), c.getDataPrimeiraResposta()).toMinutes() / 60.0);
                duracoesPrimeiraResposta.add(horas);
                if (!c.getDataPrimeiraResposta().isAfter(c.getDataLimitePrimeiraResposta())) {
                    primeiraRespostaNoPrazo++;
                } else {
                    primeiraRespostaVencida++;
                }
            } else if (c.getStatus() != StatusChamado.CANCELADO) {
                if (OffsetDateTime.now().isAfter(c.getDataLimitePrimeiraResposta())) {
                    primeiraRespostaVencida++;
                }
            }
        }

        List<Double> duracoesResolucao = new ArrayList<>();
        long resolucaoNoPrazo = 0;
        long resolucaoVencida = 0;

        for (Chamado c : chamados) {
            if (c.getDataResolucao() != null) {
                long segBrutos = Math.max(0, Duration.between(c.getDataAbertura(), c.getDataResolucao()).getSeconds());
                long segEfetivos = Math.max(0, segBrutos - c.getTotalSegundosPausaSla());
                duracoesResolucao.add(segEfetivos / 3600.0);
                if (!c.getDataResolucao().isAfter(c.getDataLimiteResolucao())) {
                    resolucaoNoPrazo++;
                } else {
                    resolucaoVencida++;
                }
            } else if (c.getStatus() != StatusChamado.CANCELADO) {
                if (c.isSlaResolucaoVencido()) {
                    resolucaoVencida++;
                } else {
                    resolucaoNoPrazo++;
                }
            }
        }

        double[] arrPrimeiraResp = duracoesPrimeiraResposta.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double[] arrResolucao = duracoesResolucao.stream().mapToDouble(Double::doubleValue).sorted().toArray();

        BigDecimal tempoMedianoPrimeiraRespostaHoras = percentilDouble(arrPrimeiraResp, 0.50);
        BigDecimal tempoP90PrimeiraRespostaHoras = percentilDouble(arrPrimeiraResp, 0.90);
        BigDecimal tempoMedianoResolucaoHoras = percentilDouble(arrResolucao, 0.50);
        BigDecimal tempoP90ResolucaoHoras = percentilDouble(arrResolucao, 0.90);
        BigDecimal tempoMedioResolucaoHoras = mediaDouble(arrResolucao);

        long totalAvaliadosPrimeiraResp = primeiraRespostaNoPrazo + primeiraRespostaVencida;
        BigDecimal taxaSlaPrimeiraRespostaPercentual = percent(primeiraRespostaNoPrazo, totalAvaliadosPrimeiraResp);

        long totalAvaliadosResolucao = resolucaoNoPrazo + resolucaoVencida;
        BigDecimal taxaSlaResolucaoPercentual = percent(resolucaoNoPrazo, totalAvaliadosResolucao);

        BigDecimal taxaResolucao = percent(totalConcluidos, totalChamados);
        BigDecimal taxaReabertura = percent(totalReabertos, totalConcluidos);

        // 5. Comparativos anteriores para indicadores principais
        long anterioresConcluidos = anteriores.stream()
                .filter(c -> c.getStatus() == StatusChamado.RESOLVIDO || c.getStatus() == StatusChamado.FECHADO)
                .count();
        BigDecimal taxaResolucaoAnterior = percent(anterioresConcluidos, totalAnteriores);

        long anterioresResolucaoNoPrazo = anteriores.stream()
                .filter(c -> (c.getDataResolucao() != null && !c.getDataResolucao().isAfter(c.getDataLimiteResolucao()))
                        || (c.getStatus().isFinalizado() && !c.isSlaResolucaoVencido()))
                .count();
        BigDecimal taxaSlaResolucaoAnterior = percent(anterioresResolucaoNoPrazo, totalAnteriores);

        long anterioresPrimeiraRespNoPrazo = anteriores.stream()
                .filter(c -> c.getDataPrimeiraResposta() != null && !c.getDataPrimeiraResposta().isAfter(c.getDataLimitePrimeiraResposta()))
                .count();
        long anterioresComPrimeiraResp = anteriores.stream().filter(c -> c.getDataPrimeiraResposta() != null).count();
        BigDecimal taxaSlaPrimeiraRespAnterior = percent(anterioresPrimeiraRespNoPrazo, anterioresComPrimeiraResp);

        Map<String, IndicadorComparativoChamado> indicadores = new LinkedHashMap<>();
        indicadores.put("totalChamados", indicador(totalChamados, totalAnteriores, null,
                "Total de solicitações e chamados abertos no período selecionado"));
        indicadores.put("chamadosResolvidos", indicador(totalConcluidos, anterioresConcluidos, null,
                "Chamados do período com atendimento concluído ou resolvido"));
        indicadores.put("taxaResolucao", indicador(taxaResolucao, taxaResolucaoAnterior, BigDecimal.valueOf(85),
                "Percentual de chamados abertos no período que já se encontram resolvidos"));
        indicadores.put("taxaSlaResolucao", indicador(taxaSlaResolucaoPercentual, taxaSlaResolucaoAnterior, BigDecimal.valueOf(90),
                "Percentual de chamados resolvidos dentro do prazo estipulado no catálogo de tópicos"));
        indicadores.put("taxaSlaPrimeiraResposta", indicador(taxaSlaPrimeiraRespostaPercentual, taxaSlaPrimeiraRespAnterior, BigDecimal.valueOf(95),
                "Percentual de chamados que receberam primeira interação dentro do prazo de SLA"));
        indicadores.put("backlogTotal", indicador(backlogHistorico.size(), backlogAnterior.size(), null,
                "Volume total de chamados que permaneciam pendentes até o término do período"));
        indicadores.put("chamadosCriticos", indicador(totalCriticos, criticosAnterior, null,
                "Volume de chamados abertos com prioridade ALTA ou CRÍTICA"));

        // 6. Aging do Backlog
        List<FaixaBacklogChamado> backlogPorIdade = montarAgingBacklog(backlogHistorico, fim);

        // 7. Cache de nomes locais para enriquecimento dimensional
        Map<UUID, String> nomesCache = new HashMap<>();

        // Desempenho por Tópico
        List<TopicoDesempenhoItem> desempenhoPorTopico = montarDesempenhoTopicos(chamados, idsReabertos, totalChamados);

        // Desempenho por Equipe
        List<EquipeDesempenhoItem> desempenhoPorEquipe = montarDesempenhoEquipes(chamados, totalChamados);

        // Desempenho por Atendente
        List<AtendenteDesempenhoItem> desempenhoPorAtendente = montarDesempenhoAtendentes(chamados, nomesCache);

        // Ranking de Solicitantes
        List<RankingSolicitanteItem> rankingSolicitantes = montarRankingSolicitantes(chamados, nomesCache, totalChamados);

        // 8. Prioridades e Visão Gerencial
        List<PrioridadeChamadoDashboard> prioridades = montarPrioridadesGerenciais(
                backlogHistorico, totalCriticos, taxaReabertura, totalReabertos, taxaSlaResolucaoPercentual);

        // 9. Qualidade dos Dados
        QualidadeDadosChamadoDashboard qualidadeDados = montarQualidadeDados(backlogHistorico, OffsetDateTime.now(DASHBOARD_ZONE));

        // 10. Série Temporal Contínua
        List<SerieTemporalChamadoItem> serieTemporal = montarSerieTemporal(chamados, granularidade, primeiroDia, ultimoDia);

        return new DashboardChamadosResponse(
                inicio,
                fim,
                OffsetDateTime.now(DASHBOARD_ZONE),
                periodo,
                qualidadeDados,
                indicadores,
                totalChamados,
                totalAbertos,
                totalEmAtendimento,
                totalAguardandoSolicitante,
                totalResolvidos,
                totalFechados,
                totalCancelados,
                totalPendentes,
                totalCriticos,
                totalReabertos,
                taxaReabertura,
                taxaResolucao,
                taxaSlaPrimeiraRespostaPercentual,
                taxaSlaResolucaoPercentual,
                tempoMedianoPrimeiraRespostaHoras,
                tempoP90PrimeiraRespostaHoras,
                tempoMedianoResolucaoHoras,
                tempoP90ResolucaoHoras,
                tempoMedioResolucaoHoras,
                primeiraRespostaNoPrazo,
                primeiraRespostaVencida,
                resolucaoNoPrazo,
                resolucaoVencida,
                totalPorStatus,
                totalPorPrioridade,
                volumePorPrioridade,
                backlogPorIdade,
                desempenhoPorTopico,
                desempenhoPorEquipe,
                desempenhoPorAtendente,
                rankingSolicitantes,
                prioridades,
                granularidade,
                serieTemporal
        );
    }

    private List<FaixaBacklogChamado> montarAgingBacklog(List<Chamado> backlog, OffsetDateTime referenciaFim) {
        record FaixaDef(String codigo, String rotulo, int minDias, Integer maxDias) {}
        List<FaixaDef> faixas = List.of(
                new FaixaDef("<24h", "Até 24 horas", 0, 0),
                new FaixaDef("1-3d", "1 a 3 dias", 1, 3),
                new FaixaDef("4-7d", "4 a 7 dias", 4, 7),
                new FaixaDef("8-15d", "8 a 15 dias", 8, 15),
                new FaixaDef(">15d", "Mais de 15 dias", 16, null)
        );

        long totalBacklog = backlog.size();
        return faixas.stream().map(f -> {
            List<Chamado> itens = backlog.stream().filter(c -> {
                long dias = Math.max(0, Duration.between(c.getDataAbertura(), referenciaFim).toDays());
                return dias >= f.minDias() && (f.maxDias() == null || dias <= f.maxDias());
            }).toList();

            Map<PrioridadeChamado, Long> porPrioridade = new EnumMap<>(PrioridadeChamado.class);
            for (PrioridadeChamado p : PrioridadeChamado.values()) porPrioridade.put(p, 0L);
            itens.forEach(c -> porPrioridade.merge(c.getPrioridade(), 1L, Long::sum));

            return new FaixaBacklogChamado(
                    f.codigo(),
                    f.rotulo(),
                    itens.size(),
                    percent(itens.size(), totalBacklog),
                    porPrioridade
            );
        }).toList();
    }

    private List<TopicoDesempenhoItem> montarDesempenhoTopicos(
            List<Chamado> chamados, Set<UUID> idsReabertos, long totalGeral) {
        Map<TopicoChamado, List<Chamado>> porTopico = chamados.stream()
                .collect(Collectors.groupingBy(Chamado::getTopico));

        return porTopico.entrySet().stream().map(e -> {
            TopicoChamado t = e.getKey();
            List<Chamado> lista = e.getValue();
            long total = lista.size();
            long resolvidos = lista.stream().filter(c -> c.getStatus() == StatusChamado.RESOLVIDO || c.getStatus() == StatusChamado.FECHADO).count();
            long pendentes = lista.stream().filter(c -> c.getStatus() == StatusChamado.ABERTO || c.getStatus() == StatusChamado.EM_ATENDIMENTO || c.getStatus() == StatusChamado.AGUARDANDO_SOLICITANTE).count();
            long noPrazo = lista.stream().filter(c -> c.getDataResolucao() != null && !c.getDataResolucao().isAfter(c.getDataLimiteResolucao())).count();
            long reabertos = lista.stream().filter(c -> c.getId() != null && idsReabertos.contains(c.getId())).count();

            double[] duracoes = lista.stream()
                    .filter(c -> c.getDataResolucao() != null)
                    .mapToDouble(c -> Math.max(0, Duration.between(c.getDataAbertura(), c.getDataResolucao()).getSeconds() - c.getTotalSegundosPausaSla()) / 3600.0)
                    .sorted().toArray();

            return new TopicoDesempenhoItem(
                    t.getId(),
                    t.getCodigo(),
                    t.getNome(),
                    total,
                    resolvidos,
                    pendentes,
                    percent(noPrazo, resolvidos),
                    percentilDouble(duracoes, 0.50),
                    reabertos,
                    percent(total, totalGeral)
            );
        }).sorted(Comparator.comparingLong(TopicoDesempenhoItem::totalChamados).reversed()).toList();
    }

    private List<EquipeDesempenhoItem> montarDesempenhoEquipes(List<Chamado> chamados, long totalGeral) {
        Map<EquipeAtendimento, List<Chamado>> porEquipe = chamados.stream()
                .collect(Collectors.groupingBy(Chamado::getEquipeAtendimento));

        return porEquipe.entrySet().stream().map(e -> {
            EquipeAtendimento eq = e.getKey();
            List<Chamado> lista = e.getValue();
            long total = lista.size();
            long resolvidos = lista.stream().filter(c -> c.getStatus() == StatusChamado.RESOLVIDO || c.getStatus() == StatusChamado.FECHADO).count();
            long pendentes = lista.stream().filter(c -> c.getStatus() == StatusChamado.ABERTO || c.getStatus() == StatusChamado.EM_ATENDIMENTO || c.getStatus() == StatusChamado.AGUARDANDO_SOLICITANTE).count();

            long respNoPrazo = lista.stream().filter(c -> c.getDataPrimeiraResposta() != null && !c.getDataPrimeiraResposta().isAfter(c.getDataLimitePrimeiraResposta())).count();
            long respTotal = lista.stream().filter(c -> c.getDataPrimeiraResposta() != null).count();

            long resolucaoNoPrazo = lista.stream().filter(c -> c.getDataResolucao() != null && !c.getDataResolucao().isAfter(c.getDataLimiteResolucao())).count();

            double[] duracoes = lista.stream()
                    .filter(c -> c.getDataResolucao() != null)
                    .mapToDouble(c -> Math.max(0, Duration.between(c.getDataAbertura(), c.getDataResolucao()).getSeconds() - c.getTotalSegundosPausaSla()) / 3600.0)
                    .sorted().toArray();

            return new EquipeDesempenhoItem(
                    eq.getId(),
                    eq.getCodigo(),
                    eq.getNome(),
                    total,
                    resolvidos,
                    pendentes,
                    percent(respNoPrazo, respTotal),
                    percent(resolucaoNoPrazo, resolvidos),
                    percentilDouble(duracoes, 0.50),
                    percent(total, totalGeral)
            );
        }).sorted(Comparator.comparingLong(EquipeDesempenhoItem::totalChamados).reversed()).toList();
    }

    private List<AtendenteDesempenhoItem> montarDesempenhoAtendentes(List<Chamado> chamados, Map<UUID, String> nomesCache) {
        Map<UUID, List<Chamado>> porAtendente = chamados.stream()
                .filter(c -> c.getUsuarioResponsavelId() != null)
                .collect(Collectors.groupingBy(Chamado::getUsuarioResponsavelId));

        return porAtendente.entrySet().stream().map(e -> {
            UUID idAtendente = e.getKey();
            List<Chamado> lista = e.getValue();
            long total = lista.size();
            long resolvidos = lista.stream().filter(c -> c.getStatus() == StatusChamado.RESOLVIDO || c.getStatus() == StatusChamado.FECHADO).count();
            long pendentes = lista.stream().filter(c -> c.getStatus() == StatusChamado.ABERTO || c.getStatus() == StatusChamado.EM_ATENDIMENTO || c.getStatus() == StatusChamado.AGUARDANDO_SOLICITANTE).count();
            long noPrazo = lista.stream().filter(c -> c.getDataResolucao() != null && !c.getDataResolucao().isAfter(c.getDataLimiteResolucao())).count();

            double[] duracoes = lista.stream()
                    .filter(c -> c.getDataResolucao() != null)
                    .mapToDouble(c -> Math.max(0, Duration.between(c.getDataAbertura(), c.getDataResolucao()).getSeconds() - c.getTotalSegundosPausaSla()) / 3600.0)
                    .sorted().toArray();

            String nome = resolverNomeUsuario(idAtendente, nomesCache);
            String nomeEquipe = lista.stream().map(c -> c.getEquipeAtendimento().getNome()).findFirst().orElse("-");

            return new AtendenteDesempenhoItem(
                    idAtendente,
                    nome,
                    nomeEquipe,
                    total,
                    resolvidos,
                    pendentes,
                    percent(noPrazo, resolvidos),
                    percentilDouble(duracoes, 0.50)
            );
        }).sorted(Comparator.comparingLong(AtendenteDesempenhoItem::totalChamados).reversed()).toList();
    }

    private List<RankingSolicitanteItem> montarRankingSolicitantes(List<Chamado> chamados, Map<UUID, String> nomesCache, long totalGeral) {
        Map<UUID, Long> porSolicitante = chamados.stream()
                .collect(Collectors.groupingBy(Chamado::getUsuarioSolicitanteId, Collectors.counting()));

        return porSolicitante.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new RankingSolicitanteItem(
                        e.getKey(),
                        resolverNomeUsuario(e.getKey(), nomesCache),
                        e.getValue(),
                        percent(e.getValue(), totalGeral)
                )).toList();
    }

    private List<PrioridadeChamadoDashboard> montarPrioridadesGerenciais(
            List<Chamado> backlog, long totalCriticos, BigDecimal taxaReabertura, long totalReabertos, BigDecimal taxaSlaResolucao) {
        List<PrioridadeChamadoDashboard> prioridades = new ArrayList<>();

        // 1. Chamados críticos sem atendente
        long criticosSemAtribuicao = backlog.stream()
                .filter(c -> (c.getPrioridade() == PrioridadeChamado.CRITICA || c.getPrioridade() == PrioridadeChamado.ALTA)
                        && c.getUsuarioResponsavelId() == null && c.getStatus() == StatusChamado.ABERTO)
                .count();
        if (criticosSemAtribuicao > 0) {
            prioridades.add(new PrioridadeChamadoDashboard(
                    "CRITICOS_SEM_ATRIBUICAO",
                    "CRITICA",
                    "Chamados críticos aguardando atribuição",
                    criticosSemAtribuicao + " chamado(s) de alta/crítica prioridade ainda estão sem atendente responsável.",
                    "Atribua imediatamente os chamados críticos para garantir a contenção do impacto.",
                    Map.of("total", criticosSemAtribuicao)
            ));
        }

        // 2. Estouro de SLA no backlog
        long vencidosNoBacklog = backlog.stream().filter(Chamado::isSlaResolucaoVencido).count();
        if (vencidosNoBacklog > 0) {
            prioridades.add(new PrioridadeChamadoDashboard(
                    "SLA_VENCIDO",
                    "ALTA",
                    "Chamados com SLA de resolução expirado",
                    vencidosNoBacklog + " chamado(s) pendente(s) já ultrapassaram o prazo limite de solução.",
                    "Priorize a resolução das pendências vencidas junto às equipes responsáveis.",
                    Map.of("total", vencidosNoBacklog)
            ));
        }

        // 3. Taxa de Reabertura
        if (taxaReabertura.compareTo(BigDecimal.valueOf(5)) > 0 && totalReabertos > 0) {
            prioridades.add(new PrioridadeChamadoDashboard(
                    "TAXA_REABERTURA",
                    "MEDIA",
                    "Taxa de reabertura acima do tolerado",
                    "Taxa de reabertura em " + taxaReabertura + "% (" + totalReabertos + " casos reabertos).",
                    "Avalie a qualidade das soluções para assegurar resolução definitiva no primeiro contato.",
                    Map.of("taxa", taxaReabertura, "total", totalReabertos)
            ));
        }

        // 4. Backlog com idade superior a 7 dias
        long antigos = backlog.stream()
                .filter(c -> Duration.between(c.getDataAbertura(), OffsetDateTime.now()).toDays() > 7)
                .count();
        if (antigos > 0) {
            prioridades.add(new PrioridadeChamadoDashboard(
                    "BACKLOG_ANTIGO",
                    "MEDIA",
                    "Pendências acumuladas há mais de 7 dias",
                    antigos + " chamado(s) aguardam desfecho há mais de uma semana.",
                    "Realize alinhamento com as equipes para destravar casos estagnados.",
                    Map.of("total", antigos)
            ));
        }

        if (prioridades.isEmpty()) {
            prioridades.add(new PrioridadeChamadoDashboard(
                    "OPERACAO_REGULAR",
                    "NORMAL",
                    "Operação dentro dos parâmetros de qualidade",
                    "Nenhum ponto crítico ou gargalo operacional foi identificado no período.",
                    "Mantenha o acompanhamento periódico das filas e dos SLAs das equipes.",
                    Map.of()
            ));
        }

        return prioridades;
    }

    private QualidadeDadosChamadoDashboard montarQualidadeDados(List<Chamado> backlog, OffsetDateTime agora) {
        long semResponsavel = backlog.stream().filter(c -> c.getUsuarioResponsavelId() == null).count();
        long slaPausado = backlog.stream().filter(Chamado::isSlaPausado).count();
        long estagnados = backlog.stream()
                .filter(c -> Duration.between(c.getDataUltimaInteracao(), agora).toDays() >= 3)
                .count();

        long totalBacklog = backlog.size();
        long comResponsavel = totalBacklog - semResponsavel;
        BigDecimal cobertura = percent(comResponsavel, totalBacklog);

        return new QualidadeDadosChamadoDashboard(
                agora,
                semResponsavel,
                slaPausado,
                estagnados,
                cobertura
        );
    }

    private List<SerieTemporalChamadoItem> montarSerieTemporal(
            List<Chamado> chamados, GranularidadeDashboard granularidade, LocalDate primeiroDia, LocalDate ultimoDia) {
        Map<LocalDate, List<Chamado>> porBalde = chamados.stream()
                .collect(Collectors.groupingBy(c -> granularidade.inicioDoBalde(c.getDataAbertura().toLocalDate())));

        List<SerieTemporalChamadoItem> serie = new ArrayList<>();
        LocalDate balde = granularidade.inicioDoBalde(primeiroDia);

        while (!balde.isAfter(ultimoDia)) {
            List<Chamado> doBalde = porBalde.getOrDefault(balde, List.of());
            long abertos = doBalde.size();
            long resolvidos = doBalde.stream()
                    .filter(c -> c.getStatus() == StatusChamado.RESOLVIDO || c.getStatus() == StatusChamado.FECHADO)
                    .count();
            long vencidos = doBalde.stream().filter(Chamado::isSlaResolucaoVencido).count();

            serie.add(new SerieTemporalChamadoItem(
                    chaveBalde(balde, granularidade),
                    rotuloBalde(balde, granularidade),
                    balde,
                    abertos,
                    resolvidos,
                    vencidos
            ));
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

    private String resolverNomeUsuario(UUID id, Map<UUID, String> nomesCache) {
        if (id == null) return "Não atribuído";
        return nomesCache.computeIfAbsent(id, k ->
                colaboradorApi.buscarNomeCompletoPorUsuario(k)
                        .orElseGet(() -> usuarioApi.buscarNomePorId(k)
                                .orElse("Usuário " + k.toString().substring(0, 8)))
        );
    }

    private IndicadorComparativoChamado indicador(long atual, long anterior, BigDecimal meta, String definicao) {
        return indicador(BigDecimal.valueOf(atual), BigDecimal.valueOf(anterior), meta, definicao);
    }

    private IndicadorComparativoChamado indicador(BigDecimal atual, BigDecimal anterior, BigDecimal meta, String definicao) {
        BigDecimal variacao = variacaoPercentual(atual, anterior);
        String status = meta == null ? "SEM_META" : atual.compareTo(meta) >= 0 ? "DENTRO_META" : "FORA_META";
        return new IndicadorComparativoChamado(atual, anterior, variacao, meta, status, definicao);
    }

    private BigDecimal variacaoPercentual(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
        return atual.subtract(anterior).multiply(CEM)
                .divide(anterior.abs(), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(long parte, long total) {
        if (total == 0) return BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
        return BigDecimal.valueOf(parte).multiply(CEM)
                .divide(BigDecimal.valueOf(total), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percentilDouble(double[] valores, double percentil) {
        if (valores.length == 0) return BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
        double posicao = percentil * (valores.length - 1);
        int inferior = (int) Math.floor(posicao);
        int superior = (int) Math.ceil(posicao);
        double valor = valores[inferior] + (valores[superior] - valores[inferior]) * (posicao - inferior);
        return BigDecimal.valueOf(valor).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal mediaDouble(double[] valores) {
        if (valores.length == 0) return BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
        double soma = Arrays.stream(valores).sum();
        return BigDecimal.valueOf(soma / valores.length).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private DashboardChamadosFilter filtroOuPadrao(DashboardChamadosFilter filter) {
        return filter == null ? DashboardChamadosFilter.vazio() : filter;
    }

    private OffsetDateTime inicioOuPadrao(DashboardChamadosFilter filter) {
        if (filter.inicio() != null) return filter.inicio();
        LocalDate primeiroDiaMes = LocalDate.now(DASHBOARD_ZONE).withDayOfMonth(1);
        return primeiroDiaMes.atStartOfDay(DASHBOARD_ZONE).toOffsetDateTime();
    }

    private OffsetDateTime fimOuPadrao(DashboardChamadosFilter filter) {
        if (filter.fim() != null) return filter.fim();
        return LocalDate.now(DASHBOARD_ZONE).atTime(LocalTime.MAX).atZone(DASHBOARD_ZONE).toOffsetDateTime();
    }

    private void validarPeriodo(OffsetDateTime inicio, OffsetDateTime fim) {
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("O fim do período não pode ser anterior ao início.");
        }
    }

    private PeriodoChamadoDashboard montarPeriodo(OffsetDateTime inicio, OffsetDateTime fim, long diasPeriodo) {
        OffsetDateTime comparacaoFim = inicio.minusNanos(1);
        OffsetDateTime comparacaoInicio = inicio.minusDays(diasPeriodo);
        return new PeriodoChamadoDashboard(
                inicio,
                fim,
                DASHBOARD_ZONE.getId(),
                comparacaoInicio,
                comparacaoFim
        );
    }
}
