package com.nexo.app.modules.desconto.application.usecases;

import com.nexo.app.modules.desconto.application.dto.*;
import com.nexo.app.modules.desconto.domain.*;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoDashboardSpecification;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ContextoOrganizacional;
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
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dashboard executivo do domínio de descontos em folha.
 *
 * <p>As métricas são consolidadas em memória a partir dos termos do período — a mesma
 * escolha feita nos dashboards de SST, e adequada a volumes de gestão. O que justifica
 * ela aqui é a natureza das perguntas: quase toda métrica interessante cruza o termo
 * com os itens dele e com a estrutura organizacional do colaborador, e nenhuma
 * agregação nativa resolveria isso sem replicar o cadastro de RH dentro de uma
 * consulta de descontos. Se o volume crescer muito, o caminho é materializar a coorte
 * do período, não espalhar SQL.</p>
 *
 * <p>Três recortes são carregados: o período consultado, a janela anterior equivalente
 * (para toda variação percentual) e o histórico de pendências até o fim do período —
 * este último é o que permite falar de backlog sem mentir, porque uma pendência aberta
 * em janeiro continua aberta em março e precisa aparecer na conta de março.</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardDescontoUseCases {

    private static final int SCALE = 2;
    /**
     * Zero com escala. Um payload que devolve ora {@code 0} ora {@code 0.00} para o
     * mesmo campo obriga o cliente a normalizar antes de formatar — e o caso sem
     * amostra é justamente o mais comum na primeira abertura da tela.
     */
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String[] MESES_ABREVIADOS = {
            "jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};

    /** Acima disto, uma pendência deixa de ser fila e vira esquecimento. */
    private static final long DIAS_PARA_COBRAR_ETAPA = 15;
    private static final long DIAS_PARA_ESCALAR_ETAPA = 30;
    /** Percentual de corte na validação a partir do qual o critério de lançamento é o problema. */
    private static final BigDecimal LIMIAR_GLOSA_PERCENTUAL = BigDecimal.valueOf(20);
    /** Termos no período a partir dos quais um colaborador entra na conversa de reincidência. */
    private static final long DESCONTOS_PARA_REINCIDENCIA = 3;
    private static final int MAX_AMOSTRA_PRIORIDADE = 50;

    private static final Set<StatusDesconto> PENDENTES =
            Set.of(StatusDesconto.SOLICITADO, StatusDesconto.VALIDADO);

    private final DescontoRepository repository;
    private final ColaboradorApi colaboradorApi;
    private final UsuarioApi usuarioApi;

    /**
     * Um termo já emparelhado com a estrutura organizacional de quem foi descontado.
     * Existe para que as dezenas de agrupamentos abaixo não tenham que consultar o mapa
     * de contexto — e para que "colaborador sem cadastro" seja um caso explícito, com
     * {@code contexto} nulo, em vez de um {@code null} que aparece no meio de um stream.
     */
    private record Termo(Desconto desconto, ContextoOrganizacional contexto) {

        UUID idColaborador() {
            return desconto.getColaboradorId();
        }

        StatusDesconto status() {
            return desconto.getStatus();
        }

        boolean pendente() {
            return PENDENTES.contains(desconto.getStatus());
        }

        LocalDate dataOcorrencia() {
            return desconto.getDataOcorrencia().toLocalDate();
        }

        LocalDate dataCriacao() {
            return desconto.getDataCriacao() != null ? desconto.getDataCriacao().toLocalDate() : desconto.getDataOcorrencia().toLocalDate();
        }
    }

    // ===================================================================== //
    //  Ponto de entrada
    // ===================================================================== //

    @Transactional(readOnly = true)
    public DashboardDescontosResponse gerar(DashboardDescontoFilter filter) {
        DashboardDescontoFilter efetivo = filter == null ? DashboardDescontoFilter.vazio() : filter;
        OffsetDateTime inicio = inicioOuPadrao(efetivo);
        OffsetDateTime fim = fimOuPadrao(efetivo);
        validarPeriodo(inicio, fim);

        LocalDate primeiroDia = inicio.toLocalDate();
        LocalDate ultimoDia = fim.toLocalDate();
        long diasPeriodo = Math.max(1, ChronoUnit.DAYS.between(primeiroDia, ultimoDia) + 1);
        GranularidadeDashboard granularidade = efetivo.granularidade() != null
                ? efetivo.granularidade()
                : GranularidadeDashboard.automatica(diasPeriodo);
        PeriodoDescontoDashboard periodo = periodo(inicio, fim, diasPeriodo);

        List<Desconto> brutosAtuais = carregar(efetivo, inicio, fim);
        List<Desconto> brutosAnteriores = carregar(efetivo, periodo.comparacaoInicio(), periodo.comparacaoFim());
        List<Desconto> brutosHistoricos = carregarPendentesAte(efetivo, fim);

        Map<UUID, ContextoOrganizacional> contextos = resolverContextos(
                brutosAtuais, brutosAnteriores, brutosHistoricos);

        List<Termo> atuais = contextualizar(brutosAtuais, contextos, efetivo);
        List<Termo> anteriores = contextualizar(brutosAnteriores, contextos, efetivo);
        List<Termo> pendentesHistoricos = contextualizar(brutosHistoricos, contextos, efetivo).stream()
                .filter(Termo::pendente)
                .toList();

        Set<UUID> comAnexo = idsComAnexo(atuais, pendentesHistoricos);
        OffsetDateTime atualizadoEm = OffsetDateTime.now(DASHBOARD_ZONE);

        ResumoFinanceiroDashboard financeiro = financeiro(atuais, anteriores);
        CicloDescontoDashboard ciclo = ciclo(atuais, pendentesHistoricos, ultimoDia);

        return new DashboardDescontosResponse(
                inicio, fim, granularidade, atualizadoEm, periodo,
                resumo(atuais, anteriores, efetivo),
                financeiro,
                ciclo,
                qualidade(atuais, pendentesHistoricos, comAnexo, atualizadoEm),
                indicadores(atuais, anteriores, financeiro, ciclo, pendentesHistoricos),
                funil(atuais),
                porStatus(atuais),
                serieTemporal(atuais, granularidade, primeiroDia, ultimoDia, Termo::dataOcorrencia),
                serieTemporal(atuais, granularidade, primeiroDia, ultimoDia, Termo::dataCriacao),
                envelhecimento(pendentesHistoricos, ultimoDia),
                ranking(atuais, t -> t.desconto().getMotivo().getId(),
                        t -> t.desconto().getMotivo().getDescricao()),
                porTipoEquipamento(atuais),
                porColaborador(atuais, ultimoDia),
                ranking(atuais, t -> idOuNulo(t, ContextoOrganizacional::idBaseOperacional),
                        t -> nomeOuNulo(t, ContextoOrganizacional::baseOperacional)),
                ranking(atuais, t -> idOuNulo(t, ContextoOrganizacional::idCentroCusto),
                        t -> nomeOuNulo(t, ContextoOrganizacional::centroCusto)),
                ranking(atuais, t -> idOuNulo(t, ContextoOrganizacional::idFuncao),
                        t -> nomeOuNulo(t, ContextoOrganizacional::funcao)),
                porSolicitante(atuais),
                prioridades(atuais, pendentesHistoricos, comAnexo, financeiro, ultimoDia)
        );
    }

    // ===================================================================== //
    //  Carga e contextualização
    // ===================================================================== //

    private List<Desconto> carregar(DashboardDescontoFilter filtro, OffsetDateTime inicio, OffsetDateTime fim) {
        return repository.findAll(DescontoDashboardSpecification.noEscopo(filtro, inicio, fim));
    }

    /**
     * Todo o histórico até o fim do período, para que o backlog inclua o que foi aberto
     * antes do recorte e continua em aberto. O filtro de status do usuário é
     * deliberadamente ignorado aqui: backlog é, por definição, o que não foi resolvido,
     * e respeitar uma seleção de "BAIXADO" esvaziaria a conta em vez de recortá-la.
     */
    private List<Desconto> carregarPendentesAte(DashboardDescontoFilter filtro, OffsetDateTime fim) {
        DashboardDescontoFilter semStatus = new DashboardDescontoFilter(
                null, null, null, null, filtro.idMotivo(), filtro.idTipoEquipamento(), filtro.idColaborador(),
                filtro.idBaseOperacional(), filtro.idCentroCusto(), filtro.idFuncao(),
                filtro.idUsuarioSolicitante(), List.of(PENDENTES.toArray(new StatusDesconto[0])), null);
        return repository.findAll(DescontoDashboardSpecification.noEscopo(semStatus, null, fim));
    }

    @SafeVarargs
    private Map<UUID, ContextoOrganizacional> resolverContextos(List<Desconto>... recortes) {
        Set<UUID> ids = Arrays.stream(recortes)
                .flatMap(List::stream)
                .map(Desconto::getColaboradorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return colaboradorApi.buscarContextoOrganizacional(ids);
    }

    /**
     * Emparelha cada termo com a estrutura de quem foi descontado e aplica, aqui, o
     * recorte por base, centro de custo e função — as três dimensões que não existem na
     * tabela de descontos. Um termo cujo colaborador não foi encontrado no RH fica de
     * fora sempre que alguma dessas dimensões está selecionada: não dá para afirmar que
     * ele pertence ao recorte, e incluí-lo por dúvida inflaria o total escolhido.
     */
    private List<Termo> contextualizar(List<Desconto> descontos,
                                       Map<UUID, ContextoOrganizacional> contextos,
                                       DashboardDescontoFilter filtro) {
        return descontos.stream()
                .map(desconto -> new Termo(desconto, contextos.get(desconto.getColaboradorId())))
                .filter(termo -> noRecorteOrganizacional(termo, filtro))
                .toList();
    }

    private boolean noRecorteOrganizacional(Termo termo, DashboardDescontoFilter filtro) {
        if (!filtro.recortaPorColaborador()) {
            return true;
        }
        if (termo.contexto() == null) {
            return false;
        }
        return SelecaoMultipla.contem(filtro.idBaseOperacional(), termo.contexto().idBaseOperacional())
                && SelecaoMultipla.contem(filtro.idCentroCusto(), termo.contexto().idCentroCusto())
                && SelecaoMultipla.contem(filtro.idFuncao(), termo.contexto().idFuncao());
    }

    @SafeVarargs
    private Set<UUID> idsComAnexo(List<Termo>... recortes) {
        Set<UUID> ids = Arrays.stream(recortes)
                .flatMap(List::stream)
                .map(termo -> termo.desconto().getId())
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Set.of() : Set.copyOf(repository.buscarIdsComAnexoAtivo(ids));
    }

    // ===================================================================== //
    //  Cartões
    // ===================================================================== //

    private ResumoDescontosDashboard resumo(List<Termo> atuais, List<Termo> anteriores,
                                            DashboardDescontoFilter filtro) {
        long total = atuais.size();
        long solicitados = contar(atuais, StatusDesconto.SOLICITADO);
        long validados = contar(atuais, StatusDesconto.VALIDADO);
        long baixados = contar(atuais, StatusDesconto.BAIXADO);
        long rejeitados = contar(atuais, StatusDesconto.REJEITADO);
        long pendentes = solicitados + validados;
        long finalizados = baixados + rejeitados;

        Map<UUID, Long> porColaborador = atuais.stream()
                .collect(Collectors.groupingBy(Termo::idColaborador, Collectors.counting()));
        long ativos = colaboradorApi.contarAtivos(filtro.idBaseOperacional(), filtro.idCentroCusto());

        long itens = atuais.stream().mapToLong(termo -> itensAtivos(termo).count()).sum();
        long parcelas = atuais.stream()
                .mapToLong(termo -> termo.desconto().getQuantidadeParcelas()).sum();

        return new ResumoDescontosDashboard(
                total, anteriores.size(),
                variacaoPercentual(BigDecimal.valueOf(total), BigDecimal.valueOf(anteriores.size())),
                solicitados, validados, baixados, rejeitados,
                pendentes, percent(pendentes, total),
                finalizados,
                percent(baixados, finalizados), percent(rejeitados, finalizados),
                porColaborador.size(), ativos, percent(porColaborador.size(), ativos),
                porColaborador.values().stream().filter(quantidade -> quantidade > 1).count(),
                media(total, porColaborador.size()),
                itens, media(itens, total), media(parcelas, total));
    }

    private ResumoFinanceiroDashboard financeiro(List<Termo> atuais, List<Termo> anteriores) {
        BigDecimal solicitado = somar(atuais, termo -> termo.desconto().getValorSugerido());
        BigDecimal validado = somar(atuais, termo -> termo.desconto().getValorValidado());
        BigDecimal baixado = somar(atuais, termo -> termo.desconto().getValorBaixado());
        BigDecimal baixadoAnterior = somar(anteriores, termo -> termo.desconto().getValorBaixado());

        List<Termo> rejeitados = filtrar(atuais, StatusDesconto.REJEITADO);
        List<Termo> chegaramNaValidacao = atuais.stream()
                .filter(termo -> termo.desconto().getValorValidado() != null).toList();
        List<Termo> baixadosTermos = filtrar(atuais, StatusDesconto.BAIXADO);
        List<Termo> pendentes = atuais.stream().filter(Termo::pendente).toList();

        BigDecimal solicitadoNaValidacao = somar(chegaramNaValidacao, termo -> termo.desconto().getValorSugerido());
        BigDecimal glosa = solicitadoNaValidacao.subtract(validado);
        BigDecimal validadoNosBaixados = somar(baixadosTermos, termo -> termo.desconto().getValorValidado());
        BigDecimal ajuste = validadoNosBaixados.subtract(baixado);

        BigDecimal parcelasBaixadas = BigDecimal.valueOf(baixadosTermos.stream()
                .mapToLong(termo -> termo.desconto().getQuantidadeParcelas()).sum());

        return new ResumoFinanceiroDashboard(
                arredondar(solicitado), arredondar(validado), arredondar(baixado),
                arredondar(baixadoAnterior), variacaoPercentual(baixado, baixadoAnterior),
                arredondar(somar(rejeitados, termo -> termo.desconto().getValorSugerido())),
                arredondar(somar(pendentes, termo -> valorEfetivo(termo.desconto()))),
                arredondar(glosa), percentualDe(glosa, solicitadoNaValidacao),
                arredondar(ajuste), percentualDe(ajuste, validadoNosBaixados),
                percentualDe(baixado, solicitado),
                dividir(solicitado, BigDecimal.valueOf(atuais.size())),
                dividir(baixado, BigDecimal.valueOf(baixadosTermos.size())),
                arredondar(atuais.stream().map(termo -> termo.desconto().getValorSugerido())
                        .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO)),
                dividir(baixado, parcelasBaixadas));
    }

    /**
     * Tempos de cada etapa. As datas de transição são gravadas na própria entidade,
     * então nenhuma delas precisa ser inferida — a única aproximação aceita é usar a
     * data de criação do termo como marco da solicitação, que é o que ela é.
     */
    private CicloDescontoDashboard ciclo(List<Termo> atuais, List<Termo> pendentes, LocalDate referencia) {
        long[] ateValidacao = duracoes(atuais,
                termo -> termo.desconto().getDataValidacao() != null,
                termo -> dias(termo.desconto().getDataCriacao(), termo.desconto().getDataValidacao()));
        long[] ateBaixa = duracoes(atuais,
                termo -> termo.desconto().getDataBaixa() != null && termo.desconto().getDataValidacao() != null,
                termo -> dias(termo.desconto().getDataValidacao(), termo.desconto().getDataBaixa()));
        long[] cicloTotal = duracoes(atuais,
                termo -> termo.desconto().getDataBaixa() != null,
                termo -> dias(termo.desconto().getDataCriacao(), termo.desconto().getDataBaixa()));
        long[] ateRejeicao = duracoes(atuais,
                termo -> termo.desconto().getDataRejeicao() != null,
                termo -> dias(termo.desconto().getDataCriacao(), termo.desconto().getDataRejeicao()));

        long[] idades = pendentes.stream()
                .mapToLong(termo -> idadeNaEtapa(termo, referencia)).sorted().toArray();

        return new CicloDescontoDashboard(
                mediaDe(ateValidacao), percentil(ateValidacao, 0.50), percentil(ateValidacao, 0.90),
                mediaDe(ateBaixa), percentil(ateBaixa, 0.50), percentil(ateBaixa, 0.90),
                mediaDe(cicloTotal), percentil(cicloTotal, 0.50), percentil(cicloTotal, 0.90),
                mediaDe(ateRejeicao),
                mediaDe(idades), idades.length == 0 ? 0 : idades[idades.length - 1],
                ateValidacao.length, ateBaixa.length);
    }

    private QualidadeDadosDescontoDashboard qualidade(List<Termo> atuais, List<Termo> pendentes,
                                                      Set<UUID> comAnexo, OffsetDateTime atualizadoEm) {
        long semAnexo = pendentes.stream()
                .filter(termo -> !comAnexo.contains(termo.desconto().getId())).count();
        long semItens = pendentes.stream().filter(termo -> itensAtivos(termo).findAny().isEmpty()).count();
        long semObservacao = pendentes.stream()
                .filter(termo -> termo.desconto().getObservacoes() == null
                        || termo.desconto().getObservacoes().isBlank()).count();

        long camposEsperados = pendentes.size() * 3L;
        long preenchidos = camposEsperados - semAnexo - semItens - semObservacao;

        OffsetDateTime ultimaCarga = atuais.stream()
                .map(termo -> termo.desconto().getDataUltimaAlteracao() != null
                        ? termo.desconto().getDataUltimaAlteracao() : termo.desconto().getDataCriacao())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(atualizadoEm);

        return new QualidadeDadosDescontoDashboard(
                ultimaCarga, semAnexo, semItens, semObservacao,
                percent(preenchidos, camposEsperados),
                atuais.stream().filter(termo -> termo.contexto() == null).count());
    }

    private Map<String, IndicadorComparativoDesconto> indicadores(
            List<Termo> atuais, List<Termo> anteriores,
            ResumoFinanceiroDashboard financeiro, CicloDescontoDashboard ciclo,
            List<Termo> pendentesHistoricos) {

        long baixadosAnteriores = contar(anteriores, StatusDesconto.BAIXADO);
        long finalizadosAnteriores = baixadosAnteriores + contar(anteriores, StatusDesconto.REJEITADO);
        long baixados = contar(atuais, StatusDesconto.BAIXADO);
        long finalizados = baixados + contar(atuais, StatusDesconto.REJEITADO);

        long[] validacaoAnterior = duracoes(anteriores,
                termo -> termo.desconto().getDataValidacao() != null,
                termo -> dias(termo.desconto().getDataCriacao(), termo.desconto().getDataValidacao()));

        Map<String, IndicadorComparativoDesconto> indicadores = new LinkedHashMap<>();
        indicadores.put("valorBaixado", indicador(
                financeiro.valorBaixado(), financeiro.valorBaixadoPeriodoAnterior(), "SUBIR",
                "Somatório do valor efetivamente descontado em folha nos termos do período"));
        indicadores.put("taxaAprovacao", indicador(
                percent(baixados, finalizados), percent(baixadosAnteriores, finalizadosAnteriores), "NEUTRO",
                "Termos baixados sobre os que já têm desfecho (baixados mais rejeitados)"));
        indicadores.put("valorEmAberto", indicador(
                arredondar(somar(pendentesHistoricos, termo -> valorEfetivo(termo.desconto()))),
                financeiro.valorEmAberto(), "DESCER",
                "Valor corrente de todas as pendências, inclusive as abertas antes do período"));
        indicadores.put("tempoValidacao", indicador(
                ciclo.mediaDiasAteValidacao(), mediaDe(validacaoAnterior), "DESCER",
                "Dias corridos entre a solicitação e a validação, nos termos já validados"));
        return indicadores;
    }

    // ===================================================================== //
    //  Gráficos de fluxo
    // ===================================================================== //

    /**
     * Conversão da coorte. Um termo baixado também foi validado, então ele conta nas
     * duas etapas — é o que faz o funil estreitar da esquerda para a direita em vez de
     * virar uma distribuição por status, que já existe logo abaixo.
     */
    private List<FunilEtapaItem> funil(List<Termo> atuais) {
        long total = atuais.size();
        BigDecimal valorSolicitado = somar(atuais, termo -> termo.desconto().getValorSugerido());

        List<Termo> validados = atuais.stream()
                .filter(termo -> termo.desconto().getValorValidado() != null).toList();
        List<Termo> baixados = filtrar(atuais, StatusDesconto.BAIXADO);
        List<Termo> rejeitados = filtrar(atuais, StatusDesconto.REJEITADO);

        return List.of(
                new FunilEtapaItem("SOLICITADOS", "Solicitados", total, arredondar(valorSolicitado),
                        percent(total, total), null),
                new FunilEtapaItem("VALIDADOS", "Validados", validados.size(),
                        arredondar(somar(validados, termo -> termo.desconto().getValorValidado())),
                        percent(validados.size(), total), percent(validados.size(), total)),
                new FunilEtapaItem("BAIXADOS", "Baixados em folha", baixados.size(),
                        arredondar(somar(baixados, termo -> termo.desconto().getValorBaixado())),
                        percent(baixados.size(), total), percent(baixados.size(), validados.size())),
                new FunilEtapaItem("REJEITADOS", "Rejeitados", rejeitados.size(),
                        arredondar(somar(rejeitados, termo -> termo.desconto().getValorSugerido())),
                        percent(rejeitados.size(), total), percent(rejeitados.size(), total)));
    }

    private List<DistribuicaoStatusItem> porStatus(List<Termo> atuais) {
        long total = atuais.size();
        BigDecimal valorTotal = somar(atuais, termo -> valorEfetivo(termo.desconto()));
        return Arrays.stream(StatusDesconto.values()).map(status -> {
            List<Termo> doStatus = filtrar(atuais, status);
            BigDecimal valor = somar(doStatus, termo -> valorEfetivo(termo.desconto()));
            return new DistribuicaoStatusItem(status, rotulo(status), doStatus.size(),
                    arredondar(valor), percent(doStatus.size(), total), percentualDe(valor, valorTotal));
        }).toList();
    }

    private String rotulo(StatusDesconto status) {
        return switch (status) {
            case SOLICITADO -> "Aguardando validação";
            case VALIDADO -> "Aguardando baixa";
            case BAIXADO -> "Baixado em folha";
            case REJEITADO -> "Rejeitado";
        };
    }

    private List<SerieDescontoItem> serieTemporal(List<Termo> atuais, GranularidadeDashboard granularidade,
                                                  LocalDate primeiroDia, LocalDate ultimoDia,
                                                  Function<Termo, LocalDate> extratorData) {
        Map<LocalDate, List<Termo>> porBalde = atuais.stream()
                .collect(Collectors.groupingBy(termo -> granularidade.inicioDoBalde(extratorData.apply(termo))));

        List<SerieDescontoItem> serie = new ArrayList<>();
        LocalDate balde = granularidade.inicioDoBalde(primeiroDia);
        while (!balde.isAfter(ultimoDia)) {
            List<Termo> doBalde = porBalde.getOrDefault(balde, List.of());
            List<Termo> validados = doBalde.stream()
                    .filter(termo -> termo.desconto().getValorValidado() != null).toList();
            List<Termo> baixados = filtrar(doBalde, StatusDesconto.BAIXADO);
            List<Termo> rejeitados = filtrar(doBalde, StatusDesconto.REJEITADO);
            BigDecimal solicitado = somar(doBalde, termo -> termo.desconto().getValorSugerido());
            BigDecimal valorBaixado = somar(baixados, termo -> termo.desconto().getValorBaixado());

            serie.add(new SerieDescontoItem(
                    chaveBalde(balde, granularidade), rotuloBalde(balde, granularidade), balde,
                    doBalde.size(), arredondar(solicitado),
                    validados.size(), arredondar(somar(validados, termo -> termo.desconto().getValorValidado())),
                    baixados.size(), arredondar(valorBaixado),
                    rejeitados.size(), arredondar(somar(rejeitados, termo -> termo.desconto().getValorSugerido())),
                    doBalde.stream().filter(Termo::pendente).count(),
                    percentualDe(valorBaixado, solicitado)));
            balde = granularidade.proximoBalde(balde);
        }
        return serie;
    }

    private List<FaixaEnvelhecimentoItem> envelhecimento(List<Termo> pendentes, LocalDate referencia) {
        record Faixa(String nome, int min, Integer max) {}
        List<Faixa> faixas = List.of(
                new Faixa("0-7", 0, 7), new Faixa("8-15", 8, 15), new Faixa("16-30", 16, 30),
                new Faixa("31-60", 31, 60), new Faixa(">60", 61, null));

        return faixas.stream().map(faixa -> {
            List<Termo> naFaixa = pendentes.stream().filter(termo -> {
                long idade = idadeNaEtapa(termo, referencia);
                return idade >= faixa.min() && (faixa.max() == null || idade <= faixa.max());
            }).toList();

            Map<StatusDesconto, Long> porStatus = new EnumMap<>(StatusDesconto.class);
            PENDENTES.forEach(status -> porStatus.put(status, 0L));
            naFaixa.forEach(termo -> porStatus.merge(termo.status(), 1L, Long::sum));

            return new FaixaEnvelhecimentoItem(faixa.nome(), faixa.min(), faixa.max(),
                    naFaixa.size(), arredondar(somar(naFaixa, termo -> valorEfetivo(termo.desconto()))),
                    porStatus);
        }).toList();
    }

    // ===================================================================== //
    //  Rankings
    // ===================================================================== //

    /**
     * Ranking genérico das dimensões simples. Termos cuja chave não pôde ser resolvida
     * (colaborador fora do cadastro) ficam de fora do agrupamento em vez de virarem um
     * balde "não informado" — a soma do ranking passa a não bater com o total do
     * período, e é justamente esse descasamento que o cartão de qualidade dos dados
     * denuncia, com o número exato ao lado.
     */
    private List<RankingDescontoItem> ranking(List<Termo> atuais,
                                              Function<Termo, UUID> chave,
                                              Function<Termo, String> nome) {
        long total = atuais.size();
        BigDecimal valorTotal = somar(atuais, termo -> valorEfetivo(termo.desconto()));

        Map<UUID, List<Termo>> grupos = atuais.stream()
                .filter(termo -> chave.apply(termo) != null)
                .collect(Collectors.groupingBy(chave));

        return grupos.entrySet().stream().map(entrada -> {
            List<Termo> doGrupo = entrada.getValue();
            long baixados = contar(doGrupo, StatusDesconto.BAIXADO);
            long rejeitados = contar(doGrupo, StatusDesconto.REJEITADO);
            BigDecimal valorEfetivo = somar(doGrupo, termo -> valorEfetivo(termo.desconto()));
            return new RankingDescontoItem(
                    entrada.getKey(),
                    nome.apply(doGrupo.getFirst()),
                    doGrupo.size(), percent(doGrupo.size(), total),
                    arredondar(somar(doGrupo, termo -> termo.desconto().getValorSugerido())),
                    arredondar(valorEfetivo), percentualDe(valorEfetivo, valorTotal),
                    baixados, rejeitados, doGrupo.stream().filter(Termo::pendente).count(),
                    percent(baixados, baixados + rejeitados),
                    dividir(valorEfetivo, BigDecimal.valueOf(doGrupo.size())));
        }).sorted(porValorDesc()).toList();
    }

    private Comparator<RankingDescontoItem> porValorDesc() {
        return Comparator.comparing(RankingDescontoItem::valorEfetivo).reversed()
                .thenComparing(Comparator.comparingLong(RankingDescontoItem::quantidade).reversed())
                .thenComparing(item -> item.nome() == null ? "" : item.nome());
    }

    /**
     * A unidade aqui é o item, não o termo: um único desconto pode lançar um capacete e
     * três luvas, e é o item que responde qual material está sumindo. Já a aprovação
     * continua sendo contada por termo, porque é sobre o termo inteiro que a validação
     * decide.
     */
    private List<TipoEquipamentoDescontoItem> porTipoEquipamento(List<Termo> atuais) {
        Map<UUID, AcumuladorTipo> porTipo = new LinkedHashMap<>();
        BigDecimal valorTotal = BigDecimal.ZERO;

        for (Termo termo : atuais) {
            // Um termo com três luvas conta três itens, mas um único termo — daí o
            // controle de quais tipos ele já creditou nesta volta.
            Set<UUID> tiposJaCreditados = new HashSet<>();
            for (DescontoItem item : itensAtivos(termo).toList()) {
                TipoEquipamento tipo = item.getTipoEquipamento();
                AcumuladorTipo acumulador = porTipo.computeIfAbsent(tipo.getId(),
                        id -> new AcumuladorTipo(id, tipo.getDescricao()));
                acumulador.adicionarItem(item.getValor());
                valorTotal = valorTotal.add(item.getValor());
                if (tiposJaCreditados.add(tipo.getId())) {
                    acumulador.adicionarTermo(termo.status());
                }
            }
        }

        BigDecimal total = valorTotal;
        return porTipo.values().stream()
                .map(acumulador -> acumulador.resultado(total))
                .sorted(Comparator.comparing(TipoEquipamentoDescontoItem::valorItens).reversed()
                        .thenComparing(item -> item.nome() == null ? "" : item.nome()))
                .toList();
    }

    /** Acumula um tipo de equipamento em uma passada só, sem depender da identidade das entidades. */
    private final class AcumuladorTipo {
        private final UUID id;
        private final String nome;
        private BigDecimal valor = BigDecimal.ZERO;
        private long itens;
        private long descontos;
        private long baixados;
        private long rejeitados;

        private AcumuladorTipo(UUID id, String nome) {
            this.id = id;
            this.nome = nome;
        }

        private void adicionarItem(BigDecimal valorItem) {
            this.valor = this.valor.add(valorItem);
            this.itens++;
        }

        private void adicionarTermo(StatusDesconto status) {
            this.descontos++;
            if (status == StatusDesconto.BAIXADO) {
                this.baixados++;
            } else if (status == StatusDesconto.REJEITADO) {
                this.rejeitados++;
            }
        }

        private TipoEquipamentoDescontoItem resultado(BigDecimal valorTotal) {
            return new TipoEquipamentoDescontoItem(
                    id, nome, itens, descontos,
                    arredondar(valor), percentualDe(valor, valorTotal),
                    dividir(valor, BigDecimal.valueOf(itens)),
                    baixados, rejeitados, percent(baixados, baixados + rejeitados));
        }
    }

    private List<ColaboradorDescontoItem> porColaborador(List<Termo> atuais, LocalDate referencia) {
        return atuais.stream()
                .collect(Collectors.groupingBy(Termo::idColaborador))
                .entrySet().stream()
                .map(entrada -> {
                    List<Termo> doColaborador = entrada.getValue();
                    ContextoOrganizacional contexto = doColaborador.getFirst().contexto();
                    LocalDate ultima = doColaborador.stream()
                            .map(Termo::dataOcorrencia).max(Comparator.naturalOrder()).orElse(referencia);
                    long parcelas = doColaborador.stream()
                            .mapToLong(termo -> termo.desconto().getQuantidadeParcelas()).sum();
                    return new ColaboradorDescontoItem(
                            entrada.getKey(),
                            contexto == null ? null : contexto.nomeCompleto(),
                            contexto == null ? null : contexto.funcao(),
                            contexto == null ? null : contexto.baseOperacional(),
                            doColaborador.size(),
                            arredondar(somar(doColaborador, termo -> termo.desconto().getValorSugerido())),
                            arredondar(somar(doColaborador, termo -> valorEfetivo(termo.desconto()))),
                            contar(doColaborador, StatusDesconto.BAIXADO),
                            contar(doColaborador, StatusDesconto.REJEITADO),
                            doColaborador.stream().filter(Termo::pendente).count(),
                            ultima, Math.max(0, ChronoUnit.DAYS.between(ultima, referencia)),
                            media(parcelas, doColaborador.size()));
                })
                .sorted(Comparator.comparing(ColaboradorDescontoItem::valorEfetivo).reversed()
                        .thenComparing(Comparator.comparingLong(ColaboradorDescontoItem::quantidade).reversed()))
                .toList();
    }

    /**
     * Quem lança os termos. O nome do colaborador vem primeiro e o login é o fallback:
     * usuário de backoffice sem colaborador vinculado existe, e um ranking com célula
     * vazia esconde justamente quem mais lança.
     */
    private List<RankingDescontoItem> porSolicitante(List<Termo> atuais) {
        Set<UUID> ids = atuais.stream()
                .map(termo -> termo.desconto().getUsuarioSolicitanteId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> logins = usuarioApi.buscarLoginsPorIds(ids);
        Map<UUID, String> nomes = ids.stream().collect(HashMap::new,
                (mapa, id) -> mapa.put(id, colaboradorApi.buscarNomeCompletoPorUsuario(id)
                        .orElseGet(() -> logins.get(id))),
                HashMap::putAll);

        return ranking(atuais,
                termo -> termo.desconto().getUsuarioSolicitanteId(),
                termo -> nomes.get(termo.desconto().getUsuarioSolicitanteId()));
    }

    // ===================================================================== //
    //  Prioridades
    // ===================================================================== //

    private List<PrioridadeDescontoItem> prioridades(List<Termo> atuais, List<Termo> pendentes,
                                                     Set<UUID> comAnexo,
                                                     ResumoFinanceiroDashboard financeiro,
                                                     LocalDate referencia) {
        List<PrioridadeDescontoItem> resultado = new ArrayList<>();

        adicionarEtapaParada(resultado, pendentes, StatusDesconto.SOLICITADO, referencia,
                "VALIDACAO_PARADA", "aguardando validação",
                "Revisar e validar ou rejeitar, para o termo parar de envelhecer");
        adicionarEtapaParada(resultado, pendentes, StatusDesconto.VALIDADO, referencia,
                "BAIXA_PARADA", "validados e ainda não baixados",
                "Encaminhar a baixa em folha ou registrar o impedimento");

        List<Termo> semComprovacao = pendentes.stream()
                .filter(termo -> !comAnexo.contains(termo.desconto().getId())).toList();
        if (!semComprovacao.isEmpty()) {
            BigDecimal proporcao = percent(semComprovacao.size(), pendentes.size());
            resultado.add(new PrioridadeDescontoItem(
                    "SEM_COMPROVACAO",
                    proporcao.compareTo(BigDecimal.valueOf(25)) > 0
                            ? SeveridadePrioridade.ALTA : SeveridadePrioridade.MEDIA,
                    semComprovacao.size() + " pendência(s) sem nenhum anexo",
                    "Somam " + arredondar(somar(semComprovacao, termo -> valorEfetivo(termo.desconto())))
                            + " e representam " + proporcao + "% do que está em aberto",
                    "Anexar a comprovação antes de levar o termo à validação",
                    amostra(semComprovacao),
                    Map.of("status", List.of("SOLICITADO", "VALIDADO"), "semAnexo", true)));
        }

        if (financeiro.percentualGlosaValidacao().compareTo(LIMIAR_GLOSA_PERCENTUAL) > 0) {
            resultado.add(new PrioridadeDescontoItem(
                    "GLOSA_ELEVADA", SeveridadePrioridade.MEDIA,
                    financeiro.percentualGlosaValidacao() + "% do valor solicitado foi cortado na validação",
                    arredondar(financeiro.glosaValidacao()) + " deixaram de virar desconto entre a "
                            + "solicitação e a validação",
                    "Revisar o critério de lançamento com quem abre os termos",
                    List.of(),
                    Map.of("status", List.of("VALIDADO", "BAIXADO"))));
        }

        Map<UUID, List<Termo>> porColaborador = atuais.stream()
                .collect(Collectors.groupingBy(Termo::idColaborador));
        List<Map.Entry<UUID, List<Termo>>> reincidentes = porColaborador.entrySet().stream()
                .filter(entrada -> entrada.getValue().size() >= DESCONTOS_PARA_REINCIDENCIA)
                .sorted(Comparator.<Map.Entry<UUID, List<Termo>>>comparingInt(
                        entrada -> entrada.getValue().size()).reversed())
                .toList();
        if (!reincidentes.isEmpty()) {
            resultado.add(new PrioridadeDescontoItem(
                    "REINCIDENCIA", SeveridadePrioridade.MEDIA,
                    reincidentes.size() + " colaborador(es) com " + DESCONTOS_PARA_REINCIDENCIA
                            + " ou mais termos no período",
                    "O mais recorrente acumula " + reincidentes.getFirst().getValue().size()
                            + " termos; juntos somam "
                            + arredondar(somar(reincidentes.stream().flatMap(entrada -> entrada.getValue().stream())
                            .toList(), termo -> valorEfetivo(termo.desconto()))),
                    "Tratar como caso de gestão de pessoas, não apenas de cobrança",
                    reincidentes.stream().limit(MAX_AMOSTRA_PRIORIDADE)
                            .map(entrada -> entrada.getKey().toString()).toList(),
                    Map.of("reincidentes", true)));
        }

        return resultado;
    }

    private void adicionarEtapaParada(List<PrioridadeDescontoItem> destino, List<Termo> pendentes,
                                      StatusDesconto status, LocalDate referencia,
                                      String tipo, String descricaoEtapa, String acao) {
        List<Termo> parados = pendentes.stream()
                .filter(termo -> termo.status() == status)
                .filter(termo -> idadeNaEtapa(termo, referencia) > DIAS_PARA_COBRAR_ETAPA)
                .sorted(Comparator.comparingLong((Termo termo) -> idadeNaEtapa(termo, referencia)).reversed())
                .toList();
        if (parados.isEmpty()) {
            return;
        }
        long criticos = parados.stream()
                .filter(termo -> idadeNaEtapa(termo, referencia) > DIAS_PARA_ESCALAR_ETAPA).count();

        destino.add(new PrioridadeDescontoItem(
                tipo,
                criticos > 0 ? SeveridadePrioridade.CRITICA : SeveridadePrioridade.ALTA,
                parados.size() + " termo(s) há mais de " + DIAS_PARA_COBRAR_ETAPA + " dias " + descricaoEtapa,
                criticos > 0
                        ? criticos + " passaram de " + DIAS_PARA_ESCALAR_ETAPA + " dias; o mais antigo está há "
                        + idadeNaEtapa(parados.getFirst(), referencia) + " dias parado"
                        : "O mais antigo está há " + idadeNaEtapa(parados.getFirst(), referencia) + " dias parado",
                acao,
                amostra(parados),
                Map.of("status", List.of(status.name()), "diasParadoMinimo", DIAS_PARA_COBRAR_ETAPA)));
    }

    private List<String> amostra(List<Termo> termos) {
        return termos.stream().limit(MAX_AMOSTRA_PRIORIDADE)
                .map(termo -> termo.desconto().getId().toString()).toList();
    }

    // ===================================================================== //
    //  Regras de leitura do termo
    // ===================================================================== //

    /**
     * O valor que vale <em>agora</em> para um termo: o que foi baixado, senão o que foi
     * validado, senão o que foi pedido. Somar {@code valorSugerido} de um termo já
     * baixado por valor menor contaria dinheiro que nunca existiu.
     */
    private BigDecimal valorEfetivo(Desconto desconto) {
        if (desconto.getValorBaixado() != null) {
            return desconto.getValorBaixado();
        }
        return desconto.getValorValidado() != null ? desconto.getValorValidado() : desconto.getValorSugerido();
    }

    /**
     * Há quantos dias o termo está na etapa em que se encontra — não desde a abertura.
     * Um termo validado ontem depois de dois meses parado é uma pendência nova na fila
     * da baixa, e cobrar a baixa por 60 dias apontaria o dedo para a pessoa errada.
     */
    private long idadeNaEtapa(Termo termo, LocalDate referencia) {
        Desconto desconto = termo.desconto();
        OffsetDateTime entrada = switch (desconto.getStatus()) {
            case VALIDADO -> desconto.getDataValidacao() != null
                    ? desconto.getDataValidacao() : desconto.getDataCriacao();
            default -> desconto.getDataCriacao();
        };
        LocalDate marco = entrada != null ? entrada.toLocalDate() : termo.dataOcorrencia();
        return Math.max(0, ChronoUnit.DAYS.between(marco, referencia));
    }

    private Stream<DescontoItem> itensAtivos(Termo termo) {
        return termo.desconto().getItens().stream().filter(DescontoItem::isAtivo);
    }

    private long contar(List<Termo> termos, StatusDesconto status) {
        return termos.stream().filter(termo -> termo.status() == status).count();
    }

    private List<Termo> filtrar(List<Termo> termos, StatusDesconto status) {
        return termos.stream().filter(termo -> termo.status() == status).toList();
    }

    private BigDecimal somar(List<Termo> termos, Function<Termo, BigDecimal> valor) {
        return termos.stream().map(valor).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private UUID idOuNulo(Termo termo, Function<ContextoOrganizacional, UUID> extrator) {
        return termo.contexto() == null ? null : extrator.apply(termo.contexto());
    }

    private String nomeOuNulo(Termo termo, Function<ContextoOrganizacional, String> extrator) {
        return termo.contexto() == null ? null : extrator.apply(termo.contexto());
    }

    // ===================================================================== //
    //  Período e aritmética
    // ===================================================================== //

    private void validarPeriodo(OffsetDateTime inicio, OffsetDateTime fim) {
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("O fim do período não pode ser anterior ao início.");
        }
    }

    /**
     * Um mês de calendário fechado é comparado com o mês anterior inteiro; qualquer
     * outro recorte, com a mesma duração deslocada para trás. Sem essa distinção,
     * fevereiro pareceria pior que janeiro só por ter menos dias.
     */
    private PeriodoDescontoDashboard periodo(OffsetDateTime inicio, OffsetDateTime fim, long diasPeriodo) {
        boolean mesCalendarioCompleto = inicio.toLocalTime().equals(LocalTime.MIDNIGHT)
                && inicio.getDayOfMonth() == 1
                && fim.toLocalDate().equals(inicio.toLocalDate().withDayOfMonth(inicio.toLocalDate().lengthOfMonth()))
                && fim.toLocalTime().compareTo(LocalTime.of(23, 59, 59)) >= 0;
        if (mesCalendarioCompleto) {
            return new PeriodoDescontoDashboard(inicio, fim, DASHBOARD_ZONE.getId(),
                    inicio.minusMonths(1), inicio.minusNanos(1), diasPeriodo);
        }
        Duration duracao = Duration.between(inicio, fim);
        OffsetDateTime comparacaoFim = inicio.minusNanos(1);
        return new PeriodoDescontoDashboard(inicio, fim, DASHBOARD_ZONE.getId(),
                comparacaoFim.minus(duracao), comparacaoFim, diasPeriodo);
    }

    private OffsetDateTime inicioOuPadrao(DashboardDescontoFilter filter) {
        if (filter.inicio() != null) {
            return filter.inicio();
        }
        return OffsetDateTime.now(DASHBOARD_ZONE).withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private OffsetDateTime fimOuPadrao(DashboardDescontoFilter filter) {
        return filter.fim() != null ? filter.fim() : OffsetDateTime.now(DASHBOARD_ZONE);
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

    private long[] duracoes(List<Termo> termos, Predicate<Termo> elegivel, ToLongFunction<Termo> dias) {
        return termos.stream().filter(elegivel).mapToLong(dias).map(valor -> Math.max(0, valor))
                .sorted().toArray();
    }

    private long dias(OffsetDateTime de, OffsetDateTime ate) {
        return de == null || ate == null ? 0 : ChronoUnit.DAYS.between(de, ate);
    }

    private BigDecimal mediaDe(long[] valores) {
        return valores.length == 0 ? ZERO : media(Arrays.stream(valores).sum(), valores.length);
    }

    /** Interpolação linear sobre a amostra já ordenada. */
    private BigDecimal percentil(long[] ordenados, double percentil) {
        if (ordenados.length == 0) {
            return ZERO;
        }
        double posicao = percentil * (ordenados.length - 1);
        int inferior = (int) Math.floor(posicao);
        int superior = (int) Math.ceil(posicao);
        double valor = ordenados[inferior] + (ordenados[superior] - ordenados[inferior]) * (posicao - inferior);
        return BigDecimal.valueOf(valor).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private IndicadorComparativoDesconto indicador(BigDecimal valor, BigDecimal anterior,
                                                   String direcao, String definicao) {
        return new IndicadorComparativoDesconto(arredondar(valor), arredondar(anterior),
                variacaoPercentual(valor, anterior), direcao, definicao);
    }

    private BigDecimal variacaoPercentual(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) {
            return ZERO;
        }
        return atual.subtract(anterior).multiply(BigDecimal.valueOf(100))
                .divide(anterior.abs(), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percentualDe(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return ZERO;
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal dividir(BigDecimal soma, BigDecimal quantidade) {
        if (quantidade == null || quantidade.signum() == 0) {
            return ZERO;
        }
        return soma.divide(quantidade, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(long parte, long total) {
        return total == 0 ? ZERO
                : BigDecimal.valueOf(parte).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal media(long soma, long quantidade) {
        return quantidade == 0 ? ZERO
                : BigDecimal.valueOf(soma).divide(BigDecimal.valueOf(quantidade), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal arredondar(BigDecimal valor) {
        return valor == null ? ZERO : valor.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
