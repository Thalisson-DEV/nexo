import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesChartComponent,
  CesChartPointSelection,
  CesDatepickerComponent,
  CesDrillStateService,
  CesFilterChip,
  CesFilterPanelComponent,
  CesMeterComponent,
  CesPageHeaderComponent,
  CesMultiSelectComponent,
  CesStatusComponent,
  NotificationService
} from '@ces/ui';

import { createUrlFilters, qp } from '../../../../core/navigation';
import { getErrorMessage } from '../../../../core/models/shared.models';
import {
  DashboardOperacionalFiltros,
  DashboardOperacionalResponse,
  EquipeOperacional
} from '../../../../core/models/dashboard.models';
import { DashboardService } from '../../../../core/services/dashboard.service';
import { DashboardMetricCardComponent } from '../../../../shared/components/dashboard/dashboard-ui.components';
import { DashboardAnaliseComponent } from '../../../../shared/components/dashboard/dashboard-analise.component';
import { AlvoAnalise, VistaAnalise } from '../../../../shared/components/dashboard/dashboard-analise.models';
import { NavegacaoAnaliseService } from '../../../../shared/components/dashboard/dashboard-analise.navegacao';
import { DashboardVisaoGerencialComponent } from '../../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import {
  CopilotoAreaComponent,
  CopilotoDashboardContext,
  CopilotoGatilhoComponent,
  CopilotoPainelService,
  CopilotoPerguntarComponent,
  filtroPainel,
  graficoPainel,
  metricaPainel,
  periodoPainel
} from '../../../copiloto/painel';
import {
  BarraDia,
  OrdemOperacional,
  SITUACAO_META,
  barrasProducao,
  dataLocal,
  diaMes,
  isoLocal,
  moeda,
  moedaCompacta,
  obrasEmDestaque,
  ordenarEquipesOperacional,
  percentual,
  serieAcumulada,
  serieMensal,
  seriePorEquipe,
  tomDaMeta,
  variacao
} from '../execucao-dashboards.utils';
import { RecorteDashboardService } from '../recorte-dashboard.service';
import { vistaOperacional } from '../analise/analise-operacional';
import { graficosDaAnalise } from '../analise/analise-copiloto';
import { visaoOperacional } from '../analise/visao-gerencial';
import { atualizarPeriodicamente } from '../../../../shared/components/dashboard/atualizacao-automatica';

type FiltroEquipes = 'todas' | 'abaixo' | 'sem-producao';
type PeriodoRapido = 'mes' | 'mes-anterior' | '7-dias';

/**
 * Dashboard operacional do processo de obras: quanto cada equipe executou nas obras no período (o mesmo
 * executado do boletim), como isso se compara à meta proporcional e se o ritmo atual fecha a meta.
 */
@Component({
  selector: 'app-dashboard-operacional',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesChartComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesMeterComponent,
    CesPageHeaderComponent,
    CesMultiSelectComponent,
    CesStatusComponent,
    DashboardMetricCardComponent,
    CopilotoAreaComponent,
    CopilotoGatilhoComponent,
    CopilotoPerguntarComponent,
    DashboardAnaliseComponent,
    DashboardVisaoGerencialComponent
  ],
  providers: [CesDrillStateService, NavegacaoAnaliseService],
  templateUrl: './dashboard-operacional.component.html',
  styleUrl: '../execucao-dashboards.css'
})
export class DashboardOperacionalComponent implements OnInit {
  private readonly service = inject(DashboardService);
  private readonly notificacoes = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly recorte = inject(RecorteDashboardService);
  protected readonly analise = inject(NavegacaoAnaliseService);
  private readonly copiloto = inject(CopilotoPainelService);
  protected readonly copilotoDisponivel = this.copiloto.disponivelParaUsuario();

