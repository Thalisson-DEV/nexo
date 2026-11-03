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
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesStatusComponent,
  NotificationService
} from '@ces/ui';

import { createUrlFilters, qp } from '../../../../core/navigation';
import { getErrorMessage } from '../../../../core/models/shared.models';
import {
  DashboardTurnosFiltros,
  DashboardTurnosResponse,
  EquipeTurno
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
  AgrupamentoTurnos,
  MarcaLancamento,
  agruparEquipesTurno,
  SITUACAO_TURNO,
  dataLocal,
  duracao,
  horaCurta,
  isoLocal,
  janelaDoTurno,
  marcasDaLinhaDoTempo,
  moeda,
  moedaCompacta,
  nomeCurto,
  ordenarEquipesTurno,
  percentual,
  serieDoDiaPorEquipe,
  tiposEmDestaque,
  tomDaMeta
} from '../execucao-dashboards.utils';
import { RecorteDashboardService } from '../recorte-dashboard.service';
import { chaveTipo } from '../analise/analise-comum';
import { graficosDaAnalise } from '../analise/analise-copiloto';
import { visaoTurnos } from '../analise/visao-gerencial';
import { vistaTurnos } from '../analise/analise-turnos';
import { atualizarPeriodicamente } from '../../../../shared/components/dashboard/atualizacao-automatica';

type FiltroTurnos = 'todas' | 'em-turno' | 'sem-lancamento' | 'encerradas' | 'sem-turno';
type VisaoEquipes = 'cartoes' | 'miniaturas';

const AGRUPAMENTOS: readonly { valor: AgrupamentoTurnos; rotulo: string }[] = [
  { valor: 'nenhum', rotulo: 'Sem grupo' },
  { valor: 'base', rotulo: 'Base' },
  { valor: 'processo', rotulo: 'Processo' },
  { valor: 'meta', rotulo: 'Meta do dia' }
];

/** Agrupamento e visão dos cartões são preferência de quem vê, guardadas neste navegador. */
const PREFERENCIA_AGRUPAMENTO = 'dash-turnos:agrupar';
const PREFERENCIA_VISAO = 'dash-turnos:visao';

function lerPreferencia<T extends string>(chave: string, validos: readonly T[], padrao: T): T {
  try {
    const valor = localStorage.getItem(chave);
    return validos.includes(valor as T) ? (valor as T) : padrao;
  } catch {
    return padrao;
  }
}

function gravarPreferencia(chave: string, valor: string): void {
  try {
    localStorage.setItem(chave, valor);
  } catch {
    // Sem armazenamento (janela anônima, bloqueio): a escolha vale só nesta visita.
  }
}


interface LinhaDoTempo {
  marcas: MarcaLancamento[];
  inicio: string;
  fim: string;
  aberto: boolean;
}

/** Com a tela aberta no dia corrente, os números se renovam sozinhos neste intervalo. */
const ATUALIZACAO_AUTOMATICA_MS = 2 * 60_000;

/**
 * Dashboard de turnos: quem está em campo, com qual composição, quanto já lançou no
 * dia e quem está há muito tempo sem lançar uma nota.
 */
@Component({
  selector: 'app-dashboard-turnos',
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
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesStatusComponent,
    DashboardMetricCardComponent,
    CopilotoAreaComponent,
    CopilotoGatilhoComponent,
    CopilotoPerguntarComponent,
    DashboardAnaliseComponent,
    DashboardVisaoGerencialComponent
  ],
  providers: [CesDrillStateService, NavegacaoAnaliseService],
  templateUrl: './dashboard-turnos.component.html',
  styleUrl: '../execucao-dashboards.css'
})
export class DashboardTurnosComponent implements OnInit {
  private readonly service = inject(DashboardService);
  private readonly notificacoes = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly recorte = inject(RecorteDashboardService);
  protected readonly analise = inject(NavegacaoAnaliseService);
  private readonly copiloto = inject(CopilotoPainelService);
  protected readonly copilotoDisponivel = this.copiloto.disponivelParaUsuario();
  protected readonly chaveTipo = chaveTipo;

