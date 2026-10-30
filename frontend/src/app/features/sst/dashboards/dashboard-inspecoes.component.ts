import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { ThemeService } from '../../../core/services/theme.service';
import {
  PALETAS_GRADIENTE_PRIMARIA,
  criarGradienteBarra,
  obterPaletaPrimaria
} from './dashboard-eventos-chart.config';
import { ChartModule } from 'primeng/chart';
import { SkeletonModule } from 'primeng/skeleton';
import { TooltipModule } from 'primeng/tooltip';

import {
  BaseOperacionalResponse,
  CentroFaturamentoResponse,
  EquipeResponse,
  ProcessoNegocioResponse
} from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { GranularidadeDashboard } from '../../../core/models/sst-dashboard.models';
import {
  DashboardInspecoesResponse,
  DashboardSstFilter,
  FILTROS_INCOMPATIVEIS_COM_META,
  FormularioInspecaoResumoResponse,
  InspecaoFilters,
  RankingItem,
  STATUS_INSPECAO_LABELS,
  StatusInspecao,
  TipoInspecaoResponse
} from '../../../core/models/sst.models';
import { CentrosCustoLookupService } from '../../../core/services/centros-custo-lookup.service';
import { RhService } from '../../../core/services/rh.service';
import { ArquivoExportacao, SstService } from '../../../core/services/sst.service';
import { normalizarSelecao, temSelecao } from '../../../core/utils/http-params.util';
import { toSaoPauloIso } from '../../../shared/utils/dashboard-date.utils';
import { DashboardMetricCardComponent } from '../../../shared/components/dashboard/dashboard-ui.components';
import { DashboardAnaliseComponent } from '../../../shared/components/dashboard/dashboard-analise.component';
import { AlvoAnalise, VistaAnalise } from '../../../shared/components/dashboard/dashboard-analise.models';
import { NavegacaoAnaliseService } from '../../../shared/components/dashboard/dashboard-analise.navegacao';
import { DashboardVisaoGerencialComponent } from '../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import {
  CesButtonComponent,
  CesCellDirective,
  CesColumnFilter,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDrillStateService,
  CesMeterComponent,
  CesRankBadgeComponent,
  CesStatusComponent,
  CesBooleanControlComponent,
  CesDatepickerComponent,
  CesFilterChip,
  CesFormDrawerComponent,
  CesFilterPanelComponent,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  NotificationService
} from '@ces/ui';
import { chipsDoRecorte, opcoesDe } from '../../../shared/components/dashboard/dashboard-filtros';
import { ColaboradorInspecionadoItem, EquipeInspecaoItem, InspetorMetaItem } from '../../../core/models/sst-dashboard.models';
import { GrupoInspecao, vistaInspecoes } from './analise/analise-inspecoes';
import { visaoInspecoes } from './analise/visao-inspecoes';
import {
  CopilotoAreaComponent,
  CopilotoDashboardContext,
  CopilotoGatilhoComponent,
  CopilotoPainelService,
  CopilotoPerguntarComponent,
  tabelaPainel,
  filtroPainel,
  graficoPainel,
  metricaPainel,
  CapacidadeSugestao,
  periodoPainel,
  rotuloDasOpcoes,
} from '../../copiloto/painel';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { atualizarPeriodicamente } from '../../../shared/components/dashboard/atualizacao-automatica';

type DistributionDimension = 'processos' | 'processosInspetor' | 'tiposInspecao' | 'bases' | 'formularios';
type SerieModo = 'periodo' | 'acumulado';

/** Id estável da tabela de inspetores no contexto do Copiloto (alvo de `APPLY_FILTER`). */
const TABELA_INSPETORES = 'sst.inspecoes#porInspetor';

/** Quantos inspetores entram no comparativo realizado × meta antes de virar ruído. */
const MAX_INSPETORES_NO_GRAFICO = 12;

/** Aba da distribuição → grupo da análise. */
const DIMENSAO_GRUPO: Record<DistributionDimension, GrupoInspecao> = {
  processos: 'processo',
  processosInspetor: 'processoInspetor',
  tiposInspecao: 'tipo',
  bases: 'base',
  formularios: 'formulario'
};


@Component({
  selector: 'app-sst-dashboard-inspecoes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesBooleanControlComponent, CesFormDrawerComponent, CesDatepickerComponent, CesFilterPanelComponent, CesMultiSelectComponent, CesPageHeaderComponent, CesSelectComponent,
    CommonModule, DecimalPipe, ChartModule, SkeletonModule, TooltipModule, DashboardMetricCardComponent,
    CesButtonComponent, CopilotoAreaComponent, CopilotoGatilhoComponent, CesCellDirective, CesDataTableComponent, CesMeterComponent, CesRankBadgeComponent,
    CesStatusComponent, CopilotoPerguntarComponent, DashboardAnaliseComponent, DashboardVisaoGerencialComponent],
  providers: [CesDrillStateService, NavegacaoAnaliseService],
  templateUrl: './dashboard-inspecoes.component.html',
  styles: [`
    :host .dash-metrics { grid-template-columns: repeat(4, minmax(0, 1fr)); }
    .dash-cell-name { color: #243b53; font-size: .78rem; font-weight: 600; }
    .dash-pct-pill {
      display: inline-block;
      padding: .15rem .45rem;
      border-radius: .4rem;
      background: #f1f5f9;
      color: #475569;
      font-size: .7rem;
      font-weight: 650;
      font-variant-numeric: tabular-nums;
    }
    .dash-badge {
      display: inline-flex;
      align-items: center;
      gap: .3rem;
      padding: .2rem .5rem;
      border-radius: .4rem;
      background: #f1f5f9;
      color: #475569;
      font-size: .7rem;
      font-weight: 650;
    }
    .dash-badge--success { background: var(--ces-success-soft); color: var(--ces-success-text); }
    .dash-badge--warning { background: var(--ces-warning-soft); color: var(--ces-warning-text); }
    .dash-badge--danger { background: var(--ces-danger-soft); color: var(--ces-danger-text); }
    .dash-badge i { font-size: .6rem; }

    :host-context(.app-dark), :host-context(.dark) {
      .dash-cell-name { color: #e2e8f0; }
      .dash-pct-pill { background: var(--ces-surface-muted, #334155); color: #cbd5e1; }
      .dash-badge { background: var(--ces-surface-muted, #334155); color: #cbd5e1; }
    }

    .dash-drill-bar {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      justify-content: space-between;
      gap: .75rem;
      margin-top: .75rem;
    }
    .dash-drill-bar ces-select { min-width: 16rem; }
    .dash-chart--drill { cursor: pointer; }

    @media (max-width: 1080px) {
      :host .dash-grid .dash-col-6, :host .dash-grid .dash-col-8, :host .dash-grid .dash-col-4, :host .dash-grid .dash-col-7, :host .dash-grid .dash-col-5 {
        grid-column: span 12;
      }
    }
  `]
})
export class DashboardInspecoesComponent implements OnInit {
  private readonly sst = inject(SstService);
  private readonly rh = inject(RhService);
  private readonly centrosCustoLookup = inject(CentrosCustoLookupService);
  private readonly themeService = inject(ThemeService);
  private readonly notificacoes = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly analise = inject(NavegacaoAnaliseService);
  private readonly copiloto = inject(CopilotoPainelService);
  protected readonly copilotoDisponivel = this.copiloto.disponivelParaUsuario();

