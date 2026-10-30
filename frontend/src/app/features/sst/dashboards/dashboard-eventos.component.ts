import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { ChartModule } from 'primeng/chart';
import { SkeletonModule } from 'primeng/skeleton';
import { TooltipModule } from 'primeng/tooltip';

import { ThemeService, CesPrimaryColor } from '../../../core/services/theme.service';

import { getErrorMessage } from '../../../core/models/shared.models';
import {
  BaseOperacionalResponse,
  CentroFaturamentoResponse,
  EquipeResponse,
  ProcessoNegocioResponse
} from '../../../core/models/rh.models';
import {
  DashboardEventosSstFilter,
  DashboardEventosSstResponse,
  RankingEventoItem,
  TIPO_EVENTO_LABELS,
  opcoesDe
} from '../../../core/models/sst-evento.models';
import { CentrosCustoLookupService } from '../../../core/services/centros-custo-lookup.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { normalizarSelecao, temSelecao } from '../../../core/utils/http-params.util';
import { toSaoPauloIso } from '../../../shared/utils/dashboard-date.utils';
import { DashboardMetricCardComponent } from '../../../shared/components/dashboard/dashboard-ui.components';
import { DashboardAnaliseComponent } from '../../../shared/components/dashboard/dashboard-analise.component';
import { AlvoAnalise, VistaAnalise } from '../../../shared/components/dashboard/dashboard-analise.models';
import { NavegacaoAnaliseService } from '../../../shared/components/dashboard/dashboard-analise.navegacao';
import { DashboardVisaoGerencialComponent } from '../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import { visaoEventos, vistaEventos } from './analise/analise-eventos';
import { diaCivil } from './analise/analise-sst-comum';
import { CesButtonComponent, CesCellDirective, CesDataTableColumn, CesDataTableComponent, CesDrillStateService, CesStatusComponent,
  CesDatepickerComponent,
  CesFilterChip,
  CesFilterPanelComponent,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  NotificationService
} from '@ces/ui';
import { chipsDoRecorte, opcoesDe as opcoesDoCadastro } from '../../../shared/components/dashboard/dashboard-filtros';
import { DiasSemAcidenteItem, TipoEventoSst } from '../../../core/models/sst-evento.models';
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
import { MapaCorpoLesoesComponent } from './mapa-corpo-lesoes.component';
import { PiramideBirdComponent } from './piramide-bird.component';
import {
  calcularEventosSemLesao,
  montarDadosGraficoEvolucao
} from './dashboard-eventos-chart.config';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { atualizarPeriodicamente } from '../../../shared/components/dashboard/atualizacao-automatica';

type DimensaoRanking = 'classificacao' | 'atividade' | 'gestor' | 'base' | 'equipe' | 'processo' | 'localLesao';

/**
 * Dashboard executivo de acidentalidade.
 *
 * Usa o mesmo shell dos demais dashboards de SST (`dash-*`, em
 * `shared/styles/dashboard-shell.css`) — as quatro telas precisam ler como a mesma
 * ferramenta, e não como quatro relatórios de origens diferentes.
 */
