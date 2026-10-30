import { rotuloNota } from '../../../core/campo/campo-regras';
import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { DbService } from '../../../core/db/db.service';
import { SyncService } from '../../../core/sync/sync.service';
import { ConnectivityService } from '../../../core/sync/connectivity.service';
import { TurnoService } from '../../../core/turno/turno.service';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { ExecucaoCampoLocal, OutboxItem } from '../../../core/db/models';
import { estadoEnvioExecucao } from '../../../core/campo/estado-envio';
import { EXECUCAO_ALTERAR_ENDPOINT, EXECUCAO_LANCAR_ENDPOINT } from '../../../core/campo/execucoes.service';
import { PeriodFilter, DEFAULT_PERIOD_FILTER, isWithinPeriod, periodStartTime } from '../../../shared/components/filter-sheet/period-filter.util';
import {
  EstadoEnvio, FolhaComponent, IconeComponent, StatusEnvioComponent, TituloGrandeComponent, VazioComponent,
} from '../../../ui';

type FiltroStatus = 'todas' | 'fila' | 'enviadas' | 'problema';

interface LinhaExecucao {
  id: string;
  nota: string;
  apoio: string;
  servicos: string;
  valor?: number;
  estado: EstadoEnvio;
  legadoIniciada: boolean;
  /** Nota sincronizada que foi alterada (no servidor ou com a alteração na fila). */
  alterada: boolean;
}

interface GrupoDia {
  titulo: string;
  linhas: LinhaExecucao[];
}

const FILTROS: { valor: FiltroStatus; rotulo: string }[] = [
  { valor: 'todas', rotulo: 'Todas' },
  { valor: 'fila', rotulo: 'Na fila' },
  { valor: 'enviadas', rotulo: 'Enviadas' },
  { valor: 'problema', rotulo: 'Com problema' },
];

const PERIODOS: { valor: PeriodFilter; rotulo: string }[] = [
  { valor: 'hoje', rotulo: 'Hoje' },
  { valor: '7dias', rotulo: 'Últimos 7 dias' },
  { valor: '30dias', rotulo: 'Últimos 30 dias' },
  { valor: 'todos', rotulo: 'Todo o período' },
];

const DIAS = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

interface EstadoTelaServicos { filtro?: FiltroStatus; periodo?: PeriodFilter; folhaPeriodo?: boolean }

/**
 * Último resultado lido, em memória: voltar do detalhe mostra a lista na hora
 * (como um app nativo) enquanto a leitura nova acontece por trás.
 */
let ultimaLeitura: { execucoes: ExecucaoCampoLocal[]; fila: Map<string, OutboxItem>; nomes: Map<string, string> } | null = null;