  constructor() {
    // Link de análise que não existe mais no recorte: fecha o painel.
    effect(() => {
      if (this.analise.alvo() && this.data() && !this.loading() && !this.vistaAnalise()) this.analise.fechar();
    });
    // "Aplicar filtro" confirmado numa resposta do Copiloto para a tabela de inspetores.
    effect(() => {
      const pedido = this.copiloto.pedidoFiltro();
      if (!pedido || pedido.alvo !== TABELA_INSPETORES) return;
      this.filtrosInspetores.set([...pedido.filtros]);
      this.copiloto.consumirFiltro(pedido.sequencia);
      document.getElementById('dash-inspetores')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    effect(() => {
      this.themeService.primaryColor();
      if (this.data()) {
        this.prepararSerie();
      }
    });
  }
  private readonly destroyRef = inject(DestroyRef);
  private readonly equipeLookup = new RemoteLookup<EquipeResponse>(
    {
      identify: (equipe) => equipe.id,
      selectedKeys: () => this.filter.idEquipe ?? [],
      load: ({ query, page, size }) =>
        this.rh.listarEquipes({ ativo: true, buscaGlobal: query, page, size, sort: 'codigo,asc' })
    },
    this.destroyRef
  );

  protected readonly loading = signal(false);
  protected readonly exportando = signal(false);
  protected readonly data = signal<DashboardInspecoesResponse | null>(null);
  protected readonly atualizadoEm = signal<Date | null>(null);
  protected readonly periodoAtivo = signal<string>('');
  protected readonly distributionDimension = signal<DistributionDimension>('processos');
  protected readonly serieModo = signal<SerieModo>('periodo');
  protected readonly mostrarDesvios = signal(false);
  protected readonly tiposInspecao = signal<TipoInspecaoResponse[]>([]);
  protected readonly processos = signal<ProcessoNegocioResponse[]>([]);
  protected readonly equipes = this.equipeLookup.items;
  protected readonly equipesLoading = this.equipeLookup.loading;
  protected readonly basesOperacionais = signal<BaseOperacionalResponse[]>([]);
  protected readonly centrosCusto = signal<CentroFaturamentoResponse[]>([]);
  protected readonly formularios = signal<FormularioInspecaoResumoResponse[]>([]);
  /** Filtros que estavam ativos na consulta que produziu `data()`. */
  private readonly filtrosAplicados = signal<DashboardSstFilter>({});

  private readonly atualizacao = atualizarPeriodicamente(() => {
    this.buscar(this.filtrosAplicados(), true);
  }, { pode: () => !this.loading() && !!this.data() });

  /** Dimensões de múltipla seleção — ordem usada na URL e na contagem de filtros. */
  private readonly chavesDeSelecao: (keyof DashboardSstFilter)[] = [
    'idTipoInspecao', 'idProcesso', 'idProcessoInspetor', 'idEquipe', 'idBaseOperacional', 'idCentroCusto', 'idFormulario'
  ];

  /** Quantas dimensões estão restringindo o recorte exibido. */
  /** Cadastros dos filtros como opções (as listas e os chips usam os mesmos rótulos). */
  protected readonly opcoes = {
    processos: computed(() => opcoesDe(this.processos(), 'nome')),
    tiposInspecao: computed(() => opcoesDe(this.tiposInspecao(), 'nome')),
    equipes: computed(() => opcoesDe(this.equipes(), 'codigo')),
    centrosCusto: computed(() => opcoesDe(this.centrosCusto(), 'nome')),
    basesOperacionais: computed(() => opcoesDe(this.basesOperacionais(), 'nome')),
    formularios: computed(() => opcoesDe(this.formularios(), 'titulo'))
  };
  /** Recorte aplicado como chips (cada um remove o próprio filtro); o agrupamento do gráfico não recorta. */
  protected readonly chips = computed<CesFilterChip[]>(() => {
    const f = this.filtrosAplicados();
    return chipsDoRecorte(f as Record<string, unknown>, [
      { chave: 'idProcesso', rotulo: 'Processo da inspeção', opcoes: this.opcoes.processos() },
      { chave: 'idProcessoInspetor', rotulo: 'Processo do inspetor', opcoes: this.opcoes.processos() },
      { chave: 'idTipoInspecao', rotulo: 'Tipo de inspeção', opcoes: this.opcoes.tiposInspecao() },
      { chave: 'idEquipe', rotulo: 'Equipe', opcoes: this.opcoes.equipes() },
      { chave: 'idCentroCusto', rotulo: 'Centro de custo', opcoes: this.opcoes.centrosCusto() },
      { chave: 'idBaseOperacional', rotulo: 'Base operacional', opcoes: this.opcoes.basesOperacionais() },
      { chave: 'idFormulario', rotulo: 'Formulário', opcoes: this.opcoes.formularios() }
    ], { inicio: f.inicio, fim: f.fim });
  });

  protected removerFiltro(chave: string): void {
    if (chave === 'periodo') {
      this.dataInicio = null;
      this.dataFim = null;
    } else {
      this.filter = { ...this.filter, [chave]: [] };
    }
    this.aplicar();
  }

  /** Veredito do recorte e leituras acionáveis (cada uma abre a análise que a sustenta). */
  protected readonly visaoGerencial = computed(() => {
    const d = this.data();
    return d ? visaoInspecoes(d, this.metaComparavel()) : null;
  });

  /**
   * `idProcesso`, `idProcessoInspetor`, `idEquipe` e `idFormulario` restringem só o realizado, não a meta:
   * com qualquer um deles ativo o `%` atingido fica distorcido. Base operacional e
   * centro de custo restringem os dois lados, então não entram nesta lista.
   */
  protected readonly metaComparavel = computed(() => {
    const filtros = this.filtrosAplicados();
    return !FILTROS_INCOMPATIVEIS_COM_META.some((chave) => temSelecao(filtros[chave] as string[] | null));
  });

  protected readonly distributionRows = computed<RankingItem[]>(() => {
    const current = this.data();
    if (!current) return [];
    return {
      processos: current.porProcesso,
      processosInspetor: current.porProcessoInspetor,
      tiposInspecao: current.porTipoInspecao,
      bases: current.porBaseOperacional,
      formularios: current.porFormulario
    }[this.distributionDimension()] ?? [];
  });
  protected readonly distributionLabel = computed(() =>
    this.distributionTabs.find((tab) => tab.value === this.distributionDimension())?.label ?? 'Agrupamento'
  );

  protected readonly distributionTabs = [
    { label: 'Processo da inspeção', value: 'processos' as const },
    { label: 'Processo do inspetor', value: 'processosInspetor' as const },
    { label: 'Tipos', value: 'tiposInspecao' as const },
    { label: 'Bases', value: 'bases' as const },
    { label: 'Formulários', value: 'formularios' as const }
  ];

  protected readonly serieModoTabs = [
    { label: 'Por período', value: 'periodo' as const },
    { label: 'Acumulado', value: 'acumulado' as const }
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

  /** Análise detalhada aberta (`?drill=`), resolvida contra os dados em tela. */
  protected readonly vistaAnalise = computed<VistaAnalise | null>(() => {
    const alvo = this.analise.alvo();
    const d = this.data();
    if (!alvo || !d) return null;
    return vistaInspecoes(alvo, { d, filtros: this.filtrosAplicados(), sst: this.sst, metaComparavel: this.metaComparavel() });
  });

  /** Períodos da série como opções do "Analisar um período" (caminho por teclado e toque). */
  protected readonly opcoesPeriodo = computed(() =>
    (this.data()?.serieTemporal ?? []).map((p) => ({ label: `${p.rotulo} — ${p.realizado} inspeç${p.realizado === 1 ? 'ão' : 'ões'}`, value: p.inicio }))
  );

  protected readonly colunasEquipes: CesDataTableColumn<EquipeInspecaoItem>[] = [
    { key: 'nomeEquipe', header: 'Equipe', priority: 1, value: (e) => this.nomeOuPlaceholder(e.nomeEquipe) },
    { key: 'inspecoes', header: 'Inspeções', type: 'integer', priority: 1 },
    { key: 'percentualDoTotal', header: '% do total', type: 'percent', priority: 3 },
    { key: 'desvios', header: 'Desvios', type: 'integer', priority: 2 },
    { key: 'desviosPorInspecao', header: 'Desvios/insp.', type: 'number', priority: 3 },
    { key: 'percentualSemDesvio', header: '% sem desvio', type: 'custom', value: (e) => e.percentualSemDesvio, priority: 1 },
    { key: 'colaboradoresInspecionados', header: 'Colaboradores', type: 'integer', priority: 3 },
    { key: 'diasSemInspecao', header: 'Dias s/ insp.', type: 'custom', value: (e) => e.diasSemInspecao, priority: 2 }
  ];
  protected readonly colunasColaboradores: CesDataTableColumn<ColaboradorInspecionadoItem>[] = [
    { key: 'nomeColaborador', header: 'Colaborador', priority: 1, value: (c) => this.nomeOuPlaceholder(c.nomeColaborador) },
    { key: 'vezesInspecionado', header: 'Inspeções', type: 'integer', priority: 1 },
    { key: 'desviosRecebidos', header: 'Desvios', type: 'custom', value: (c) => c.desviosRecebidos, priority: 1 },
    { key: 'ultimaInspecao', header: 'Última inspeção', priority: 2, value: (c) => c.ultimaInspecao, display: (c) => (c.ultimaInspecao ? new Date(c.ultimaInspecao).toLocaleDateString('pt-BR') : '—') },
    { key: 'diasDesdeUltimaInspecao', header: 'Dias s/ insp.', type: 'integer', priority: 2 }
  ];
  protected readonly chaveEquipe = (e: EquipeInspecaoItem) => e.idEquipe;
  protected readonly rotuloEquipe = (e: EquipeInspecaoItem) => this.nomeOuPlaceholder(e.nomeEquipe);
  protected readonly chaveColaborador = (c: ColaboradorInspecionadoItem) => c.idColaborador;
  protected readonly rotuloColaborador = (c: ColaboradorInspecionadoItem) => this.nomeOuPlaceholder(c.nomeColaborador);

  protected readonly colunasInspetores: CesDataTableColumn<InspetorMetaItem & { posicao: number }>[] = [
    { key: 'posicao', header: 'Posição', type: 'custom', value: (item) => item.posicao, width: '5.5rem', priority: 1 },
    { key: 'nomeInspetor', header: 'Inspetor', priority: 1, value: (item) => item.nomeInspetor ?? 'Sem nome' },
    { key: 'realizado', header: 'Realizado', type: 'integer', width: '6.5rem', priority: 1 },
    { key: 'percentualAtingido', header: '% da meta', type: 'custom', value: (item) => item.percentualAtingido, width: '11rem', priority: 1 },
    { key: 'desvios', header: 'Desvios', type: 'custom', value: (item) => item.desvios, width: '6rem', priority: 2 },
    { key: 'percentualDoTotal', header: '% do total', type: 'percent', width: '6.5rem', priority: 2 }
  ];

  /** Ranking com posição fixa (pela ordem do backend), estável sob ordenação e busca. */
  protected readonly linhasInspetores = computed(() =>
    (this.data()?.porInspetor ?? []).map((item, indice) => ({ ...item, posicao: indice + 1 }))
  );
  /** Filtros da tabela de inspetores (chips) — do usuário ou sugeridos pelo Copiloto e confirmados. */
  protected readonly filtrosInspetores = signal<CesColumnFilter[]>([]);
  protected readonly chaveInspetor = (item: InspetorMetaItem) => item.idInspetor;
  protected readonly rotuloInspetor = (item: InspetorMetaItem) => this.nomeOuPlaceholder(item.nomeInspetor);

  /** Contexto do dashboard para o Copiloto: o recorte aplicado e os números em tela. */
  protected readonly contextoCopiloto = computed(() => this.montarContextoCopiloto());

  protected dataInicio: Date | null = null;
  protected dataFim: Date | null = null;
  protected filter: DashboardSstFilter = this.filtroVazio();

  protected serieData: unknown;
  protected serieOptions: unknown;
  protected inspetorData: unknown;
  protected inspetorOptions: unknown;
  protected distribuicaoData: unknown;
  protected readonly distribuicaoOptions = this.donutOptions();

  protected showExportDialog = false;
  protected exportTipo = 'inspecoes';
  protected exportFormato = 'resumo';
  protected exportDataDe: Date | null = null;
  protected exportDataAte: Date | null = null;
  protected exportFilters: InspecaoFilters = {};
  protected exportApenasMinhas = false;
  protected readonly exportOptions = [
    { label: 'Inspeções', value: 'inspecoes' }, { label: 'Desvios', value: 'desvios' }
  ];
  protected readonly formatOptions = [
    { label: 'Resumido', value: 'resumo' }, { label: 'Detalhado', value: 'detalhado' }
  ];
  protected exportArquivo: ArquivoExportacao = 'xlsx';
  protected readonly arquivoOptions = [
    { label: 'Excel (.xlsx)', value: 'xlsx' as const }, { label: 'CSV (.csv)', value: 'csv' as const }
  ];
  // Os valores precisam bater com o enum do backend (`CONCLUIDA`/`CANCELADA`).
  protected readonly statusOptions = (Object.keys(STATUS_INSPECAO_LABELS) as StatusInspecao[])
    .map((value) => ({ label: STATUS_INSPECAO_LABELS[value], value }));
  protected readonly desvioOptions = [
    { label: 'Com desvios', value: true }, { label: 'Sem desvios', value: false }
  ];

  ngOnInit(): void {
    this.restaurarFiltrosDaUrl();
    this.hidratarEquipesSelecionadas();
    this.carregarLookups();
    this.carregar();
  }

  protected aplicar(): void {
    if (!this.periodoValido()) return;
    this.periodoAtivo.set('');
    this.carregar();
  }
  protected openEquipes(): void { this.equipeLookup.open(); }
  protected searchEquipes(event: { filter?: string }): void { this.equipeLookup.search(event.filter ?? ''); }
  protected loadMoreEquipes(): void { this.equipeLookup.loadNext(); }

  protected limpar(): void {
    this.dataInicio = null;
    this.dataFim = null;
    this.filter = this.filtroVazio();
    this.periodoAtivo.set('');
    this.carregar();
  }

  private filtroVazio(): DashboardSstFilter {
    return {
      idTipoInspecao: [], idProcesso: [], idProcessoInspetor: [], idEquipe: [], idBaseOperacional: [], idCentroCusto: [],
      idFormulario: [], granularidade: null
    };
  }

  /** Recompõe o recorte compartilhado por link (`?idBaseOperacional=a&idBaseOperacional=b`). */
  private restaurarFiltrosDaUrl(): void {
    const query = this.route.snapshot.queryParamMap;
    this.chavesDeSelecao.forEach((chave) => {
      (this.filter[chave] as string[]) = normalizarSelecao(query.getAll(chave));
    });
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
  private sincronizarUrl(filter: DashboardSstFilter): void {
    const queryParams: Record<string, string[] | string | null> = {};
    this.chavesDeSelecao.forEach((chave) => {
      const selecao = (filter[chave] as string[] | null) ?? [];
      queryParams[chave] = selecao.length ? selecao : null;
    });
    queryParams['granularidade'] = filter.granularidade ?? null;
    queryParams['inicio'] = filter.inicio ?? null;
    queryParams['fim'] = filter.fim ?? null;
    // `merge` preserva o `?drill=` do painel de detalhe aberto.
    this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }

  /** Clique numa barra/ponto da série (p-chart): abre a análise daquele período. */
  protected onSerieDataSelect(evento: { element?: { index?: number } }): void {
    const indice = evento?.element?.index;
    const ponto = typeof indice === 'number' ? this.data()?.serieTemporal[indice] : undefined;
    if (ponto) this.analisarPeriodo(ponto.inicio);
  }

  protected analisarPeriodo(inicio: string | null): void {
    const d = this.data();
    if (!d || !inicio) return;
    this.analise.abrir(d.granularidade === 'DIA' ? 'dia' : d.granularidade === 'MES' ? 'mes' : 'ano', inicio);
  }

  /** Clique numa fatia de "Onde as inspeções aconteceram". */
  protected onDistribuicaoSelect(evento: { element?: { index?: number } }): void {
    const indice = evento?.element?.index;
    const item = typeof indice === 'number' ? this.distributionRows()[indice] : undefined;
    const grupo = DIMENSAO_GRUPO[this.distributionDimension()];
    if (item && grupo) this.analise.abrir('grupo', `${grupo}~${item.id}`);
  }

  protected analisar(dimension: string, value: string): void {
    this.analise.abrir(dimension, value);
  }

  protected aprofundar(alvo: AlvoAnalise): void {
    this.analise.aprofundar(alvo, this.vistaAnalise()?.titulo ?? '');
  }

  /** "Analisar com o Copiloto" de dentro da análise: fecha o painel e pergunta com o recorte explícito. */
  protected perguntarAnalise(vista: VistaAnalise): void {
    if (!vista.copiloto) return;
    this.analise.fechar();
    this.copiloto.perguntarSobre(vista.copiloto.foco, vista.copiloto.pergunta);
  }

  protected perguntarVisao(pergunta: string): void {
    this.copiloto.perguntarSobre({ tipo: 'secao', id: 'visaoGerencial', rotulo: 'Visão gerencial' }, pergunta);
  }

  protected abrirInspetor(item: InspetorMetaItem): void {
    this.analise.abrir('inspetor', item.idInspetor);
  }

  protected setPeriodo(tipo: 'semanal' | 'mensal' | 'anual'): void {
    const hoje = new Date();
    this.dataFim = new Date(hoje);
    this.dataInicio = new Date(hoje);
    if (tipo === 'semanal') this.dataInicio.setDate(hoje.getDate() - 7);
    if (tipo === 'mensal') this.dataInicio.setMonth(hoje.getMonth() - 1);
    if (tipo === 'anual') this.dataInicio.setFullYear(hoje.getFullYear() - 1);
    this.periodoAtivo.set(tipo);
    this.carregar();
  }

  protected onGranularidadeChange(): void {
    this.carregar();
  }

  /** A barra enche até 100%, mas o percentual em si pode passar disso. */
  protected barWidth(value: number): number {
    return Math.max(2, Math.min(100, value ?? 0));
  }

  protected percentualOuSemAmostra(total: number, value: number): string {
    return total > 0 ? `${value.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%` : 'Sem dados no período';
  }

  protected variacao(resumo: { totalPeriodoAnterior: number; variacaoPercentual: number }): string {
    if (resumo.totalPeriodoAnterior === 0) return 'Sem período anterior para comparar';
    const valor = resumo.variacaoPercentual.toLocaleString('pt-BR', {
      maximumFractionDigits: 1, signDisplay: 'always'
    });
    return `${valor}% vs. período anterior (${resumo.totalPeriodoAnterior})`;
  }

  protected nomeOuPlaceholder(nome: string | null): string {
    return nome ?? 'Cadastro não disponível';
  }

  /** Explica o gráfico conforme o modo — sem isso o leitor não sabe o que a linha significa. */
  protected descricaoSerie(granularidade: GranularidadeDashboard): string {
    const intervalo = this.granularidadeLabels[granularidade];
    return this.serieModo() === 'acumulado'
      ? 'Soma corrida do que foi realizado contra a meta acumulada. Enquanto a área azul estiver abaixo da linha tracejada, o plano está atrasado.'
      : `Inspeções realizadas em cada ${intervalo}. A linha em degraus é a meta daquele ${intervalo}; barras verdes indicam que ela foi cumprida.`;
  }

  /**
   * Recorte estruturado para o Copiloto: período, filtros aplicados, KPIs (com a ressalva de meta não
   * comparável quando for o caso), agregações exibidas e datasets. Nunca os registros individuais.
   */
  private montarContextoCopiloto(): CopilotoDashboardContext | null {
    const d = this.data();
    const f = this.filtrosAplicados();
    const nomes = <T>(itens: readonly T[], ids: readonly string[] | null | undefined, id: (i: T) => string, nome: (i: T) => string) =>
      rotuloDasOpcoes(itens, ids ?? [], id, nome);
    const comMeta = !!d?.meta.possuiMeta && this.metaComparavel();
    const serie = d?.serieTemporal ?? [];
    const inspetores = d?.porInspetor ?? [];
    const equipes = d?.porEquipe ?? [];
    const distribuicao = this.distributionRows();
    const um = (n: number, casas = 1) => n.toLocaleString('pt-BR', { maximumFractionDigits: casas });
    return {
      id: 'sst.inspecoes',
      nome: 'Inspeções de segurança',
      modulo: 'SST',
      descricao: 'Execução do plano de inspeções (realizado × meta), conformidade (inspeções sem desvio), cobertura do efetivo, inspetores, equipes e colaboradores alcançados.',
      rota: '/sst/dashboards/inspecoes',
      periodo: d ? periodoPainel(d.inicio, d.fim) : periodoPainel(this.dataInicio, this.dataFim),
      filtros: [
        filtroPainel('tipoInspecao', 'Tipo de inspeção', nomes(this.tiposInspecao(), f.idTipoInspecao, (i) => i.id, (i) => i.nome), f.idTipoInspecao ?? []),
        filtroPainel('processo', 'Processo da inspeção', nomes(this.processos(), f.idProcesso, (i) => i.id, (i) => i.nome), f.idProcesso ?? []),
        filtroPainel('processoInspetor', 'Processo do inspetor', nomes(this.processos(), f.idProcessoInspetor, (i) => i.id, (i) => i.nome), f.idProcessoInspetor ?? []),
        filtroPainel('equipe', 'Equipe', nomes(this.equipes(), f.idEquipe, (i) => i.id, (i) => i.codigo ?? i.id), f.idEquipe ?? []),
        filtroPainel('centroCusto', 'Centro de custo', nomes(this.centrosCusto(), f.idCentroCusto, (i) => i.id, (i) => i.nome), f.idCentroCusto ?? []),
        filtroPainel('baseOperacional', 'Base operacional', nomes(this.basesOperacionais(), f.idBaseOperacional, (i) => i.id, (i) => i.nome), f.idBaseOperacional ?? []),
        filtroPainel('formulario', 'Formulário', nomes(this.formularios(), f.idFormulario, (i) => i.id, (i) => i.titulo), f.idFormulario ?? []),
      ],
      metricas: d
        ? [
            metricaPainel('totalInspecoes', 'Inspeções realizadas', d.resumo.totalInspecoes, 'inteiro', {
              comparacao: d.resumo.totalPeriodoAnterior
                ? { rotulo: 'vs. período anterior', valorAnterior: d.resumo.totalPeriodoAnterior, variacaoPercentual: d.resumo.variacaoPercentual, melhorQuando: 'maior' }
                : undefined,
            }),
            metricaPainel('percentualSemDesvio', 'Inspeções sem desvio', d.resumo.percentualSemDesvio, 'percentual', {
              descricao: `${d.resumo.inspecoesSemDesvio} de ${d.resumo.totalInspecoes}`,
            }),
            metricaPainel('totalDesvios', 'Desvios registrados', d.resumo.totalDesvios, 'inteiro', {
              descricao: `${um(d.resumo.mediaDesviosPorInspecao, 2)} por inspeção`,
            }),
            metricaPainel('inspetoresAtivos', 'Inspetores ativos', d.resumo.inspetoresAtivos, 'inteiro', {
              descricao: `${um(d.resumo.mediaInspecoesPorInspetor)} inspeções por inspetor; ${um(d.resumo.mediaInspecoesPorDia)} por dia`,
            }),
            ...(comMeta
              ? [
                  metricaPainel('metaPercentualAtingido', 'Meta de inspeções atingida', d.meta.percentualAtingido, 'percentual', {
                    descricao: `realizado ${d.meta.realizadoPeriodo} de ${um(d.meta.metaPeriodo)}; faltam ${um(d.meta.saldoParaMeta)}`,
                  }),
                  metricaPainel('inspetoresNaMeta', 'Colaboradores na meta', d.meta.inspetoresNaMeta, 'inteiro', {
                    descricao: `de ${d.meta.colaboradoresComMeta} com meta; ${d.meta.colaboradoresSemAcesso} sem acesso ao sistema`,
                  }),
                ]
              : []),
            metricaPainel('coberturaEfetivo', 'Cobertura do efetivo', d.colaboradores.percentualCobertura, 'percentual', {
              descricao: `${d.colaboradores.colaboradoresInspecionados} de ${d.colaboradores.colaboradoresAtivos} colaboradores ativos`,
            }),
            metricaPainel('colaboradoresSemInspecao', 'Colaboradores sem inspeção', d.colaboradores.colaboradoresSemInspecao, 'inteiro'),
            metricaPainel('colaboradoresComDesvio', 'Colaboradores com desvio', d.colaboradores.colaboradoresComDesvio, 'inteiro', {
              descricao: `${um(d.colaboradores.percentualComDesvio)}%; ${d.colaboradores.colaboradoresReincidentes} reincidentes (2+ desvios)`,
            }),
          ]
        : [],
      graficos: d
        ? [
            graficoPainel('realizadoMeta', 'barra', 'Realizado × meta ao longo do período', serie.map((i) => i.rotulo), [
              { nome: 'Realizado', valores: serie.map((i) => i.realizado) },
              ...(comMeta ? [{ nome: 'Meta', valores: serie.map((i) => i.meta) }] : []),
              { nome: 'Desvios', valores: serie.map((i) => i.desvios) },
            ], { metrica: 'inspeções', dimensao: `período (${this.granularidadeLabels[d.granularidade]})`, formato: 'inteiro' }),
            graficoPainel('inspetores', 'tabela', 'Quem está lançando as inspeções', inspetores.map((i) => this.nomeOuPlaceholder(i.nomeInspetor)), [
              { nome: 'Realizado', valores: inspetores.map((i) => i.realizado) },
              { nome: 'Meta', valores: inspetores.map((i) => i.meta) },
              { nome: 'Desvios', valores: inspetores.map((i) => i.desvios) },
            ], { metrica: 'inspeções', dimensao: 'inspetor', totalCategorias: inspetores.length * 3 }),
            graficoPainel('equipes', 'tabela', 'Cobertura e conformidade por equipe', equipes.map((e) => this.nomeOuPlaceholder(e.nomeEquipe)), [
              { nome: 'Inspeções', valores: equipes.map((e) => e.inspecoes) },
              { nome: '% sem desvio', valores: equipes.map((e) => e.percentualSemDesvio) },
              { nome: 'Dias sem inspeção', valores: equipes.map((e) => e.diasSemInspecao) },
            ], { metrica: 'inspeções', dimensao: 'equipe', totalCategorias: equipes.length * 3 }),
            graficoPainel(`distribuicao.${this.distributionDimension()}`, 'rosca', `Onde as inspeções aconteceram — por ${this.distributionLabel().toLowerCase()}`,
              distribuicao.map((i) => this.nomeOuPlaceholder(i.nome)), distribuicao.map((i) => i.total), {
                metrica: 'inspeções', dimensao: this.distributionLabel().toLowerCase(), formato: 'inteiro',
              }),
          ]
        : [],
      datasets: ['inspecoes', 'desvios', 'metas_inspecao', 'equipes', 'colaboradores', 'bases_operacionais', 'processos_negocio'],
      tabelas: d
        ? [
            tabelaPainel(TABELA_INSPETORES, 'Produtividade · Inspetores', [
              { campo: 'nomeInspetor', rotulo: 'Inspetor', tipo: 'texto' },
              { campo: 'realizado', rotulo: 'Realizado', tipo: 'numero' },
              ...(comMeta ? [{ campo: 'percentualAtingido', rotulo: '% da meta', tipo: 'percentual' as const }] : []),
              { campo: 'desvios', rotulo: 'Desvios apontados', tipo: 'numero' },
              { campo: 'percentualDoTotal', rotulo: '% do total', tipo: 'percentual' },
            ]),
          ]
        : [],
      extras: !this.metaComparavel()
        ? { Meta: 'não comparável com os filtros de processo, equipe ou formulário ativos (restringem só o realizado)' }
        : undefined,
      sugestoes: [
        ...(comMeta
          ? [{ rotulo: 'Quem está abaixo de 80% da meta?', icone: 'pi pi-filter', requer: ['dados'] as CapacidadeSugestao[],
              pergunta: 'Quais inspetores ficaram abaixo de 80% da meta neste recorte? Filtre a tabela de produtividade para mim e explique os casos mais críticos.' }]
          : []),
        { rotulo: 'Estamos cumprindo o plano?', icone: 'pi pi-flag', requer: ['dados'],
          pergunta: 'Estamos cumprindo o plano de inspeções neste recorte? Quais inspetores e equipes estão abaixo da meta e por quê?' },
        { rotulo: 'Equipes sem inspeção', icone: 'pi pi-users', requer: ['dados'],
          pergunta: 'Quais equipes e colaboradores estão há mais tempo sem inspeção neste recorte? Priorize por risco.' },
        { rotulo: 'Itens que mais reprovam', icone: 'pi pi-exclamation-triangle', requer: ['dados', 'analise'],
          pergunta: 'Quais itens de checklist mais geram desvios nas inspeções deste recorte e em quais equipes?' },
      ],
    };
  }

  protected verListagem(): void {
    this.router.navigate(['/sst/inspecoes']);
  }

  protected irParaMetas(): void {
    this.router.navigate(['/sst/metas-inspecao']);
  }

  protected abrirModalExportacao(): void {
    this.exportDataDe = this.dataInicio;
    this.exportDataAte = this.dataFim;
    this.exportFilters = {
      idProcesso: [...(this.filter.idProcesso ?? [])],
      idProcessoInspetor: [...(this.filter.idProcessoInspetor ?? [])],
      idBaseOperacional: [...(this.filter.idBaseOperacional ?? [])],
      idCentroCusto: [...(this.filter.idCentroCusto ?? [])],
      idFormulario: [...(this.filter.idFormulario ?? [])],
      status: [], apenasComDesvios: null
    };
    this.exportApenasMinhas = false;
    this.showExportDialog = true;
  }

  /** PDF executivo: o Copiloto gera o relatório com os dados reais do recorte (capacidade "relatório"). */
  protected relatorioComCopiloto(): void {
    this.showExportDialog = false;
    this.copiloto.perguntarSobre(
      { tipo: 'secao', id: 'relatorio', rotulo: 'Relatório do painel' },
      'Gere um relatório PDF executivo deste painel de inspeções com os indicadores, realizado × meta, inspetores abaixo da meta, desvios críticos e recomendações, usando os dados reais deste recorte.'
    );
  }

  protected executarExportacao(): void {
    this.exportando.set(true);
    const params: InspecaoFilters = {
      ...this.exportFilters,
      dataRealizacaoDe: this.exportDataDe ? this.toIso(this.exportDataDe, false) : null,
      dataRealizacaoAte: this.exportDataAte ? this.toIso(this.exportDataAte, true) : null,
      apenasMinhas: this.exportApenasMinhas || null
    };
    const action$ = this.exportTipo === 'inspecoes'
      ? (this.exportFormato === 'resumo' ? this.sst.exportarInspecoesResumo(params, this.exportArquivo) : this.sst.exportarInspecoesDetalhado(params, this.exportArquivo))
      : (this.exportFormato === 'resumo' ? this.sst.exportarDesviosResumo(params, this.exportArquivo) : this.sst.exportarDesviosDetalhado(params, this.exportArquivo));
    action$.subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${this.exportTipo}_${this.exportFormato}_${Date.now()}.${this.exportArquivo}`;
        link.click();
        URL.revokeObjectURL(url);
        this.exportando.set(false);
        this.showExportDialog = false;
        this.notificacoes.success('O arquivo foi baixado.', { title: 'Exportação concluída' });
      },
      error: (error) => {
        this.exportando.set(false);
        this.notificacoes.error(getErrorMessage(error), { title: 'Falha na exportação' });
      }
    });
  }

  private carregarLookups(): void {
    forkJoin({
      tipos: this.sst.listarOpcoesTiposInspecao(),
      processos: this.rh.listarProcessosAtivos(),
      bases: this.rh.listarBasesOperacionais({ ativo: true, page: 0, size: 100, sort: 'nome,asc' }),
      centrosCusto: this.centrosCustoLookup.listar(),
      formularios: this.sst.listarFormulariosAtivos()
    }).subscribe({
      next: (response) => {
        this.tiposInspecao.set(response.tipos);
        this.processos.set(response.processos.content);
        this.basesOperacionais.set(response.bases.content);
        this.centrosCusto.set(response.centrosCusto);
        this.formularios.set(response.formularios.content);
      }
    });
  }

  private hidratarEquipesSelecionadas(): void {
    const ids = this.filter.idEquipe ?? [];
    if (!ids.length) return;
    forkJoin(ids.map(id => this.rh.buscarEquipePorId(id).pipe(catchError(() => of(null)))))
      .subscribe(equipes => this.equipeLookup.setSeed(equipes.filter((equipe): equipe is EquipeResponse => equipe != null)));
  }

  private carregar(): void {
    const filter: DashboardSstFilter = {
      ...this.filter,
      inicio: this.dataInicio ? this.toIso(this.dataInicio, false) : null,
      fim: this.dataFim ? this.toIso(this.dataFim, true) : null
    };
    this.sincronizarUrl(filter);
    this.buscar(filter);
  }

  /** Busca um recorte; a atualização automática repete o aplicado sem mostrar carregamento nem avisar erro. */
  private buscar(filter: DashboardSstFilter, silencioso = false): void {
    if (!silencioso) this.loading.set(true);
    this.sst.gerarDashboardInspecoes(filter).subscribe({
      next: (response) => {
        this.data.set(response);
        this.filtrosAplicados.set(filter);
        this.atualizadoEm.set(new Date(response.atualizadoEm));
        this.prepararGraficos(response);
        this.loading.set(false);
      },
      error: (error) => {
        if (silencioso) return;
        this.loading.set(false);
        this.notificacoes.error(getErrorMessage(error), { title: 'Não foi possível carregar' });
      }
    });
  }

  private periodoValido(): boolean {
    if (!this.dataInicio || !this.dataFim || this.dataInicio <= this.dataFim) return true;
    this.notificacoes.warning('A data inicial deve ser anterior ou igual à data final.', { title: 'Período inválido' });
    return false;
  }

  private prepararGraficos(data: DashboardInspecoesResponse): void {
    this.prepararSerie();
    this.prepararInspetores(data);
    this.prepararDistribuicao();
  }

  protected onSerieModoChange(modo: SerieModo): void {
    this.serieModo.set(modo);
    this.prepararSerie();
  }

  protected alternarDesvios(): void {
    this.mostrarDesvios.update((valor) => !valor);
    this.prepararSerie();
  }

  /**
   * Dois modos de leitura da mesma série:
   * - `periodo`: barras do realizado em cada intervalo, coloridas por atingimento, com a
   *   meta como linha em degraus (é um alvo por intervalo, não uma tendência);
   * - `acumulado`: soma corrida das duas séries. É a leitura que responde "estamos
   *   cumprindo o plano?" quando a meta rateada por dia fica rente a zero.
   * Os desvios saem por padrão — moram no dashboard próprio e o eixo secundário
   * competia com a meta.
   */
  private prepararSerie(): void {
    const atual = this.data();
    const serie = atual?.serieTemporal ?? [];
    const comMeta = !!atual && this.metaComparavel() && atual.meta.possuiMeta;
    const acumulado = this.serieModo() === 'acumulado';
    const desvios = this.mostrarDesvios();
    const paleta = obterPaletaPrimaria(this.themeService.primaryColor());

    const realizado = acumulado ? this.somaCorrida(serie.map((i) => i.realizado)) : serie.map((i) => i.realizado);
    const meta = acumulado ? this.somaCorrida(serie.map((i) => i.meta)) : serie.map((i) => i.meta);

    const datasets: unknown[] = [
      acumulado
        ? {
            type: 'line', label: 'Realizado (acumulado)', data: realizado,
            borderColor: paleta.forte,
            backgroundColor: (context: any) => {
              const chart = context?.chart;
              if (!chart || !chart.ctx || !chart.chartArea) return 'rgba(37, 99, 235, 0.12)';
              const { ctx, chartArea } = chart;
              const grad = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
              grad.addColorStop(0, paleta.claro);
              grad.addColorStop(1, 'rgba(255, 255, 255, 0)');
              return grad;
            },
            borderWidth: 2.5,
            pointRadius: 0, pointHoverRadius: 5, pointHitRadius: 16, tension: 0.3, fill: true, order: 3
          }
        : {
            type: 'bar', label: 'Realizado', data: realizado,
            backgroundColor: (context: any) => {
              const chart = context?.chart;
              if (!chart || !chart.ctx) return paleta.forte;
              const { ctx, chartArea } = chart;
              if (!chartArea) return paleta.forte;

              const idx = context.dataIndex;
              const item = serie[idx];
              const bateuMeta = comMeta && item && item.meta > 0 && item.realizado >= item.meta;
              const p = bateuMeta ? PALETAS_GRADIENTE_PRIMARIA.emerald : paleta;

              const element = context.element;
              if (element && typeof element.y === 'number' && typeof element.base === 'number' && Math.abs(element.base - element.y) > 0.5) {
                return criarGradienteBarra(ctx, element.y, element.base, p.claro, p.forte);
              }
              return criarGradienteBarra(ctx, chartArea.top, chartArea.bottom, p.claro, p.forte);
            },
            hoverBackgroundColor: (context: any) => {
              const idx = context?.dataIndex;
              const item = serie[idx];
              const bateuMeta = comMeta && item && item.meta > 0 && item.realizado >= item.meta;
              return bateuMeta ? PALETAS_GRADIENTE_PRIMARIA.emerald.hover : paleta.hover;
            },
            borderColor: (context: any) => {
              const idx = context?.dataIndex;
              const item = serie[idx];
              const bateuMeta = comMeta && item && item.meta > 0 && item.realizado >= item.meta;
              return bateuMeta ? PALETAS_GRADIENTE_PRIMARIA.emerald.borda : paleta.borda;
            },
            borderWidth: 1,
            borderRadius: 6,
            borderSkipped: false,
            maxBarThickness: 36,
            order: 3
          }
    ];

    if (comMeta) {
      datasets.push({
        type: 'line', label: acumulado ? 'Meta (acumulada)' : 'Meta', data: meta,
        borderColor: '#c57a16', backgroundColor: '#c57a16', borderDash: [6, 4], borderWidth: 2,
        pointRadius: 0, pointHoverRadius: 4, tension: acumulado ? 0.25 : 0, fill: false,
        stepped: acumulado ? false : 'middle', order: 1
      });
    }

    if (desvios) {
      datasets.push({
        type: 'line', label: 'Desvios', data: serie.map((item) => item.desvios), yAxisID: 'y1',
        borderColor: '#c2413b', backgroundColor: '#c2413b', borderWidth: 1.5,
        pointRadius: 0, pointHoverRadius: 4, tension: 0.3, fill: false, order: 2
      });
    }

    this.serieData = { labels: serie.map((item) => item.rotulo), datasets };
    this.serieOptions = {
      maintainAspectRatio: false,
      responsive: true,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: {
          display: true, position: 'top', align: 'end',
          labels: { boxWidth: 12, boxHeight: 12, borderRadius: 3, useBorderRadius: true, color: '#627d98', font: { size: 11, weight: '600' } }
        },
        tooltip: {
          mode: 'index', intersect: false, backgroundColor: '#102a43', titleColor: '#d9e2ec',
          bodyColor: '#fff', padding: 11, cornerRadius: 8,
          callbacks: {
            // A meta é fracionária (rateio) — sem casas decimais o tooltip mente.
            label: (contexto: any) => {
              const valor = Number(contexto.parsed?.y ?? 0);
              const casas = contexto.dataset?.label?.startsWith('Meta') ? 1 : 0;
              return `${contexto.dataset?.label}: ${valor.toLocaleString('pt-BR', { maximumFractionDigits: casas })}`;
            },
            footer: (itens: any[]) => {
              if (!comMeta) return '';
              const indice = itens?.[0]?.dataIndex ?? -1;
              const alvo = meta[indice] ?? 0;
              if (alvo <= 0) return '';
              return `${((realizado[indice] / alvo) * 100).toLocaleString('pt-BR', { maximumFractionDigits: 0 })}% da meta`;
            }
          },
          footerColor: '#bfdbfe', footerFont: { size: 11, weight: '600' }
        }
      },
      scales: {
        x: { grid: { display: false }, border: { display: false }, ticks: { color: '#829ab1', font: { size: 10 }, maxTicksLimit: 12, maxRotation: 0, padding: 8 } },
        y: {
          beginAtZero: true, grace: '8%', border: { display: false },
          grid: { color: '#edf2f7', drawTicks: false },
          ticks: { color: '#9fb3c8', maxTicksLimit: 5, padding: 10 }
        },
        ...(desvios
          ? {
              y1: {
                beginAtZero: true, position: 'right', border: { display: false },
                grid: { display: false }, ticks: { color: '#c2413b', precision: 0, maxTicksLimit: 4, padding: 6 }
              }
            }
          : {})
      }
    };
  }

  private somaCorrida(valores: number[]): number[] {
    let total = 0;
    return valores.map((valor) => (total += valor));
  }

  /** Barras horizontais agrupadas: realizado × meta, coloridas por atingimento. */
  private prepararInspetores(data: DashboardInspecoesResponse): void {
    const itens = (data.porInspetor ?? []).slice(0, MAX_INSPETORES_NO_GRAFICO);
    this.inspetorData = {
      labels: itens.map((item) => this.nomeOuPlaceholder(item.nomeInspetor)),
      datasets: [
        {
          label: 'Realizado', data: itens.map((item) => item.realizado),
          backgroundColor: itens.map((item) => (item.atingiuMeta ? 'rgba(56, 161, 105, 0.85)' : 'rgba(37, 99, 235, 0.85)')),
          borderRadius: 5, maxBarThickness: 16
        },
        {
          // Inspetores sem meta entram com 0 e simplesmente não desenham barra.
          label: 'Meta', data: itens.map((item) => item.meta),
          backgroundColor: 'rgba(197, 122, 22, 0.35)', borderRadius: 5, maxBarThickness: 16
        }
      ]
    };
    this.inspetorOptions = {
      indexAxis: 'y',
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: true, position: 'top', align: 'end',
          labels: { boxWidth: 10, boxHeight: 10, usePointStyle: true, color: '#627d98', font: { size: 11 } }
        },
        tooltip: { backgroundColor: '#102a43', titleColor: '#d9e2ec', bodyColor: '#fff', padding: 11, cornerRadius: 8 }
      },
      scales: {
        x: { beginAtZero: true, border: { display: false }, grid: { color: '#edf2f7', drawTicks: false }, ticks: { color: '#9fb3c8', maxTicksLimit: 6, padding: 6 } },
        y: { grid: { display: false }, border: { display: false }, ticks: { color: '#486581', font: { size: 11 }, crossAlign: 'far' } }
      }
    };
  }

  protected onDistributionChange(dimension: DistributionDimension): void {
    this.distributionDimension.set(dimension);
    this.prepararDistribuicao();
  }

  private prepararDistribuicao(): void {
    const rows = this.distributionRows().slice(0, 8);
    this.distribuicaoData = {
      labels: rows.map((item) => this.nomeOuPlaceholder(item.nome)),
      datasets: [{
        data: rows.map((item) => item.total),
        backgroundColor: ['#2563eb', '#60a5fa', '#93c5fd', '#0f766e', '#14b8a6', '#c57a16', '#94a3b8', '#475569'],
        borderWidth: 0, hoverOffset: 6
      }]
    };
  }

  private donutOptions(): unknown {
    return {
      maintainAspectRatio: false,
      cutout: '62%',
      plugins: {
        legend: {
          position: 'right',
          labels: { boxWidth: 10, boxHeight: 10, usePointStyle: true, color: '#486581', font: { size: 11 }, padding: 12 }
        },
        tooltip: { backgroundColor: '#102a43', titleColor: '#d9e2ec', bodyColor: '#fff', padding: 11, cornerRadius: 8 }
      }
    };
  }

  private toIso(date: Date, endOfDay: boolean): string {
    const value = new Date(date);
    value.setHours(endOfDay ? 23 : 0, endOfDay ? 59 : 0, endOfDay ? 59 : 0, endOfDay ? 999 : 0);
    return toSaoPauloIso(value, endOfDay);
  }
}
