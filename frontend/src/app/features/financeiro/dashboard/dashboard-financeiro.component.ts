import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ChartModule } from 'primeng/chart';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';

import {
  ChaveIndicadorFinanceiro,
  DIMENSOES_FILTRO_FINANCEIRO,
  DashboardFinanceiroFilter,
  DashboardFinanceiroResponse,
  GranularidadeDashboard,
  GrauPrioridade,
  IndicadorComparativoFinanceiro,
  PrioridadeFinanceiraItem,
  ROTULO_PRIORIDADE,
  ROTULO_STATUS_SOLICITACAO,
  ROTULO_TIPO_DESPESA,
  RankingFinanceiroItem,
  TipoDespesa
} from '../../../core/models/financeiro-dashboard.models';
import { FormularioResumo, Fornecedor, StatusSolicitacao } from '../../../core/models/financeiro.models';
import { ContratoResponse } from '../../../core/models/contrato.models';
import { BaseOperacionalResponse, ProcessoNegocioResponse } from '../../../core/models/rh.models';
import { PagedResponse, getErrorMessage } from '../../../core/models/shared.models';
import { ContratoService } from '../../../core/services/contrato.service';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { RhService } from '../../../core/services/rh.service';
import { normalizarSelecao } from '../../../core/utils/http-params.util';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import {
  DashboardInsightPanelComponent,
  DashboardMetricCardComponent
} from '../../../shared/components/dashboard/dashboard-ui.components';
import {
  CesButtonComponent,
  CesDataTableColumn,
  CesDatepickerComponent,
  CesDrillStateService,
  CesFilterChip,
  CesFilterPanelComponent,
  CesIconButtonComponent,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesSelectOption,
  NotificationService
} from '@ces/ui';
import { DashboardDrillComponent, DrillRecorte } from '../../../shared/components/dashboard/dashboard-drill.component';
import { SolicitacaoFinanceira, SolicitacaoFinanceiraFilter } from '../../../core/models/financeiro.models';
import {
  CopilotoAreaComponent,
  CopilotoDashboardContext,
  CopilotoGatilhoComponent,
  filtroPainel,
  graficoPainel,
  metricaPainel,
  periodoPainel,
  rotuloDasOpcoes,
} from '../../copiloto/painel';
import { toSaoPauloIso } from '../../../shared/utils/dashboard-date.utils';
import {
  construirInsightsFinanceiros,
  formatarMoeda,
  formatarMoedaCompacta,
  formatarPercentual
} from './financeiro-dashboard-insights';
import { atualizarPeriodicamente } from '../../../shared/components/dashboard/atualizacao-automatica';

/**
 * Configuração de um eixo do Chart.js. Só o suficiente para que espalhar o bloco
 * base com `...` continue tipado — sem isso, `ticks` cai numa assinatura de índice
 * e o compilador exige acesso por colchetes em toda sobrescrita.
 */
interface EixoChart {
  ticks: Record<string, unknown>;
  [chave: string]: unknown;
}

/** Como a série e os rankings são lidos: em dinheiro ou em número de pedidos. */
type Unidade = 'valor' | 'quantidade';
type SerieModo = 'periodo' | 'acumulado';
type DimensaoRanking = 'base' | 'centroCusto' | 'obra' | 'fornecedor' | 'tipoDespesa' | 'formulario';

/** Linha do recorte de solicitações (camada 2 do drill do ranking). */
interface LinhaSolicitacaoDrill {
  id: string;
  numero: number;
  tipo: string;
  formulario: string;
  fornecedor: string;
  solicitante: string;
  status: string;
  valor: number;
  abertaEm: string;
  documentos: number;
}

/** Dimensão do ranking → filtro (valor único) da listagem de solicitações. Centro de custo não é filtrável lá. */
const FILTRO_LISTAGEM_POR_DIMENSAO: Partial<Record<DimensaoRanking, keyof SolicitacaoFinanceiraFilter>> = {
  base: 'baseId',
  obra: 'obraId',
  fornecedor: 'fornecedorId',
  tipoDespesa: 'tipoDespesa',
  formulario: 'formularioId',
};

/** Filtros do painel que a listagem aceita (um valor por dimensão). */
const FILTROS_PAINEL_NA_LISTAGEM: [keyof DashboardFinanceiroFilter, keyof SolicitacaoFinanceiraFilter][] = [
  ['idBase', 'baseId'], ['idProcesso', 'processoId'], ['idObra', 'obraId'], ['idFornecedor', 'fornecedorId'],
  ['idFormulario', 'formularioId'], ['status', 'status'], ['tipoDespesa', 'tipoDespesa'],
];

/** Acima disto, um ranking em barras vira ruído em vez de comparação. */
const MAX_BARRAS_NO_GRAFICO = 10;

/** Paleta única dos gráficos — as quatro telas de dashboard usam as mesmas famílias. */
const COR = {
  solicitado: '#93c5fd',
  submetido: '#2563eb',
  aprovado: '#0f766e',
  rejeitado: '#c2413b',
  pendente: '#c57a16',
  neutro: '#94a3b8',
  eixo: '#9fb3c8',
  rotulo: '#829ab1',
  grade: '#edf2f7',
  tooltip: '#102a43'
};

const CORES_CATEGORICAS = [
  '#2563eb', '#0f766e', '#c57a16', '#60a5fa', '#14b8a6',
  '#9f1239', '#7c3aed', '#475569', '#93c5fd', '#a16207'
];