  protected readonly situacaoMeta = SITUACAO_META;
  protected readonly moeda = moeda;
  protected readonly moedaCompacta = moedaCompacta;
  protected readonly percentual = percentual;
  protected readonly variacao = variacao;
  protected readonly diaMes = diaMes;
  protected readonly tomDaMeta = tomDaMeta;
  protected readonly obrasEmDestaque = obrasEmDestaque;
  protected readonly limiteObras = 10;
  /** A tabela de obras abre nas maiores; o restante entra sob demanda. */
  protected readonly todasObras = signal(false);
  protected readonly obrasVisiveis = computed(() => {
    const obras = this.data()?.obras ?? [];
    return this.todasObras() ? obras : obras.slice(0, this.limiteObras);
  });

  protected readonly loading = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly data = signal<DashboardOperacionalResponse | null>(null);

  // Filtros em edição (só valem depois de "Aplicar").
  protected readonly inicio = signal<Date | null>(null);
  protected readonly fim = signal<Date | null>(null);
  protected readonly idsBase = signal<string[]>([]);
  protected readonly idsProcesso = signal<string[]>([]);

  /** Recorte que produziu os dados em tela — é o que o Copiloto recebe. */
  private readonly aplicado = signal<DashboardOperacionalFiltros>({});

  private readonly atualizacao = atualizarPeriodicamente(() => this.buscar(this.aplicado(), true), {
    pode: () => !this.loading() && !!this.data()
  });

  protected readonly ordem = signal<OrdemOperacional>('meta');
  protected readonly filtroEquipes = signal<FiltroEquipes>('todas');
  protected readonly visaoSerie = signal<'dia' | 'mes'>('dia');

  protected readonly serieTemporal = computed(() => {
    const dias = this.data()?.producaoDiaria ?? [];
    return this.visaoSerie() === 'mes' ? serieMensal(dias) : serieAcumulada(dias);
  });
  protected readonly seriePorEquipe = computed(() => seriePorEquipe(this.data()?.equipes ?? []));
  /** Uma faixa por equipe: o gráfico cresce com o recorte em vez de espremer os rótulos. */
  protected readonly alturaGraficoEquipes = computed(() => Math.max(300, this.seriePorEquipe().rotulos.length * 34 + 70));

  protected readonly ordens: { valor: OrdemOperacional; rotulo: string }[] = [
    { valor: 'meta', rotulo: 'Atenção primeiro' },
    { valor: 'producao', rotulo: 'Maior produção' },
    { valor: 'equipe', rotulo: 'Equipe' }
  ];
  protected readonly periodosRapidos: { valor: PeriodoRapido; rotulo: string }[] = [
    { valor: 'mes', rotulo: 'Este mês' },
    { valor: 'mes-anterior', rotulo: 'Mês anterior' },
    { valor: '7-dias', rotulo: 'Últimos 7 dias' }
  ];

  private readonly filtrosUrl = createUrlFilters<{
    inicio: Date;
    fim: Date;
    idBaseOperacional: string[];
    idProcesso: string[];
  }>({
    inicio: qp.date('inicio'),
    fim: qp.date('fim'),
    idBaseOperacional: qp.list('base'),
    idProcesso: qp.list('processo')
  });

  protected readonly equipesVisiveis = computed(() => {
    const equipes = this.data()?.equipes ?? [];
    const filtradas = equipes.filter((e) => {
      switch (this.filtroEquipes()) {
        case 'abaixo':
          return e.situacao === 'ABAIXO_DO_RITMO';
        case 'sem-producao':
          return this.semExecucao(e);
        default:
          return true;
      }
    });
    return ordenarEquipesOperacional(filtradas, this.ordem());
  });

  protected readonly contagemFiltros = computed(() => {
    const equipes = this.data()?.equipes ?? [];
    return {
      todas: equipes.length,
      abaixo: equipes.filter((e) => e.situacao === 'ABAIXO_DO_RITMO').length,
      'sem-producao': equipes.filter((e) => this.semExecucao(e)).length
    } satisfies Record<FiltroEquipes, number>;
  });