  protected readonly situacaoTurno = SITUACAO_TURNO;
  protected readonly moeda = moeda;
  protected readonly moedaCompacta = moedaCompacta;
  protected readonly percentual = percentual;
  protected readonly tiposEmDestaque = tiposEmDestaque;
  protected readonly duracao = duracao;
  protected readonly horaCurta = horaCurta;
  protected readonly nomeCurto = nomeCurto;
  protected readonly tomDaMeta = tomDaMeta;

  protected readonly loading = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly data = signal<DashboardTurnosResponse | null>(null);

  protected readonly hojeLocal = new Date();
  protected readonly dia = signal<Date | null>(new Date());
  protected readonly idsBase = signal<string[]>([]);
  protected readonly idsProcesso = signal<string[]>([]);
  private readonly aplicado = signal<DashboardTurnosFiltros>({});

  /** Só o dia corrente muda ao longo do tempo; por isso a atualização é mais frequente e só nele. */
  private readonly atualizacao = atualizarPeriodicamente(() => this.carregar(this.aplicado(), true), {
    intervaloMs: ATUALIZACAO_AUTOMATICA_MS,
    pode: () => !!this.data()?.hoje && !this.loading()
  });

  protected readonly filtro = signal<FiltroTurnos>('todas');
  protected readonly agrupamentos = AGRUPAMENTOS;
  protected readonly agrupamento = signal<AgrupamentoTurnos>(
    lerPreferencia(PREFERENCIA_AGRUPAMENTO, AGRUPAMENTOS.map((a) => a.valor), 'nenhum')
  );
  protected readonly visao = signal<VisaoEquipes>(lerPreferencia(PREFERENCIA_VISAO, ['cartoes', 'miniaturas'], 'cartoes'));
  protected readonly serieDoDia = computed(() => serieDoDiaPorEquipe(this.data()?.equipes ?? []));

  private readonly filtrosUrl = createUrlFilters<{ data: Date; idBaseOperacional: string[]; idProcesso: string[] }>({
    data: qp.date('dia'),
    idBaseOperacional: qp.list('base'),
    idProcesso: qp.list('processo')
  });

  protected readonly filtros = computed(() => {
    const d = this.data();
    const r = d?.resumo;
    const lista: { valor: FiltroTurnos; rotulo: string; total: number }[] = [
      { valor: 'todas', rotulo: 'Todas', total: r?.equipesAtivas ?? 0 },
      { valor: 'em-turno', rotulo: 'Em turno', total: r?.emTurno ?? 0 },
      { valor: 'encerradas', rotulo: 'Encerradas', total: r?.encerrados ?? 0 },
      { valor: 'sem-turno', rotulo: 'Sem turno', total: r?.semTurno ?? 0 }
    ];
    if (d?.hoje) {
      lista.splice(2, 0, { valor: 'sem-lancamento', rotulo: 'Sem lançar há 2h+', total: r?.semLancamentoRecente ?? 0 });
    }
    return lista;
  });

  protected readonly equipesVisiveis = computed(() => {
    const equipes = (this.data()?.equipes ?? []).filter((e) => {
      switch (this.filtro()) {
        case 'em-turno':
          return e.situacao === 'EM_TURNO';
        case 'sem-lancamento':
          return e.semLancamentoRecente;
        case 'encerradas':
          return e.situacao === 'ENCERRADO';
        case 'sem-turno':
          return e.situacao === 'SEM_TURNO';
        default:
          return true;
      }
    });
    return ordenarEquipesTurno(equipes);
  });

  protected readonly grupos = computed(() => agruparEquipesTurno(this.equipesVisiveis(), this.agrupamento()));

  private readonly nomeBases = computed(() => this.recorte.resumoDasBases(this.aplicado().idBaseOperacional));
  private readonly nomeProcessos = computed(() => this.recorte.resumoDosProcessos(this.aplicado().idProcesso));

