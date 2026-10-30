import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { ThemeService } from '../../../core/services/theme.service';
import { ChartModule } from 'primeng/chart';
import { SkeletonModule } from 'primeng/skeleton';
import { TooltipModule } from 'primeng/tooltip';

import {
  ExposicaoNormalizadaItem,
  IndicadorComparativo,
  ItemCriticoDashboard,
  PendenciaAgrupadaResponse,
  VolumePorGravidade
} from '../../../core/models/sst-dashboard.models';
import {
  BaseOperacionalResponse,
  CentroFaturamentoResponse,
  EquipeResponse,
  ProcessoNegocioResponse
} from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import {
  DashboardDesviosResponse, DashboardSstFilter, GRAVIDADE_LABELS, GravidadeDesvio,
  STATUS_DESVIO_LABELS, StatusDesvio
} from '../../../core/models/sst.models';
import { CentrosCustoLookupService } from '../../../core/services/centros-custo-lookup.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { normalizarSelecao, temSelecao } from '../../../core/utils/http-params.util';
import { toPlainDashboardLanguage } from './sst-dashboard-insights';
import { toSaoPauloIso } from '../../../shared/utils/dashboard-date.utils';
import { DashboardMetricCardComponent } from '../../../shared/components/dashboard/dashboard-ui.components';
import { DashboardAnaliseComponent } from '../../../shared/components/dashboard/dashboard-analise.component';
import { AlvoAnalise, VistaAnalise } from '../../../shared/components/dashboard/dashboard-analise.models';
import { NavegacaoAnaliseService } from '../../../shared/components/dashboard/dashboard-analise.navegacao';
import { DashboardVisaoGerencialComponent } from '../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import { GrupoDesvio, visaoDesvios, vistaDesvios } from './analise/analise-desvios';
import { CesButtonComponent, CesDrillStateService,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesStatusComponent,
  CesDatepickerComponent,
  CesFilterChip,
  CesFilterPanelComponent,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  NotificationService
} from '@ces/ui';
import { chipsDoRecorte, opcoesDe } from '../../../shared/components/dashboard/dashboard-filtros';
import {
  CopilotoAreaComponent,
  CopilotoDashboardContext,
  CopilotoGatilhoComponent,
  CopilotoPainelService,
  CopilotoPerguntarComponent,
  filtroPainel,
  graficoPainel,
  metricaPainel,
  periodoPainel,
  rotuloDasOpcoes,
} from '../../copiloto/painel';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import {
  montarDadosTendenciaDesvios,
  opcoesGraficoTendenciaDesvios
} from './dashboard-desvios-chart.config';
import { atualizarPeriodicamente } from '../../../shared/components/dashboard/atualizacao-automatica';

type PendingDimension = 'processo' | 'processoInspetor' | 'base' | 'equipe';