/** Aba Serviços: execuções da equipe, agrupadas por dia, com o estado de envio de cada uma. */
@Component({
  selector: 'app-execucoes-list',
  standalone: true,
  imports: [CurrencyPipe, RouterLink, TituloGrandeComponent, IconeComponent, StatusEnvioComponent, VazioComponent, FolhaComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-titulo-grande titulo="Serviços" [subtitulo]="subtitulo()">
      <button type="button" class="ces-btn ces-btn--neutro ces-btn--compacto periodo" (click)="abrirPeriodo()"
        [attr.aria-label]="'Período: ' + rotuloPeriodo()">
        <ces-icone nome="filtro" [tamanho]="18" />{{ rotuloPeriodoCurto() }}
      </button>
    </ces-titulo-grande>

    <div class="chips" role="radiogroup" aria-label="Filtrar por envio">
      @for (f of filtros; track f.valor) {
        <button type="button" role="radio" class="chip" [attr.aria-checked]="filtro() === f.valor" (click)="definirFiltro(f.valor)">
          {{ f.rotulo }}
          @if (contagem()[f.valor]; as n) { <span class="ces-num conta">{{ n }}</span> }
        </button>
      }
    </div>

    @if (carregando()) {
      <div class="esqueletos" aria-busy="true" aria-label="Carregando">
        @for (i of [1, 2, 3, 4]; track i) { <div class="ces-esqueleto" style="height: 60px"></div> }
      </div>
    } @else if (grupos().length) {
      @for (g of grupos(); track g.titulo) {
        <h2 class="ces-secao">{{ g.titulo }}</h2>
        <ul class="ces-grupo">
          @for (l of g.linhas; track l.id) {
            <li class="linha-lista" [attr.data-ancora]="l.id">
              <button type="button" class="ces-linha ces-linha--acao" (click)="abrir(l)">
                <span class="ces-linha__texto">
                  <span class="ces-linha__titulo ces-num">{{ l.nota }}</span>
                  @if (l.servicos) { <span class="ces-linha__resumo">{{ l.servicos }}</span> }
                  <span class="ces-linha__apoio ces-num">@if (l.alterada) {<span class="ces-cond ces-cond--sucata">Alterada</span> · }{{ l.apoio }}@if (l.valor) { · {{ l.valor | currency:'BRL':'symbol':'1.2-2':'pt-BR' }} }</span>
                </span>
                @if (l.legadoIniciada) {
                  <span class="ces-btn ces-btn--tonal ces-btn--compacto" aria-hidden="true">Terminar</span>
                } @else {
                  <ces-status-envio [estado]="l.estado" />
                }
              </button>
            </li>
          }
        </ul>
      }
      <div class="respiro"></div>
    } @else {
      <ces-vazio icone="servicos" [titulo]="filtro() === 'todas' ? 'Nenhuma execução no período' : 'Nada com esse filtro'"
        [texto]="filtro() === 'todas' ? 'Mude o período ou lance uma execução.' : 'Volte para Todas para ver o resto.'">
        @if (filtro() !== 'todas' || periodo() !== 'todos') {
          <button type="button" class="ces-btn ces-btn--tonal" (click)="limpar()">Mostrar todas</button>
        }
      </ces-vazio>
    }

    @if (turno.turnoAtivo()) {
      <a class="ces-fab" routerLink="/campo/nova"><ces-icone nome="mais" />Nova</a>
    }

    <ces-folha [aberta]="folhaPeriodo()" (fechar)="fecharPeriodo()" titulo="Período">
      <ul class="ces-grupo opcoes" role="radiogroup" aria-label="Período">
        @for (p of periodos; track p.valor) {
          <li>
            <button type="button" role="radio" class="ces-linha ces-linha--acao" [attr.aria-checked]="periodo() === p.valor" (click)="definirPeriodo(p.valor)">
              <span class="ces-linha__texto"><span class="ces-linha__titulo">{{ p.rotulo }}</span></span>
              @if (periodo() === p.valor) { <ces-icone nome="check" /> }
            </button>
          </li>
        }
      </ul>
    </ces-folha>
  `,
  styles: [`
    :host { display: block; position: relative; min-height: 100%; }
    .periodo { color: var(--ces-texto); }
    .chips { display: flex; gap: var(--ces-e2); overflow-x: auto; padding: var(--ces-e3) var(--ces-e4) var(--ces-e1); scrollbar-width: none;
      overscroll-behavior-x: contain; }
    .chips::-webkit-scrollbar { display: none; }
    .chip { flex: none; display: inline-flex; align-items: center; gap: 6px; min-height: 40px; padding: 0 14px; border-radius: var(--ces-r-pilula);
      border: 1px solid var(--ces-linha-forte); background: var(--ces-superficie); color: var(--ces-texto-2); font: 500 .875rem/1 var(--ces-fonte); cursor: pointer; }
    .chip[aria-checked='true'] { background: var(--ces-destaque-suave); border-color: transparent; color: var(--ces-destaque); font-weight: 600; }
    .conta { font-weight: 700; }
    .esqueletos { display: grid; gap: var(--ces-e2); padding: var(--ces-e4); }
    /* Listas com milhares de linhas: o navegador pula o trabalho das que estão fora da tela. */
    .linha-lista { content-visibility: auto; contain-intrinsic-size: auto 64px; }
    .respiro { height: 96px; }
    .opcoes { background: var(--ces-fundo); }
  `],
})
export class ExecucoesListComponent implements OnInit {
  private readonly dbService = inject(DbService);
  private readonly sync = inject(SyncService);
  private readonly rede = inject(ConnectivityService);
  private readonly sessao = inject(SessaoService);
  private readonly router = inject(Router);
  readonly turno = inject(TurnoService);

  readonly filtros = FILTROS;
  readonly periodos = PERIODOS;
  private readonly estado = this.sessao.estadoTela<EstadoTelaServicos>();
  readonly filtro = signal<FiltroStatus>(this.estado.filtro ?? 'todas');
  readonly periodo = signal<PeriodFilter>(this.estado.periodo ?? DEFAULT_PERIOD_FILTER);
  readonly folhaPeriodo = signal(!!this.estado.folhaPeriodo);
  readonly carregando = signal(!ultimaLeitura);
  private readonly execucoes = signal<ExecucaoCampoLocal[]>(ultimaLeitura?.execucoes ?? []);
  private readonly fila = signal<Map<string, OutboxItem>>(ultimaLeitura?.fila ?? new Map());
  private readonly nomesAcoes = signal<Map<string, string>>(ultimaLeitura?.nomes ?? new Map());

  constructor() {
    // Recarrega quando uma rodada de envio termina (estado de cada linha muda).
    effect(() => {
      this.sync.outboxSyncVersion();
      untracked(() => void this.carregar());
    });
  }

  async ngOnInit(): Promise<void> {
    await this.turno.loadTurnoAtivo();
    // Só esvazia a fila; o download completo fica com os gatilhos de rede e o sincronizar
    // manual — refazê-lo a cada visita travava a leitura da lista em aparelho lento.
    if (this.rede.isOnline()) void this.sync.syncOutbox({ reconciliar: false }).catch(() => {});
  }

  private readonly noPeriodo = computed(() => {
    const p = this.periodo();
    const inicio = periodStartTime(p);
    return inicio === null ? this.execucoes() : this.execucoes().filter(e => isWithinPeriod(e.dataLancamento, p, inicio));
  });

  private readonly linhas = computed<LinhaExecucao[]>(() => {
    const fila = this.fila();
    const nomes = this.nomesAcoes();
    return this.noPeriodo().map(e => ({
      id: e.id,
      nota: rotuloNota(e),
      apoio: `${e.tipoServico?.descricao ?? 'Tipo não informado'} · ${e.acoes.length ? e.acoes.length + (e.acoes.length === 1 ? ' serviço' : ' serviços') : 'Sem serviço'} · ${hora(e.dataLancamento)}`,
      servicos: e.acoes.map(id => nomes.get(id) ?? 'Serviço').join(' · '),
      valor: e.valorTotalFaturado,
      // Com alteração na fila, o estado mostrado é o do envio da alteração.
      estado: e.alteracaoPendente
        ? estadoEnvioExecucao({ statusSync: 'LOCAL' }, fila.get(e.id), false)
        : estadoEnvioExecucao(e, fila.get(e.id), false),
      legadoIniciada: e.statusExecucao === 'INICIADO',
      alterada: !!e.alteracaoPendente || (e.totalAlteracoes ?? 0) > 0,
    }));
  });

  readonly contagem = computed(() => {
    const c: Partial<Record<FiltroStatus, number>> = {};
    for (const l of this.linhas()) {
      const f = filtroDe(l.estado);
      c[f] = (c[f] ?? 0) + 1;
    }
    return { ...c, todas: undefined } as Partial<Record<FiltroStatus, number>>;
  });

  readonly grupos = computed<GrupoDia[]>(() => {
    const f = this.filtro();
    const grupos = new Map<string, GrupoDia>();
    const execPorId = new Map(this.noPeriodo().map(e => [e.id, e]));
    for (const l of this.linhas()) {
      if (f !== 'todas' && filtroDe(l.estado) !== f) continue;
      const titulo = tituloDia(execPorId.get(l.id)!.dataLancamento);
      if (!grupos.has(titulo)) grupos.set(titulo, { titulo, linhas: [] });
      grupos.get(titulo)!.linhas.push(l);
    }
    return [...grupos.values()];
  });

  readonly subtitulo = computed(() => {
    const n = this.noPeriodo().length;
    return `${n} ${n === 1 ? 'execução' : 'execuções'} · ${this.rotuloPeriodo().toLowerCase()}`;
  });
  readonly rotuloPeriodo = computed(() => PERIODOS.find(p => p.valor === this.periodo())?.rotulo ?? '');
  readonly rotuloPeriodoCurto = computed(() => ({ hoje: 'Hoje', '7dias': '7 dias', '30dias': '30 dias', todos: 'Tudo' })[this.periodo()]);

  definirFiltro(f: FiltroStatus): void {
    this.filtro.set(f);
    this.sessao.definirEstadoTela({ filtro: f });
  }

  definirPeriodo(p: PeriodFilter): void {
    this.periodo.set(p);
    this.sessao.definirEstadoTela({ periodo: p });
    this.fecharPeriodo();
  }

  abrirPeriodo(): void {
    this.folhaPeriodo.set(true);
    this.sessao.definirEstadoTela({ folhaPeriodo: true });
  }

  fecharPeriodo(): void {
    this.folhaPeriodo.set(false);
    this.sessao.definirEstadoTela({ folhaPeriodo: false });
  }

  limpar(): void {
    this.definirFiltro('todas');
    this.periodo.set('todos');
    this.sessao.definirEstadoTela({ periodo: 'todos' });
  }

  abrir(l: LinhaExecucao): void {
    // Rascunho de versões antigas do app (antes do lançamento único) abre direto em edição.
    if (l.legadoIniciada) void this.router.navigate(['/campo/nova'], { queryParams: { editId: l.id } });
    else void this.router.navigate(['/campo', l.id]);
  }

  private async carregar(): Promise<void> {
    try {
      const [execs, fila, acoes] = await Promise.all([
        this.dbService.getExecucoes(),
        this.dbService.getPendingOutbox(),
        this.dbService.getAcoes(),
      ]);
      const mapaFila = new Map(fila
        .filter(i => (i.endpoint === EXECUCAO_LANCAR_ENDPOINT || i.endpoint === EXECUCAO_ALTERAR_ENDPOINT) && i.payload?.tempId)
        .map(i => [i.payload.tempId as string, i] as const));
      const nomes = new Map(acoes.map(a => [a.id, a.nome] as const));
      this.execucoes.set(execs);
      this.fila.set(mapaFila);
      this.nomesAcoes.set(nomes);
      ultimaLeitura = { execucoes: execs, fila: mapaFila, nomes };
    } finally {
      this.carregando.set(false);
    }
  }
}

function filtroDe(estado: EstadoEnvio): FiltroStatus {
  if (estado === 'synced') return 'enviadas';
  if (estado === 'error' || estado === 'conflict') return 'problema';
  return 'fila';
}

function hora(iso: string): string {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

function tituloDia(iso: string): string {
  const d = new Date(iso);
  const hoje = new Date();
  const ontem = new Date();
  ontem.setDate(hoje.getDate() - 1);
  if (d.toDateString() === hoje.toDateString()) return 'Hoje';
  if (d.toDateString() === ontem.toDateString()) return 'Ontem';
  return `${DIAS[d.getDay()]}, ${d.getDate()} de ${MESES[d.getMonth()]}`;
}