  /** Linha do tempo de cada equipe: janela do turno no dia e a posição de cada lançamento. */
  protected readonly linhasDoTempo = computed(() => {
    const d = this.data();
    const mapa = new Map<string, LinhaDoTempo>();
    if (!d) return mapa;
    // O dia de operação é o de São Paulo (UTC−3, sem horário de verão desde 2019).
    const inicioDia = new Date(`${d.data}T00:00:00-03:00`).getTime();
    const janelaDia = { inicio: inicioDia, fim: inicioDia + 24 * 60 * 60_000 };
    const agora = Date.parse(d.geradoEm);
    for (const equipe of d.equipes) {
      const janela = janelaDoTurno(equipe, janelaDia, agora);
      if (!janela) continue;
      mapa.set(equipe.idEquipe, {
        marcas: marcasDaLinhaDoTempo(equipe.lancamentos, janela),
        inicio: horaCurta(new Date(janela.inicio).toISOString()),
        fim: equipe.situacao === 'EM_TURNO' && d.hoje ? 'agora' : horaCurta(new Date(janela.fim).toISOString()),
        aberto: equipe.situacao === 'EM_TURNO'
      });
    }
    return mapa;
  });

  /** Veredito do recorte e leituras acionáveis (cada uma abre a análise que a sustenta). */
  protected readonly visaoGerencial = computed(() => {
    const d = this.data();
    return d ? visaoTurnos(d) : null;
  });

  /** Análise detalhada aberta (`?drill=`), resolvida contra os dados em tela. */
  protected readonly vistaAnalise = computed<VistaAnalise | null>(() => {
    const alvo = this.analise.alvo();
    const d = this.data();
    if (!alvo || !d) return null;
    const f = this.aplicado();
    return vistaTurnos(alvo, {
      d,
      filtros: f,
      service: this.service,
      nomeBase: this.nomeBases(),
      nomeProcesso: this.nomeProcessos()
    });
  });

  /** Última análise levada ao Copiloto: seus gráficos entram no contexto da conversa. */
  private readonly analiseNoCopiloto = signal<VistaAnalise | null>(null);

  protected readonly contextoCopiloto = computed<CopilotoDashboardContext | null>(() => {
    const d = this.data();
    const f = this.aplicado();
    const equipes = d?.equipes ?? [];
    return {
      id: 'execucao.turnos',
      nome: 'Dashboard de turnos',
      modulo: 'Execução',
      descricao:
        'Situação dos turnos das equipes num dia: em turno, encerrado ou sem turno; composição na abertura; notas e produção do dia contra a meta diária; tempo sem lançamento.',
      rota: '/execucao/dashboard-turnos',
      periodo: periodoPainel(d ? dataLocal(d.data) : null, d ? dataLocal(d.data) : null),
      filtros: [
        filtroPainel('baseOperacional', 'Base operacional', this.nomeBases(), f.idBaseOperacional?.join(',') ?? null),
        filtroPainel('processo', 'Processo', this.nomeProcessos(), f.idProcesso?.join(',') ?? null)
      ],
      metricas: d
        ? [
            metricaPainel('emTurno', 'Equipes em turno', d.resumo.emTurno, 'inteiro', { descricao: `de ${d.resumo.equipesAtivas} equipes ativas` }),
            metricaPainel('semTurno', 'Equipes sem turno', d.resumo.semTurno, 'inteiro'),
            metricaPainel('semLancamentoRecente', 'Em turno sem lançar há 2h ou mais', d.resumo.semLancamentoRecente, 'inteiro'),
            metricaPainel('producao', 'Produção do dia', d.resumo.producao, 'moeda'),
            metricaPainel('notas', 'Notas lançadas no dia', d.resumo.notas, 'inteiro'),
            metricaPainel('percentualMetaDiaria', 'Meta diária atingida (equipes com meta)', d.resumo.percentualMetaDiaria, 'percentual', {
              comparacao: { rotulo: 'meta', valorAnterior: 100, melhorQuando: 'maior' }
            })
          ]
        : [],
      graficos: d
        ? [
            graficoPainel('equipes', 'tabela', 'Turnos por equipe', equipes.map((e) => e.codigo), [
              { nome: 'Produção (R$)', valores: equipes.map((e) => e.producao) },
              { nome: 'Notas', valores: equipes.map((e) => e.notas) },
              { nome: 'Minutos em turno', valores: equipes.map((e) => e.minutosEmTurno) },
              { nome: 'Minutos sem lançar', valores: equipes.map((e) => e.minutosSemLancamento) }
            ], { metrica: 'turno e produção', dimensao: 'equipe', totalCategorias: equipes.length * 4 }),
            graficoPainel('producaoDoDia', 'barra', 'Produção do dia × meta diária por equipe', this.serieDoDia().rotulos,
              this.serieDoDia().series.map((s) => ({ nome: s.name, valores: s.values })), { metrica: 'produção', dimensao: 'equipe', formato: 'moeda' }),
            graficoPainel('tiposServico', 'tabela', 'Tipos de serviço', d.tiposServico.map((t) => t.descricao), [
              { nome: 'Notas', valores: d.tiposServico.map((t) => t.notas) },
              { nome: 'Produção (R$)', valores: d.tiposServico.map((t) => t.producao) }
            ], { metrica: 'notas e produção', dimensao: 'tipo de serviço' }),
            ...graficosDaAnalise(this.analiseNoCopiloto())
          ]
        : [],
      datasets: ['turnos', 'execucoes_comerciais', 'equipes'],
      sugestoes: [
        {
          rotulo: 'Equipes paradas',
          icone: 'pi pi-clock',
          requer: ['dados'],
          pergunta: 'Quais equipes estão em turno sem lançar nota há mais tempo e como foi o ritmo delas nos últimos dias?'
        },
        {
          rotulo: 'Quem não abriu turno',
          icone: 'pi pi-user-minus',
          requer: ['dados'],
          pergunta: 'Quais equipes não abriram turno hoje e com que frequência isso aconteceu neste mês?'
        }
      ]
    };
  });