@Component({
  selector: 'app-sst-dashboard-desvios',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesDatepickerComponent, CesFilterPanelComponent, CesMultiSelectComponent, CesPageHeaderComponent,
    CommonModule, DecimalPipe, ChartModule, SkeletonModule, TooltipModule, CesCellDirective, CesDataTableComponent, CesStatusComponent,
    DashboardMetricCardComponent, CesButtonComponent, CopilotoAreaComponent, CopilotoGatilhoComponent, CopilotoPerguntarComponent,
    DashboardAnaliseComponent, DashboardVisaoGerencialComponent],
  providers: [CesDrillStateService, NavegacaoAnaliseService],
  templateUrl: './dashboard-desvios.component.html',
  styles: [`
    :host .dash-metrics { grid-template-columns: repeat(4, minmax(0, 1fr)); }
    /* Cartões de gravidade e de idade clicáveis: botão esticado sobre o cartão. */
    .dash-aging-card { position: relative; }
    .dash-card-hit {
      position: absolute; inset: 0; z-index: 1; width: 100%; padding: 0; border: 0; border-radius: inherit;
      background: transparent; cursor: pointer;
    }
    .dash-card-hit:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: -2px; }
    .dash-aging-card:has(.dash-card-hit):hover { border-color: color-mix(in srgb, var(--ces-brand) 45%, var(--ces-border)); box-shadow: var(--ces-shadow-md); }
    .dash-aging-card:has(.dash-card-hit:disabled) .dash-card-hit { cursor: default; }
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
    .dash-badge--success { background: #f0fdf4; color: #15803d; }
    .dash-badge--warning { background: #fffbeb; color: #b45309; }
    .dash-badge--orange { background: #fff7ed; color: #c2410c; }
    .dash-badge--danger { background: #fef2f2; color: #b91c1c; }
    .dash-badge i { font-size: .6rem; }

    .dash-chart--responsive {
      position: relative;
      width: 100%;
      height: clamp(240px, 30vw, 320px);
    }
    .dash-chart--responsive .p-chart { display: block; width: 100%; height: 100%; }

    /* --- Aging Pipeline & Severity Pipeline --- */
    .dash-aging-grid {
      display: grid;
      grid-template-columns: repeat(5, minmax(0, 1fr));
      gap: 0.85rem;
    }
    .dash-severity-grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 0.85rem;
    }
    .dash-aging-card {
      display: flex;
      flex-direction: column;
      border: 1px solid #e2e8f0;
      border-radius: 0.75rem;
      background: #fbfdff;
      overflow: hidden;
      transition: transform 150ms ease, box-shadow 150ms ease, border-color 150ms ease;
    }
    .dash-aging-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
      border-color: #cbd5e1;
    }
    .dash-aging-card.is-vazio {
      opacity: 0.6;
      background: #f8fafc;
    }
    .dash-aging-card.is-alerta {
      border-color: #f87171;
      background: #fffafa;
      box-shadow: 0 0 0 1px rgba(239, 68, 68, 0.2);
    }
    .dash-aging-card__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      padding: 0.65rem 0.85rem;
      background: #f1f5f9;
      border-bottom: 1px solid #e2e8f0;
    }
    .dash-aging-card__range {
      font-size: 0.78rem;
      font-weight: 700;
      color: #1e293b;
    }
    .dash-aging-card__stage {
      font-size: 0.62rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .dash-aging-card__stage[data-tone='success'] { color: #15803d; }
    .dash-aging-card__stage[data-tone='info'] { color: #2563eb; }
    .dash-aging-card__stage[data-tone='warning'] { color: #b45309; }
    .dash-aging-card__stage[data-tone='orange'] { color: #c2410c; }
    .dash-aging-card__stage[data-tone='danger'] { color: #b91c1c; }

    .dash-aging-dot {
      width: 0.5rem;
      height: 0.5rem;
      border-radius: 99px;
      flex: none;
    }
    .dash-aging-dot[data-tone='success'] { background: #22c55e; }
    .dash-aging-dot[data-tone='info'] { background: #3b82f6; }
    .dash-aging-dot[data-tone='warning'] { background: #f59e0b; }
    .dash-aging-dot[data-tone='orange'] { background: #f97316; }
    .dash-aging-dot[data-tone='danger'] { background: #ef4444; }

    .dash-aging-card__body {
      padding: 0.85rem;
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
      flex: 1;
    }
    .dash-aging-card__total-wrap {
      display: flex;
      align-items: baseline;
      gap: 0.35rem;
    }
    .dash-aging-card__total {
      font-size: 1.5rem;
      font-weight: 800;
      line-height: 1;
      color: #0f172a;
      font-variant-numeric: tabular-nums;
    }
    .dash-aging-card__unit {
      font-size: 0.72rem;
      color: #64748b;
      font-weight: 500;
    }

    .dash-aging-bar {
      display: flex;
      height: 0.45rem;
      width: 100%;
      border-radius: 99px;
      overflow: hidden;
      background: #e2e8f0;
    }
    .dash-aging-bar__seg {
      height: 100%;
      transition: width 200ms ease;
    }
    .dash-aging-bar__seg--critical { background: #dc2626; }
    .dash-aging-bar__seg--high { background: #ea580c; }
    .dash-aging-bar__seg--medium { background: #f59e0b; }
    .dash-aging-bar__seg--low { background: #3b82f6; }

    .dash-aging-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.3rem;
    }
    .dash-badge--orange { background: #fff7ed; color: #c2410c; }

    .dash-aging-card__empty {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      font-size: 0.7rem;
      color: #94a3b8;
      padding: 0.4rem 0;
    }
    .dash-share-pill {
      display: inline-flex;
      align-items: center;
      padding: 0.15rem 0.5rem;
      border-radius: 99px;
      font-size: 0.72rem;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      background: #f1f5f9;
      color: #334155;
    }
    .dash-share-pill[data-tone='danger'] { background: #fef2f2; color: #b91c1c; }
    .dash-share-pill[data-tone='orange'] { background: #fff7ed; color: #c2410c; }
    .dash-share-pill[data-tone='warning'] { background: #fffbeb; color: #b45309; }
    .dash-share-pill[data-tone='info'] { background: #eff6ff; color: #1d4ed8; }

    .dash-card-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      padding-top: 0.6rem;
      margin-top: auto;
      border-top: 1px solid #f1f5f9;
      font-size: 0.72rem;
    }

    :host-context(.app-dark), :host-context(.dark) {
      .dash-cell-name { color: #e2e8f0; }
      .dash-pct-pill { background: var(--ces-surface-muted, #334155); color: #cbd5e1; }
      .dash-badge { background: var(--ces-surface-muted, #334155); color: #cbd5e1; }
      .dash-badge--success { background: rgba(34,197,94,.12); color: #4ade80; }
      .dash-badge--warning { background: rgba(245,158,11,.12); color: #fbbf24; }
      .dash-badge--orange { background: rgba(249,115,22,.15); color: #fb923c; }
      .dash-badge--danger { background: rgba(239,68,68,.12); color: #f87171; }

      .dash-aging-card { background: var(--ces-surface, #1e293b); border-color: var(--ces-border, #334155); }
      .dash-aging-card:hover { border-color: color-mix(in srgb, var(--ces-brand, #3b82f6) 40%, transparent); }
      .dash-aging-card.is-vazio { background: rgba(15, 23, 42, 0.6); opacity: 0.5; }
      .dash-aging-card.is-alerta { background: rgba(239, 68, 68, 0.08); border-color: rgba(239, 68, 68, 0.5); }
      .dash-aging-card__header { background: var(--ces-surface-subtle, #0f172a); border-color: var(--ces-border, #334155); }
      .dash-aging-card__range { color: #f8fafc; }
      .dash-aging-card__stage[data-tone='success'] { color: #4ade80; }
      .dash-aging-card__stage[data-tone='info'] { color: #60a5fa; }
      .dash-aging-card__stage[data-tone='warning'] { color: #fbbf24; }
      .dash-aging-card__stage[data-tone='orange'] { color: #fb923c; }
      .dash-aging-card__stage[data-tone='danger'] { color: #f87171; }
      .dash-aging-card__total { color: #fff; }
      .dash-aging-card__unit { color: #94a3b8; }
      .dash-aging-bar { background: #334155; }
      .dash-share-pill { background: var(--ces-surface-muted, #334155); color: #cbd5e1; }
      .dash-share-pill[data-tone='danger'] { background: rgba(239,68,68,.15); color: #f87171; }
      .dash-share-pill[data-tone='orange'] { background: rgba(249,115,22,.15); color: #fb923c; }
      .dash-share-pill[data-tone='warning'] { background: rgba(245,158,11,.15); color: #fbbf24; }
      .dash-share-pill[data-tone='info'] { background: rgba(59,130,246,.15); color: #60a5fa; }
      .dash-card-footer { border-color: rgba(51, 65, 85, 0.6); }
    }

    @media (max-width: 1200px) {
      .dash-aging-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
    }
    @media (max-width: 1024px) {
      .dash-severity-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    }
    @media (max-width: 768px) {
      .dash-aging-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    }
    @media (max-width: 540px) {
      .dash-severity-grid { grid-template-columns: 1fr; }
    }
    @media (max-width: 500px) {
      .dash-aging-grid { grid-template-columns: 1fr; }
    }
    @media (max-width: 1080px) {
      :host .dash-grid .dash-col-6, :host .dash-grid .dash-col-8, :host .dash-grid .dash-col-4, :host .dash-grid .dash-col-7, :host .dash-grid .dash-col-5 {
        grid-column: span 12;
      }
    }
  `]
})
export class DashboardDesviosComponent implements OnInit {
  private readonly sst = inject(SstService);
  private readonly rh = inject(RhService);
  private readonly centrosCustoLookup = inject(CentrosCustoLookupService);
  private readonly themeService = inject(ThemeService);
  private readonly notificacoes = inject(NotificationService);
  private readonly router = inject(Router);
  protected readonly analise = inject(NavegacaoAnaliseService);
  private readonly route = inject(ActivatedRoute);
  private readonly copiloto = inject(CopilotoPainelService);
  protected readonly copilotoDisponivel = this.copiloto.disponivelParaUsuario();