  protected readonly barrasPorEquipe = computed(() => {
    const mapa = new Map<string, BarraDia[]>();
    for (const equipe of this.data()?.equipes ?? []) {
      mapa.set(equipe.idEquipe, barrasProducao(equipe.producaoDiaria));
    }
    return mapa;
  });

  protected readonly periodoAtivo = computed<PeriodoRapido | null>(() => {
    const { inicio, fim } = this.aplicado();
    if (!inicio || !fim) return null;
    for (const periodo of this.periodosRapidos) {
      const [de, ate] = intervaloRapido(periodo.valor);
      if (isoLocal(de) === inicio && isoLocal(ate) === fim) return periodo.valor;
    }
    return null;
  });

  /** Veredito do recorte e leituras acionáveis (cada uma abre a análise que a sustenta). */
  protected readonly visaoGerencial = computed(() => {
    const d = this.data();
    return d ? visaoOperacional(d) : null;
  });

  /** Análise detalhada aberta (`?drill=`), resolvida contra os dados em tela. */
  protected readonly vistaAnalise = computed<VistaAnalise | null>(() => {
    const alvo = this.analise.alvo();
    const d = this.data();
    if (!alvo || !d) return null;
    const f = this.aplicado();
    return vistaOperacional(alvo, {
      d,
      filtros: f,
      service: this.service,
      nomeBase: this.recorte.resumoDasBases(f.idBaseOperacional),
      nomeProcesso: this.recorte.resumoDosProcessos(f.idProcesso)
    });
  });

  /** Última análise levada ao Copiloto: seus gráficos entram no contexto da conversa. */
  private readonly analiseNoCopiloto = signal<VistaAnalise | null>(null);