  constructor() {
    // Link de análise que não existe mais no recorte (outro dia, outra base): fecha o painel.
    effect(() => {
      if (this.analise.alvo() && this.data() && !this.loading() && !this.vistaAnalise()) this.analise.fechar();
    });
  }

  ngOnInit(): void {
    this.recorte.carregar(this.destroyRef);
    const lidos = this.filtrosUrl.read();
    if (lidos.data) this.dia.set(lidos.data);
    this.idsBase.set(lidos.idBaseOperacional ?? []);
    this.idsProcesso.set(lidos.idProcesso ?? []);
    this.aplicar();
  }

  protected aplicar(): void {
    const filtros: DashboardTurnosFiltros = {
      data: this.dia() ? isoLocal(this.dia()!) : undefined,
      idBaseOperacional: this.idsBase().length ? this.idsBase() : undefined,
      idProcesso: this.idsProcesso().length ? this.idsProcesso() : undefined
    };
    this.filtrosUrl.write({
      data: this.dia() ?? undefined,
      idBaseOperacional: filtros.idBaseOperacional,
      idProcesso: filtros.idProcesso
    });
    this.carregar(filtros, false);
  }

  private carregar(filtros: DashboardTurnosFiltros, silencioso: boolean): void {
    this.loading.set(true);
    if (!silencioso) this.erro.set(null);
    this.service
      .gerarDashboardTurnos(filtros)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.data.set(res);
          this.aplicado.set({ ...filtros, data: res.data });
          this.erro.set(null);
          this.loading.set(false);
          if (!res.hoje && this.filtro() === 'sem-lancamento') this.filtro.set('todas');
        },
        error: (err) => {
          const mensagem = getErrorMessage(err);
          this.loading.set(false);
          // Falha numa atualização automática não apaga o que já está em tela.
          if (silencioso) return;
          this.erro.set(mensagem);
          this.notificacoes.show({ variant: 'error', title: 'Não foi possível carregar os turnos', message: mensagem });
        }
      });
  }

  protected hoje(): void {
    this.dia.set(new Date());
    this.aplicar();
  }

  /** Recorte aplicado como chips: o dia sempre vale (não removível); cada base e processo sai pelo seu chip. */
  protected readonly chips = computed<CesFilterChip[]>(() => {
    const f = this.aplicado();
    const chips: CesFilterChip[] = [];
    if (f.data) chips.push({ key: 'dia', label: `Dia: ${f.data.substring(8, 10)}/${f.data.substring(5, 7)}/${f.data.substring(0, 4)}`, removable: false });
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
    this.dia.set(new Date());
    this.idsBase.set([]);
    this.idsProcesso.set([]);
    this.aplicar();
  }

  // ------------------------------------------------------------------ análise detalhada

  protected analisar(dimension: string, value: string): void {
    this.analise.abrir(dimension, value);
  }

  protected aprofundar(alvo: AlvoAnalise): void {
    this.analise.aprofundar(alvo, this.vistaAnalise()?.titulo ?? '');
  }

  protected selecionarEquipe(selecao: CesChartPointSelection): void {
    const codigo = this.serieDoDia().rotulos[selecao.index];
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

  protected abrirOperacional(): void {
    const f = this.aplicado();
    void this.router.navigate(['/execucao/dashboard-operacional'], {
      queryParams: { base: f.idBaseOperacional ?? null, processo: f.idProcesso ?? null }
    });
  }

  protected agrupar(criterio: AgrupamentoTurnos): void {
    this.agrupamento.set(criterio);
    gravarPreferencia(PREFERENCIA_AGRUPAMENTO, criterio);
  }

  protected mudarVisao(visao: VisaoEquipes): void {
    this.visao.set(visao);
    gravarPreferencia(PREFERENCIA_VISAO, visao);
  }

  /** Situação em poucas letras para a miniatura (o rótulo completo vai no leitor de tela). */
  protected situacaoCurta(equipe: EquipeTurno): string {
    switch (equipe.situacao) {
      case 'EM_TURNO':
        return 'Em turno';
      case 'ENCERRADO':
        return `Encerrou ${horaCurta(equipe.fechamento)}`;
      default:
        return 'Sem turno';
    }
  }

  protected resumoMiniatura(equipe: EquipeTurno, d: DashboardTurnosResponse): string {
    const partes = [
      `Equipe ${equipe.codigo}`,
      this.rotuloSituacao(equipe, d),
      `produção ${moedaCompacta(equipe.producao)}`,
      `${equipe.notas} ${equipe.notas === 1 ? 'nota' : 'notas'}`,
      this.composicao(equipe)
    ];
    if (equipe.percentualMetaDiaria != null) partes.push(`${percentual(equipe.percentualMetaDiaria)} da meta do dia`);
    if (equipe.semLancamentoRecente) partes.push(`sem lançar há ${duracao(equipe.minutosSemLancamento)}`);
    if (equipe.abertoDesdeOutroDia) partes.push('turno aberto desde outro dia');
    return `${partes.join(', ')}. Analisar turno.`;
  }

  protected rotuloSituacao(equipe: EquipeTurno, d: DashboardTurnosResponse): string {
    switch (equipe.situacao) {
      case 'EM_TURNO':
        return equipe.abertoDesdeOutroDia ? 'Em turno' : `Em turno desde ${horaCurta(equipe.abertura)}`;
      case 'ENCERRADO':
        return `Encerrado às ${horaCurta(equipe.fechamento)}`;
      default:
        return d.hoje ? 'Não abriu turno' : 'Sem turno';
    }
  }

  protected detalheTurnos(d: DashboardTurnosResponse): string {
    const r = d.resumo;
    const partes = [`${r.encerrados} ${r.encerrados === 1 ? 'encerrado' : 'encerrados'}`, `${r.semTurno} sem turno`];
    return partes.join(' · ');
  }

  protected detalheProducao(d: DashboardTurnosResponse): string {
    const r = d.resumo;
    const notas = `${r.notas} ${r.notas === 1 ? 'nota' : 'notas'}`;
    if (r.metaDiaria === null) {
      return d.diaUtil ? `${notas} · nenhuma equipe com meta cadastrada` : `${notas} · dia não útil, sem meta diária`;
    }
    return `${notas} · meta diária ${moedaCompacta(r.metaDiaria)}`;
  }

  protected tomProducao(d: DashboardTurnosResponse): 'green' | 'amber' | 'primary' {
    const p = d.resumo.percentualMetaDiaria;
    if (p === null) return 'primary';
    return p >= 100 ? 'green' : 'amber';
  }

  protected diaPorExtenso(iso: string): string {
    return dataLocal(iso).toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: '2-digit' });
  }

  protected composicao(equipe: EquipeTurno): string {
    const pessoas = equipe.membros.length;
    return pessoas ? `${pessoas} ${pessoas === 1 ? 'pessoa' : 'pessoas'}` : 'Composição não registrada';
  }

  protected membrosResumo(equipe: EquipeTurno): string {
    return equipe.membros.map((m) => nomeCurto(m)).join(', ');
  }
}