@Component({
  selector: 'app-sst-dashboard-eventos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesDatepickerComponent, CesFilterPanelComponent, CesMultiSelectComponent, CesPageHeaderComponent, CesSelectComponent,
    CommonModule, DecimalPipe, ChartModule, SkeletonModule, TooltipModule, CesCellDirective, CesDataTableComponent, CesStatusComponent,
    DashboardMetricCardComponent, MapaCorpoLesoesComponent, PiramideBirdComponent, CesButtonComponent, CopilotoAreaComponent, CopilotoGatilhoComponent,
    CopilotoPerguntarComponent, DashboardAnaliseComponent, DashboardVisaoGerencialComponent],
  providers: [CesDrillStateService, NavegacaoAnaliseService],
  templateUrl: './dashboard-eventos.component.html',
  styles: [`
    :host .dash-filterbar--eventos { grid-template-columns: repeat(5, minmax(0, 1fr)); }
    :host .dash-filterbar--eventos .dash-field,
    :host .dash-filterbar--eventos .dash-filterbar__actions { grid-column: auto; }
    :host .dash-filterbar--eventos .dash-filterbar__actions { grid-column: span 2; justify-content: flex-end; }
    :host .dash-metrics { grid-template-columns: repeat(5, minmax(0, 1fr)); }
    :host .dash-grid--overview .dash-col-8 { grid-column: span 7; }
    :host .dash-grid--overview .dash-col-4 { grid-column: span 5; }
    :host .dash-col-12 { grid-column: 1 / -1; }

    /* --- Responsive chart --- */
    .dash-chart--responsive { height: clamp(260px, 22vw, 360px); padding-top: .25rem; }
    .dash-chart--responsive .p-chart { display: block; width: 100%; height: 100%; }
    .dash-chart--responsive canvas { width: 100% !important; max-width: 100%; }

    /* --- Backlog card grid --- */
    .backlog-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: .65rem;
    }
    .backlog-card {
      border: 1px solid #e5edf4;
      border-radius: .85rem;
      background: #fbfdff;
      overflow: hidden;
      transition: border-color 150ms ease, box-shadow 150ms ease;
    }
    .backlog-card:hover { border-color: #bfdbfe; box-shadow: 0 4px 12px rgba(16, 42, 67, .06); }
    /* Faixa clicável: botão esticado sobre o cartão abre os eventos dela. */
    .backlog-card { position: relative; }
    .backlog-card__hit {
      position: absolute; inset: 0; z-index: 1; width: 100%; padding: 0; border: 0; border-radius: inherit;
      background: transparent; cursor: pointer;
    }
    .backlog-card__hit:disabled { cursor: default; }
    .backlog-card__hit:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: -2px; }
    .backlog-card.is-vazia { opacity: .5; }
    .backlog-card__header {
      padding: .6rem .85rem;
      border-bottom: 1px solid #f1f5f9;
      background: #f8fafc;
    }
    .backlog-card__eyebrow {
      display: block;
      font-size: .54rem;
      font-weight: 700;
      color: #94a3b8;
      text-transform: uppercase;
      letter-spacing: .07em;
    }
    .backlog-card__faixa { display: block; font-size: .82rem; font-weight: 650; color: #334155; margin-top: .1rem; }
    .backlog-card__body {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: .75rem;
      padding: .65rem .85rem;
    }
    .backlog-card__status { display: flex; flex-direction: column; gap: .2rem; }
    .backlog-card__status span {
      display: inline-flex;
      align-items: center;
      gap: .3rem;
      font-size: .64rem;
      color: #64748b;
    }
    .backlog-card__status i { font-size: .58rem; }
    .backlog-card__total { font-size: 1.6rem; font-weight: 700; color: #0f172a; line-height: 1; font-variant-numeric: tabular-nums; }

    /* --- Table polish --- */
    .dash-cell-name { color: #243b53; font-size: .78rem; font-weight: 600; }
    :host-context(.app-dark) .dash-cell-name, :host-context(.dark) .dash-cell-name { color: #e2e8f0; }
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
    .dash-badge--danger { background: #fef2f2; color: #b91c1c; }
    .dash-badge--danger-text { color: #b91c1c; font-weight: 700; }
    .dash-badge i { font-size: .6rem; }

    /* --- Compact empty state --- */
    .dash-empty--compact {
      display: flex;
      align-items: center;
      gap: .75rem;
      padding: 1.5rem 1.25rem;
      border: 1px dashed #d9e2ec;
      border-radius: .75rem;
      color: #94a3b8;
      font-size: .82rem;
      line-height: 1.5;
    }
    .dash-empty--compact i { font-size: 1.25rem; flex: none; color: #cbd5e1; }
    .dash-empty--compact strong { color: #475569; }

    /* --- Dark mode --- */
    :host-context(.app-dark), :host-context(.dark) {
      .backlog-card { background: var(--ces-surface, #1e293b); border-color: var(--ces-border, #334155); }
      .backlog-card:hover { border-color: color-mix(in srgb, var(--ces-brand, #3b82f6) 40%, transparent); }
      .backlog-card__header { background: var(--ces-surface-subtle, #0f172a); border-color: var(--ces-border, #334155); }
      .backlog-card__faixa, .backlog-card__total { color: #fff !important; }
      .backlog-card__status span { color: #cbd5e1; }

      .dash-pct-pill { background: var(--ces-surface-muted, #334155); color: #cbd5e1; }
      .dash-badge { background: var(--ces-surface-muted, #334155); color: #cbd5e1; }
      .dash-badge--success { background: rgba(34,197,94,.12); color: #4ade80; }
      .dash-badge--danger { background: rgba(239,68,68,.12); color: #f87171; }
      .dash-badge--danger-text { color: #f87171; }
      .dash-empty--compact { border-color: var(--ces-border, #334155); color: var(--ces-foreground-subtle, #94a3b8); }
      .dash-empty--compact strong { color: #cbd5e1; }
    }

    /* --- Responsiveness --- */
    @media (max-width: 1360px) {
      :host .dash-filterbar--eventos { grid-template-columns: repeat(4, minmax(0, 1fr)); }
      :host .dash-filterbar--eventos .dash-filterbar__actions { grid-column: span 4; }
      :host .dash-metrics { grid-template-columns: repeat(6, minmax(0, 1fr)); }
      :host .dash-metrics > * { grid-column: span 2; }
      :host .dash-metrics > :nth-last-child(-n + 2) { grid-column: span 3; }
    }
    @media (max-width: 1080px) {
      :host .dash-filterbar--eventos { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      :host .dash-filterbar--eventos .dash-filterbar__actions { grid-column: span 2; }
      :host .dash-grid--overview .dash-col-8,
      :host .dash-grid--overview .dash-col-4 { grid-column: span 12; }
    }
    @media (max-width: 720px) {
      :host .dash-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      :host .dash-metrics > *, :host .dash-metrics > :nth-last-child(-n + 2) { grid-column: auto; }
      :host .dash-metrics > :last-child { grid-column: 1 / -1; }
      .backlog-grid { grid-template-columns: 1fr; }
      .dash-chart--responsive { height: clamp(220px, 50vw, 300px); }
    }
    @media (max-width: 560px) {
      :host .dash-filterbar--eventos { grid-template-columns: 1fr; }
      :host .dash-filterbar--eventos .dash-filterbar__actions { grid-column: auto; }
      :host .dash-metrics { grid-template-columns: 1fr; }
      :host .dash-metrics > :last-child { grid-column: auto; }
    }
  `]
})
export class DashboardEventosComponent implements OnInit {
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