  protected readonly contextoCopiloto = computed<CopilotoDashboardContext | null>(() => {
    const d = this.data();
    const f = this.aplicado();
    const equipes = ordenarEquipesOperacional(d?.equipes ?? [], 'producao');
    return {
      id: 'execucao.operacional',
      nome: 'Dashboard operacional',
      modulo: 'Obras',
      descricao:
        'Produção das equipes de obras no período (executado apontado nas obras), meta proporcional aos dias úteis, ritmo necessário para fechar a meta, projeção e produção por obra.',
      rota: '/execucao/dashboard-operacional',
      periodo: periodoPainel(f.inicio ? dataLocal(f.inicio) : null, f.fim ? dataLocal(f.fim) : null),
      filtros: [
        filtroPainel('baseOperacional', 'Base operacional', this.recorte.resumoDasBases(f.idBaseOperacional), f.idBaseOperacional?.join(',') ?? null),
        filtroPainel('processo', 'Processo', this.recorte.resumoDosProcessos(f.idProcesso), f.idProcesso?.join(',') ?? null)
      ],
      metricas: d
        ? [
            metricaPainel('producao', 'Produção no período', d.resumo.producao, 'moeda', {
              comparacao: { rotulo: 'período anterior', valorAnterior: d.resumo.producaoPeriodoAnterior, melhorQuando: 'maior' }
            }),
            metricaPainel('percentualMeta', 'Meta atingida (equipes com meta)', d.resumo.percentualMeta, 'percentual', {
              comparacao: { rotulo: 'meta', valorAnterior: 100, melhorQuando: 'maior' }
            }),
            metricaPainel('projecao', 'Projeção das equipes com meta', d.resumo.projecaoEquipesComMeta, 'moeda'),
            metricaPainel('obrasAtendidas', 'Obras atendidas', d.resumo.obrasAtendidas, 'inteiro'),
            metricaPainel('valorMedioObra', 'Produção média por obra', d.resumo.valorMedioObra, 'moeda'),
            metricaPainel('servicos', 'Serviços apontados', d.resumo.servicos, 'inteiro'),
            metricaPainel('equipesAbaixoDoRitmo', 'Equipes abaixo do ritmo', d.resumo.equipesAbaixoDoRitmo, 'inteiro', {
              descricao: `de ${d.resumo.equipesComMeta} equipes com meta`
            })
          ]
        : [],
      graficos: d
        ? [
            graficoPainel(
              'equipes',
              'tabela',
              'Produção por equipe',
              equipes.map((e) => e.codigo),
              [
                { nome: 'Produção (R$)', valores: equipes.map((e) => e.producao) },
                { nome: 'Obras', valores: equipes.map((e) => e.obrasAtendidas) },
                { nome: 'Serviços', valores: equipes.map((e) => e.servicos) },
                { nome: '% da meta', valores: equipes.map((e) => e.percentualMeta) }
              ],
              { metrica: 'produção', dimensao: 'equipe', totalCategorias: equipes.length * 3 }
            ),
            graficoPainel('producaoDiaria', 'linha', 'Produção diária', d.producaoDiaria.map((p) => diaMes(p.data)),
              d.producaoDiaria.map((p) => p.valor), { metrica: 'produção', dimensao: 'dia', formato: 'moeda' }),
            graficoPainel('producaoMeta', this.visaoSerie() === 'dia' ? 'linha' : 'barra', 'Produção × meta', this.serieTemporal().rotulos,
              this.serieTemporal().series.map((s) => ({ nome: s.name, valores: s.values })), { metrica: 'produção', dimensao: this.visaoSerie(), formato: 'moeda' }),
            graficoPainel('producaoMetaEquipe', 'barra', 'Produção × meta por equipe', this.seriePorEquipe().rotulos,
              this.seriePorEquipe().series.map((s) => ({ nome: s.name, valores: s.values })), { metrica: 'produção', dimensao: 'equipe', formato: 'moeda' }),
            graficoPainel('obras', 'tabela', 'Obras do período', d.obras.map((o) => o.numero), [
              { nome: 'Produção (R$)', valores: d.obras.map((o) => o.producao) },
              { nome: 'Serviços', valores: d.obras.map((o) => o.servicos) },
              { nome: 'Equipes', valores: d.obras.map((o) => o.equipes) }
            ], { metrica: 'produção', dimensao: 'obra' }),
            ...graficosDaAnalise(this.analiseNoCopiloto())
          ]
        : [],
      datasets: ['turnos', 'equipes'],
      sugestoes: [
        {
          rotulo: 'Equipes abaixo do ritmo',
          icone: 'pi pi-flag',
          requer: ['dados'],
          pergunta:
            'Quais equipes de obras estão abaixo do ritmo da meta neste recorte e o que explica a diferença (dias com turno, dias com execução, obras atendidas)?'
        },
        {
          rotulo: 'Obras que mais produzem',
          icone: 'pi pi-building',
          requer: ['dados'],
          pergunta: 'Quais obras concentraram a produção deste período e quais equipes executaram nelas?'
        }
      ]
    };
  });

  constructor() {
    // Link de análise que não existe mais no recorte (outra equipe, outro período): fecha o painel.
    effect(() => {
      if (this.analise.alvo() && this.data() && !this.loading() && !this.vistaAnalise()) this.analise.fechar();
    });
  }

  ngOnInit(): void {
    this.recorte.carregar(this.destroyRef);
    const lidos = this.filtrosUrl.read();
    const [inicioPadrao, fimPadrao] = intervaloRapido('mes');
    this.inicio.set(lidos.inicio ?? inicioPadrao);
    this.fim.set(lidos.fim ?? fimPadrao);
    this.idsBase.set(lidos.idBaseOperacional ?? []);
    this.idsProcesso.set(lidos.idProcesso ?? []);
    this.aplicar();
  }

  protected aplicar(): void {
    const filtros: DashboardOperacionalFiltros = {
      inicio: this.inicio() ? isoLocal(this.inicio()!) : undefined,
      fim: this.fim() ? isoLocal(this.fim()!) : undefined,
      idBaseOperacional: this.idsBase().length ? this.idsBase() : undefined,
      idProcesso: this.idsProcesso().length ? this.idsProcesso() : undefined
    };
    this.filtrosUrl.write({
      inicio: this.inicio() ?? undefined,
      fim: this.fim() ?? undefined,
      idBaseOperacional: filtros.idBaseOperacional,
      idProcesso: filtros.idProcesso
    });
    this.buscar(filtros);
  }