@Component({
  selector: 'app-dashboard-financeiro',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, DecimalPipe, ChartModule, SkeletonModule, TableModule, TooltipModule,
    DashboardInsightPanelComponent, DashboardMetricCardComponent, CesButtonComponent,
    CopilotoAreaComponent, CopilotoGatilhoComponent, DashboardDrillComponent, CesIconButtonComponent,
    CesDatepickerComponent, CesFilterPanelComponent, CesMultiSelectComponent, CesPageHeaderComponent, CesSelectComponent
  ],
  providers: [CesDrillStateService],
  templateUrl: './dashboard-financeiro.component.html'
})
export class DashboardFinanceiroComponent implements OnInit {
  private readonly financeiro = inject(FinanceiroService);
  protected readonly drill = inject(CesDrillStateService);
  private readonly rh = inject(RhService);
  private readonly contratos = inject(ContratoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formularioLookup = new RemoteLookup<FormularioResumo>(
    {
      identify: (formulario) => formulario.id,
      selectedKeys: () => this.filter.idFormulario ?? [],
      load: ({ query, page, size }) =>
        this.financeiro.listarFormularios({ ativo: true, buscaGlobal: query, page, size, sort: 'titulo,asc' })
    },
    this.destroyRef
  );
  private readonly baseLookup = new RemoteLookup<BaseOperacionalResponse>(
    {
      identify: (base) => base.id,
      selectedKeys: () => this.filter.idBase ?? [],
      load: ({ query, page, size }) =>
        this.rh.listarBasesOperacionais({ ativo: true, buscaGlobal: query, page, size, sort: 'nome,asc' })
    },
    this.destroyRef
  );
  private readonly processoLookup = new RemoteLookup<ProcessoNegocioResponse>(
    {
      identify: (processo) => processo.id,
      selectedKeys: () => this.filter.idProcesso ?? [],
      load: ({ query, page, size }) =>
        this.rh.listarProcessos({ ativo: true, buscaGlobal: query, page, size, sort: 'nome,asc' })
    },
    this.destroyRef
  );
  private readonly obraLookup = new RemoteLookup<ContratoResponse>(
    {
      identify: (obra) => obra.id,
      selectedKeys: () => this.filter.idObra ?? [],
      load: ({ query, page, size }) =>
        this.contratos.listarContratos({ ativo: true, buscaGlobal: query, page, size, sort: 'numeroContrato,asc' })
    },
    this.destroyRef
  );
  private readonly fornecedorLookup = new RemoteLookup<Fornecedor>(
    {
      identify: (fornecedor) => fornecedor.id,
      selectedKeys: () => this.filter.idFornecedor ?? [],
      load: ({ query, page, size }) =>
        this.financeiro.listarFornecedores({ ativo: true, buscaGlobal: query, page, size, sort: 'nomeFantasia,asc' })
    },
    this.destroyRef
  );

  protected readonly loading = signal(false);
  protected readonly data = signal<DashboardFinanceiroResponse | null>(null);
  protected readonly atualizadoEm = signal<Date | null>(null);
  protected readonly showManagementView = signal(true);
  protected readonly periodoAtivo = signal<string>('');

  protected readonly unidadeSerie = signal<Unidade>('valor');
  protected readonly serieModo = signal<SerieModo>('periodo');
  protected readonly unidadeStatus = signal<Unidade>('valor');
  protected readonly dimensaoRanking = signal<DimensaoRanking>('fornecedor');

  protected readonly formularios = this.formularioLookup.items;
  protected readonly formulariosLoading = this.formularioLookup.loading;
  protected readonly bases = this.baseLookup.items;
  protected readonly basesLoading = this.baseLookup.loading;
  protected readonly processos = this.processoLookup.items;
  protected readonly processosLoading = this.processoLookup.loading;
  protected readonly obras = this.obraLookup.items;
  protected readonly obrasLoading = this.obraLookup.loading;
  protected readonly fornecedores = this.fornecedorLookup.items;
  protected readonly fornecedoresLoading = this.fornecedorLookup.loading;

  /** Filtros que estavam ativos na consulta que produziu `data()`. */
  private readonly filtrosAplicados = signal<DashboardFinanceiroFilter>({});

  private readonly atualizacao = atualizarPeriodicamente(() => {
    this.buscar(this.filtrosAplicados(), true);
  }, { pode: () => !this.loading() && !!this.data() });

  private readonly chavesDeSelecao = DIMENSOES_FILTRO_FINANCEIRO;

  protected readonly opcoesFornecedores = computed<CesSelectOption<string>[]>(() => this.fornecedores().map((f) => ({ label: f.nomeFantasia, value: f.id })));
  protected readonly opcoesBases = computed<CesSelectOption<string>[]>(() => this.bases().map((b) => ({ label: b.nome, value: b.id })));
  protected readonly opcoesProcessos = computed<CesSelectOption<string>[]>(() => this.processos().map((p) => ({ label: p.nome, value: p.id })));
  protected readonly opcoesObras = computed<CesSelectOption<string>[]>(() => this.obras().map((o) => ({ label: o.numeroContrato, value: o.id })));
  protected readonly opcoesFormularios = computed<CesSelectOption<string>[]>(() => this.formularios().map((f) => ({ label: f.titulo, value: f.id })));

  /** Recorte aplicado como chips; o agrupamento do gráfico não recorta e fica fora. */
  protected readonly chips = computed<CesFilterChip[]>(() => {
    const f = this.filtrosAplicados();
    const chips: CesFilterChip[] = [];
    const dia = (iso: string) => new Date(iso).toLocaleDateString('pt-BR');
    if (f.tipo) chips.push({ key: 'tipo', label: f.tipo === 'COMPRA' ? 'Só compras' : 'Só pagamentos' });
    if (f.inicio || f.fim) {
      const texto = f.inicio && f.fim ? `${dia(f.inicio)} a ${dia(f.fim)}` : f.inicio ? `a partir de ${dia(f.inicio)}` : `até ${dia(f.fim!)}`;
      chips.push({ key: 'periodo', label: `Abertura: ${texto}` });
    }
    const grupos: [keyof DashboardFinanceiroFilter, string, CesSelectOption<string>[]][] = [
      ['idFornecedor', 'Fornecedor', this.opcoesFornecedores()],
      ['idBase', 'Base', this.opcoesBases()],
      ['idProcesso', 'Centro de custo', this.opcoesProcessos()],
      ['idObra', 'Obra', this.opcoesObras()],
      ['tipoDespesa', 'Despesa', this.tipoDespesaOptions],
      ['status', 'Situação', this.statusOptions],
      ['prioridade', 'Urgência', this.prioridadeOptions],
      ['idFormulario', 'Formulário', this.opcoesFormularios()]
    ];
    for (const [chave, rotulo, opcoes] of grupos) {
      const ids = (f[chave] as string[] | null | undefined) ?? [];
      if (!ids.length) continue;
      const texto = ids.length === 1 ? (opcoes.find((o) => o.value === ids[0])?.label ?? 'selecionado') : `${ids.length} selecionados`;
      chips.push({ key: chave, label: `${rotulo}: ${texto}` });
    }
    return chips;
  });

  protected removerFiltro(chave: string): void {
    if (chave === 'periodo') {
      this.dataInicio = null;
      this.dataFim = null;
    } else if (chave === 'tipo') {
      this.filter = { ...this.filter, tipo: null };
    } else {
      this.filter = { ...this.filter, [chave]: [] };
    }
    this.carregar();
  }

  protected readonly insights = computed(() => {
    const atual = this.data();
    return atual ? construirInsightsFinanceiros(atual) : [];
  });

  protected readonly linhasRanking = computed<RankingFinanceiroItem[]>(() => {
    const atual = this.data();
    if (!atual) return [];
    return {
      base: atual.porBase,
      centroCusto: atual.porCentroCusto,
      obra: atual.porObra,
      fornecedor: atual.porFornecedor,
      tipoDespesa: atual.porTipoDespesa,
      formulario: atual.porFormulario
    }[this.dimensaoRanking()] ?? [];
  });

  protected readonly rotuloRanking = computed(
    () => this.abasRanking.find((aba) => aba.value === this.dimensaoRanking())?.label ?? 'Agrupamento'
  );

  /**
   * Total de pedidos parados no backlog histórico, que é o que o gráfico de
   * envelhecimento mostra. Não dá para usar `resumo.pendentes`: aquele número é só
   * o do período consultado, e o painel ficaria vazio num recorte sem pedidos novos
   * mas com decisões antigas em aberto — justamente o caso que mais precisa ser visto.
   */
  protected readonly totalPendenteHistorico = computed(() =>
    (this.data()?.envelhecimento ?? []).reduce((soma, faixa) => soma + faixa.quantidade, 0)
  );

  /** Total a liquidar: soma das faixas da régua de vencimentos. */
  protected readonly totalALiquidar = computed(() =>
    (this.data()?.vencimentos ?? []).reduce((soma, faixa) => soma + faixa.quantidade, 0)
  );

  /**
   * Indicadores comparativos já formatados. Fica num `computed` porque o template
   * precisaria de um `switch` por chave para escolher entre moeda, percentual e
   * dias — decisão que é de apresentação, não de marcação.
   */
  protected readonly kpisComparativos = computed(() => {
    const indicadores = this.data()?.indicadores;
    if (!indicadores) return [];

    const formatos: { chave: ChaveIndicadorFinanceiro; rotulo: string; formatar: (valor: number) => string }[] = [
      {
        chave: 'backlogEmAberto',
        rotulo: 'Parado esperando decisão',
        formatar: formatarMoedaCompacta
      },
      {
        chave: 'taxaAprovacao',
        rotulo: 'Aprovação dos decididos',
        formatar: (valor) => formatarPercentual(valor)
      },
      {
        chave: 'tempoDecisao',
        rotulo: 'Dias até a decisão',
        formatar: (valor) => `${valor.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dia(s)`
      },
      {
        chave: 'coberturaFiscal',
        rotulo: 'Aprovadas com nota ou boleto',
        formatar: (valor) => formatarPercentual(valor)
      }
    ];

    return formatos
      .filter(({ chave }) => !!indicadores[chave])
      .map(({ chave, rotulo, formatar }) => {
        const indicador = indicadores[chave];
        const variou = indicador.anterior !== 0 && indicador.variacaoPercentual !== 0;
        return {
          chave,
          rotulo,
          valor: formatar(indicador.valor),
          comparativo: this.comparativo(indicador),
          tom: this.tomDaVariacao(indicador),
          seta: variou
            ? indicador.variacaoPercentual > 0
              ? 'pi-arrow-up-right'
              : 'pi-arrow-down-right'
            : null,
          definicao: indicador.definicao
        };
      });
  });

  protected readonly abasRanking = [
    { label: 'Fornecedores', value: 'fornecedor' as const },
    { label: 'Bases', value: 'base' as const },
    { label: 'Centros de custo', value: 'centroCusto' as const },
    { label: 'Obras', value: 'obra' as const },
    { label: 'Tipos de despesa', value: 'tipoDespesa' as const },
    { label: 'Formulários', value: 'formulario' as const }
  ];
  protected readonly abasUnidade = [
    { label: 'Em valor', value: 'valor' as const },
    { label: 'Em quantidade', value: 'quantidade' as const }
  ];
  protected readonly abasSerieModo = [
    { label: 'Por período', value: 'periodo' as const },
    { label: 'Acumulado', value: 'acumulado' as const }
  ];
  protected readonly periodosRapidos = [
    { label: '30 dias', value: 'mensal' as const },
    { label: '90 dias', value: 'trimestral' as const },
    { label: '1 ano', value: 'anual' as const }
  ];

  protected readonly tipoOptions = [
    { label: 'Compras e pagamentos', value: null },
    { label: 'Só compras', value: 'COMPRA' as const },
    { label: 'Só pagamentos', value: 'PAGAMENTO' as const }
  ];
  protected readonly granularidadeOptions = [
    { label: 'Automático', value: null },
    { label: 'Por dia', value: 'DIA' as GranularidadeDashboard },
    { label: 'Por mês', value: 'MES' as GranularidadeDashboard },
    { label: 'Por ano', value: 'ANO' as GranularidadeDashboard }
  ];
  protected readonly granularidadeLabels: Record<GranularidadeDashboard, string> = {
    DIA: 'dia', MES: 'mês', ANO: 'ano'
  };
  protected readonly statusOptions = (Object.keys(ROTULO_STATUS_SOLICITACAO) as StatusSolicitacao[])
    .map((value) => ({ label: ROTULO_STATUS_SOLICITACAO[value], value }));
  protected readonly tipoDespesaOptions = (Object.keys(ROTULO_TIPO_DESPESA) as TipoDespesa[])
    .map((value) => ({ label: ROTULO_TIPO_DESPESA[value], value }));
  protected readonly prioridadeOptions = (Object.keys(ROTULO_PRIORIDADE) as GrauPrioridade[])
    .map((value) => ({ label: ROTULO_PRIORIDADE[value], value }));

  /** Contexto do dashboard para o Copiloto: o recorte aplicado e os números em tela. */
  protected readonly contextoCopiloto = computed(() => this.montarContextoCopiloto());

  protected dataInicio: Date | null = null;
  protected dataFim: Date | null = null;
  protected filter: DashboardFinanceiroFilter = this.filtroVazio();

  protected serieData: unknown;
  protected serieOptions: unknown;
  protected funilData: unknown;
  protected funilOptions: unknown;
  protected statusData: unknown;
  protected statusChartOptions: unknown;
  protected envelhecimentoData: unknown;
  protected envelhecimentoOptions: unknown;
  protected vencimentoData: unknown;
  protected vencimentoOptions: unknown;
  protected rankingData: unknown;
  protected rankingOptions: unknown;
  protected documentoData: unknown;
  protected documentoOptions: unknown;
  protected cicloData: unknown;
  protected cicloOptions: unknown;

  // ------------------------------------------------------------------ //
  //  Ciclo de vida
  // ------------------------------------------------------------------ //

  ngOnInit(): void {
    this.restaurarFiltrosDaUrl();
    this.hidratarLookupsSelecionados();
    this.carregar();
  }

  protected aplicar(): void {
    if (!this.periodoValido()) return;
    this.periodoAtivo.set('');
    this.carregar();
  }

  protected limpar(): void {
    this.dataInicio = null;
    this.dataFim = null;
    this.filter = this.filtroVazio();
    this.periodoAtivo.set('');
    this.carregar();
  }

  protected setPeriodo(tipo: 'mensal' | 'trimestral' | 'anual'): void {
    const hoje = new Date();
    this.dataFim = new Date(hoje);
    this.dataInicio = new Date(hoje);
    if (tipo === 'mensal') this.dataInicio.setMonth(hoje.getMonth() - 1);
    if (tipo === 'trimestral') this.dataInicio.setMonth(hoje.getMonth() - 3);
    if (tipo === 'anual') this.dataInicio.setFullYear(hoje.getFullYear() - 1);
    this.periodoAtivo.set(tipo);
    this.carregar();
  }

  protected onGranularidadeChange(): void {
    this.carregar();
  }

  protected openFormularios(): void { this.formularioLookup.open(); }
  protected searchFormularios(event: { filter?: string }): void { this.formularioLookup.search(event.filter ?? ''); }
  protected loadMoreFormularios(): void { this.formularioLookup.loadNext(); }
  protected openBases(): void { this.baseLookup.open(); }
  protected searchBases(event: { filter?: string }): void { this.baseLookup.search(event.filter ?? ''); }
  protected loadMoreBases(): void { this.baseLookup.loadNext(); }
  protected openProcessos(): void { this.processoLookup.open(); }
  protected searchProcessos(event: { filter?: string }): void { this.processoLookup.search(event.filter ?? ''); }
  protected loadMoreProcessos(): void { this.processoLookup.loadNext(); }
  protected openObras(): void { this.obraLookup.open(); }
  protected searchObras(event: { filter?: string }): void { this.obraLookup.search(event.filter ?? ''); }
  protected loadMoreObras(): void { this.obraLookup.loadNext(); }
  protected openFornecedores(): void { this.fornecedorLookup.open(); }
  protected searchFornecedores(event: { filter?: string }): void { this.fornecedorLookup.search(event.filter ?? ''); }
  protected loadMoreFornecedores(): void { this.fornecedorLookup.loadNext(); }

  private filtroVazio(): DashboardFinanceiroFilter {
    return {
      tipo: null, idFormulario: [], idBase: [], idProcesso: [], idObra: [],
      idFornecedor: [], idSolicitante: [], status: [], tipoDespesa: [], prioridade: [],
      granularidade: null
    };
  }

  /** Recompõe o recorte compartilhado por link (`?idFornecedor=a&idFornecedor=b`). */
  private restaurarFiltrosDaUrl(): void {
    const query = this.route.snapshot.queryParamMap;
    this.chavesDeSelecao.forEach((chave) => {
      (this.filter[chave] as string[]) = normalizarSelecao(query.getAll(chave));
    });
    const tipo = query.get('tipo');
    if (tipo === 'COMPRA' || tipo === 'PAGAMENTO') {
      this.filter.tipo = tipo;
    }
    const granularidade = query.get('granularidade') as GranularidadeDashboard | null;
    if (granularidade && ['DIA', 'MES', 'ANO'].includes(granularidade)) {
      this.filter.granularidade = granularidade;
    }
    this.dataInicio = this.parsearData(query.get('inicio'));
    this.dataFim = this.parsearData(query.get('fim'));
  }

  private parsearData(valor: string | null): Date | null {
    if (!valor) return null;
    const data = new Date(valor);
    return Number.isNaN(data.getTime()) ? null : data;
  }

  /** Deixa o recorte na URL para o gestor conseguir compartilhar a tela. */
  private sincronizarUrl(filter: DashboardFinanceiroFilter): void {
    const queryParams: Record<string, string[] | string | null> = {};
    this.chavesDeSelecao.forEach((chave) => {
      const selecao = (filter[chave] as string[] | null) ?? [];
      queryParams[chave] = selecao.length ? selecao : null;
    });
    queryParams['tipo'] = filter.tipo ?? null;
    queryParams['granularidade'] = filter.granularidade ?? null;
    queryParams['inicio'] = filter.inicio ?? null;
    queryParams['fim'] = filter.fim ?? null;
    // `merge` preserva o `?drill=` do painel de detalhe aberto.
    this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }

  /**
   * Os lookups são independentes entre si e nenhum deles é essencial para a tela:
   * uma lista de opções que não carregou apenas deixa aquele filtro vazio, e
   * derrubar o dashboard inteiro por isso seria desproporcional.
   */
  private carregarLookups(): void {
    this.hidratarLookupsSelecionados();
  }

  /** Extrai o conteúdo de uma página de lookup e degrada para lista vazia em falha. */
  private hidratarLookupsSelecionados(): void {
    this.hidratarSelecionados(this.filter.idFormulario, id => this.financeiro.buscarSchema(id), this.formularioLookup);
    this.hidratarSelecionados(this.filter.idBase, id => this.rh.buscarBaseOperacionalPorId(id), this.baseLookup);
    this.hidratarSelecionados(this.filter.idProcesso, id => this.rh.buscarProcessoPorId(id), this.processoLookup);
    this.hidratarSelecionados(this.filter.idObra, id => this.contratos.buscarContratoPorId(id), this.obraLookup);
    this.hidratarSelecionados(this.filter.idFornecedor, id => this.financeiro.buscarFornecedorPorId(id), this.fornecedorLookup);
  }

  private hidratarSelecionados<T>(
    ids: string[] | null | undefined,
    buscar: (id: string) => Observable<T>,
    lookup: RemoteLookup<T>
  ): void {
    if (!ids?.length) return;
    forkJoin(ids.map(id => buscar(id).pipe(catchError(() => of(null)))))
      .subscribe(itens => lookup.setSeed(itens.filter((item): item is T => item != null)));
  }

  private carregar(): void {
    const filter: DashboardFinanceiroFilter = {
      ...this.filter,
      inicio: this.dataInicio ? this.toIso(this.dataInicio, false) : null,
      fim: this.dataFim ? this.toIso(this.dataFim, true) : null
    };

    this.sincronizarUrl(filter);
    this.buscar(filter);
  }

  /** Busca um recorte; a atualização automática repete o aplicado sem mostrar carregamento nem avisar erro. */
  private buscar(filter: DashboardFinanceiroFilter, silencioso = false): void {
    if (!silencioso) this.loading.set(true);
    this.financeiro.gerarDashboard(filter).subscribe({
      next: (resposta) => {
        this.data.set(resposta);
        this.filtrosAplicados.set(filter);
        this.atualizadoEm.set(new Date(resposta.atualizadoEm));
        this.prepararGraficos(resposta);
        this.loading.set(false);
      },
      error: (erro) => {
        if (silencioso) return;
        this.loading.set(false);
        this.notificacoes.error(getErrorMessage(erro), { title: 'Não foi possível carregar' });
      }
    });
  }

  private periodoValido(): boolean {
    if (!this.dataInicio || !this.dataFim || this.dataInicio <= this.dataFim) return true;
    this.notificacoes.warning('A data inicial deve ser anterior ou igual à data final.', { title: 'Período inválido' });
    return false;
  }

  private toIso(date: Date, endOfDay: boolean): string {
    const value = new Date(date);
    value.setHours(endOfDay ? 23 : 0, endOfDay ? 59 : 0, endOfDay ? 59 : 0, endOfDay ? 999 : 0);
    return toSaoPauloIso(value, endOfDay);
  }

  // ------------------------------------------------------------------ //
  //  Formatação e leitura
  // ------------------------------------------------------------------ //

  protected readonly moeda = formatarMoeda;
  protected readonly moedaCompacta = formatarMoedaCompacta;
  protected readonly percentual = formatarPercentual;

  protected nomeOuPlaceholder(nome: string | null): string {
    return nome ?? 'Cadastro não disponível';
  }

  /** A barra enche até 100%, mas o percentual em si pode passar disso. */
  protected barWidth(value: number): number {
    return Math.max(2, Math.min(100, value ?? 0));
  }

  protected variacaoDoVolume(resumo: { totalPeriodoAnterior: number; variacaoPercentual: number }): string {
    if (resumo.totalPeriodoAnterior === 0) return 'Sem período anterior para comparar';
    const valor = resumo.variacaoPercentual.toLocaleString('pt-BR', {
      maximumFractionDigits: 1, signDisplay: 'always'
    });
    return `${valor}% vs. período anterior (${resumo.totalPeriodoAnterior})`;
  }

  protected comparativo(indicador: IndicadorComparativoFinanceiro): string {
    if (indicador.anterior === 0) return 'Sem período anterior para comparar';
    const variacao = indicador.variacaoPercentual.toLocaleString('pt-BR', {
      maximumFractionDigits: 1, signDisplay: 'always'
    });
    return `${variacao}% vs. período anterior`;
  }

  /**
   * Cor da variação pela direção que o backend declara como desejável. Sem isso,
   * uma queda no valor parado sairia vermelha — e queda de backlog é boa notícia.
   */
  protected tomDaVariacao(indicador: IndicadorComparativoFinanceiro): 'positivo' | 'negativo' | 'neutro' {
    if (indicador.anterior === 0 || indicador.variacaoPercentual === 0) return 'neutro';
    if (indicador.direcaoDesejada === 'NEUTRO') return 'neutro';
    const subiu = indicador.variacaoPercentual > 0;
    const queriaSubir = indicador.direcaoDesejada === 'SUBIR';
    return subiu === queriaSubir ? 'positivo' : 'negativo';
  }

  protected descricaoSerie(granularidade: GranularidadeDashboard): string {
    const intervalo = this.granularidadeLabels[granularidade];
    if (this.serieModo() === 'acumulado') {
      return 'Soma corrida do que foi pedido contra o que foi autorizado. A distância entre as '
        + 'duas curvas é o que não passou.';
    }
    return this.unidadeSerie() === 'valor'
      ? `Valor pedido e valor autorizado dos pedidos abertos em cada ${intervalo}. A linha é o percentual autorizado.`
      : `Pedidos abertos em cada ${intervalo}, pelo desfecho que têm hoje.`;
  }

  protected alertaDeIdade(dias: number): 'low' | 'medium' | 'high' {
    if (dias > 30) return 'high';
    return dias > 15 ? 'medium' : 'low';
  }

  /** Mesma cor que a etapa recebe no gráfico — legenda e barra têm que combinar. */
  protected corDaEtapa(etapa: string): string {
    return {
      ABERTAS: COR.solicitado,
      SUBMETIDAS: COR.submetido,
      APROVADAS: COR.aprovado,
      REJEITADAS: COR.rejeitado,
      CANCELADAS: COR.neutro
    }[etapa] ?? COR.neutro;
  }

  protected corDaFaixa(idadeMinimaDias: number): string {
    if (idadeMinimaDias > 30) return COR.rejeitado;
    return idadeMinimaDias > 15 ? COR.pendente : 'var(--p-primary-400)';
  }

  protected corDoVencimento(faixa: string): string {
    if (faixa === 'VENCIDO') return COR.rejeitado;
    if (faixa === '0-7') return COR.pendente;
    return faixa === '>30' ? COR.neutro : 'var(--p-primary-400)';
  }

  /** Texto do extremo de cada faixa da régua — "vence em" e "vencido há" não são a mesma frase. */
  protected textoDoExtremo(faixa: { faixa: string; quantidade: number; diasExtremo: number }): string {
    if (!faixa.quantidade) return 'Nada nesta faixa';
    return faixa.faixa === 'VENCIDO'
      ? `A mais antiga venceu há ${faixa.diasExtremo} dia(s)`
      : `A mais próxima vence em ${faixa.diasExtremo} dia(s)`;
  }

  // ------------------------------------------------------------------ //
  //  Navegação
  // ------------------------------------------------------------------ //

  // ------------------------------------------------------------------ //
  //  Copiloto contextual
  // ------------------------------------------------------------------ //

  /**
   * Recorte estruturado para o Copiloto: identidade, período, filtros aplicados (com nomes legíveis e
   * ids), KPIs com comparação, agregações visíveis dos gráficos e datasets. Nunca os registros: o
   * agente aprofunda pelas ferramentas, com as permissões do usuário.
   */
  private montarContextoCopiloto(): CopilotoDashboardContext | null {
    const d = this.data();
    const f = this.filtrosAplicados();
    const nomes = <T>(itens: readonly T[], ids: readonly string[] | null | undefined, id: (i: T) => string, nome: (i: T) => string) =>
      rotuloDasOpcoes(itens, ids ?? [], id, nome);
    const rotulos = <T extends string>(valores: readonly T[] | null | undefined, mapa: Record<T, string>) =>
      (valores ?? []).map((valor) => mapa[valor] ?? valor);
    const direcao = (indicador?: IndicadorComparativoFinanceiro) =>
      indicador?.direcaoDesejada === 'SUBIR' ? 'maior' as const : indicador?.direcaoDesejada === 'DESCER' ? 'menor' as const : undefined;
    const comparacao = (indicador?: IndicadorComparativoFinanceiro) =>
      indicador && indicador.anterior
        ? { rotulo: 'vs. período anterior', valorAnterior: indicador.anterior, variacaoPercentual: indicador.variacaoPercentual, melhorQuando: direcao(indicador) }
        : undefined;
    const ranking = this.linhasRanking();
    const serie = d?.serieTemporal ?? [];
    return {
      id: 'financeiro.executivo',
      nome: 'Financeiro — compras e pagamentos',
      modulo: 'Financeiro',
      descricao: 'Pedido × autorizado, funil de aprovação, fila de decisão, vencimentos, tempo de processo, documentos recebidos e concentração da despesa. Pedidos contados pela data de abertura, com o desfecho atual.',
      rota: '/financeiro/dashboard',
      periodo: d ? periodoPainel(d.inicio, d.fim) : periodoPainel(this.dataInicio, this.dataFim),
      filtros: [
        filtroPainel('tipo', 'Tipo', f.tipo === 'COMPRA' ? 'Só compras' : f.tipo === 'PAGAMENTO' ? 'Só pagamentos' : null, f.tipo ?? null),
        filtroPainel('fornecedor', 'Fornecedor', nomes(this.fornecedores(), f.idFornecedor, (i) => i.id, (i) => i.nomeFantasia), f.idFornecedor ?? []),
        filtroPainel('base', 'Base operacional', nomes(this.bases(), f.idBase, (i) => i.id, (i) => i.nome), f.idBase ?? []),
        filtroPainel('centroCusto', 'Centro de custo', nomes(this.processos(), f.idProcesso, (i) => i.id, (i) => i.nome), f.idProcesso ?? []),
        filtroPainel('obra', 'Obra', nomes(this.obras(), f.idObra, (i) => i.id, (i) => i.numeroContrato), f.idObra ?? []),
        filtroPainel('tipoDespesa', 'Tipo de despesa', rotulos(f.tipoDespesa, ROTULO_TIPO_DESPESA), f.tipoDespesa ?? []),
        filtroPainel('status', 'Situação', rotulos(f.status, ROTULO_STATUS_SOLICITACAO), f.status ?? []),
        filtroPainel('prioridade', 'Urgência', rotulos(f.prioridade, ROTULO_PRIORIDADE), f.prioridade ?? []),
        filtroPainel('formulario', 'Formulário', nomes(this.formularios(), f.idFormulario, (i) => i.id, (i) => i.titulo), f.idFormulario ?? []),
      ],
      metricas: d
        ? [
            metricaPainel('valorAprovado', 'Autorizado no período', d.valores.valorAprovado, 'moeda', {
              comparacao: d.valores.valorAprovadoPeriodoAnterior
                ? { rotulo: 'vs. período anterior', valorAnterior: d.valores.valorAprovadoPeriodoAnterior, variacaoPercentual: d.valores.variacaoValorAprovado, melhorQuando: direcao(d.indicadores?.valorAprovado) }
                : undefined,
            }),
            metricaPainel('valorSolicitado', 'Valor pedido', d.valores.valorSolicitado, 'moeda'),
            metricaPainel('taxaAutorizacao', 'Do pedido, quanto foi autorizado', d.valores.taxaAutorizacao, 'percentual'),
            metricaPainel('corteNaAprovacao', 'Cortado ao aprovar', d.valores.corteNaAprovacao, 'moeda'),
            metricaPainel('valorRejeitado', 'Recusado por inteiro', d.valores.valorRejeitado, 'moeda'),
            metricaPainel('valorEmAnalise', 'Esperando decisão', d.valores.valorEmAnalise, 'moeda', {
              descricao: `${d.resumo.pendentes} pedido(s) sem desfecho; o mais antigo espera há ${d.ciclo.idadeMaximaFilaDias} dia(s)`,
            }),
            metricaPainel('valorVencido', 'Vencido e sem comprovante', d.valores.valorVencido, 'moeda', {
              descricao: `${d.valores.quantidadeVencida} despesa(s) vencidas; ${formatarMoeda(d.valores.valorAVencer)} vencem nos próximos 30 dias`,
            }),
            metricaPainel('pedidos', 'Pedidos no período', d.resumo.total, 'inteiro', {
              comparacao: d.resumo.totalPeriodoAnterior
                ? { rotulo: 'vs. período anterior', valorAnterior: d.resumo.totalPeriodoAnterior, variacaoPercentual: d.resumo.variacaoPercentual }
                : undefined,
            }),
            metricaPainel('backlogEmAberto', 'Parado esperando decisão', d.indicadores?.backlogEmAberto?.valor, 'moeda', {
              descricao: d.indicadores?.backlogEmAberto?.definicao, comparacao: comparacao(d.indicadores?.backlogEmAberto),
            }),
            metricaPainel('taxaAprovacao', 'Aprovação dos decididos', d.indicadores?.taxaAprovacao?.valor, 'percentual', {
              descricao: d.indicadores?.taxaAprovacao?.definicao, comparacao: comparacao(d.indicadores?.taxaAprovacao),
            }),
            metricaPainel('tempoDecisao', 'Dias até a decisão', d.indicadores?.tempoDecisao?.valor, 'dias', {
              descricao: d.indicadores?.tempoDecisao?.definicao, comparacao: comparacao(d.indicadores?.tempoDecisao),
            }),
            metricaPainel('coberturaFiscal', 'Aprovadas com nota ou boleto', d.indicadores?.coberturaFiscal?.valor, 'percentual', {
              descricao: d.indicadores?.coberturaFiscal?.definicao, comparacao: comparacao(d.indicadores?.coberturaFiscal),
            }),
            metricaPainel('p90DiasAteDecisao', 'P90 de dias até a decisão', d.ciclo.p90DiasAteDecisao, 'dias', {
              descricao: `mediana ${d.ciclo.medianaDiasAteDecisao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dia(s)`,
            }),
            metricaPainel('aguardandoConferencia', 'Documentos esperando conferência', d.conformidade.aguardandoConferencia, 'inteiro', {
              descricao: `${d.conformidade.comPendencia} devolvidos com pendência; ${d.conformidade.documentosRecebidos} recebidos no período`,
            }),
          ]
        : [],
      graficos: d
        ? [
            graficoPainel('pedidoAutorizado', 'barra', 'Pedido × autorizado ao longo do período', serie.map((i) => i.rotulo), [
              { nome: 'Pedido (R$)', valores: serie.map((i) => i.valorSolicitado) },
              { nome: 'Autorizado (R$)', valores: serie.map((i) => i.valorAprovado) },
            ], { metrica: 'valor', dimensao: `período (${this.granularidadeLabels[d.granularidade]})`, formato: 'moeda' }),
            graficoPainel('funil', 'funil', 'Onde o pedido se perde', d.funil.map((e) => e.rotulo), d.funil.map((e) => e.valor), {
              metrica: 'valor dos pedidos', dimensao: 'etapa', formato: 'moeda',
            }),
            graficoPainel('filaDecisao', 'barra_empilhada', 'Há quanto tempo cada pedido está parado', d.envelhecimento.map((f) => `${f.faixa} dias`),
              d.envelhecimento.map((f) => f.quantidade), { metrica: 'pedidos', dimensao: 'faixa de espera', formato: 'inteiro' }),
            graficoPainel('vencimentos', 'barra', 'O que vence a seguir', d.vencimentos.map((v) => v.rotulo), d.vencimentos.map((v) => v.valor), {
              metrica: 'valor a pagar', dimensao: 'faixa de vencimento', formato: 'moeda',
            }),
            graficoPainel('porStatus', 'rosca', 'Onde os pedidos estão', d.porStatus.map((s) => s.rotulo), d.porStatus.map((s) => s.valor), {
              metrica: 'valor', dimensao: 'situação', formato: 'moeda',
            }),
            graficoPainel(`ranking.${this.dimensaoRanking()}`, 'ranking', `Quem consome o orçamento — por ${this.rotuloRanking().toLowerCase()}`,
              ranking.map((i) => i.nome ?? 'Cadastro não disponível'), ranking.map((i) => i.valorAprovado), {
                metrica: 'valor autorizado', dimensao: this.rotuloRanking().toLowerCase(), formato: 'moeda', totalCategorias: ranking.length,
              }),
          ]
        : [],
      datasets: ['solicitacoes_compra', 'solicitacoes_pagamento', 'documentos_financeiros', 'fornecedores', 'bases_operacionais', 'processos_negocio', 'obras'],
      extras: d
        ? { 'Período de comparação': `${new Date(d.periodo.comparacaoInicio).toLocaleDateString('pt-BR')} a ${new Date(d.periodo.comparacaoFim).toLocaleDateString('pt-BR')}` }
        : undefined,
      sugestoes: [
        { rotulo: 'Por que o autorizado variou?', icone: 'pi pi-arrow-right-arrow-left', requer: ['dados', 'analise'],
          pergunta: 'O que explica a variação do valor autorizado em relação ao período anterior? Detalhe por fornecedor, centro de custo e tipo de despesa.' },
        { rotulo: 'Onde a fila trava', icone: 'pi pi-hourglass', requer: ['dados'],
          pergunta: 'Quais pedidos estão há mais tempo esperando decisão neste recorte, quem precisa decidir e quanto valor está parado?' },
        { rotulo: 'Despesas sem documento', icone: 'pi pi-file-excel', requer: ['dados'],
          pergunta: 'Quais despesas aprovadas deste recorte estão sem nota ou boleto, e quais fornecedores concentram essa pendência?' },
      ],
    };
  }

  /** O ranking abre o recorte no drawer quando a listagem consegue reproduzi-lo. */
  protected podeDetalharRanking(): boolean {
    return !!FILTRO_LISTAGEM_POR_DIMENSAO[this.dimensaoRanking()];
  }

  protected detalharRanking(item: RankingFinanceiroItem): void {
    this.drill.open({ dimension: this.dimensaoRanking(), value: item.chave });
  }

  private readonly colunasDrill: CesDataTableColumn<LinhaSolicitacaoDrill>[] = [
    { key: 'numero', header: 'Nº', type: 'integer', priority: 1, width: '4.5rem' },
    { key: 'formulario', header: 'Formulário', priority: 2 },
    { key: 'fornecedor', header: 'Fornecedor', priority: 1 },
    { key: 'solicitante', header: 'Solicitante', priority: 2 },
    { key: 'status', header: 'Status', priority: 1 },
    { key: 'valor', header: 'Valor', type: 'currency', priority: 1 },
    { key: 'abertaEm', header: 'Aberta em', type: 'date', priority: 2 },
    { key: 'documentos', header: 'Documentos', type: 'integer', priority: 3 },
  ];

  /** Recorte aberto (`?drill=fornecedor:<chave>`): solicitações abertas no período para o item do ranking. */
  protected readonly recorteDrill = computed<DrillRecorte<LinhaSolicitacaoDrill> | null>(() => {
    const alvo = this.drill.target();
    const dados = this.data();
    if (!alvo || !dados) return null;
    const dimensao = alvo.dimension as DimensaoRanking;
    const chaveListagem = FILTRO_LISTAGEM_POR_DIMENSAO[dimensao];
    if (!chaveListagem) return null;
    const ranking = { base: dados.porBase, centroCusto: dados.porCentroCusto, obra: dados.porObra, fornecedor: dados.porFornecedor,
      tipoDespesa: dados.porTipoDespesa, formulario: dados.porFormulario }[dimensao] ?? [];
    const item = ranking.find((linha) => linha.chave === alvo.value);
    if (!item) return null;

    const filtros = this.filtrosAplicados();
    const filtro: SolicitacaoFinanceiraFilter = {
      dataCriacaoInicio: filtros.inicio ?? dados.inicio,
      dataCriacaoFim: filtros.fim ?? dados.fim,
      page: 0,
      size: 100,
      sort: 'dataCriacao,desc',
    };
    const ignorados: string[] = [];
    for (const [chavePainel, chaveLista] of FILTROS_PAINEL_NA_LISTAGEM) {
      if (chaveLista === chaveListagem) continue;
      const selecao = filtros[chavePainel] as string[] | null | undefined;
      if (!selecao?.length) continue;
      if (selecao.length === 1) (filtro as Record<string, unknown>)[chaveLista] = selecao[0];
      else ignorados.push(chavePainel);
    }
    (['idSolicitante', 'prioridade'] as const).forEach((chave) => {
      if ((filtros[chave] as unknown[] | null | undefined)?.length) ignorados.push(chave);
    });
    (filtro as Record<string, unknown>)[chaveListagem] = item.chave;
    const rotulo = this.abasRanking.find((aba) => aba.value === dimensao)?.label ?? 'Item';

    return {
      chave: `${dimensao}:${item.chave}:${filtro.dataCriacaoInicio}:${filtro.dataCriacaoFim}`,
      eyebrow: `Ranking · ${rotulo}`,
      titulo: this.nomeOuPlaceholder(item.nome),
      descricao: 'Pedidos abertos no período (mesma coorte do painel), com os filtros aplicados.',
      kpis: [
        { rotulo: 'Pedidos', valor: String(item.quantidade), detalhe: `${item.aprovadas} aprovado(s) · ${item.rejeitadas} rejeitado(s)` },
        { rotulo: 'Pedido', valor: this.moedaCompacta(item.valorSolicitado) },
        { rotulo: 'Autorizado', valor: this.moedaCompacta(item.valorAprovado), detalhe: `${item.percentualValor.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}% do autorizado no painel` },
        { rotulo: 'Esperando decisão', valor: String(item.pendentes) },
      ],
      aviso: ignorados.length
        ? 'A listagem aceita um valor por filtro: filtros do painel com mais de um valor, solicitante e prioridade não foram aplicados a esta lista.'
        : undefined,
      colunas: this.colunasDrill,
      carregar: () => this.financeiro.listarSolicitacoes(filtros.tipo ?? 'AMBOS', filtro).pipe(
        map((pagina) => ({
          linhas: pagina.content.slice(0, 100).map((solicitacao) => this.linhaDrill(solicitacao)),
          total: pagina.page.totalElements,
        })),
      ),
      rotuloLinha: (linha) => `Pedido nº ${linha.numero}`,
      abrirLinha: (linha) => this.router.navigate([linha.tipo === 'PAGAMENTO' ? '/financeiro/pagamentos' : '/financeiro/compras', linha.id]),
      rotuloVerTudo: 'Ver solicitações na listagem',
      // A listagem lê os mesmos filtros pela URL (datas como AAAA-MM-DD, dia local de gestão).
      verTudo: () => {
        const { page: _p, size: _s, sort: _o, ...recorte } = filtro;
        const queryParams = Object.fromEntries(Object.entries(recorte)
          .filter(([, valor]) => valor !== null && valor !== undefined && valor !== '')
          .map(([chave, valor]) => [chave, chave.startsWith('dataCriacao') ? String(valor).slice(0, 10) : String(valor)]));
        this.router.navigate([filtros.tipo === 'PAGAMENTO' ? '/financeiro/pagamentos' : '/financeiro/compras'], { queryParams });
      },
      vazio: 'Nenhum pedido aberto no período para este item',
      nomeArquivo: `pedidos-${dimensao}`,
    };
  });

  private linhaDrill(solicitacao: SolicitacaoFinanceira): LinhaSolicitacaoDrill {
    return {
      id: solicitacao.id,
      numero: solicitacao.numero,
      tipo: solicitacao.formulario?.tipoSolicitacao ?? 'COMPRA',
      formulario: solicitacao.formulario?.titulo ?? solicitacao.formulario?.codigo ?? '—',
      fornecedor: solicitacao.fornecedor?.nome ?? solicitacao.fornecedor?.razaoSocial ?? '—',
      solicitante: solicitacao.nomeColaboradorSolicitante ?? solicitacao.usuarioSolicitante ?? '—',
      status: ROTULO_STATUS_SOLICITACAO[solicitacao.status] ?? solicitacao.status,
      valor: solicitacao.valor,
      abertaEm: solicitacao.dataCriacao,
      documentos: solicitacao.documentos?.length ?? 0,
    };
  }

  protected verSolicitacoes(): void {
    this.router.navigate(['/financeiro/compras']);
  }

  protected verDocumentos(): void {
    this.router.navigate(['/financeiro/documentos']);
  }

  /** O painel de leitura devolve o índice do cartão clicado. */
  protected abrirPrioridadePorIndice(indice: number): void {
    const prioridade = this.data()?.prioridades?.[indice];
    // Os cartões que não vieram de uma prioridade (autorização, tempo, cobertura)
    // ficam depois delas na lista e não têm para onde navegar.
    if (prioridade) {
      this.abrirPrioridade(prioridade);
    }
  }

  /**
   * Leva o gestor do cartão direto para os registros que o produziram. O `destino`
   * decide a listagem; o `drilldown` traz o recorte já pronto, e por isso não é
   * remontado aqui — refazer o filtro no cliente é como as duas telas passam a
   * mostrar conjuntos diferentes para o mesmo cartão.
   */
  protected abrirPrioridade(prioridade: PrioridadeFinanceiraItem): void {
    const drilldown = prioridade.drilldown;
    const queryParams: Record<string, string> = {};

    // As duas listagens aceitam um valor por dimensão; com mais de um, o recorte
    // fica com o gestor em vez de sair pela metade e parecer o conjunto inteiro.
    const primeiroDe = (chave: string): string | null => {
      const valor = drilldown[chave];
      return Array.isArray(valor) && valor.length === 1 ? String(valor[0]) : null;
    };

    if (prioridade.destino === 'DOCUMENTOS') {
      const situacao = primeiroDe('situacao');
      if (situacao) queryParams['situacao'] = situacao;
      this.router.navigate(['/financeiro/documentos'], { queryParams });
      return;
    }

    const status = primeiroDe('status');
    if (status) queryParams['status'] = status;
    this.router.navigate(['/financeiro/compras'], { queryParams });
  }

  // ------------------------------------------------------------------ //
  //  Gráficos
  // ------------------------------------------------------------------ //

  private prepararGraficos(dados: DashboardFinanceiroResponse): void {
    this.prepararSerie();
    this.prepararFunil(dados);
    this.prepararStatus();
    this.prepararEnvelhecimento(dados);
    this.prepararVencimentos(dados);
    this.prepararRanking();
    this.prepararDocumentos(dados);
    this.prepararCiclo(dados);
  }

  protected onUnidadeSerieChange(unidade: Unidade): void {
    this.unidadeSerie.set(unidade);
    this.prepararSerie();
  }

  protected onSerieModoChange(modo: SerieModo): void {
    this.serieModo.set(modo);
    this.prepararSerie();
  }

  protected onUnidadeStatusChange(unidade: Unidade): void {
    this.unidadeStatus.set(unidade);
    this.prepararStatus();
  }

  protected onDimensaoRankingChange(dimensao: DimensaoRanking): void {
    this.dimensaoRanking.set(dimensao);
    this.prepararRanking();
  }

  /**
   * Duas leituras da mesma série:
   * - `periodo`: barras de pedido x autorizado em cada intervalo, com o percentual
   *   autorizado como linha no eixo direito;
   * - `acumulado`: soma corrida das duas — é a leitura que mostra a lacuna crescendo
   *   ao longo do tempo, que a versão por intervalo dilui.
   */
  private prepararSerie(): void {
    const serie = this.data()?.serieTemporal ?? [];
    const emValor = this.unidadeSerie() === 'valor';
    const acumulado = this.serieModo() === 'acumulado';

    const pedido = serie.map((item) => (emValor ? item.valorSolicitado : item.abertas));
    const autorizado = serie.map((item) => (emValor ? item.valorAprovado : item.aprovadas));
    const primario = acumulado ? this.somaCorrida(pedido) : pedido;
    const secundario = acumulado ? this.somaCorrida(autorizado) : autorizado;

    const datasets: unknown[] = acumulado
      ? [
          {
            type: 'line', label: emValor ? 'Pedido (acumulado)' : 'Abertos (acumulado)',
            data: primario, borderColor: COR.submetido, backgroundColor: 'rgba(37, 99, 235, 0.10)',
            borderWidth: 2.5, pointRadius: 0, pointHoverRadius: 4, pointHitRadius: 16,
            tension: 0.3, fill: true, order: 2
          },
          {
            type: 'line', label: emValor ? 'Autorizado (acumulado)' : 'Aprovados (acumulado)',
            data: secundario, borderColor: COR.aprovado, backgroundColor: 'rgba(15, 118, 110, 0.16)',
            borderWidth: 2.5, pointRadius: 0, pointHoverRadius: 4, pointHitRadius: 16,
            tension: 0.3, fill: true, order: 1
          }
        ]
      : [
          {
            type: 'bar', label: emValor ? 'Pedido' : 'Abertos', data: primario,
            backgroundColor: COR.solicitado, hoverBackgroundColor: '#60a5fa',
            borderRadius: 6, maxBarThickness: 30, order: 3
          },
          {
            type: 'bar', label: emValor ? 'Autorizado' : 'Aprovados', data: secundario,
            backgroundColor: COR.aprovado, hoverBackgroundColor: '#0d9488',
            borderRadius: 6, maxBarThickness: 30, order: 2
          },
          {
            type: 'line', label: '% autorizado', yAxisID: 'y1',
            data: serie.map((item) => item.taxaAutorizacao),
            borderColor: COR.pendente, backgroundColor: COR.pendente, borderDash: [6, 4],
            borderWidth: 2, pointRadius: 0, pointHoverRadius: 4, tension: 0.25, fill: false, order: 1
          }
        ];

    this.serieData = { labels: serie.map((item) => item.rotulo), datasets };
    this.serieOptions = {
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: this.legenda(),
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) => {
              const valor = Number(contexto.parsed?.y ?? 0);
              const rotulo = contexto.dataset?.label ?? '';
              if (rotulo.startsWith('%')) return `${rotulo}: ${formatarPercentual(valor)}`;
              return `${rotulo}: ${emValor ? formatarMoeda(valor) : valor.toLocaleString('pt-BR')}`;
            }
          }
        }
      },
      scales: {
        x: this.eixoX(),
        y: {
          ...this.eixoY(),
          ticks: {
            ...this.eixoY().ticks,
            callback: (valor: number) =>
              emValor ? formatarMoedaCompacta(valor) : Number(valor).toLocaleString('pt-BR')
          }
        },
        ...(acumulado
          ? {}
          : {
              y1: {
                beginAtZero: true, position: 'right', border: { display: false },
                grid: { display: false },
                ticks: {
                  color: COR.pendente, maxTicksLimit: 4, padding: 6,
                  callback: (valor: number) => `${valor}%`
                }
              }
            })
      }
    };
  }

  private somaCorrida(valores: number[]): number[] {
    let total = 0;
    return valores.map((valor) => (total += valor));
  }

  /**
   * Barras horizontais decrescentes — a forma de funil que se lê sem legenda. As
   * etapas de vazamento (rejeitadas, canceladas) saem da cascata e aparecem só na
   * legenda abaixo: empilhá-las aqui esconderia de qual lado o pedido se perdeu.
   */
  private prepararFunil(dados: DashboardFinanceiroResponse): void {
    const etapas = dados.funil.filter((etapa) => !etapa.vazamento);
    const vazamentos = dados.funil.filter((etapa) => etapa.vazamento);

    this.funilData = {
      labels: etapas.map((etapa) => etapa.rotulo),
      datasets: [
        {
          label: 'Valor', data: etapas.map((etapa) => etapa.valor),
          backgroundColor: etapas.map((etapa) => this.corDaEtapa(etapa.etapa)),
          borderRadius: 6, maxBarThickness: 42
        }
      ]
    };
    this.funilOptions = {
      indexAxis: 'y',
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) => formatarMoeda(Number(contexto.parsed?.x ?? 0)),
            afterLabel: (contexto: any) => {
              const etapa = etapas[contexto.dataIndex];
              const linhas = [
                `${etapa.quantidade} pedido(s) · ${formatarPercentual(etapa.percentualDoTopo)} do total`
              ];
              if (etapa.percentualDaEtapaAnterior !== null && contexto.dataIndex > 0) {
                linhas.push(`${formatarPercentual(etapa.percentualDaEtapaAnterior)} da etapa anterior`);
              }
              if (contexto.dataIndex === 0) {
                vazamentos
                  .filter((vazamento) => vazamento.quantidade > 0)
                  .forEach((vazamento) =>
                    linhas.push(`${vazamento.quantidade} ${vazamento.rotulo.toLowerCase()} — ${formatarMoeda(vazamento.valor)}`));
              }
              return linhas;
            }
          }
        }
      },
      scales: {
        x: {
          beginAtZero: true, border: { display: false },
          grid: { color: COR.grade, drawTicks: false },
          ticks: { color: COR.eixo, maxTicksLimit: 5, padding: 6, callback: (v: number) => formatarMoedaCompacta(v) }
        },
        y: {
          grid: { display: false }, border: { display: false },
          ticks: { color: '#486581', font: { size: 11 }, crossAlign: 'far' }
        }
      }
    };
  }

  private prepararStatus(): void {
    const linhas = this.data()?.porStatus ?? [];
    const emValor = this.unidadeStatus() === 'valor';
    this.statusData = {
      labels: linhas.map((item) => item.rotulo),
      datasets: [
        {
          data: linhas.map((item) => (emValor ? item.valor : item.quantidade)),
          backgroundColor: [COR.neutro, COR.pendente, COR.aprovado, COR.rejeitado, '#cbd5e1'],
          borderWidth: 0, hoverOffset: 6
        }
      ]
    };
    this.statusChartOptions = {
      maintainAspectRatio: false,
      cutout: '62%',
      plugins: {
        legend: {
          position: 'right',
          labels: { boxWidth: 10, boxHeight: 10, usePointStyle: true, color: '#486581', font: { size: 11 }, padding: 12 }
        },
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) => {
              const item = linhas[contexto.dataIndex];
              return emValor
                ? `${formatarMoeda(item.valor)} · ${formatarPercentual(item.percentualValor)}`
                : `${item.quantidade} pedido(s) · ${formatarPercentual(item.percentualQuantidade)}`;
            }
          }
        }
      }
    };
  }

  /** Barras empilhadas por domínio dentro de cada faixa de idade da fila. */
  private prepararEnvelhecimento(dados: DashboardFinanceiroResponse): void {
    const faixas = dados.envelhecimento;
    this.envelhecimentoData = {
      labels: faixas.map((faixa) => `${faixa.faixa} dias`),
      datasets: [
        {
          label: 'Compras', data: faixas.map((faixa) => faixa.porTipo.COMPRA ?? 0),
          backgroundColor: COR.submetido, borderRadius: 5, maxBarThickness: 44, stack: 'fila'
        },
        {
          label: 'Pagamentos', data: faixas.map((faixa) => faixa.porTipo.PAGAMENTO ?? 0),
          backgroundColor: COR.pendente, borderRadius: 5, maxBarThickness: 44, stack: 'fila'
        }
      ]
    };
    this.envelhecimentoOptions = {
      maintainAspectRatio: false,
      plugins: {
        legend: this.legenda(),
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            footer: (itens: any[]) => {
              const faixa = faixas[itens?.[0]?.dataIndex ?? 0];
              return faixa ? `${formatarMoeda(faixa.valor)} pedidos nesta faixa` : '';
            }
          },
          footerColor: '#bfdbfe', footerFont: { size: 11, weight: '600' }
        }
      },
      scales: {
        x: { ...this.eixoX(), stacked: true },
        y: { ...this.eixoY(), stacked: true, ticks: { ...this.eixoY().ticks, precision: 0 } }
      }
    };
  }

  /**
   * A régua de vencimentos. A faixa vencida tem cor própria e vem primeiro porque
   * não é "o próximo balde": é o passivo que já estourou.
   */
  private prepararVencimentos(dados: DashboardFinanceiroResponse): void {
    const faixas = dados.vencimentos;
    this.vencimentoData = {
      labels: faixas.map((faixa) => faixa.rotulo),
      datasets: [
        {
          label: 'A pagar', data: faixas.map((faixa) => faixa.valor),
          backgroundColor: faixas.map((faixa) => this.corDoVencimento(faixa.faixa)),
          borderRadius: 5, maxBarThickness: 40
        }
      ]
    };
    this.vencimentoOptions = {
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) => formatarMoeda(Number(contexto.parsed?.y ?? 0)),
            afterLabel: (contexto: any) => {
              const faixa = faixas[contexto.dataIndex];
              return [`${faixa.quantidade} despesa(s)`, this.textoDoExtremo(faixa)];
            }
          }
        }
      },
      scales: {
        x: this.eixoX(),
        y: {
          ...this.eixoY(),
          ticks: { ...this.eixoY().ticks, callback: (v: number) => formatarMoedaCompacta(v) }
        }
      }
    };
  }

  private prepararRanking(): void {
    const linhas = this.linhasRanking().slice(0, MAX_BARRAS_NO_GRAFICO);
    const emValor = true;
    this.rankingData = {
      labels: linhas.map((item) => this.nomeOuPlaceholder(item.nome)),
      datasets: [
        {
          label: 'Valor autorizado', data: linhas.map((item) => item.valorAprovado),
          backgroundColor: CORES_CATEGORICAS.slice(0, Math.max(1, linhas.length)),
          borderRadius: 5, maxBarThickness: 18
        }
      ]
    };
    this.rankingOptions = this.opcoesBarraHorizontal((indice) => {
      const item = linhas[indice];
      if (!item) return [];
      return [
        `${item.quantidade} pedido(s) · ${formatarPercentual(item.percentualValor)} do autorizado`,
        `${item.aprovadas} aprovado(s), ${item.rejeitadas} rejeitado(s), ${item.pendentes} pendente(s)`,
        `Ticket médio: ${formatarMoeda(item.ticketMedio)}`,
        `${formatarPercentual(item.percentualComDocumentoFiscal)} das aprovadas com nota ou boleto`
      ];
    }, emValor);
  }

  /** Rosca por natureza do documento — a leitura de "o que a contabilidade recebe". */
  private prepararDocumentos(dados: DashboardFinanceiroResponse): void {
    const linhas = dados.documentosPorTipo.filter((item) => item.quantidade > 0);
    this.documentoData = {
      labels: linhas.map((item) => item.rotulo),
      datasets: [
        {
          data: linhas.map((item) => item.quantidade),
          backgroundColor: CORES_CATEGORICAS.slice(0, Math.max(1, linhas.length)),
          borderWidth: 0, hoverOffset: 6
        }
      ]
    };
    this.documentoOptions = {
      maintainAspectRatio: false,
      cutout: '58%',
      plugins: {
        legend: {
          position: 'right',
          labels: { boxWidth: 10, boxHeight: 10, usePointStyle: true, color: '#486581', font: { size: 11 }, padding: 10 }
        },
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) => {
              const item = linhas[contexto.dataIndex];
              return `${item.quantidade} documento(s) · ${formatarPercentual(item.percentualQuantidade)}`;
            },
            afterLabel: (contexto: any) => {
              const item = linhas[contexto.dataIndex];
              return item.emAberto > 0 ? `${item.emAberto} ainda em aberto` : 'Todos já conferidos';
            }
          }
        }
      }
    };
  }

  /**
   * Mediana ao lado do P90 em cada etapa. É a comparação que mostra se o processo é
   * previsível: barras próximas significam ritmo constante; um P90 muito acima da
   * mediana significa que existe um punhado de casos que trava.
   */
  private prepararCiclo(dados: DashboardFinanceiroResponse): void {
    const ciclo = dados.ciclo;
    this.cicloData = {
      labels: ['Preenchimento', 'Até a decisão', 'Do começo ao fim'],
      datasets: [
        {
          label: 'Metade dos casos (mediana)',
          data: [ciclo.medianaDiasAteSubmissao, ciclo.medianaDiasAteDecisao, ciclo.medianaDiasCicloTotal],
          backgroundColor: COR.submetido, borderRadius: 5, maxBarThickness: 26
        },
        {
          label: '9 em cada 10 casos (P90)',
          data: [ciclo.p90DiasAteSubmissao, ciclo.p90DiasAteDecisao, ciclo.p90DiasCicloTotal],
          backgroundColor: COR.pendente, borderRadius: 5, maxBarThickness: 26
        }
      ]
    };
    this.cicloOptions = {
      maintainAspectRatio: false,
      plugins: {
        legend: this.legenda(),
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) =>
              `${contexto.dataset?.label}: ${Number(contexto.parsed?.y ?? 0).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dia(s)`
          }
        }
      },
      scales: {
        x: this.eixoX(),
        y: { ...this.eixoY(), ticks: { ...this.eixoY().ticks, callback: (v: number) => `${v}d` } }
      }
    };
  }

  // ------------------------------------------------------------------ //
  //  Blocos de configuração reaproveitados pelos gráficos
  // ------------------------------------------------------------------ //

  private opcoesBarraHorizontal(detalhe: (indice: number) => string[], emValor = true): unknown {
    return {
      indexAxis: 'y',
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          ...this.tooltip(),
          callbacks: {
            label: (contexto: any) => {
              const valor = Number(contexto.parsed?.x ?? 0);
              return emValor ? formatarMoeda(valor) : valor.toLocaleString('pt-BR');
            },
            afterLabel: (contexto: any) => detalhe(contexto.dataIndex)
          }
        }
      },
      scales: {
        x: {
          beginAtZero: true, border: { display: false },
          grid: { color: COR.grade, drawTicks: false },
          ticks: { color: COR.eixo, maxTicksLimit: 5, padding: 6, callback: (v: number) => formatarMoedaCompacta(v) }
        },
        y: {
          grid: { display: false }, border: { display: false },
          ticks: { color: '#486581', font: { size: 11 }, crossAlign: 'far' }
        }
      }
    };
  }

  private legenda(): unknown {
    return {
      display: true, position: 'top', align: 'end',
      labels: { boxWidth: 10, boxHeight: 10, usePointStyle: true, color: '#627d98', font: { size: 11 } }
    };
  }

  private tooltip(): Record<string, unknown> {
    return {
      mode: 'index', intersect: false, backgroundColor: COR.tooltip,
      titleColor: '#d9e2ec', bodyColor: '#fff', padding: 11, cornerRadius: 8
    };
  }

  private eixoX(): EixoChart {
    return {
      grid: { display: false }, border: { display: false },
      ticks: { color: COR.rotulo, font: { size: 10 }, maxTicksLimit: 12, maxRotation: 0, padding: 8 }
    };
  }

  private eixoY(): EixoChart {
    return {
      beginAtZero: true, grace: '8%', border: { display: false },
      grid: { color: COR.grade, drawTicks: false },
      ticks: { color: COR.eixo, maxTicksLimit: 5, padding: 10 }
    };
  }
}