  constructor() {
    // Link de análise que não existe mais no recorte: fecha o painel.
    effect(() => {
      if (this.analise.alvo() && this.data() && !this.loading() && !this.vistaAnalise()) this.analise.fechar();
    });
    effect(() => {
      const corPrimaria = this.themeService.primaryColor();
      const dadosAtuais = this.data();
      if (dadosAtuais) {
        this.prepararGrafico(dadosAtuais, corPrimaria);
      }
    });
  }

  protected readonly loading = signal(false);
  protected readonly data = signal<DashboardEventosSstResponse | null>(null);
  protected readonly atualizadoEm = signal<Date | null>(null);
  protected readonly mostrarMapaCorporal = signal(false);
  protected readonly processos = signal<ProcessoNegocioResponse[]>([]);
  protected readonly equipes = this.equipeLookup.items;
  protected readonly equipesLoading = this.equipeLookup.loading;
  protected readonly basesOperacionais = signal<BaseOperacionalResponse[]>([]);
  protected readonly centrosCusto = signal<CentroFaturamentoResponse[]>([]);
  protected readonly rankingDimensao = signal<DimensaoRanking>('classificacao');
  /** Veredito do recorte e leituras acionáveis (cada uma abre a análise que a sustenta). */
  protected readonly visaoGerencial = computed(() => {
    const d = this.data();
    return d ? visaoEventos(d) : null;
  });

