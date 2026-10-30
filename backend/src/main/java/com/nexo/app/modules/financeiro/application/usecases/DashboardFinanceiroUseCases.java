package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.CicloSolicitacaoDashboard;
import com.nexo.app.modules.financeiro.application.dto.ConformidadeDocumentalDashboard;
import com.nexo.app.modules.financeiro.application.dto.DashboardFinanceiroFilter;
import com.nexo.app.modules.financeiro.application.dto.DashboardFinanceiroResponse;
import com.nexo.app.modules.financeiro.application.dto.DistribuicaoDocumentoItem;
import com.nexo.app.modules.financeiro.application.dto.DistribuicaoStatusFinanceiroItem;
import com.nexo.app.modules.financeiro.application.dto.FaixaEnvelhecimentoFinanceiroItem;
import com.nexo.app.modules.financeiro.application.dto.FaixaVencimentoItem;
import com.nexo.app.modules.financeiro.application.dto.FunilFinanceiroItem;
import com.nexo.app.modules.financeiro.application.dto.IndicadorComparativoFinanceiro;
import com.nexo.app.modules.financeiro.application.dto.PeriodoFinanceiroDashboard;
import com.nexo.app.modules.financeiro.application.dto.PrioridadeFinanceiraItem;
import com.nexo.app.modules.financeiro.application.dto.QualidadeDadosFinanceiroDashboard;
import com.nexo.app.modules.financeiro.application.dto.RankingFinanceiroItem;
import com.nexo.app.modules.financeiro.application.dto.ResumoSolicitacoesDashboard;
import com.nexo.app.modules.financeiro.application.dto.ResumoValoresDashboard;
import com.nexo.app.modules.financeiro.application.dto.SerieFinanceiroItem;
import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.FonteReferencia;
import com.nexo.app.modules.financeiro.domain.Fornecedor;
import com.nexo.app.modules.financeiro.domain.GranularidadeDashboard;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.SeveridadePrioridade;
import com.nexo.app.modules.financeiro.domain.SituacaoDocumento;
import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.modules.financeiro.domain.StatusFornecedor;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DashboardFinanceiroSpecification;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroSpecification;
import com.nexo.app.modules.financeiro.infrastructure.persistence.EventoSolicitacaoFinanceiraRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.FornecedorRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * Dashboard executivo do módulo financeiro: compras e pagamentos numa leitura só,
 * mais a conformidade documental vinda do repositório.
 *
 * <p>As métricas são consolidadas em memória a partir das solicitações do período
 * — a mesma escolha dos dashboards de SST e de descontos, e adequada a volumes de
 * gestão. O que a justifica aqui é a natureza das perguntas: quase toda métrica
 * interessante cruza a solicitação com o desfecho dela, com as datas de transição
 * e com o que existe (ou não) no repositório documental. Nenhuma agregação nativa
 * resolveria isso sem espalhar SQL por dez consultas que teriam de concordar entre
 * si. Se o volume crescer muito, o caminho é materializar a coorte do período.</p>
 *
 * <p>Três recortes de solicitação são carregados: o período consultado, a janela
 * anterior equivalente (de onde sai toda variação percentual) e o backlog
 * histórico até o fim do período. Este último é o que permite falar de fila sem
 * mentir — um pedido submetido em janeiro e não decidido continua parado em março
 * e precisa aparecer na conta de março.</p>
 *
 * <p>O bloco documental é carregado à parte, porque documento e solicitação têm
 * ciclos independentes desde que o repositório virou agregado próprio: a nota
 * fiscal de uma compra de julho chega em agosto, e as duas leituras precisam
 * conviver na mesma tela sem uma distorcer a janela da outra.</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardFinanceiroUseCases {

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

    /** Acima disto, um pedido na fila de decisão deixa de ser espera e vira esquecimento. */
    private static final long DIAS_PARA_COBRAR_DECISAO = 7;
    private static final long DIAS_PARA_ESCALAR_DECISAO = 15;
    /** Rascunho aberto e nunca submetido: a partir daqui, ou some ou é abandonado. */
    private static final long DIAS_PARA_COBRAR_RASCUNHO = 15;
    /** Janela em que um vencimento deixa de ser futuro e vira agenda da semana. */
    private static final long DIAS_VENCIMENTO_PROXIMO = 7;
    /** Documento parado na fila de conferência além disto trava o fechamento contábil. */
    private static final long DIAS_PARA_COBRAR_CONFERENCIA = 10;
    /** Corte na aprovação a partir do qual o problema é o critério de quem pede, não a decisão. */
    private static final BigDecimal LIMIAR_CORTE_PERCENTUAL = BigDecimal.valueOf(15);
    /** Fatia do valor aprovado num único fornecedor a partir da qual há dependência. */
    private static final BigDecimal LIMIAR_CONCENTRACAO_PERCENTUAL = BigDecimal.valueOf(40);
    /** Abaixo deste volume, concentração é acaso, não dependência. */
    private static final int MINIMO_PARA_CONCENTRACAO = 5;
    private static final int MAX_AMOSTRA_PRIORIDADE = 50;

    private static final Set<StatusSolicitacao> PENDENTES =
            Set.of(StatusSolicitacao.RASCUNHO, StatusSolicitacao.SOLICITADO);

    private static final List<TipoDocumentoFinanceiro> TIPOS_FISCAIS =
            Arrays.stream(TipoDocumentoFinanceiro.values()).filter(TipoDocumentoFinanceiro::isFiscal).toList();

    private final SolicitacaoCompraRepository compraRepository;
    private final SolicitacaoPagamentoRepository pagamentoRepository;
    private final DocumentoFinanceiroRepository documentoRepository;
    private final FornecedorRepository fornecedorRepository;
    private final EventoSolicitacaoFinanceiraRepository eventoRepository;
    private final ResolvedorReferencias resolvedor;
    private final UsuarioApi usuarioApi;

    /**
     * Uma solicitação já etiquetada com o domínio de onde veio. Compra e pagamento
     * compartilham {@link SolicitacaoBase} e {@link NucleoSolicitacao}, mas nenhuma
     * das duas sabe dizer sozinha qual é qual sem um {@code instanceof} espalhado
     * por cinquenta lambdas — o tipo viaja junto para que ele exista uma vez só.
     */
    private record Registro(TipoSolicitacao tipo, SolicitacaoBase solicitacao) {

        UUID id() {
            return solicitacao.getId();
        }

        StatusSolicitacao status() {
            return solicitacao.getStatus();
        }

        NucleoSolicitacao nucleo() {
            return solicitacao.getNucleo();
        }

        boolean pendente() {
            return PENDENTES.contains(solicitacao.getStatus());
        }

        boolean aprovada() {
            return solicitacao.getStatus() == StatusSolicitacao.APROVADO;
        }

        BigDecimal valorSolicitado() {
            return solicitacao.getNucleo().valorOuZero();
        }

        /** Autorizado quando houve decisão; zero quando não houve — nunca o pedido. */
        BigDecimal valorAprovado() {
            return solicitacao.getValorAprovado() == null ? BigDecimal.ZERO : solicitacao.getValorAprovado();
        }

        BigDecimal valorVigente() {
            return solicitacao.valorVigente();
        }

        LocalDate dataAbertura() {
            OffsetDateTime criacao = solicitacao.getDataCriacao();
            return criacao == null ? LocalDate.now(DASHBOARD_ZONE) : criacao.toLocalDate();
        }
    }

    /**
     * O que o repositório documental tem a dizer sobre o recorte, carregado de uma
     * vez. Os conjuntos por origem vêm de consultas agregadas: a tela precisa saber
     * <em>se</em> e <em>quantos</em> documentos cada solicitação tem, e trazer as
     * entidades só para contá-las carregaria milhares de linhas que ninguém lê.
     */
    private record ContextoDocumental(
            Map<UUID, Long> porOrigem,
            Set<UUID> comDocumentoFiscal,
            Set<UUID> comComprovantePagamento,
            List<DocumentoFinanceiro> recebidos,
            long recebidosNoPeriodoAnterior,
            List<DocumentoFinanceiro> emAberto,
            Set<String> hashesDuplicados) {

        long documentosDe(UUID solicitacaoId) {
            return porOrigem.getOrDefault(solicitacaoId, 0L);
        }

        boolean temDocumento(UUID solicitacaoId) {
            return documentosDe(solicitacaoId) > 0;
        }
    }

    // ===================================================================== //
    //  Ponto de entrada
    // ===================================================================== //

    @Transactional(readOnly = true)
    public DashboardFinanceiroResponse gerar(DashboardFinanceiroFilter filter) {
        DashboardFinanceiroFilter efetivo = filter == null ? DashboardFinanceiroFilter.vazio() : filter;
        OffsetDateTime inicio = inicioOuPadrao(efetivo);
        OffsetDateTime fim = fimOuPadrao(efetivo);
        validarPeriodo(inicio, fim);

        LocalDate primeiroDia = inicio.toLocalDate();
        LocalDate ultimoDia = fim.toLocalDate();
        long diasPeriodo = Math.max(1, ChronoUnit.DAYS.between(primeiroDia, ultimoDia) + 1);
        GranularidadeDashboard granularidade = efetivo.granularidade() != null
                ? efetivo.granularidade()
                : GranularidadeDashboard.automatica(diasPeriodo);
        PeriodoFinanceiroDashboard periodo = periodo(inicio, fim, diasPeriodo);

        List<Registro> atuais = carregar(efetivo, inicio, fim);
        List<Registro> anteriores = carregar(efetivo, periodo.comparacaoInicio(), periodo.comparacaoFim());
        List<Registro> backlog = carregarPendentesAte(efetivo, fim);

        ContextoDocumental documental = carregarDocumental(atuais, inicio, fim, periodo);
        Map<UUID, Fornecedor> fornecedores = carregarFornecedores(atuais);
        Descritor descritor = new Descritor();
        OffsetDateTime atualizadoEm = OffsetDateTime.now(DASHBOARD_ZONE);
        LocalDate hoje = atualizadoEm.toLocalDate();

        List<Registro> aLiquidar = aLiquidar(atuais, documental);
        List<FaixaVencimentoItem> vencimentos = vencimentos(aLiquidar, hoje);

        ResumoValoresDashboard valores = valores(atuais, anteriores, aLiquidar, hoje);
        CicloSolicitacaoDashboard ciclo = ciclo(atuais, backlog, ultimoDia);
        ConformidadeDocumentalDashboard conformidade = conformidade(atuais, documental, hoje);

        return new DashboardFinanceiroResponse(
                inicio, fim, granularidade, atualizadoEm, periodo,
                resumo(atuais, anteriores),
                valores,
                ciclo,
                conformidade,
                qualidade(atuais, documental, fornecedores, atualizadoEm),
                indicadores(atuais, anteriores, valores, ciclo, conformidade, backlog),
                funil(atuais),
                porStatus(atuais),
                serieTemporal(atuais, granularidade, primeiroDia, ultimoDia),
                envelhecimento(backlog, ultimoDia),
                vencimentos,
                ranking(atuais, documental, registro -> chaveUuid(registro.nucleo().getBaseId()),
                        registro -> descritor.descrever(FonteReferencia.BASE_OPERACIONAL,
                                registro.nucleo().getBaseId())),
                ranking(atuais, documental, registro -> chaveUuid(registro.nucleo().getProcessoId()),
                        registro -> descritor.descrever(FonteReferencia.PROCESSO,
                                registro.nucleo().getProcessoId())),
                ranking(atuais, documental, registro -> chaveUuid(registro.nucleo().getObraId()),
                        registro -> descritor.descrever(FonteReferencia.OBRA, registro.nucleo().getObraId())),
                ranking(atuais, documental, registro -> chaveUuid(registro.nucleo().getFornecedorId()),
                        registro -> nomeDoFornecedor(registro, fornecedores)),
                ranking(atuais, documental, registro -> nomeDoEnum(registro.nucleo().getTipoDespesa()),
                        registro -> registro.nucleo().getTipoDespesa().getRotulo()),
                ranking(atuais, documental, registro -> chaveUuid(registro.solicitacao().getFormulario().getId()),
                        registro -> registro.solicitacao().getFormulario().getTitulo()),
                porSolicitante(atuais, documental),
                documentosPorTipo(documental),
                documentosPorSituacao(documental),
                prioridades(atuais, backlog, documental, fornecedores, valores, aLiquidar, hoje, ultimoDia));
    }

    // ===================================================================== //
    //  Carga
    // ===================================================================== //

    /**
     * Compras e pagamentos entram no mesmo balde, cada um com sua etiqueta. Somá-los
     * é a leitura correta para quem olha a despesa da empresa: os dois consomem o
     * mesmo orçamento e disputam a mesma fila de aprovação. Onde a distinção importa,
     * ela reaparece quebrada dentro do bloco.
     */
    private List<Registro> carregar(DashboardFinanceiroFilter filtro, OffsetDateTime inicio, OffsetDateTime fim) {
        List<Registro> registros = new ArrayList<>();
        if (filtro.incluiCompras()) {
            compraRepository.findAll(DashboardFinanceiroSpecification.noEscopo(filtro, inicio, fim))
                    .forEach(compra -> registros.add(new Registro(TipoSolicitacao.COMPRA, compra)));
        }
        if (filtro.incluiPagamentos()) {
            pagamentoRepository.findAll(DashboardFinanceiroSpecification.noEscopo(filtro, inicio, fim))
                    .forEach(pagamento -> registros.add(new Registro(TipoSolicitacao.PAGAMENTO, pagamento)));
        }
        return registros;
    }

    /**
     * Todo o histórico em aberto até o fim do período, para que o backlog inclua o
     * que foi aberto antes do recorte e continua parado. O filtro de status do
     * usuário é deliberadamente ignorado: backlog é, por definição, o que não foi
     * resolvido, e respeitar uma seleção de "APROVADO" esvaziaria a conta em vez de
     * recortá-la.
     */
    private List<Registro> carregarPendentesAte(DashboardFinanceiroFilter filtro, OffsetDateTime fim) {
        DashboardFinanceiroFilter apenasPendentes = new DashboardFinanceiroFilter(
                null, null, filtro.tipo(), filtro.idFormulario(), filtro.idBase(), filtro.idProcesso(),
                filtro.idObra(), filtro.idFornecedor(), filtro.idSolicitante(), List.copyOf(PENDENTES),
                filtro.tipoDespesa(), filtro.prioridade(), filtro.granularidade());
        return carregar(apenasPendentes, null, fim);
    }

    private ContextoDocumental carregarDocumental(List<Registro> atuais, OffsetDateTime inicio,
                                                  OffsetDateTime fim, PeriodoFinanceiroDashboard periodo) {
        List<UUID> ids = atuais.stream().map(Registro::id).toList();

        Map<UUID, Long> porOrigem = ids.isEmpty() ? Map.of()
                : documentoRepository.contarPorOrigem(ids).stream()
                .collect(Collectors.toMap(DocumentoFinanceiroRepository.ContagemPorOrigem::getSolicitacaoId,
                        DocumentoFinanceiroRepository.ContagemPorOrigem::getQuantidade));

        return new ContextoDocumental(
                porOrigem,
                origensComDocumentoDosTipos(ids, TIPOS_FISCAIS),
                origensComDocumentoDosTipos(ids, List.of(TipoDocumentoFinanceiro.COMPROVANTE_PAGAMENTO)),
                documentoRepository.findAll(DocumentoFinanceiroSpecification.recebidosEntre(inicio, fim)),
                documentoRepository.count(DocumentoFinanceiroSpecification.recebidosEntre(
                        periodo.comparacaoInicio(), periodo.comparacaoFim())),
                documentoRepository.findAll(DocumentoFinanceiroSpecification.emAbertoAte(fim)),
                Set.copyOf(documentoRepository.buscarHashesDuplicados()));
    }

    private Set<UUID> origensComDocumentoDosTipos(List<UUID> ids, List<TipoDocumentoFinanceiro> tipos) {
        return ids.isEmpty() ? Set.of()
                : Set.copyOf(documentoRepository.buscarOrigensComDocumentoDosTipos(ids, tipos));
    }

    /**
     * Fornecedores usados no período, carregados de uma vez. O estado de curadoria
     * deles é a diferença entre "despesa com fornecedor conhecido" e "despesa com
     * um cadastro que alguém criou no meio do formulário e ninguém conferiu".
     */
    // Atenção: quando nenhuma solicitação do período aponta para fornecedor, o retorno é um
    // Map.of() imutável — e este rejeita chave nula com NPE, ao contrário do HashMap do
    // Collectors.toMap. Todo lookup aqui deve descartar o id nulo antes de consultar o mapa.
    private Map<UUID, Fornecedor> carregarFornecedores(List<Registro> atuais) {
        List<UUID> ids = atuais.stream()
                .map(registro -> registro.nucleo().getFornecedorId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return ids.isEmpty() ? Map.of()
                : fornecedorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Fornecedor::getId, Function.identity()));
    }

    /**
     * Memoriza a tradução de cada referência dentro de uma requisição. Sem isso, um
     * ranking de cinquenta linhas da mesma obra consultaria o contrato cinquenta
     * vezes, e cada célula do ranking custaria uma ida ao outro módulo.
     */
    private final class Descritor {
        private final Map<String, String> cache = new HashMap<>();

        private String descrever(FonteReferencia fonte, UUID id) {
            if (id == null) {
                return null;
            }
            return cache.computeIfAbsent(fonte.name() + ":" + id, chave -> resolvedor.descrever(fonte, id));
        }
    }

    // ===================================================================== //
    //  Cartões
    // ===================================================================== //

    private ResumoSolicitacoesDashboard resumo(List<Registro> atuais, List<Registro> anteriores) {
        long total = atuais.size();
        long compras = contarTipo(atuais, TipoSolicitacao.COMPRA);
        long pagamentos = total - compras;

        long rascunhos = contar(atuais, StatusSolicitacao.RASCUNHO);
        long aguardando = contar(atuais, StatusSolicitacao.SOLICITADO);
        long aprovadas = contar(atuais, StatusSolicitacao.APROVADO);
        long rejeitadas = contar(atuais, StatusSolicitacao.REJEITADO);
        long canceladas = contar(atuais, StatusSolicitacao.CANCELADO);
        long pendentes = rascunhos + aguardando;
        long decididas = aprovadas + rejeitadas;

        long comCompraPrevia = atuais.stream()
                .filter(registro -> registro.tipo() == TipoSolicitacao.PAGAMENTO)
                .filter(registro -> registro.nucleo().getSolicitacaoCompraId() != null)
                .count();

        return new ResumoSolicitacoesDashboard(
                total, anteriores.size(),
                variacaoPercentual(BigDecimal.valueOf(total), BigDecimal.valueOf(anteriores.size())),
                compras, pagamentos,
                rascunhos, aguardando, aprovadas, rejeitadas, canceladas,
                pendentes, percent(pendentes, total),
                decididas, percent(aprovadas, decididas), percent(rejeitadas, decididas),
                percent(canceladas, total),
                distintos(atuais, registro -> registro.solicitacao().getUsuarioSolicitanteId()),
                distintos(atuais, registro -> registro.nucleo().getFornecedorId()),
                comCompraPrevia, percent(comCompraPrevia, pagamentos));
    }

    private ResumoValoresDashboard valores(List<Registro> atuais, List<Registro> anteriores,
                                           List<Registro> aLiquidar, LocalDate hoje) {
        List<Registro> aprovadas = filtrar(atuais, StatusSolicitacao.APROVADO);
        List<Registro> rejeitadas = filtrar(atuais, StatusSolicitacao.REJEITADO);
        List<Registro> canceladas = filtrar(atuais, StatusSolicitacao.CANCELADO);
        List<Registro> pendentes = atuais.stream().filter(Registro::pendente).toList();

        BigDecimal solicitado = somar(atuais, Registro::valorSolicitado);
        BigDecimal aprovado = somar(aprovadas, Registro::valorAprovado);
        BigDecimal aprovadoAnterior = somar(filtrar(anteriores, StatusSolicitacao.APROVADO), Registro::valorAprovado);

        // O corte só existe onde houve decisão favorável: é a diferença entre o que
        // se pediu e o que se autorizou. Somá-lo ao valor rejeitado apagaria a
        // diferença entre negociar e recusar.
        BigDecimal pedidoNasAprovadas = somar(aprovadas, Registro::valorSolicitado);
        BigDecimal corte = pedidoNasAprovadas.subtract(aprovado);

        List<Registro> vencidas = aLiquidar.stream()
                .filter(registro -> registro.nucleo().getVencimento().isBefore(hoje)).toList();
        List<Registro> aVencer = aLiquidar.stream()
                .filter(registro -> !registro.nucleo().getVencimento().isBefore(hoje))
                .filter(registro -> diasAte(hoje, registro) <= 30).toList();

        return new ResumoValoresDashboard(
                arredondar(solicitado), arredondar(aprovado),
                arredondar(aprovadoAnterior), variacaoPercentual(aprovado, aprovadoAnterior),
                arredondar(somar(rejeitadas, Registro::valorSolicitado)),
                arredondar(somar(canceladas, Registro::valorSolicitado)),
                arredondar(somar(pendentes, Registro::valorSolicitado)),
                arredondar(corte), percentualDe(corte, pedidoNasAprovadas),
                percentualDe(aprovado, solicitado),
                dividir(solicitado, BigDecimal.valueOf(atuais.size())),
                dividir(aprovado, BigDecimal.valueOf(aprovadas.size())),
                arredondar(atuais.stream().map(Registro::valorSolicitado)
                        .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO)),
                arredondar(somar(aprovadas.stream()
                        .filter(registro -> registro.tipo() == TipoSolicitacao.COMPRA).toList(),
                        Registro::valorAprovado)),
                arredondar(somar(aprovadas.stream()
                        .filter(registro -> registro.tipo() == TipoSolicitacao.PAGAMENTO).toList(),
                        Registro::valorAprovado)),
                arredondar(somar(vencidas, Registro::valorAprovado)), vencidas.size(),
                arredondar(somar(aVencer, Registro::valorAprovado)), aVencer.size());
    }

    /**
     * Tempos de cada etapa. As datas de transição são gravadas na própria entidade,
     * então nenhuma precisa ser inferida.
     *
     * <p>O tempo de preenchimento é medido separado de propósito: um rascunho que
     * leva duas semanas para ser submetido não é problema do aprovador, e somá-lo ao
     * tempo de decisão apontaria o dedo para a pessoa errada.</p>
     */
    private CicloSolicitacaoDashboard ciclo(List<Registro> atuais, List<Registro> backlog, LocalDate referencia) {
        long[] ateSubmissao = duracoes(atuais,
                registro -> registro.solicitacao().getDataSubmissao() != null,
                registro -> dias(registro.solicitacao().getDataCriacao(),
                        registro.solicitacao().getDataSubmissao()));
        long[] ateDecisao = duracoes(atuais,
                registro -> registro.solicitacao().getDataDecisao() != null
                        && registro.solicitacao().getDataSubmissao() != null,
                registro -> dias(registro.solicitacao().getDataSubmissao(),
                        registro.solicitacao().getDataDecisao()));
        long[] cicloTotal = duracoes(atuais,
                registro -> registro.solicitacao().getDataDecisao() != null,
                registro -> dias(registro.solicitacao().getDataCriacao(),
                        registro.solicitacao().getDataDecisao()));
        long[] ateRejeicao = duracoes(atuais,
                registro -> registro.status() == StatusSolicitacao.REJEITADO
                        && registro.solicitacao().getDataSubmissao() != null,
                registro -> dias(registro.solicitacao().getDataSubmissao(),
                        registro.solicitacao().getDataDecisao()));

        long[] fila = backlog.stream()
                .filter(registro -> registro.status() == StatusSolicitacao.SOLICITADO)
                .mapToLong(registro -> idadeNaEtapa(registro, referencia)).sorted().toArray();
        long[] rascunhos = backlog.stream()
                .filter(registro -> registro.status() == StatusSolicitacao.RASCUNHO)
                .mapToLong(registro -> idadeNaEtapa(registro, referencia)).sorted().toArray();
        List<UUID> ids = atuais.stream().map(Registro::id).toList();
        long desfeitas = ids.isEmpty() ? 0
                : eventoRepository.countByIdSolicitacaoInAndTipo(ids, TipoEventoSolicitacao.DECISAO_DESFEITA);

        return new CicloSolicitacaoDashboard(
                mediaDe(ateSubmissao), percentil(ateSubmissao, 0.50), percentil(ateSubmissao, 0.90),
                mediaDe(ateDecisao), percentil(ateDecisao, 0.50), percentil(ateDecisao, 0.90),
                mediaDe(cicloTotal), percentil(cicloTotal, 0.50), percentil(cicloTotal, 0.90),
                mediaDe(ateRejeicao),
                mediaDe(fila), fila.length == 0 ? 0 : fila[fila.length - 1],
                mediaDe(rascunhos),
                ateSubmissao.length, ateDecisao.length, desfeitas);
    }

    private ConformidadeDocumentalDashboard conformidade(List<Registro> atuais, ContextoDocumental documental,
                                                         LocalDate hoje) {
        List<DocumentoFinanceiro> recebidos = documental.recebidos();
        List<DocumentoFinanceiro> emAberto = documental.emAberto();

        long conferidos = contarDocumentos(recebidos, SituacaoDocumento.CONFERIDO);
        long arquivados = contarDocumentos(recebidos, SituacaoDocumento.ARQUIVADO);

        long[] idades = emAberto.stream()
                .mapToLong(documento -> documento.diasNaSituacao(hoje)).sorted().toArray();

        List<Registro> aprovadas = filtrar(atuais, StatusSolicitacao.APROVADO);
        List<Registro> semFiscal = aprovadas.stream()
                .filter(registro -> !documental.comDocumentoFiscal().contains(registro.id())).toList();
        long comFiscal = aprovadas.size() - semFiscal.size();

        List<DocumentoFinanceiro> vencidos = emAberto.stream()
                .filter(documento -> documento.vencidoEm(hoje)).toList();

        return new ConformidadeDocumentalDashboard(
                recebidos.size(), documental.recebidosNoPeriodoAnterior(),
                variacaoPercentual(BigDecimal.valueOf(recebidos.size()),
                        BigDecimal.valueOf(documental.recebidosNoPeriodoAnterior())),
                arredondar(somarDocumentos(recebidos)),
                contarDocumentos(emAberto, SituacaoDocumento.PENDENTE),
                contarDocumentos(emAberto, SituacaoDocumento.COM_PENDENCIA),
                conferidos, arquivados,
                percent(conferidos + arquivados, recebidos.size()),
                mediaDe(idades), idades.length == 0 ? 0 : idades[idades.length - 1],
                comFiscal, semFiscal.size(), percent(comFiscal, aprovadas.size()),
                arredondar(somar(semFiscal, Registro::valorAprovado)),
                recebidos.stream().filter(DocumentoFinanceiro::identificacaoPendente).count(),
                recebidos.stream().filter(documento -> documento.getHashConteudo() != null
                        && documental.hashesDuplicados().contains(documento.getHashConteudo())).count(),
                vencidos.size(), arredondar(somarDocumentos(vencidos)),
                recebidos.stream().filter(documento -> documento.getOrigem().vazia()).count());
    }

    private QualidadeDadosFinanceiroDashboard qualidade(List<Registro> atuais, ContextoDocumental documental,
                                                        Map<UUID, Fornecedor> fornecedores,
                                                        OffsetDateTime atualizadoEm) {
        long semFornecedor = atuais.stream().filter(registro ->
                registro.nucleo().getFornecedorId() == null
                        && !StringUtils.hasText(registro.nucleo().getFornecedorNome())).count();
        long semDescricao = atuais.stream()
                .filter(registro -> !StringUtils.hasText(registro.nucleo().getDescricao())).count();
        long semCentroCusto = atuais.stream()
                .filter(registro -> registro.nucleo().getProcessoId() == null).count();
        long semBase = atuais.stream().filter(registro -> registro.nucleo().getBaseId() == null).count();

        Set<UUID> naoHomologados = fornecedores.values().stream()
                .filter(fornecedor -> fornecedor.getStatus() != StatusFornecedor.HOMOLOGADO)
                .map(Fornecedor::getId)
                .collect(Collectors.toSet());

        long camposEsperados = atuais.size() * 4L;
        long ausentes = semFornecedor + semDescricao + semCentroCusto + semBase;

        OffsetDateTime ultima = atuais.stream()
                .map(registro -> registro.solicitacao().getDataUltimaAlteracao() != null
                        ? registro.solicitacao().getDataUltimaAlteracao()
                        : registro.solicitacao().getDataCriacao())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(atualizadoEm);

        return new QualidadeDadosFinanceiroDashboard(
                ultima, semFornecedor, semDescricao, semCentroCusto, semBase,
                naoHomologados.size(),
                atuais.stream()
                        .filter(registro -> naoHomologados.contains(registro.nucleo().getFornecedorId())).count(),
                filtrar(atuais, StatusSolicitacao.APROVADO).stream()
                        .filter(registro -> !documental.temDocumento(registro.id())).count(),
                percent(camposEsperados - ausentes, camposEsperados));
    }

    private Map<String, IndicadorComparativoFinanceiro> indicadores(
            List<Registro> atuais, List<Registro> anteriores,
            ResumoValoresDashboard valores, CicloSolicitacaoDashboard ciclo,
            ConformidadeDocumentalDashboard conformidade, List<Registro> backlog) {

        long aprovadas = contar(atuais, StatusSolicitacao.APROVADO);
        long decididas = aprovadas + contar(atuais, StatusSolicitacao.REJEITADO);
        long aprovadasAnteriores = contar(anteriores, StatusSolicitacao.APROVADO);
        long decididasAnteriores = aprovadasAnteriores + contar(anteriores, StatusSolicitacao.REJEITADO);

        long[] decisaoAnterior = duracoes(anteriores,
                registro -> registro.solicitacao().getDataDecisao() != null
                        && registro.solicitacao().getDataSubmissao() != null,
                registro -> dias(registro.solicitacao().getDataSubmissao(),
                        registro.solicitacao().getDataDecisao()));

        Map<String, IndicadorComparativoFinanceiro> indicadores = new LinkedHashMap<>();
        indicadores.put("valorAprovado", indicador(
                valores.valorAprovado(), valores.valorAprovadoPeriodoAnterior(), "SUBIR",
                "Somatório do valor autorizado nas solicitações abertas no período"));
        indicadores.put("taxaAprovacao", indicador(
                percent(aprovadas, decididas), percent(aprovadasAnteriores, decididasAnteriores), "NEUTRO",
                "Solicitações aprovadas sobre as que já têm desfecho (aprovadas mais rejeitadas)"));
        indicadores.put("tempoDecisao", indicador(
                ciclo.mediaDiasAteDecisao(), mediaDe(decisaoAnterior), "DESCER",
                "Dias corridos entre a submissão e a decisão, nas solicitações já decididas"));
        indicadores.put("backlogEmAberto", indicador(
                arredondar(somar(backlog, Registro::valorSolicitado)), valores.valorEmAnalise(), "DESCER",
                "Valor pedido em tudo que está sem desfecho, inclusive o aberto antes do período"));
        indicadores.put("coberturaFiscal", indicador(
                conformidade.coberturaFiscal(), ZERO, "SUBIR",
                "Percentual das solicitações aprovadas no período com nota, boleto ou recibo no repositório"));
        return indicadores;
    }

    // ===================================================================== //
    //  Gráficos de fluxo
    // ===================================================================== //

    /**
     * Conversão da coorte. Uma solicitação aprovada também foi submetida, então ela
     * conta nas duas etapas — é o que faz o funil estreitar da esquerda para a
     * direita em vez de virar uma distribuição por status, que já existe logo abaixo.
     *
     * <p>Rejeitadas e canceladas ficam fora da cascata, marcadas como vazamento: uma
     * é decisão de quem autoriza, a outra é desistência de quem pediu, e empilhá-las
     * na sequência principal esconderia de qual lado o pedido se perdeu.</p>
     */
    private List<FunilFinanceiroItem> funil(List<Registro> atuais) {
        long total = atuais.size();
        List<Registro> submetidas = atuais.stream()
                .filter(registro -> registro.solicitacao().getDataSubmissao() != null).toList();
        List<Registro> aprovadas = filtrar(atuais, StatusSolicitacao.APROVADO);
        List<Registro> rejeitadas = filtrar(atuais, StatusSolicitacao.REJEITADO);
        List<Registro> canceladas = filtrar(atuais, StatusSolicitacao.CANCELADO);

        return List.of(
                new FunilFinanceiroItem("ABERTAS", "Abertas", total,
                        arredondar(somar(atuais, Registro::valorSolicitado)),
                        percent(total, total), null, false),
                new FunilFinanceiroItem("SUBMETIDAS", "Submetidas à decisão", submetidas.size(),
                        arredondar(somar(submetidas, Registro::valorSolicitado)),
                        percent(submetidas.size(), total), percent(submetidas.size(), total), false),
                new FunilFinanceiroItem("APROVADAS", "Aprovadas", aprovadas.size(),
                        arredondar(somar(aprovadas, Registro::valorAprovado)),
                        percent(aprovadas.size(), total), percent(aprovadas.size(), submetidas.size()), false),
                new FunilFinanceiroItem("REJEITADAS", "Rejeitadas", rejeitadas.size(),
                        arredondar(somar(rejeitadas, Registro::valorSolicitado)),
                        percent(rejeitadas.size(), total), null, true),
                new FunilFinanceiroItem("CANCELADAS", "Canceladas", canceladas.size(),
                        arredondar(somar(canceladas, Registro::valorSolicitado)),
                        percent(canceladas.size(), total), null, true));
    }

    private List<DistribuicaoStatusFinanceiroItem> porStatus(List<Registro> atuais) {
        long total = atuais.size();
        BigDecimal valorTotal = somar(atuais, Registro::valorVigente);
        return Arrays.stream(StatusSolicitacao.values()).map(status -> {
            List<Registro> doStatus = filtrar(atuais, status);
            BigDecimal valor = somar(doStatus, Registro::valorVigente);
            return new DistribuicaoStatusFinanceiroItem(status, rotulo(status), doStatus.size(),
                    arredondar(valor), percent(doStatus.size(), total), percentualDe(valor, valorTotal));
        }).toList();
    }

    private String rotulo(StatusSolicitacao status) {
        return switch (status) {
            case RASCUNHO -> "Em preenchimento";
            case SOLICITADO -> "Aguardando decisão";
            case APROVADO -> "Aprovada";
            case REJEITADO -> "Rejeitada";
            case CANCELADO -> "Cancelada";
        };
    }

    private List<SerieFinanceiroItem> serieTemporal(List<Registro> atuais, GranularidadeDashboard granularidade,
                                                    LocalDate primeiroDia, LocalDate ultimoDia) {
        Map<LocalDate, List<Registro>> porBalde = atuais.stream()
                .collect(Collectors.groupingBy(registro ->
                        granularidade.inicioDoBalde(registro.dataAbertura())));

        List<SerieFinanceiroItem> serie = new ArrayList<>();
        LocalDate balde = granularidade.inicioDoBalde(primeiroDia);
        while (!balde.isAfter(ultimoDia)) {
            List<Registro> doBalde = porBalde.getOrDefault(balde, List.of());
            List<Registro> aprovadas = filtrar(doBalde, StatusSolicitacao.APROVADO);
            List<Registro> rejeitadas = filtrar(doBalde, StatusSolicitacao.REJEITADO);
            BigDecimal solicitado = somar(doBalde, Registro::valorSolicitado);
            BigDecimal aprovado = somar(aprovadas, Registro::valorAprovado);

            serie.add(new SerieFinanceiroItem(
                    chaveBalde(balde, granularidade), rotuloBalde(balde, granularidade), balde,
                    doBalde.size(), arredondar(solicitado),
                    aprovadas.size(), arredondar(aprovado),
                    rejeitadas.size(), arredondar(somar(rejeitadas, Registro::valorSolicitado)),
                    contar(doBalde, StatusSolicitacao.CANCELADO),
                    doBalde.stream().filter(Registro::pendente).count(),
                    percentualDe(aprovado, solicitado)));
            balde = granularidade.proximoBalde(balde);
        }
        return serie;
    }

    private List<FaixaEnvelhecimentoFinanceiroItem> envelhecimento(List<Registro> backlog, LocalDate referencia) {
        record Faixa(String nome, int min, Integer max) {
        }
        List<Faixa> faixas = List.of(
                new Faixa("0-7", 0, 7), new Faixa("8-15", 8, 15), new Faixa("16-30", 16, 30),
                new Faixa("31-60", 31, 60), new Faixa(">60", 61, null));

        return faixas.stream().map(faixa -> {
            List<Registro> naFaixa = backlog.stream().filter(registro -> {
                long idade = idadeNaEtapa(registro, referencia);
                return idade >= faixa.min() && (faixa.max() == null || idade <= faixa.max());
            }).toList();

            Map<TipoSolicitacao, Long> porTipo = new EnumMap<>(TipoSolicitacao.class);
            Arrays.stream(TipoSolicitacao.values()).forEach(tipo -> porTipo.put(tipo, 0L));
            naFaixa.forEach(registro -> porTipo.merge(registro.tipo(), 1L, Long::sum));

            return new FaixaEnvelhecimentoFinanceiroItem(faixa.nome(), faixa.min(), faixa.max(),
                    naFaixa.size(), arredondar(somar(naFaixa, Registro::valorSolicitado)), porTipo);
        }).toList();
    }

    /**
     * Solicitações aprovadas, com vencimento marcado e ainda sem comprovante de
     * pagamento no repositório. As três condições andam juntas: sem aprovação não há
     * compromisso, sem data não há prazo, e com comprovante o compromisso já foi
     * cumprido — cobrá-lo seria cobrar duas vezes a mesma conta.
     */
    private List<Registro> aLiquidar(List<Registro> atuais, ContextoDocumental documental) {
        return atuais.stream()
                .filter(Registro::aprovada)
                .filter(registro -> registro.nucleo().getVencimento() != null)
                .filter(registro -> !documental.comComprovantePagamento().contains(registro.id()))
                .toList();
    }

    private List<FaixaVencimentoItem> vencimentos(List<Registro> aLiquidar, LocalDate hoje) {
        record Faixa(String nome, String rotulo, Long min, Long max) {
        }
        List<Faixa> faixas = List.of(
                new Faixa("VENCIDO", "Vencido", null, -1L),
                new Faixa("0-7", "Vence em até 7 dias", 0L, 7L),
                new Faixa("8-15", "Vence em 8 a 15 dias", 8L, 15L),
                new Faixa("16-30", "Vence em 16 a 30 dias", 16L, 30L),
                new Faixa(">30", "Vence em mais de 30 dias", 31L, null));

        BigDecimal valorTotal = somar(aLiquidar, Registro::valorAprovado);

        return faixas.stream().map(faixa -> {
            List<Registro> naFaixa = aLiquidar.stream().filter(registro -> {
                long dias = diasAte(hoje, registro);
                return (faixa.min() == null || dias >= faixa.min())
                        && (faixa.max() == null || dias <= faixa.max());
            }).toList();

            BigDecimal valor = somar(naFaixa, Registro::valorAprovado);
            // Na faixa vencida, o extremo relevante é o atraso maior; nas demais, o
            // vencimento mais próximo — é sempre o compromisso que cobra primeiro.
            long extremo = "VENCIDO".equals(faixa.nome())
                    ? naFaixa.stream().mapToLong(registro -> -diasAte(hoje, registro)).max().orElse(0)
                    : naFaixa.stream().mapToLong(registro -> diasAte(hoje, registro)).min().orElse(0);

            return new FaixaVencimentoItem(faixa.nome(), faixa.rotulo(), naFaixa.size(),
                    arredondar(valor), percentualDe(valor, valorTotal), extremo);
        }).toList();
    }

    // ===================================================================== //
    //  Rankings
    // ===================================================================== //

    /**
     * Ranking genérico das dimensões simples. Solicitações cuja chave não foi
     * preenchida (sem base, sem centro de custo, sem fornecedor) ficam de fora do
     * agrupamento em vez de virarem um balde "não informado" — a soma do ranking
     * passa a não bater com o total do período, e é justamente esse descasamento que
     * o cartão de qualidade dos dados denuncia, com o número exato ao lado.
     */
    private List<RankingFinanceiroItem> ranking(List<Registro> atuais, ContextoDocumental documental,
                                                Function<Registro, String> chave,
                                                Function<Registro, String> nome) {
        long total = atuais.size();
        BigDecimal aprovadoTotal = somar(atuais.stream().filter(Registro::aprovada).toList(),
                Registro::valorAprovado);

        Map<String, List<Registro>> grupos = atuais.stream()
                .filter(registro -> chave.apply(registro) != null)
                .collect(Collectors.groupingBy(chave));

        return grupos.entrySet().stream().map(entrada -> {
            List<Registro> doGrupo = entrada.getValue();
            List<Registro> aprovadas = doGrupo.stream().filter(Registro::aprovada).toList();
            long rejeitadas = contar(doGrupo, StatusSolicitacao.REJEITADO);
            BigDecimal aprovado = somar(aprovadas, Registro::valorAprovado);
            long comFiscal = aprovadas.stream()
                    .filter(registro -> documental.comDocumentoFiscal().contains(registro.id())).count();

            return new RankingFinanceiroItem(
                    entrada.getKey(),
                    idDaChave(entrada.getKey()),
                    nome.apply(doGrupo.getFirst()),
                    doGrupo.size(), percent(doGrupo.size(), total),
                    arredondar(somar(doGrupo, Registro::valorSolicitado)),
                    arredondar(aprovado), percentualDe(aprovado, aprovadoTotal),
                    aprovadas.size(), rejeitadas,
                    doGrupo.stream().filter(Registro::pendente).count(),
                    percent(aprovadas.size(), aprovadas.size() + rejeitadas),
                    dividir(aprovado, BigDecimal.valueOf(aprovadas.size())),
                    doGrupo.stream().mapToLong(registro -> documental.documentosDe(registro.id())).sum(),
                    percent(comFiscal, aprovadas.size()));
        }).sorted(porValorDesc()).toList();
    }

    private Comparator<RankingFinanceiroItem> porValorDesc() {
        return Comparator.comparing(RankingFinanceiroItem::valorAprovado).reversed()
                .thenComparing(Comparator.comparingLong(RankingFinanceiroItem::quantidade).reversed())
                .thenComparing(item -> item.nome() == null ? "" : item.nome());
    }

    /**
     * Quem abre as solicitações. O login é resolvido em lote e serve de rótulo: um
     * ranking com célula vazia esconde justamente quem mais pede.
     */
    private List<RankingFinanceiroItem> porSolicitante(List<Registro> atuais, ContextoDocumental documental) {
        Map<UUID, String> logins = usuarioApi.buscarLoginsPorIds(atuais.stream()
                .map(registro -> registro.solicitacao().getUsuarioSolicitanteId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        return ranking(atuais, documental,
                registro -> chaveUuid(registro.solicitacao().getUsuarioSolicitanteId()),
                registro -> logins.get(registro.solicitacao().getUsuarioSolicitanteId()));
    }

    // ===================================================================== //
    //  Repositório documental
    // ===================================================================== //

    private List<DistribuicaoDocumentoItem> documentosPorTipo(ContextoDocumental documental) {
        return distribuicaoDocumental(documental.recebidos(),
                Arrays.stream(TipoDocumentoFinanceiro.values()).toList(),
                Enum::name, TipoDocumentoFinanceiro::getRotulo, DocumentoFinanceiro::getTipo);
    }

    private List<DistribuicaoDocumentoItem> documentosPorSituacao(ContextoDocumental documental) {
        return distribuicaoDocumental(documental.recebidos(),
                Arrays.stream(SituacaoDocumento.values()).toList(),
                Enum::name, SituacaoDocumento::getRotulo, DocumentoFinanceiro::getSituacao);
    }

    /** Todas as constantes aparecem, inclusive as zeradas — buraco em legenda é lido como erro. */
    private <E extends Enum<E>> List<DistribuicaoDocumentoItem> distribuicaoDocumental(
            List<DocumentoFinanceiro> documentos, List<E> constantes,
            Function<E, String> chave, Function<E, String> rotulo,
            Function<DocumentoFinanceiro, E> extrator) {

        long total = documentos.size();
        return constantes.stream().map(constante -> {
            List<DocumentoFinanceiro> daFatia = documentos.stream()
                    .filter(documento -> extrator.apply(documento) == constante).toList();
            return new DistribuicaoDocumentoItem(
                    chave.apply(constante), rotulo.apply(constante),
                    daFatia.size(), percent(daFatia.size(), total),
                    arredondar(somarDocumentos(daFatia)),
                    daFatia.stream().filter(documento -> documento.getSituacao().isEmAberto()).count());
        }).toList();
    }

    // ===================================================================== //
    //  Prioridades
    // ===================================================================== //

    private List<PrioridadeFinanceiraItem> prioridades(List<Registro> atuais, List<Registro> backlog,
                                                       ContextoDocumental documental,
                                                       Map<UUID, Fornecedor> fornecedores,
                                                       ResumoValoresDashboard valores,
                                                       List<Registro> aLiquidar,
                                                       LocalDate hoje, LocalDate referencia) {
        List<PrioridadeFinanceiraItem> resultado = new ArrayList<>();

        adicionarEtapaParada(resultado, backlog, StatusSolicitacao.SOLICITADO, referencia,
                DIAS_PARA_COBRAR_DECISAO, "APROVACAO_PARADA", "aguardando decisão",
                "Aprovar ou rejeitar, para o pedido parar de envelhecer na fila");
        adicionarEtapaParada(resultado, backlog, StatusSolicitacao.RASCUNHO, referencia,
                DIAS_PARA_COBRAR_RASCUNHO, "RASCUNHO_ESQUECIDO", "em rascunho, nunca submetidos",
                "Submeter ou cancelar — rascunho parado ocupa valor que ninguém está decidindo");

        List<Registro> vencidas = aLiquidar.stream()
                .filter(registro -> registro.nucleo().getVencimento().isBefore(hoje))
                .sorted(Comparator.comparing(registro -> registro.nucleo().getVencimento()))
                .toList();
        if (!vencidas.isEmpty()) {
            resultado.add(new PrioridadeFinanceiraItem(
                    "VENCIDO_SEM_COMPROVANTE", SeveridadePrioridade.CRITICA,
                    vencidas.size() + " despesa(s) aprovada(s) já vencida(s) e sem comprovante",
                    "Somam " + arredondar(somar(vencidas, Registro::valorAprovado))
                            + "; a mais antiga venceu há "
                            + Math.abs(diasAte(hoje, vencidas.getFirst())) + " dias",
                    "Pagar e anexar o comprovante, ou registrar por que o pagamento não ocorreu",
                    "SOLICITACOES", amostra(vencidas),
                    Map.of("status", List.of(StatusSolicitacao.APROVADO.name()), "vencidas", true)));
        }

        List<Registro> proximas = aLiquidar.stream()
                .filter(registro -> !registro.nucleo().getVencimento().isBefore(hoje))
                .filter(registro -> diasAte(hoje, registro) <= DIAS_VENCIMENTO_PROXIMO)
                .sorted(Comparator.comparing(registro -> registro.nucleo().getVencimento()))
                .toList();
        if (!proximas.isEmpty()) {
            resultado.add(new PrioridadeFinanceiraItem(
                    "VENCIMENTO_PROXIMO", SeveridadePrioridade.ALTA,
                    proximas.size() + " despesa(s) vencem nos próximos " + DIAS_VENCIMENTO_PROXIMO + " dias",
                    "Somam " + arredondar(somar(proximas, Registro::valorAprovado))
                            + "; a mais próxima vence em " + diasAte(hoje, proximas.getFirst()) + " dia(s)",
                    "Programar o pagamento antes que a conta entre em atraso",
                    "SOLICITACOES", amostra(proximas),
                    Map.of("status", List.of(StatusSolicitacao.APROVADO.name()),
                            "vencimentoAte", hoje.plusDays(DIAS_VENCIMENTO_PROXIMO).toString())));
        }

        List<DocumentoFinanceiro> paradosNaConferencia = documental.emAberto().stream()
                .filter(documento -> documento.getSituacao() == SituacaoDocumento.PENDENTE)
                .filter(documento -> documento.diasNaSituacao(hoje) > DIAS_PARA_COBRAR_CONFERENCIA)
                .sorted(Comparator.comparingLong((DocumentoFinanceiro documento) ->
                        documento.diasNaSituacao(hoje)).reversed())
                .toList();
        if (!paradosNaConferencia.isEmpty()) {
            resultado.add(new PrioridadeFinanceiraItem(
                    "DOCUMENTO_AGUARDANDO_CONFERENCIA", SeveridadePrioridade.MEDIA,
                    paradosNaConferencia.size() + " documento(s) há mais de "
                            + DIAS_PARA_COBRAR_CONFERENCIA + " dias sem conferência",
                    "O mais antigo está parado há "
                            + paradosNaConferencia.getFirst().diasNaSituacao(hoje) + " dias",
                    "Conferir ou apontar pendência, para o documento sair da fila da contabilidade",
                    "DOCUMENTOS", amostraDocumentos(paradosNaConferencia),
                    Map.of("situacao", List.of(SituacaoDocumento.PENDENTE.name()))));
        }

        List<Registro> semFiscal = atuais.stream()
                .filter(Registro::aprovada)
                .filter(registro -> !documental.comDocumentoFiscal().contains(registro.id()))
                .sorted(Comparator.comparing(Registro::valorAprovado).reversed())
                .toList();
        if (!semFiscal.isEmpty()) {
            BigDecimal proporcao = percent(semFiscal.size(), contar(atuais, StatusSolicitacao.APROVADO));
            resultado.add(new PrioridadeFinanceiraItem(
                    "SEM_NOTA_FISCAL",
                    proporcao.compareTo(BigDecimal.valueOf(30)) > 0
                            ? SeveridadePrioridade.ALTA : SeveridadePrioridade.MEDIA,
                    semFiscal.size() + " despesa(s) aprovada(s) sem nenhum documento fiscal",
                    "Somam " + arredondar(somar(semFiscal, Registro::valorAprovado))
                            + " e representam " + proporcao + "% do que foi aprovado no período",
                    "Cobrar a nota, o boleto ou o recibo de quem solicitou e anexar ao repositório",
                    "SOLICITACOES", amostra(semFiscal),
                    Map.of("status", List.of(StatusSolicitacao.APROVADO.name()), "semDocumentoFiscal", true)));
        }

        if (valores.percentualCorte().compareTo(LIMIAR_CORTE_PERCENTUAL) > 0) {
            resultado.add(new PrioridadeFinanceiraItem(
                    "CORTE_ELEVADO", SeveridadePrioridade.MEDIA,
                    valores.percentualCorte() + "% do valor pedido foi cortado na aprovação",
                    arredondar(valores.corteNaAprovacao()) + " deixaram de ser autorizados sem que o "
                            + "pedido fosse recusado — o que se pede não é o que se pratica",
                    "Revisar como o valor é estimado com quem abre as solicitações",
                    "SOLICITACOES", List.of(),
                    Map.of("status", List.of(StatusSolicitacao.APROVADO.name()))));
        }

        List<Registro> comNaoHomologado = atuais.stream()
                .filter(registro -> registro.nucleo().getFornecedorId() != null)
                .filter(registro -> {
                    Fornecedor fornecedor = fornecedores.get(registro.nucleo().getFornecedorId());
                    return fornecedor != null && fornecedor.getStatus() != StatusFornecedor.HOMOLOGADO;
                })
                .sorted(Comparator.comparing(Registro::valorVigente).reversed())
                .toList();
        if (!comNaoHomologado.isEmpty()) {
            long distintos = comNaoHomologado.stream()
                    .map(registro -> registro.nucleo().getFornecedorId()).distinct().count();
            resultado.add(new PrioridadeFinanceiraItem(
                    "FORNECEDOR_NAO_HOMOLOGADO", SeveridadePrioridade.MEDIA,
                    distintos + " fornecedor(es) usados no período ainda não foram homologados",
                    comNaoHomologado.size() + " solicitação(ões) apontam para eles, somando "
                            + arredondar(somar(comNaoHomologado, Registro::valorVigente)),
                    "Conferir os dados cadastrais e homologar, ou bloquear o fornecedor",
                    "SOLICITACOES", amostra(comNaoHomologado),
                    Map.of("fornecedorNaoHomologado", true)));
        }

        adicionarConcentracao(resultado, atuais, fornecedores, valores);

        List<DocumentoFinanceiro> duplicados = documental.recebidos().stream()
                .filter(documento -> documento.getHashConteudo() != null
                        && documental.hashesDuplicados().contains(documento.getHashConteudo()))
                .toList();
        if (!duplicados.isEmpty()) {
            long conteudos = duplicados.stream()
                    .map(DocumentoFinanceiro::getHashConteudo).distinct().count();
            resultado.add(new PrioridadeFinanceiraItem(
                    "DOCUMENTO_DUPLICADO", SeveridadePrioridade.ALTA,
                    duplicados.size() + " documento(s) repetem " + conteudos + " conteúdo(s) já no repositório",
                    "Arquivo idêntico entrando duas vezes por caminhos diferentes é como um "
                            + "pagamento em duplicidade começa",
                    "Comparar os documentos e remover a cópia, mantendo o vínculo correto",
                    "DOCUMENTOS", amostraDocumentos(duplicados),
                    Map.of("duplicados", true)));
        }

        return resultado;
    }

    private void adicionarEtapaParada(List<PrioridadeFinanceiraItem> destino, List<Registro> backlog,
                                      StatusSolicitacao status, LocalDate referencia, long limite,
                                      String tipo, String descricaoEtapa, String acao) {
        List<Registro> parados = backlog.stream()
                .filter(registro -> registro.status() == status)
                .filter(registro -> idadeNaEtapa(registro, referencia) > limite)
                .sorted(Comparator.comparingLong((Registro registro) ->
                        idadeNaEtapa(registro, referencia)).reversed())
                .toList();
        if (parados.isEmpty()) {
            return;
        }
        long criticos = parados.stream()
                .filter(registro -> idadeNaEtapa(registro, referencia) > DIAS_PARA_ESCALAR_DECISAO).count();

        destino.add(new PrioridadeFinanceiraItem(
                tipo,
                criticos > 0 ? SeveridadePrioridade.CRITICA : SeveridadePrioridade.ALTA,
                parados.size() + " solicitação(ões) há mais de " + limite + " dias " + descricaoEtapa,
                "Somam " + arredondar(somar(parados, Registro::valorSolicitado)) + "; a mais antiga está há "
                        + idadeNaEtapa(parados.getFirst(), referencia) + " dias parada",
                acao,
                "SOLICITACOES", amostra(parados),
                Map.of("status", List.of(status.name()), "diasParadoMinimo", limite)));
    }

    /**
     * Um fornecedor que concentra boa parte do valor autorizado não é irregularidade
     * — pode ser o único que atende à região. Vira pendência porque a dependência
     * precisa ser decisão consciente, e não descoberta no dia em que ele falha.
     */
    private void adicionarConcentracao(List<PrioridadeFinanceiraItem> destino, List<Registro> atuais,
                                       Map<UUID, Fornecedor> fornecedores, ResumoValoresDashboard valores) {
        List<Registro> aprovadas = atuais.stream()
                .filter(Registro::aprovada)
                .filter(registro -> registro.nucleo().getFornecedorId() != null)
                .toList();
        if (aprovadas.size() < MINIMO_PARA_CONCENTRACAO || valores.valorAprovado().signum() == 0) {
            return;
        }

        Map<UUID, List<Registro>> porFornecedor = aprovadas.stream()
                .collect(Collectors.groupingBy(registro -> registro.nucleo().getFornecedorId()));

        porFornecedor.entrySet().stream()
                .map(entrada -> Map.entry(entrada.getKey(), somar(entrada.getValue(), Registro::valorAprovado)))
                .max(Map.Entry.comparingByValue())
                .filter(maior -> percentualDe(maior.getValue(), valores.valorAprovado())
                        .compareTo(LIMIAR_CONCENTRACAO_PERCENTUAL) > 0)
                .ifPresent(maior -> {
                    Fornecedor fornecedor = fornecedores.get(maior.getKey());
                    String nome = fornecedor == null ? "Um fornecedor" : fornecedor.getNomeFantasia();
                    destino.add(new PrioridadeFinanceiraItem(
                            "CONCENTRACAO_FORNECEDOR", SeveridadePrioridade.BAIXA,
                            nome + " concentra "
                                    + percentualDe(maior.getValue(), valores.valorAprovado())
                                    + "% do valor aprovado no período",
                            arredondar(maior.getValue()) + " em "
                                    + porFornecedor.get(maior.getKey()).size() + " solicitação(ões)",
                            "Confirmar que a dependência é intencional e manter alternativa homologada",
                            "SOLICITACOES",
                            amostra(porFornecedor.get(maior.getKey())),
                            Map.of("idFornecedor", List.of(maior.getKey().toString()))));
                });
    }

    private List<String> amostra(List<Registro> registros) {
        return registros.stream().limit(MAX_AMOSTRA_PRIORIDADE)
                .map(registro -> registro.id().toString()).toList();
    }

    private List<String> amostraDocumentos(List<DocumentoFinanceiro> documentos) {
        return documentos.stream().limit(MAX_AMOSTRA_PRIORIDADE)
                .map(documento -> documento.getId().toString()).toList();
    }

    // ===================================================================== //
    //  Regras de leitura da solicitação
    // ===================================================================== //

    /**
     * Há quantos dias a solicitação está na etapa em que se encontra — não desde a
     * abertura. Um pedido submetido ontem depois de duas semanas em rascunho é uma
     * pendência nova na fila do aprovador, e cobrá-lo por 15 dias apontaria o dedo
     * para a pessoa errada.
     */
    private long idadeNaEtapa(Registro registro, LocalDate referencia) {
        OffsetDateTime entrada = registro.status() == StatusSolicitacao.SOLICITADO
                && registro.solicitacao().getDataSubmissao() != null
                ? registro.solicitacao().getDataSubmissao()
                : registro.solicitacao().getDataCriacao();
        LocalDate marco = entrada != null ? entrada.toLocalDate() : referencia;
        return Math.max(0, ChronoUnit.DAYS.between(marco, referencia));
    }

    /** Dias até o vencimento; negativo quando a data já passou. */
    private long diasAte(LocalDate hoje, Registro registro) {
        return ChronoUnit.DAYS.between(hoje, registro.nucleo().getVencimento());
    }

    private long contar(List<Registro> registros, StatusSolicitacao status) {
        return registros.stream().filter(registro -> registro.status() == status).count();
    }

    private long contarTipo(List<Registro> registros, TipoSolicitacao tipo) {
        return registros.stream().filter(registro -> registro.tipo() == tipo).count();
    }

    private List<Registro> filtrar(List<Registro> registros, StatusSolicitacao status) {
        return registros.stream().filter(registro -> registro.status() == status).toList();
    }

    private BigDecimal somar(List<Registro> registros, Function<Registro, BigDecimal> valor) {
        return registros.stream().map(valor).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private long distintos(List<Registro> registros, Function<Registro, UUID> extrator) {
        return registros.stream().map(extrator).filter(Objects::nonNull).distinct().count();
    }

    private long contarDocumentos(List<DocumentoFinanceiro> documentos, SituacaoDocumento situacao) {
        return documentos.stream().filter(documento -> documento.getSituacao() == situacao).count();
    }

    private BigDecimal somarDocumentos(List<DocumentoFinanceiro> documentos) {
        return documentos.stream().map(DocumentoFinanceiro::valorOuZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String nomeDoFornecedor(Registro registro, Map<UUID, Fornecedor> fornecedores) {
        Fornecedor fornecedor = fornecedores.get(registro.nucleo().getFornecedorId());
        return fornecedor == null ? null : fornecedor.getNomeFantasia();
    }

    private static String chaveUuid(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String nomeDoEnum(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }

    /** Devolve o UUID quando a chave do ranking for um, e nulo nas dimensões enumeradas. */
    private static UUID idDaChave(String chave) {
        try {
            return UUID.fromString(chave);
        } catch (IllegalArgumentException naoEhUuid) {
            return null;
        }
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
    private PeriodoFinanceiroDashboard periodo(OffsetDateTime inicio, OffsetDateTime fim, long diasPeriodo) {
        boolean mesCalendarioCompleto = inicio.toLocalTime().equals(LocalTime.MIDNIGHT)
                && inicio.getDayOfMonth() == 1
                && fim.toLocalDate().equals(inicio.toLocalDate().withDayOfMonth(inicio.toLocalDate().lengthOfMonth()))
                && fim.toLocalTime().compareTo(LocalTime.of(23, 59, 59)) >= 0;
        if (mesCalendarioCompleto) {
            return new PeriodoFinanceiroDashboard(inicio, fim, DASHBOARD_ZONE.getId(),
                    inicio.minusMonths(1), inicio.minusNanos(1), diasPeriodo);
        }
        Duration duracao = Duration.between(inicio, fim);
        OffsetDateTime comparacaoFim = inicio.minusNanos(1);
        return new PeriodoFinanceiroDashboard(inicio, fim, DASHBOARD_ZONE.getId(),
                comparacaoFim.minus(duracao), comparacaoFim, diasPeriodo);
    }

    private OffsetDateTime inicioOuPadrao(DashboardFinanceiroFilter filter) {
        if (filter.inicio() != null) {
            return filter.inicio();
        }
        return OffsetDateTime.now(DASHBOARD_ZONE).withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private OffsetDateTime fimOuPadrao(DashboardFinanceiroFilter filter) {
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

    private long[] duracoes(List<Registro> registros, Predicate<Registro> elegivel,
                            ToLongFunction<Registro> dias) {
        return registros.stream().filter(elegivel).mapToLong(dias).map(valor -> Math.max(0, valor))
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

    private IndicadorComparativoFinanceiro indicador(BigDecimal valor, BigDecimal anterior,
                                                     String direcao, String definicao) {
        return new IndicadorComparativoFinanceiro(arredondar(valor), arredondar(anterior),
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