  constructor() {
    // Link de análise que não existe mais no recorte: fecha o painel.
    effect(() => {
      if (this.analise.alvo() && this.data() && !this.loading() && !this.vistaAnalise()) this.analise.fechar();
    });
    effect(() => {
      this.themeService.primaryColor();
      const current = this.data();
      if (current) {
        this.prepararGrafico(current);
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
  protected readonly data = signal<DashboardDesviosResponse | null>(null);
  protected readonly atualizadoEm = signal<Date | null>(null);
  protected readonly processos = signal<ProcessoNegocioResponse[]>([]);
  protected readonly equipes = this.equipeLookup.items;
  protected readonly equipesLoading = this.equipeLookup.loading;
  protected readonly basesOperacionais = signal<BaseOperacionalResponse[]>([]);
  protected readonly centrosCusto = signal<CentroFaturamentoResponse[]>([]);
  protected readonly pendingDimension = signal<PendingDimension>('processo');
  protected readonly exposureDimension = signal<PendingDimension>('processo');
  /** Veredito do recorte e leituras acionáveis (cada uma abre a análise que a sustenta). */
  protected readonly visaoGerencial = computed(() => {
    const d = this.data();
    return d ? visaoDesvios(d) : null;
  });

  /** Análise detalhada aberta (`?drill=`), resolvida contra os dados em tela. */
  protected readonly vistaAnalise = computed<VistaAnalise | null>(() => {
    const alvo = this.analise.alvo();
    const d = this.data();
    if (!alvo || !d) return null;
    return vistaDesvios(alvo, { d, filtros: this.filtrosAplicados(), sst: this.sst });
  });

  protected readonly colunasPendencias: CesDataTableColumn<PendenciaAgrupadaResponse>[] = [
    { key: 'nome', header: 'Agrupamento', priority: 1, value: (i) => i.nome ?? 'Cadastro não localizado', display: (i) => `${i.nome ?? 'Cadastro não localizado'} · ${i.totalAbertos} abertos, ${i.totalEmTratamento} em tratamento` },
    { key: 'totalPendentes', header: 'Pendentes', type: 'integer', priority: 1 },
    { key: 'totalVencidos', header: 'Vencidos', type: 'custom', value: (i) => i.totalVencidos, priority: 1 },
    { key: 'totalCriticos', header: 'Críticos', type: 'custom', value: (i) => i.totalCriticos, priority: 2 }
  ];
  protected readonly colunasExposicao: CesDataTableColumn<ExposicaoNormalizadaItem>[] = [
    { key: 'nome', header: 'Agrupamento', priority: 1, value: (i) => i.nome ?? 'Cadastro não localizado' },
    { key: 'inspecoes', header: 'Inspeções', type: 'integer', priority: 2 },
    { key: 'desvios', header: 'Desvios', type: 'integer', priority: 2 },
    { key: 'desviosPor100Inspecoes', header: 'A cada 100', type: 'number', priority: 1 },
    { key: 'vencidos', header: 'Vencidos', type: 'custom', value: (i) => i.vencidos, priority: 1 }
  ];
  protected readonly colunasItens: CesDataTableColumn<ItemCriticoDashboard>[] = [
    { key: 'item', header: 'Item', priority: 1, value: (i) => i.item ?? 'Cadastro não localizado', display: (i) => `${i.item ?? 'Cadastro não localizado'} · ${i.respostas} respostas` },
    { key: 'reprovacoes', header: 'Desvios', type: 'integer', priority: 1 },
    { key: 'taxaReprovacao', header: '% com desvio', type: 'percent', priority: 1 },
    { key: 'maiorGravidade', header: 'Gravidade', type: 'custom', value: (i) => ['BAIXA', 'MEDIA', 'ALTA', 'CRITICA'].indexOf(i.maiorGravidade), priority: 2 }
  ];
  protected readonly chaveGrupo = (i: { id: string }) => i.id;
  protected readonly rotuloGrupo = (i: { nome: string | null }) => i.nome ?? 'Cadastro não localizado';
  protected readonly chaveItem = (i: ItemCriticoDashboard) => i.idPergunta;
  protected readonly rotuloItem = (i: ItemCriticoDashboard) => i.item ?? 'Item';
  protected readonly pendingRows = computed<PendenciaAgrupadaResponse[]>(() => {
    const current = this.data();
    if (!current) return [];
    return {
      processo: current.pendenciasPorProcesso,
      processoInspetor: current.pendenciasPorProcessoInspetor ?? [],
      base: current.pendenciasPorBaseOperacional,
      equipe: current.pendenciasPorEquipe
    }[this.pendingDimension()] ?? [];
  });
  protected readonly exposureRows = computed<ExposicaoNormalizadaItem[]>(() => {
    const current = this.data();
    if (!current) return [];
    return {
      processo: current.exposicaoPorProcesso,
      processoInspetor: current.exposicaoPorProcessoInspetor ?? [],
      base: current.exposicaoPorBaseOperacional,
      equipe: current.exposicaoPorEquipe
    }[this.exposureDimension()] ?? [];
  });
  protected readonly pendingTabs = [
    { label: 'Processo da inspeção', value: 'processo' as const },
    { label: 'Processo do inspetor', value: 'processoInspetor' as const },
    { label: 'Bases', value: 'base' as const },
    { label: 'Equipes', value: 'equipe' as const }
  ];
  protected readonly gravidadeOptions = (Object.keys(GRAVIDADE_LABELS) as GravidadeDesvio[])
    .map((value) => ({ label: GRAVIDADE_LABELS[value], value }));
  protected readonly statusOptions = (Object.keys(STATUS_DESVIO_LABELS) as StatusDesvio[])
    .map((value) => ({ label: STATUS_DESVIO_LABELS[value], value }));

  protected dataInicio: Date | null = null;
  protected dataFim: Date | null = null;
  protected filter: DashboardSstFilter = this.filtroVazio();
  protected deviationTrendData: unknown;
  protected readonly trendChartOptions = opcoesGraficoTendenciaDesvios();
  private requestId = 0;

  /** Dimensões de múltipla seleção — ordem usada na URL e na contagem de filtros. */
  private readonly chavesDeSelecao: (keyof DashboardSstFilter)[] = [
    'idProcesso', 'idProcessoInspetor', 'idEquipe', 'idBaseOperacional', 'idCentroCusto', 'gravidade', 'status'
  ];
  /** Filtros que estavam ativos na consulta que produziu `data()`. */
  private readonly filtrosAplicados = signal<DashboardSstFilter>({});

  private readonly atualizacao = atualizarPeriodicamente(() => {
    this.buscar(this.filtrosAplicados(), true);
  }, { pode: () => !this.loading() && !!this.data() });

  /** Cadastros dos filtros como opções (as listas e os chips usam os mesmos rótulos). */
  protected readonly opcoes = {
    processos: computed(() => opcoesDe(this.processos(), 'nome')),
    equipes: computed(() => opcoesDe(this.equipes(), 'codigo')),
    centrosCusto: computed(() => opcoesDe(this.centrosCusto(), 'nome')),
    basesOperacionais: computed(() => opcoesDe(this.basesOperacionais(), 'nome'))
  };
  /** Recorte aplicado como chips (cada um remove o próprio filtro); o agrupamento do gráfico não recorta. */
  protected readonly chips = computed<CesFilterChip[]>(() => {
    const f = this.filtrosAplicados();
    return chipsDoRecorte(f as Record<string, unknown>, [
      { chave: 'idProcesso', rotulo: 'Processo da inspeção', opcoes: this.opcoes.processos() },
      { chave: 'idProcessoInspetor', rotulo: 'Processo do inspetor', opcoes: this.opcoes.processos() },
      { chave: 'idEquipe', rotulo: 'Equipe', opcoes: this.opcoes.equipes() },
      { chave: 'idCentroCusto', rotulo: 'Centro de custo', opcoes: this.opcoes.centrosCusto() },
      { chave: 'idBaseOperacional', rotulo: 'Base operacional', opcoes: this.opcoes.basesOperacionais() },
      { chave: 'gravidade', rotulo: 'Gravidade', opcoes: this.gravidadeOptions },
      { chave: 'status', rotulo: 'Status', opcoes: this.statusOptions }
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

  ngOnInit(): void {
    this.restaurarFiltrosDaUrl();
    this.hidratarEquipesSelecionadas();
    this.carregarLookups();
    this.carregar();
  }

  protected aplicar(): void {
    if (!this.periodoValido()) return;
    this.carregar();
  }
  protected openEquipes(): void { this.equipeLookup.open(); }
  protected searchEquipes(event: { filter?: string }): void { this.equipeLookup.search(event.filter ?? ''); }
  protected loadMoreEquipes(): void { this.equipeLookup.loadNext(); }
  protected limpar(): void {
    this.dataInicio = null;
    this.dataFim = null;
    this.filter = this.filtroVazio();
    this.carregar();
  }

  private filtroVazio(): DashboardSstFilter {
    return { idProcesso: [], idProcessoInspetor: [], idEquipe: [], idBaseOperacional: [], idCentroCusto: [], gravidade: [], status: [] };
  }

  /** Recompõe o recorte compartilhado por link (`?gravidade=ALTA&gravidade=CRITICA`). */
  private restaurarFiltrosDaUrl(): void {
    const query = this.route.snapshot.queryParamMap;
    this.chavesDeSelecao.forEach((chave) => {
      (this.filter[chave] as string[]) = normalizarSelecao(query.getAll(chave));
    });
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
    queryParams['inicio'] = filter.inicio ?? null;
    queryParams['fim'] = filter.fim ?? null;
    // `merge` preserva o `?drill=` do painel de detalhe aberto.
    this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }
  protected barWidth(value: number): number { return Math.max(0, Math.min(100, value ?? 0)); }
  protected percentualOuSemAmostra(total: number, value: number): string {
    return total > 0 ? `${value.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%` : 'Sem dados no período';
  }
  protected comparison(indicator: IndicadorComparativo): string {
    return this.comparisonFrom(indicator.anterior, indicator.variacaoPercentual);
  }
  protected descricaoIndicador(indicator: IndicadorComparativo): string {
    return toPlainDashboardLanguage(indicator.definicao);
  }
  protected comparisonFrom(previous: number, variationValue: number): string {
    if (previous === 0) return 'Sem período anterior para comparar';
    const variation = variationValue.toLocaleString('pt-BR', { maximumFractionDigits: 2, signDisplay: 'always' });
    return `${variation}% vs. período anterior`;
  }
  protected iconeVariacao(value: number): string {
    if (value > 0) return 'pi pi-arrow-up';
    if (value < 0) return 'pi pi-arrow-down';
    return 'pi pi-arrow-right';
  }
  protected recorteDesviosAplicado(): string | null {
    const filtros = this.filtrosAplicados();
    const partes: string[] = [];
    if (temSelecao(filtros.status)) {
      partes.push(
        `status atual: ${filtros.status!.map((status) => STATUS_DESVIO_LABELS[status]).join(', ')}`
      );
    }
    if (temSelecao(filtros.gravidade)) {
      partes.push(
        `gravidade: ${filtros.gravidade!.map((gravidade) => GRAVIDADE_LABELS[gravidade]).join(', ')}`
      );
    }
    return partes.length ? partes.join(' · ') : null;
  }
  protected rotuloGranularidade(granularidade: DashboardDesviosResponse['granularidade']): string {
    return { DIA: 'dia a dia', MES: 'mês a mês', ANO: 'ano a ano' }[granularidade];
  }
  protected rotuloGravidade(gravidade: GravidadeDesvio): string {
    return GRAVIDADE_LABELS[gravidade] ?? 'Não informada';
  }
  protected periodoComparativo(data: DashboardDesviosResponse): string {
    return `Comparação: ${this.formatarInstante(data.periodo.comparacaoInicio)} a ${this.formatarInstante(data.periodo.comparacaoFim)}`;
  }
  protected hasDataQualityWarning(data: DashboardDesviosResponse): boolean {
    const quality = data.qualidadeDados;
    return quality.registrosSemPrazo > 0 || quality.registrosSemResponsavel > 0;
  }
  // ------------------------------------------------------------------ análise detalhada

  protected analisar(dimension: string, value: string): void {
    this.analise.abrir(dimension, value);
  }

  protected analisarGrupo(dimensao: GrupoDesvio, id: string): void {
    this.analise.abrir('grupo', `${dimensao}~${id}`);
  }

  protected aprofundar(alvo: AlvoAnalise): void {
    this.analise.aprofundar(alvo, this.vistaAnalise()?.titulo ?? '');
  }

  /** Clique num ponto da tendência (p-chart): abre a análise daquele período. */
  protected onTendenciaSelect(evento: { element?: { index?: number } }): void {
    const d = this.data();
    const indice = evento?.element?.index;
    const ponto = d && typeof indice === 'number' ? d.tendenciaDesvios[indice] : undefined;
    if (d && ponto) this.analise.abrir(d.granularidade === 'DIA' ? 'dia' : d.granularidade === 'MES' ? 'mes' : 'ano', ponto.inicio);
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

  /** Contexto do dashboard para o Copiloto: o recorte aplicado e os números em tela. */
  protected readonly contextoCopiloto = computed(() => this.montarContextoCopiloto());

  /**
   * Recorte estruturado para o Copiloto: período, filtros aplicados (incluindo gravidade e status),
   * indicadores comparativos, pendências, agregações exibidas e datasets. Nunca os desvios individuais.
   */
  private montarContextoCopiloto(): CopilotoDashboardContext | null {
    const d = this.data();
    const f = this.filtrosAplicados();
    const nomes = <T>(itens: readonly T[], ids: readonly string[] | null | undefined, id: (i: T) => string, nome: (i: T) => string) =>
      rotuloDasOpcoes(itens, ids ?? [], id, nome);
    const um = (n: number, casas = 1) => (n ?? 0).toLocaleString('pt-BR', { maximumFractionDigits: casas });
    const indicador = (id: string, rotulo: string, ind: IndicadorComparativo | undefined, formato: 'inteiro' | 'percentual', melhor: 'maior' | 'menor') =>
      metricaPainel(id, rotulo, ind?.valor, formato, {
        descricao: ind?.definicao ? toPlainDashboardLanguage(ind.definicao) : undefined,
        comparacao: ind && ind.anterior
          ? { rotulo: 'vs. período anterior', valorAnterior: ind.anterior, variacaoPercentual: ind.variacaoPercentual, melhorQuando: melhor }
          : undefined,
      });
    const agrupamento = { processo: 'processo da inspeção', processoInspetor: 'processo do inspetor', base: 'base operacional', equipe: 'equipe' };
    const pendencias = this.pendingRows();
    const exposicao = this.exposureRows();
    const itens = d?.itensCriticosRecorrentes ?? [];
    const tendencia = d?.tendenciaDesvios ?? [];
    return {
      id: 'sst.desvios',
      nome: 'Desvios de segurança',
      modulo: 'SST',
      descricao: 'Ocorrência e tratamento dos desvios apontados em inspeções: volume por gravidade, pendências e vencidos, tempo de resolução, exposição por área e itens que mais reprovam.',
      rota: '/sst/dashboards/desvios',
      periodo: d ? periodoPainel(d.inicio, d.fim) : periodoPainel(this.dataInicio, this.dataFim),
      filtros: [
        filtroPainel('processo', 'Processo da inspeção', nomes(this.processos(), f.idProcesso, (i) => i.id, (i) => i.nome), f.idProcesso ?? []),
        filtroPainel('processoInspetor', 'Processo do inspetor', nomes(this.processos(), f.idProcessoInspetor, (i) => i.id, (i) => i.nome), f.idProcessoInspetor ?? []),
        filtroPainel('equipe', 'Equipe', nomes(this.equipes(), f.idEquipe, (i) => i.id, (i) => i.codigo ?? i.id), f.idEquipe ?? []),
        filtroPainel('centroCusto', 'Centro de custo', nomes(this.centrosCusto(), f.idCentroCusto, (i) => i.id, (i) => i.nome), f.idCentroCusto ?? []),
        filtroPainel('baseOperacional', 'Base operacional', nomes(this.basesOperacionais(), f.idBaseOperacional, (i) => i.id, (i) => i.nome), f.idBaseOperacional ?? []),
        filtroPainel('gravidade', 'Gravidade', (f.gravidade ?? []).map((g) => GRAVIDADE_LABELS[g] ?? g), f.gravidade ?? []),
        filtroPainel('status', 'Status', (f.status ?? []).map((s) => STATUS_DESVIO_LABELS[s] ?? s), f.status ?? []),
      ],
      metricas: d
        ? [
            indicador('totalDesvios', 'Desvios identificados', d.indicadores.totalDesvios, 'inteiro', 'menor'),
            indicador('desviosCriticos', 'Desvios críticos identificados', d.indicadores.desviosCriticos, 'inteiro', 'menor'),
            indicador('inspecoesComDesvio', 'Inspeções com desvio', d.indicadores.inspecoesComDesvio, 'inteiro', 'menor'),
            indicador('taxaResolucao', 'Desvios resolvidos (%)', d.indicadores.taxaResolucao, 'percentual', 'maior'),
            indicador('backlog', 'Total pendente até o fim do período', d.indicadores.backlog, 'inteiro', 'menor'),
            indicador('vencidos', 'Total vencido até o fim do período', d.indicadores.vencidos, 'inteiro', 'menor'),
            metricaPainel('pendentesPeriodo', 'Pendências identificadas no período', d.totalPendentes, 'inteiro', {
              descricao: `${d.totalAbertos} abertos; ${d.totalEmTratamento} em tratamento`,
            }),
            metricaPainel('vencidosPeriodo', 'Pendências vencidas do período', d.totalVencidos, 'inteiro', {
              descricao: `${d.totalNoPrazo} no prazo; ${d.totalSemPrazo} sem prazo`,
            }),
            metricaPainel('tempoMedianoResolucaoDias', 'Tempo mediano para resolver', d.tempoMedianoResolucaoDias, 'dias', {
              descricao: `P90 ${um(d.tempoP90ResolucaoDias, 2)} dia(s); ${d.totalResolvidos} resolvidos`,
            }),
            metricaPainel('colaboradoresReincidentes', 'Colaboradores reincidentes', d.totalColaboradoresReincidentes, 'inteiro', {
              descricao: `de ${d.totalColaboradoresEnvolvidos} envolvidos (${um(d.percentualReincidencia)}%)`,
            }),
          ]
        : [],
      graficos: d
        ? [
            graficoPainel('tendencia', 'linha', 'Desvios identificados ao longo do período', tendencia.map((i) => i.rotulo), tendencia.map((i) => i.totalDesvios), {
              metrica: 'desvios', dimensao: `período (${this.rotuloGranularidade(d.granularidade)})`, formato: 'inteiro',
            }),
            graficoPainel('porGravidade', 'barra', 'Distribuição por gravidade', d.volumePorGravidade.map((v) => this.rotuloGravidade(v.gravidade)), [
              { nome: 'Atual', valores: d.volumePorGravidade.map((v) => v.total) },
              { nome: 'Período anterior', valores: d.volumePorGravidade.map((v) => v.anterior) },
            ], { metrica: 'desvios', dimensao: 'gravidade', formato: 'inteiro' }),
            graficoPainel('backlogPorIdade', 'barra', 'Pendências por tempo em aberto', d.backlogPorIdade.map((b) => this.rotuloFaixaIdade(b.faixa)),
              d.backlogPorIdade.map((b) => b.total), { metrica: 'pendências', dimensao: 'tempo em aberto', formato: 'inteiro' }),
            graficoPainel(`pendencias.${this.pendingDimension()}`, 'tabela', `Pendências que exigem gestão — por ${agrupamento[this.pendingDimension()]}`,
              pendencias.map((p) => p.nome ?? 'Cadastro não localizado'), [
                { nome: 'Pendentes', valores: pendencias.map((p) => p.totalPendentes) },
                { nome: 'Vencidos', valores: pendencias.map((p) => p.totalVencidos) },
                { nome: 'Críticos', valores: pendencias.map((p) => p.totalCriticos) },
              ], { metrica: 'pendências', dimensao: agrupamento[this.pendingDimension()], totalCategorias: pendencias.length * 3 }),
            graficoPainel(`exposicao.${this.exposureDimension()}`, 'tabela', `Desvios a cada 100 inspeções — por ${agrupamento[this.exposureDimension()]}`,
              exposicao.map((e) => e.nome ?? 'Cadastro não localizado'), exposicao.map((e) => e.desviosPor100Inspecoes), {
                metrica: 'desvios por 100 inspeções', dimensao: agrupamento[this.exposureDimension()], formato: 'numero',
              }),
            graficoPainel('itensCriticos', 'tabela', 'Itens com mais desvios', itens.map((i) => i.item ?? 'Cadastro não localizado'),
              itens.map((i) => i.reprovacoes), { metrica: 'desvios (reprovações)', dimensao: 'item do checklist', formato: 'inteiro' }),
          ]
        : [],
      datasets: ['desvios', 'inspecoes', 'equipes', 'colaboradores', 'bases_operacionais', 'processos_negocio'],
      extras: d ? { 'Período de comparação': this.periodoComparativo(d).replace('Comparação: ', '') } : undefined,
      sugestoes: [
        { rotulo: 'Desvios vencidos', icone: 'pi pi-calendar-times', requer: ['dados'],
          pergunta: 'Quais desvios deste recorte estão vencidos ou sem prazo, com qual gravidade e em quais equipes?' },
        { rotulo: 'Onde os desvios se concentram', icone: 'pi pi-map-marker', requer: ['dados', 'analise'],
          pergunta: 'Onde os desvios se concentram neste recorte, considerando o número de inspeções de cada área? O que explica isso?' },
        { rotulo: 'Por que o volume mudou?', icone: 'pi pi-arrow-right-arrow-left', requer: ['dados', 'analise'],
          pergunta: 'O que explica a variação do número de desvios em relação ao período anterior?' },
      ],
    };
  }

  private carregarLookups(): void {
    forkJoin({
      processos: this.rh.listarProcessosAtivos(),
      bases: this.rh.listarBasesOperacionais({ ativo: true, page: 0, size: 100, sort: 'nome,asc' }),
      centrosCusto: this.centrosCustoLookup.listar()
    }).subscribe({
      next: (response) => {
        this.processos.set(response.processos.content);
        this.basesOperacionais.set(response.bases.content);
        this.centrosCusto.set(response.centrosCusto);
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
    const requestId = ++this.requestId;
    if (!silencioso) this.loading.set(true);
    this.sst.gerarDashboardDesvios(filter).subscribe({
      next: (response) => {
        if (requestId !== this.requestId) return;
        this.data.set(response);
        this.filtrosAplicados.set(filter);
        this.atualizadoEm.set(new Date(response.atualizadoEm));
        this.prepararGrafico(response);
        this.loading.set(false);
      },
      error: (error) => {
        if (silencioso) return;
        if (requestId !== this.requestId) return;
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

  private prepararGrafico(data: DashboardDesviosResponse): void {
    this.deviationTrendData = montarDadosTendenciaDesvios(
      data.tendenciaDesvios ?? [],
      this.themeService.primaryColor()
    );
  }

  protected rotuloFaixaIdade(faixa: string): string {
    if (faixa === '0-7') return 'Até 7 dias';
    if (faixa === '8-15') return '8 a 15 dias';
    if (faixa === '16-30') return '16 a 30 dias';
    if (faixa === '31-60') return '31 a 60 dias';
    if (faixa === '>60') return 'Mais de 60 dias';
    return `${faixa} dias`;
  }

  protected subtituloFaixaIdade(faixa: string): string {
    if (faixa === '0-7') return 'Recente';
    if (faixa === '8-15') return 'Em prazo';
    if (faixa === '16-30') return 'Atenção';
    if (faixa === '31-60') return 'Alerta';
    if (faixa === '>60') return 'Crítico';
    return 'Em aberto';
  }

  protected tomFaixaIdade(faixa: string): 'success' | 'info' | 'warning' | 'orange' | 'danger' {
    if (faixa === '0-7') return 'success';
    if (faixa === '8-15') return 'info';
    if (faixa === '16-30') return 'warning';
    if (faixa === '31-60') return 'orange';
    if (faixa === '>60') return 'danger';
    return 'info';
  }

  protected subtituloGravidade(gravidade: GravidadeDesvio): string {
    if (gravidade === 'CRITICA') return 'Urgente';
    if (gravidade === 'ALTA') return 'Alta prioridade';
    if (gravidade === 'MEDIA') return 'Moderada';
    if (gravidade === 'BAIXA') return 'Rotina';
    return 'Desvio';
  }

  protected tomGravidade(gravidade: GravidadeDesvio): 'success' | 'info' | 'warning' | 'orange' | 'danger' {
    if (gravidade === 'CRITICA') return 'danger';
    if (gravidade === 'ALTA') return 'orange';
    if (gravidade === 'MEDIA') return 'warning';
    if (gravidade === 'BAIXA') return 'info';
    return 'info';
  }

  protected classeVariacaoGravidade(volume: VolumePorGravidade): string {
    if (volume.anterior === 0 || volume.variacaoPercentual === 0) {
      return '';
    }
    if (volume.variacaoPercentual < 0) {
      return 'dash-badge--success';
    }
    if (volume.gravidade === 'CRITICA') {
      return 'dash-badge--danger';
    }
    if (volume.gravidade === 'ALTA') {
      return 'dash-badge--orange';
    }
    if (volume.gravidade === 'MEDIA') {
      return 'dash-badge--warning';
    }
    return '';
  }

  protected formatarVariacao(variacao: number, anterior: number): string {
    if (anterior === 0) return 'Sem base anterior';
    if (variacao === 0) return '0% estável';
    const sinal = variacao > 0 ? '+' : '';
    return `${sinal}${variacao.toLocaleString('pt-BR', { maximumFractionDigits: 1, minimumFractionDigits: 0 })}%`;
  }

  protected textoTooltipVariacao(volume: VolumePorGravidade): string {
    if (volume.anterior === 0) {
      return `Sem registros no período anterior para comparar (atual: ${volume.total}).`;
    }
    const sinal = volume.variacaoPercentual > 0 ? '+' : '';
    const perc = `${sinal}${volume.variacaoPercentual.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`;
    return `${perc} vs. período anterior (${volume.anterior} no período anterior vs. ${volume.total} atual)`;
  }

  protected percentualGravidade(total: number, quantidade: number): number {
    if (!total || total <= 0 || !quantidade) return 0;
    return Math.min(100, Math.max(0, (quantidade / total) * 100));
  }

  protected totalBacklogPorIdade(data: DashboardDesviosResponse): number {
    return (data.backlogPorIdade ?? []).reduce((acc, f) => acc + (f.total || 0), 0);
  }

  private toIso(date: Date, endOfDay: boolean): string {
    const value = new Date(date);
    value.setHours(endOfDay ? 23 : 0, endOfDay ? 59 : 0, endOfDay ? 59 : 0, endOfDay ? 999 : 0);
    return toSaoPauloIso(value, endOfDay);
  }

  private formatarInstante(value: string): string {
    return new Intl.DateTimeFormat('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: 'America/Sao_Paulo'
    }).format(new Date(value));
  }
}