  /** Análise detalhada aberta (`?drill=`), resolvida contra os dados em tela. */
  protected readonly vistaAnalise = computed<VistaAnalise | null>(() => {
    const alvo = this.analise.alvo();
    const d = this.data();
    if (!alvo || !d) return null;
    return vistaEventos(alvo, { d, filtros: this.filtrosAplicados(), sst: this.sst });
  });

  protected readonly colunasComposicao: CesDataTableColumn<{ tipo: TipoEventoSst; rotulo: string; total: number; percentual: number }>[] = [
    { key: 'rotulo', header: 'Tipo', priority: 1 },
    { key: 'total', header: 'Total', type: 'integer', priority: 1 },
    { key: 'percentual', header: '%', type: 'percent', priority: 1 }
  ];
  protected readonly colunasDiasSem: CesDataTableColumn<DiasSemAcidenteItem>[] = [
    { key: 'baseNome', header: 'Base', priority: 1 },
    { key: 'diasSemAcidente', header: 'Dias sem lesão', type: 'custom', priority: 1, value: (b) => b.diasSemAcidente ?? 99999 },
    { key: 'ultimaOcorrencia', header: 'Última ocorrência', priority: 2, value: (b) => b.ultimaOcorrencia ?? '', display: (b) => (b.ultimaOcorrencia ? new Date(b.ultimaOcorrencia).toLocaleDateString('pt-BR') : '—') },
    { key: 'comLesaoNoPeriodo', header: 'Com lesão', type: 'custom', priority: 1, value: (b) => b.comLesaoNoPeriodo }
  ];
  protected readonly colunasRanking: CesDataTableColumn<RankingEventoItem>[] = [
    { key: 'nome', header: 'Item', priority: 1 },
    { key: 'total', header: 'Eventos', type: 'integer', priority: 1 },
    { key: 'percentual', header: '% do período', type: 'percent', priority: 1 }
  ];
  protected readonly chaveTipo = (l: { tipo: string }) => l.tipo;
  protected readonly chaveBase = (b: DiasSemAcidenteItem) => b.idBaseOperacional;
  protected readonly rotuloBase = (b: DiasSemAcidenteItem) => b.baseNome;
  protected readonly chaveRanking = (r: RankingEventoItem) => r.id;
  protected readonly rotuloRanking = (r: RankingEventoItem) => r.nome;

  protected readonly tipoOptions = opcoesDe(TIPO_EVENTO_LABELS);
  protected readonly granularidadeOptions = [
    { label: 'Automática', value: null },
    { label: 'Por dia', value: 'DIA' },
    { label: 'Por mês', value: 'MES' },
    { label: 'Por ano', value: 'ANO' }
  ];

  protected readonly abasRanking: { label: string; value: DimensaoRanking }[] = [
    { label: 'Classificação', value: 'classificacao' },
    { label: 'Atividade', value: 'atividade' },
    { label: 'Gestor', value: 'gestor' },
    { label: 'Base', value: 'base' },
    { label: 'Equipe', value: 'equipe' },
    { label: 'Processo', value: 'processo' },
    { label: 'Local da lesão', value: 'localLesao' }
  ];

  protected readonly linhasRanking = computed<RankingEventoItem[]>(() => {
    const atual = this.data();
    if (!atual) return [];
    return {
      classificacao: atual.porClassificacao,
      atividade: atual.porAtividade,
      gestor: atual.porGestor,
      base: atual.porBaseOperacional,
      equipe: atual.porEquipe,
      processo: atual.porProcesso,
      localLesao: atual.porLocalLesao
    }[this.rankingDimensao()] ?? [];
  });

  protected dataInicio: Date | null = null;
  protected dataFim: Date | null = null;
  protected filter: DashboardEventosSstFilter = this.filtroVazio();
  protected serieChartData: unknown;
  protected readonly serieChartOptions = this.chartOptions();