  /** Busca um recorte; a atualização automática repete o aplicado sem mostrar carregamento nem avisar erro. */
  private buscar(filtros: DashboardOperacionalFiltros, silencioso = false): void {
    if (!silencioso) {
      this.loading.set(true);
      this.erro.set(null);
    }
    this.service
      .gerarDashboardOperacional(filtros)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.data.set(res);
          this.aplicado.set({ ...filtros, inicio: res.inicio, fim: res.fim });
          this.loading.set(false);
        },
        error: (err) => {
          if (silencioso) return;
          const mensagem = getErrorMessage(err);
          this.erro.set(mensagem);
          this.loading.set(false);
          this.notificacoes.show({ variant: 'error', title: 'Não foi possível carregar o dashboard', message: mensagem });
        }
      });
  }

  /** Recorte aplicado como chips: o período sempre vale (não removível); cada base e processo sai pelo seu chip. */
  protected readonly chips = computed<CesFilterChip[]>(() => {
    const f = this.aplicado();
    const chips: CesFilterChip[] = [];
    if (f.inicio && f.fim) chips.push({ key: 'periodo', label: `Período: ${dataCurta(f.inicio)} a ${dataCurta(f.fim)}`, removable: false });
    for (const id of f.idBaseOperacional ?? []) chips.push({ key: `base:${id}`, label: `Base: ${this.recorte.nomeDaBase(id)}` });
    for (const id of f.idProcesso ?? []) chips.push({ key: `processo:${id}`, label: `Processo: ${this.recorte.nomeDoProcesso(id)}` });
    return chips;
  });

  protected removerFiltro(chave: string): void {
    const [dimensao, id] = chave.split(':');
    if (dimensao === 'base') this.idsBase.update((ids) => ids.filter((i) => i !== id));
    if (dimensao === 'processo') this.idsProcesso.update((ids) => ids.filter((i) => i !== id));
    this.aplicar();
  }

  protected limpar(): void {
    const [inicio, fim] = intervaloRapido('mes');
    this.inicio.set(inicio);
    this.fim.set(fim);
    this.idsBase.set([]);
    this.idsProcesso.set([]);
    this.aplicar();
  }

  protected usarPeriodo(periodo: PeriodoRapido): void {
    const [inicio, fim] = intervaloRapido(periodo);
    this.inicio.set(inicio);
    this.fim.set(fim);
    this.aplicar();
  }

  protected abrirBoletim(): void {
    void this.router.navigate(['/obras/boletim'], {
      queryParams: { base: this.aplicado().idBaseOperacional ?? null }
    });
  }

  /** Abre os turnos de obra da equipe abertos no período em tela. */
  protected linkTurnos(equipe: EquipeOperacional): Record<string, string> {
    const f = this.aplicado();
    return {
      equipe: equipe.idEquipe,
      ...(f.inicio || f.fim ? { abertura: `${f.inicio ?? ''}~${f.fim ?? ''}` } : {})
    };
  }

  protected semExecucao(equipe: EquipeOperacional): boolean {
    return equipe.producao === 0 && equipe.servicos === 0;
  }

  // ------------------------------------------------------------------ análise detalhada

  protected analisar(dimension: string, value: string): void {
    this.analise.abrir(dimension, value);
  }

  protected aprofundar(alvo: AlvoAnalise): void {
    this.analise.aprofundar(alvo, this.vistaAnalise()?.titulo ?? '');
  }

  /** Clique num ponto do gráfico de produção × meta: o dia (ou o mês) daquele ponto. */
  protected selecionarSerie(selecao: CesChartPointSelection): void {
    const dias = this.data()?.producaoDiaria ?? [];
    if (this.visaoSerie() === 'dia') {
      const dia = dias[selecao.index];
      if (dia) this.analisar('dia', dia.data);
      return;
    }
    const meses = [...new Set(dias.map((p) => p.data.slice(0, 7)))].sort();
    if (meses[selecao.index]) this.analisar('mes', meses[selecao.index]);
  }

  protected selecionarEquipe(selecao: CesChartPointSelection): void {
    const codigo = this.seriePorEquipe().rotulos[selecao.index];
    const equipe = this.data()?.equipes.find((e) => e.codigo === codigo);
    if (equipe) this.analisar('equipe', equipe.idEquipe);
  }

  protected perguntarVisao(pergunta: string): void {
    this.copiloto.perguntarSobre({ tipo: 'secao', id: 'visaoGerencial', rotulo: 'Visão gerencial' }, pergunta);
  }

  /** "Analisar com o Copiloto" de dentro da análise: fecha o painel e leva o recorte como contexto. */
  protected perguntarAnalise(vista: VistaAnalise): void {
    if (!vista.copiloto) return;
    this.analiseNoCopiloto.set(vista);
    this.analise.fechar();
    this.copiloto.perguntarSobre(vista.copiloto.foco, vista.copiloto.pergunta);
  }

  protected falta(meta: number, producao: number): number {
    return Math.max(0, meta - producao);
  }

  protected notaMeta(d: DashboardOperacionalResponse): string | undefined {
    const r = d.resumo;
    if (r.equipesComMeta === 0) return 'Nenhuma equipe do recorte tem meta cadastrada; o gráfico mostra só a produção.';
    if (r.equipesComMeta < r.equipesAtivas) {
      return `A meta soma as ${r.equipesComMeta} equipes com meta cadastrada; a produção inclui todas as ${r.equipesAtivas}.`;
    }
    return undefined;
  }

  protected tomMeta(percentualMeta: number | null): 'green' | 'amber' | 'slate' {
    if (percentualMeta === null) return 'slate';
    return percentualMeta >= 100 ? 'green' : 'amber';
  }

  protected detalheProducao(d: DashboardOperacionalResponse): string {
    if (d.resumo.variacaoPercentual === null) return 'Sem produção no período anterior para comparar';
    return `${variacao(d.resumo.variacaoPercentual)} vs. período anterior (${moedaCompacta(d.resumo.producaoPeriodoAnterior)})`;
  }

  protected detalheMeta(d: DashboardOperacionalResponse): string {
    const r = d.resumo;
    if (r.metaPeriodo === null) return 'Nenhuma equipe do recorte tem meta mensal cadastrada';
    const base = `${moedaCompacta(r.producaoEquipesComMeta)} de ${moedaCompacta(r.metaPeriodo)}`;
    return r.projecaoEquipesComMeta !== null && d.diasUteisEncerrados < d.diasUteisPeriodo
      ? `${base} · projeção ${moedaCompacta(r.projecaoEquipesComMeta)}`
      : base;
  }

}

/** Intervalos dos atalhos de período, em datas locais (até hoje no mês corrente). */
/** `yyyy-MM-dd` → `dd/MM/yyyy`. */
function dataCurta(iso: string): string {
  const [ano, mes, dia] = iso.substring(0, 10).split('-');
  return `${dia}/${mes}/${ano}`;
}

function intervaloRapido(periodo: PeriodoRapido): [Date, Date] {
  const hoje = new Date();
  hoje.setHours(12, 0, 0, 0);
  switch (periodo) {
    case 'mes-anterior': {
      const inicio = new Date(hoje.getFullYear(), hoje.getMonth() - 1, 1, 12);
      const fim = new Date(hoje.getFullYear(), hoje.getMonth(), 0, 12);
      return [inicio, fim];
    }
    case '7-dias': {
      const inicio = new Date(hoje);
      inicio.setDate(hoje.getDate() - 6);
      return [inicio, hoje];
    }
    default:
      return [new Date(hoje.getFullYear(), hoje.getMonth(), 1, 12), hoje];
  }
}