  private readonly chavesDeSelecao: (keyof DashboardEventosSstFilter)[] = [
    'tipo', 'idClassificacao', 'idAtividade', 'idGestor', 'idProcesso', 'idBaseOperacional',
    'idCentroCusto', 'idEquipe'
  ];
  private readonly filtrosAplicados = signal<DashboardEventosSstFilter>({});

  private readonly atualizacao = atualizarPeriodicamente(() => {
    this.buscar(this.filtrosAplicados(), true);
  }, { pode: () => !this.loading() && !!this.data() });

  /** Cadastros dos filtros como opções (as listas e os chips usam os mesmos rótulos). */
  protected readonly opcoes = {
    basesOperacionais: computed(() => opcoesDoCadastro(this.basesOperacionais(), 'nome')),
    centrosCusto: computed(() => opcoesDoCadastro(this.centrosCusto(), 'nome')),
    equipes: computed(() => opcoesDoCadastro(this.equipes(), 'codigo')),
    processos: computed(() => opcoesDoCadastro(this.processos(), 'nome'))
  };
  /** Recorte aplicado como chips (cada um remove o próprio filtro); o agrupamento do gráfico não recorta. */
  protected readonly chips = computed<CesFilterChip[]>(() => {
    const f = this.filtrosAplicados();
    return chipsDoRecorte(f as Record<string, unknown>, [
      { chave: 'tipo', rotulo: 'Tipo', opcoes: this.tipoOptions },
      { chave: 'idBaseOperacional', rotulo: 'Base operacional', opcoes: this.opcoes.basesOperacionais() },
      { chave: 'idCentroCusto', rotulo: 'Centro de custo', opcoes: this.opcoes.centrosCusto() },
      { chave: 'idEquipe', rotulo: 'Equipe', opcoes: this.opcoes.equipes() },
      { chave: 'idProcesso', rotulo: 'Processo', opcoes: this.opcoes.processos() }
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

  /** Contexto do dashboard para o Copiloto: o recorte aplicado e os números em tela. */
  protected readonly contextoCopiloto = computed(() => this.montarContextoCopiloto());

  ngOnInit(): void {
    this.restaurarFiltrosDaUrl();
    this.hidratarEquipesSelecionadas();
    this.carregarLookups();
    this.carregar();
  }

  /**
   * Recorte estruturado para o Copiloto: período, filtros, resumo de acidentalidade, pirâmide, dias sem
   * acidente, backlog e rankings exibidos. Nunca os eventos individuais nem dados das vítimas — o agente
   * consulta as ferramentas (que aplicam as permissões e preferem agregados para dados sensíveis).
   */
  private montarContextoCopiloto(): CopilotoDashboardContext | null {
    const d = this.data();
    const f = this.filtrosAplicados();
    const nomes = <T>(itens: readonly T[], ids: readonly string[] | null | undefined, id: (i: T) => string, nome: (i: T) => string) =>
      rotuloDasOpcoes(itens, ids ?? [], id, nome);
    const doRanking = (itens: readonly RankingEventoItem[] | undefined, ids: readonly string[] | null | undefined) =>
      nomes(itens ?? [], ids, (i) => i.id, (i) => i.nome);
    const um = (n: number | null | undefined, casas = 1) => (n ?? 0).toLocaleString('pt-BR', { maximumFractionDigits: casas });
    const rotuloRanking = this.abasRanking.find((aba) => aba.value === this.rankingDimensao())?.label ?? 'Agrupamento';
    const ranking = this.linhasRanking();
    const serie = d?.serie ?? [];
    return {
      id: 'sst.eventos',
      nome: 'Acidentalidade (eventos de segurança)',
      modulo: 'SST',
      descricao: 'Eventos de segurança (acidentes e incidentes): volume, lesões, dias perdidos, CAT, comunicação, pirâmide de Bird, dias sem acidente por base, eventos em aberto e rankings.',
      rota: '/sst/dashboards/eventos',
      periodo: d ? periodoPainel(d.inicio, d.fim) : periodoPainel(this.dataInicio, this.dataFim),
      filtros: [
        filtroPainel('tipo', 'Tipo de evento', (f.tipo ?? []).map((t) => TIPO_EVENTO_LABELS[t] ?? t), f.tipo ?? []),
        filtroPainel('classificacao', 'Classificação', doRanking(d?.porClassificacao, f.idClassificacao), f.idClassificacao ?? []),
        filtroPainel('atividade', 'Atividade', doRanking(d?.porAtividade, f.idAtividade), f.idAtividade ?? []),
        filtroPainel('gestor', 'Gestor', doRanking(d?.porGestor, f.idGestor), f.idGestor ?? []),
        filtroPainel('processo', 'Processo', nomes(this.processos(), f.idProcesso, (i) => i.id, (i) => i.nome), f.idProcesso ?? []),
        filtroPainel('baseOperacional', 'Base operacional', nomes(this.basesOperacionais(), f.idBaseOperacional, (i) => i.id, (i) => i.nome), f.idBaseOperacional ?? []),
        filtroPainel('centroCusto', 'Centro de custo', nomes(this.centrosCusto(), f.idCentroCusto, (i) => i.id, (i) => i.nome), f.idCentroCusto ?? []),
        filtroPainel('equipe', 'Equipe', nomes(this.equipes(), f.idEquipe, (i) => i.id, (i) => i.codigo ?? i.id), f.idEquipe ?? []),
      ],
      metricas: d
        ? [
            metricaPainel('totalEventos', 'Eventos registrados', d.resumo.total, 'inteiro', {
              comparacao: d.resumo.totalAnterior
                ? { rotulo: 'vs. período anterior', valorAnterior: d.resumo.totalAnterior, variacaoPercentual: d.resumo.variacaoPercentual, melhorQuando: 'menor' }
                : undefined,
            }),
            metricaPainel('comLesao', 'Eventos com lesão', d.resumo.comLesao, 'inteiro', {
              comparacao: d.resumo.comLesaoAnterior
                ? { rotulo: 'vs. período anterior', valorAnterior: d.resumo.comLesaoAnterior, variacaoPercentual: d.resumo.variacaoComLesao, melhorQuando: 'menor' }
                : undefined,
            }),
            metricaPainel('diasSemEventos', 'Dias sem eventos', d.resumo.diasSemEventos, 'inteiro', {
              descricao: 'métrica global: ignora período e filtros',
            }),
            metricaPainel('diasPerdidos', 'Dias perdidos', d.resumo.diasPerdidos, 'inteiro'),
            metricaPainel('coberturaCat', 'Cobertura de CAT', d.resumo.coberturaCat, 'percentual', {
              descricao: `${d.resumo.comCat} eventos com CAT`,
            }),
            metricaPainel('mediaHorasComunicacao', 'Tempo médio até a comunicação', d.resumo.mediaHorasComunicacao, 'horas', {
              descricao: `${d.resumo.comunicacoesAtrasadas} comunicações atrasadas (${um(d.resumo.percentualAtrasadas)}%)`,
            }),
            metricaPainel('abertos', 'Eventos em aberto', d.resumo.abertos, 'inteiro', { descricao: `${d.resumo.concluidos} concluídos` }),
            metricaPainel('razaoDesviosPorLesao', 'Desvios por evento com lesão', d.resumo.razaoDesviosPorLesao, 'numero', {
              descricao: `${d.resumo.desviosNoPeriodo} desvios de inspeção no período`,
            }),
          ]
        : [],
      graficos: d
        ? [
            graficoPainel('serie', 'barra', 'Eventos ao longo do período', serie.map((i) => i.rotulo), [
              { nome: 'Eventos', valores: serie.map((i) => i.total) },
              { nome: 'Com lesão', valores: serie.map((i) => i.comLesao) },
              { nome: 'Dias perdidos', valores: serie.map((i) => i.diasPerdidos) },
            ], { metrica: 'eventos', dimensao: 'período', formato: 'inteiro' }),
            graficoPainel('piramide', 'piramide', 'Pirâmide de acidentalidade (Bird)', d.piramide.map((p) => p.rotulo), d.piramide.map((p) => p.total), {
              metrica: 'ocorrências', dimensao: 'degrau', formato: 'inteiro',
            }),
            graficoPainel('diasSemAcidente', 'tabela', 'Dias sem acidente por base', d.diasSemAcidente.map((b) => b.baseNome),
              d.diasSemAcidente.map((b) => b.diasSemAcidente), { metrica: 'dias sem acidente com lesão', dimensao: 'base operacional', formato: 'inteiro' }),
            graficoPainel('backlog', 'barra', 'Eventos em aberto por idade', d.backlog.map((b) => `${b.faixa} dias`), d.backlog.map((b) => b.total), {
              metrica: 'eventos em aberto', dimensao: 'idade', formato: 'inteiro',
            }),
            graficoPainel(`ranking.${this.rankingDimensao()}`, 'ranking', `Eventos por ${rotuloRanking.toLowerCase()}`, ranking.map((i) => i.nome),
              ranking.map((i) => i.total), { metrica: 'eventos', dimensao: rotuloRanking.toLowerCase(), formato: 'inteiro', totalCategorias: ranking.length }),
          ]
        : [],
      datasets: ['eventos_sst', 'desvios', 'inspecoes', 'bases_operacionais', 'equipes', 'processos_negocio'],
      sugestoes: [
        { rotulo: 'O que explica os eventos?', icone: 'pi pi-search', requer: ['dados', 'analise'],
          pergunta: 'O que explica os eventos de segurança deste recorte? Relacione com atividades, classificação e desvios de inspeção.' },
        { rotulo: 'Comunicações atrasadas', icone: 'pi pi-clock', requer: ['dados'],
          pergunta: 'Quais eventos deste recorte foram comunicados com atraso e quais gestores concentram esses casos?' },
        { rotulo: 'Bases com mais risco', icone: 'pi pi-map-marker', requer: ['dados', 'analise'],
          pergunta: 'Quais bases concentram eventos com lesão neste recorte e há quanto tempo estão sem acidente?' },
      ],
    };
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

  // ------------------------------------------------------------------ análise detalhada

  protected analisar(dimension: string, value: string): void {
    this.analise.abrir(dimension, value);
  }

  protected aprofundar(alvo: AlvoAnalise): void {
    this.analise.aprofundar(alvo, this.vistaAnalise()?.titulo ?? '');
  }

  /** Clique numa barra da evolução (p-chart): abre a análise daquele período. */
  protected onSerieSelect(evento: { element?: { index?: number } }): void {
    const d = this.data();
    const indice = evento?.element?.index;
    const ponto = d && typeof indice === 'number' ? d.serie[indice] : undefined;
    if (!d || !ponto) return;
    this.analise.abrir(d.granularidade === 'DIA' ? 'dia' : d.granularidade === 'MES' ? 'mes' : 'ano', diaCivil(ponto.data));
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

  private filtroVazio(): DashboardEventosSstFilter {
    return {
      tipo: [], idClassificacao: [], idAtividade: [], idGestor: [], idProcesso: [],
      idBaseOperacional: [], idCentroCusto: [], idEquipe: [], granularidade: null
    };
  }

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

  private sincronizarUrl(filter: DashboardEventosSstFilter): void {
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
    const filter: DashboardEventosSstFilter = {
      ...this.filter,
      inicio: this.dataInicio ? this.toIso(this.dataInicio, false) : null,
      fim: this.dataFim ? this.toIso(this.dataFim, true) : null
    };
    this.sincronizarUrl(filter);
    this.buscar(filter);
  }

  /** Busca um recorte; a atualização automática repete o aplicado sem mostrar carregamento nem avisar erro. */
  private buscar(filter: DashboardEventosSstFilter, silencioso = false): void {
    if (!silencioso) this.loading.set(true);
    this.sst.gerarDashboardEventos(filter).subscribe({
      next: (response) => {
        this.data.set(response);
        this.filtrosAplicados.set(filter);
        this.atualizadoEm.set(new Date(response.geradoEm));
        this.prepararGrafico(response);
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

  /** Total em barras e lesões em linha: séries coincidentes continuam distinguíveis. */
  private prepararGrafico(data: DashboardEventosSstResponse, corPrimaria?: CesPrimaryColor): void {
    const cor = corPrimaria ?? this.themeService.primaryColor();
    this.serieChartData = montarDadosGraficoEvolucao(data.serie ?? [], cor);
  }

  private chartOptions(): unknown {
    return {
      maintainAspectRatio: false,
      responsive: true,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: {
          display: true,
          position: 'bottom',
          labels: {
            boxWidth: 12, boxHeight: 12,
            borderRadius: 3, useBorderRadius: true,
            font: { size: 11, weight: '600' },
            color: '#64748b',
            padding: 16
          }
        },
        tooltip: {
          mode: 'index', intersect: false,
          backgroundColor: '#0f172a',
          titleColor: '#e2e8f0', bodyColor: '#fff',
          titleFont: { size: 12, weight: '700' },
          bodyFont: { size: 12 },
          footerFont: { size: 11, weight: '600' },
          footerColor: '#94a3b8',
          padding: 14, cornerRadius: 10,
          displayColors: true,
          boxPadding: 6,
          callbacks: {
            footer: (itens: { dataset: { label?: string }; parsed: { y: number } }[]) => {
              const total = itens.find((item) => item.dataset.label === 'Total de eventos')?.parsed.y ?? 0;
              const comLesao = itens.find((item) => item.dataset.label === 'Com lesão')?.parsed.y ?? 0;
              return `Sem lesão: ${calcularEventosSemLesao(total, comLesao)}`;
            }
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          border: { display: false },
          ticks: {
            color: '#829ab1',
            font: { size: 11, weight: '500' },
            maxTicksLimit: 10,
            maxRotation: 45,
            autoSkip: true,
            padding: 8
          }
        },
        y: {
          beginAtZero: true,
          grace: '10%',
          border: { display: false },
          grid: { color: 'rgba(203, 213, 225, 0.3)', drawTicks: false },
          ticks: {
            color: '#94a3b8',
            font: { size: 11, weight: '500' },
            precision: 0,
            maxTicksLimit: 6,
            padding: 12
          }
        }
      }
    };
  }

  protected barWidth(value: number): number {
    return Math.max(2, Math.min(100, value ?? 0));
  }

  /** Zero em todas as faixas é "nada em aberto", e merece a mensagem, não quatro linhas vazias. */
  protected totalEmAberto(data: DashboardEventosSstResponse): number {
    return data.backlog.reduce((soma, faixa) => soma + faixa.total, 0);
  }

  /** A pirâmide agrega por consequência; esta tabela preserva as naturezas originais. */
  protected composicaoPorTipo(data: DashboardEventosSstResponse): { tipo: TipoEventoSst; rotulo: string; total: number; percentual: number }[] {
    const total = data.resumo.total;
    return this.tipoOptions.map((tipo) => ({
      tipo: tipo.value,
      rotulo: tipo.label,
      total: data.resumo.porTipo[tipo.value] ?? 0,
      percentual: total > 0 ? ((data.resumo.porTipo[tipo.value] ?? 0) * 100) / total : 0
    }));
  }

  protected comparacao(anterior: number, variacao: number): string {
    if (anterior === 0) return 'Sem período anterior para comparar';
    const texto = variacao.toLocaleString('pt-BR', { maximumFractionDigits: 2, signDisplay: 'always' });
    return `${texto}% vs. período anterior`;
  }

  private toIso(date: Date, endOfDay: boolean): string {
    const value = new Date(date);
    value.setHours(endOfDay ? 23 : 0, endOfDay ? 59 : 0, endOfDay ? 59 : 0, endOfDay ? 999 : 0);
    return toSaoPauloIso(value, endOfDay);
  }
}
