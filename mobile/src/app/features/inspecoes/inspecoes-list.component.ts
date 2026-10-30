import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DbService } from '../../core/db/db.service';
import { OutboxService } from '../../core/sync/outbox.service';
import { SyncService } from '../../core/sync/sync.service';
import { TurnoService } from '../../core/turno/turno.service';
import { AuthService } from '../../core/auth/auth.service';
import { SessaoService } from '../../core/sessao/sessao.service';
import { RascunhoStore, ResumoRascunho } from '../../core/sessao/rascunho.store';
import { TokenService } from '../../core/auth/token.service';
import { BaseOperacionalLocal, InspecaoLocal, OutboxItem, TipoInspecaoLocal } from '../../core/db/models';
import { DEFAULT_PERIOD_FILTER, PeriodFilter, isWithinPeriod, periodStartTime } from '../../shared/components/filter-sheet/period-filter.util';
import {
  ChipsComponent, EstadoEnvio, FolhaComponent, IconeComponent, IndicadorRedeComponent, OpcaoChip, OpcaoSeletor, PeriodoComponent,
  SeletorComponent, StatusEnvioComponent, TituloGrandeComponent, VazioComponent, agruparPorDia, hora, rotuloPeriodo, tituloDia,
} from '../../ui';
import { AbasSecaoComponent } from '../../ui/abas-secao.component';
import { abasSeguranca } from '../shell/abas-seguranca';

type Filtro = 'todas' | 'andamento' | 'fila' | 'enviadas' | 'problema' | 'canceladas';

const FILTROS: OpcaoChip<Filtro>[] = [
  { valor: 'todas', rotulo: 'Todas' },
  { valor: 'andamento', rotulo: 'Não terminadas' },
  { valor: 'fila', rotulo: 'Na fila' },
  { valor: 'enviadas', rotulo: 'Enviadas' },
  { valor: 'problema', rotulo: 'Com problema' },
  { valor: 'canceladas', rotulo: 'Canceladas' },
];

interface Linha {
  id: string;
  tipo: string;
  apoio: string;
  desvios: number;
  data: string;
  filtro: Filtro;
  emAndamento: boolean;
  estado: EstadoEnvio | null;
  rotuloEstado?: string;
}

interface EstadoTela { filtro?: Filtro; periodo?: PeriodFilter; folhaPeriodo?: boolean; base?: string; tipo?: string; folhaFiltros?: boolean }

/** Inspeções de segurança feitas neste aparelho, por dia, com desvios e estado de envio. */
@Component({
  selector: 'app-inspecoes-list',
  standalone: true,
  imports: [AbasSecaoComponent, ChipsComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, PeriodoComponent, RouterLink, SeletorComponent, StatusEnvioComponent, TituloGrandeComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-titulo-grande titulo="Inspeções" [subtitulo]="subtitulo()">
      <ces-indicador-rede />
      <ces-periodo [valor]="periodo()" (valorChange)="definir({ periodo: $event })" [aberto]="folhaPeriodo()" (abertoChange)="definir({ folhaPeriodo: $event })" />
    </ces-titulo-grande>
    @if (abasSeguranca().length) { <ces-abas-secao rotulo="Segurança" [abas]="abasSeguranca()" /> }

    @if (authService.isCampo() && !turnoService.turnoAtivo()) {
      <p class="aviso"><ces-icone nome="relogio" [tamanho]="18" />Abra o turno na tela Hoje para fazer uma inspeção.</p>
    }

    <div class="linha-filtros">
      <ces-chips rotulo="Filtrar inspeções" [opcoes]="filtros" [contagem]="contagem()" [valor]="filtro()" (valorChange)="definir({ filtro: $any($event) })" />
    </div>
    @if (basesUsadas().length > 1 || tiposUsados().length > 1) {
      <button type="button" class="ces-btn ces-btn--texto ces-btn--compacto mais-filtros" (click)="definir({ folhaFiltros: true })">
        <ces-icone nome="filtro" [tamanho]="16" />{{ idBase() || idTipo() ? 'Base e tipo filtrados' : 'Filtrar por base ou tipo' }}
      </button>
    }

    @if (filtro() === 'todas' || filtro() === 'andamento') {
      @if (rascunhos().length) {
        <h2 class="ces-secao">Não terminadas</h2>
        <ul class="ces-grupo" aria-label="Inspeções não terminadas">
          @for (r of rascunhos(); track r.chave) {
            <li><button type="button" class="ces-linha ces-linha--acao" (click)="continuarRascunho(r)">
              <span class="ces-selo ces-selo--destaque"><ces-icone nome="retomar" /></span>
              <span class="ces-linha__texto">
                <span class="ces-linha__titulo">{{ r.titulo || 'Inspeção' }}</span>
                <span class="ces-linha__apoio ces-num">Salva sozinha · {{ quando(r.atualizadoEm) }}{{ r.temAnexos ? ' · com fotos' : '' }}</span>
              </span>
              <span class="ces-btn ces-btn--tonal ces-btn--compacto" aria-hidden="true">Continuar</span>
            </button></li>
          }
        </ul>
      }
    }

    @if (carregando()) {
      <div class="ces-esqueletos" aria-busy="true" aria-label="Carregando">
        @for (i of [1, 2, 3]; track i) { <div class="ces-esqueleto" style="height: 72px"></div> }
      </div>
    } @else if (grupos().length || rascunhos().length) {
      @for (g of grupos(); track g.titulo) {
        <h2 class="ces-secao">{{ g.titulo }}</h2>
        <ul class="ces-grupo">
          @for (l of g.itens; track l.id) {
            <li class="ces-linha-lista" [attr.data-ancora]="l.id">
              <button type="button" class="ces-linha ces-linha--acao" (click)="abrir(l)">
                <span class="ces-selo" [class.ces-selo--atencao]="l.desvios > 0" [class.ces-selo--ok]="!l.desvios && !l.emAndamento"><ces-icone nome="inspecao" /></span>
                <span class="ces-linha__texto">
                  <span class="ces-linha__titulo">{{ l.tipo }}</span>
                  <span class="ces-linha__apoio ces-num">{{ l.apoio }}</span>
                </span>
                <span class="direita">
                  @if (l.emAndamento) {
                    <span class="ces-btn ces-btn--tonal ces-btn--compacto" aria-hidden="true">Continuar</span>
                  } @else {
                    <span class="ces-qtd" [class.com-desvio]="l.desvios > 0">{{ l.desvios }}<small>{{ l.desvios === 1 ? 'desvio' : 'desvios' }}</small></span>
                    @if (l.estado) { <ces-status-envio [estado]="l.estado" [rotulo]="l.rotuloEstado" /> }
                  }
                </span>
              </button>
            </li>
          }
        </ul>
      }
      <div class="ces-respiro"></div>
    } @else {
      <ces-vazio icone="inspecao" [titulo]="filtro() === 'todas' ? 'Nenhuma inspeção no período' : 'Nada com esse filtro'"
        [texto]="filtro() === 'todas' ? 'As inspeções feitas neste aparelho aparecem aqui.' : 'Volte para Todas para ver o resto.'">
        @if (filtro() !== 'todas' || periodo() !== 'todos' || idBase() || idTipo()) {
          <button type="button" class="ces-btn ces-btn--tonal" (click)="limpar()">Mostrar todas</button>
        }
      </ces-vazio>
    }

    @if (podeCriar()) {
      <a class="ces-fab" routerLink="/inspecoes/nova"><ces-icone nome="mais" />Inspecionar</a>
    }

    <ces-folha [aberta]="folhaFiltros()" (fechar)="definir({ folhaFiltros: false })" titulo="Base e tipo">
      <div class="campos">
        <ces-seletor rotulo="Base" placeholder="Todas" [opcoes]="basesUsadas()" [valor]="idBase() || null" (valorChange)="definir({ base: $event ?? '' })" />
        <ces-seletor rotulo="Tipo de inspeção" placeholder="Todos" [opcoes]="tiposUsados()" [valor]="idTipo() || null" (valorChange)="definir({ tipo: $event ?? '' })" />
      </div>
      <div rodape class="ces-folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="definir({ base: '', tipo: '' })">Limpar</button>
        <button type="button" class="ces-btn ces-btn--principal" (click)="definir({ folhaFiltros: false })">Mostrar</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: block; position: relative; min-height: 100%; }
    .aviso { display: flex; gap: var(--ces-e2); align-items: flex-start; margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e3) var(--ces-e4);
      border-radius: var(--ces-r-grupo); background: var(--ces-atencao-suave); font: var(--ces-t-apoio); font-size: .875rem; }
    .mais-filtros { margin: 0 var(--ces-e3); gap: 4px; }
    .direita { display: grid; justify-items: end; gap: 6px; }
    .ces-qtd.com-desvio { color: var(--ces-atencao); }
    .campos { display: grid; gap: var(--ces-e3); padding: 0 var(--ces-e4); }
  `],
})
export class InspecoesListComponent implements OnInit {
  readonly abasSeguranca = abasSeguranca();
  private readonly dbService = inject(DbService);
  private readonly outboxService = inject(OutboxService);
  private readonly syncService = inject(SyncService);
  private readonly sessao = inject(SessaoService);
  private readonly rascunhoStore = inject(RascunhoStore);
  private readonly tokens = inject(TokenService);
  readonly rascunhos = signal<ResumoRascunho[]>([]);
  private readonly router = inject(Router);
  readonly authService = inject(AuthService);
  readonly turnoService = inject(TurnoService);

  readonly filtros = FILTROS;
  private readonly estadoTela = this.sessao.estadoTela<EstadoTela>();
  readonly filtro = signal<Filtro>(this.estadoTela.filtro ?? 'todas');
  readonly periodo = signal<PeriodFilter>(this.estadoTela.periodo ?? DEFAULT_PERIOD_FILTER);
  readonly folhaPeriodo = signal(!!this.estadoTela.folhaPeriodo);
  readonly folhaFiltros = signal(!!this.estadoTela.folhaFiltros);
  readonly idBase = signal(this.estadoTela.base ?? '');
  readonly idTipo = signal(this.estadoTela.tipo ?? '');
  readonly carregando = signal(true);
  private readonly inspecoes = signal<InspecaoLocal[]>([]);
  private readonly bases = signal<BaseOperacionalLocal[]>([]);
  private readonly tipos = signal<TipoInspecaoLocal[]>([]);
  private readonly fila = signal<OutboxItem[]>([]);

  constructor() {
    effect(() => {
      this.syncService.outboxSyncVersion();
      untracked(() => void this.carregar());
    });
  }

  async ngOnInit(): Promise<void> {
    if (this.authService.isCampo()) await this.turnoService.loadTurnoAtivo();
  }

  readonly podeCriar = computed(() => !this.authService.isCampo() || !!this.turnoService.turnoAtivo());

  private readonly linhas = computed<Linha[]>(() => {
    const p = this.periodo();
    const inicio = periodStartTime(p);
    const bases = new Map(this.bases().map(b => [b.id, b.nome]));
    const tipos = new Map(this.tipos().map(t => [t.id, t.nome]));
    return this.inspecoes()
      .filter(i => inicio === null || isWithinPeriod(i.dataRealizacao, p, inicio))
      .filter(i => !this.idBase() || i.idBaseOperacional === this.idBase())
      .filter(i => !this.idTipo() || i.idTipoInspecao === this.idTipo() || i.tipoInspecao?.id === this.idTipo())
      .sort((a, b) => (b.dataRealizacao ?? '').localeCompare(a.dataRealizacao ?? ''))
      .map(i => {
        const base = i.nomeBaseOperacional || (i.idBaseOperacional ? bases.get(i.idBaseOperacional) : '') || '';
        const desvios = contarDesvios(i);
        return {
          id: i.id,
          tipo: i.nomeTipoInspecao || i.tipoInspecao?.nome || (i.idTipoInspecao ? tipos.get(i.idTipoInspecao) : '') || 'Inspeção',
          apoio: [hora(i.dataRealizacao), base].filter(Boolean).join(' · '),
          desvios,
          data: i.dataRealizacao,
          emAndamento: i.status === 'EM_ANDAMENTO',
          ...this.estadoDe(i),
        };
      });
  });

  readonly contagem = computed(() => {
    const c: Partial<Record<string, number>> = {};
    for (const l of this.linhas()) c[l.filtro] = (c[l.filtro] ?? 0) + 1;
    if (this.rascunhos().length) c['andamento'] = (c['andamento'] ?? 0) + this.rascunhos().length;
    return c;
  });

  readonly grupos = computed(() => {
    const f = this.filtro();
    return agruparPorDia(this.linhas().filter(l => f === 'todas' || l.filtro === f), l => l.data);
  });

  readonly basesUsadas = computed<OpcaoSeletor[]>(() => {
    const ids = new Set(this.inspecoes().map(i => i.idBaseOperacional).filter(Boolean));
    return this.bases().filter(b => ids.has(b.id)).map(b => ({ valor: b.id, rotulo: b.nome }));
  });
  readonly tiposUsados = computed<OpcaoSeletor[]>(() => {
    const ids = new Set(this.inspecoes().map(i => i.idTipoInspecao ?? i.tipoInspecao?.id).filter(Boolean));
    return this.tipos().filter(t => ids.has(t.id)).map(t => ({ valor: t.id, rotulo: t.nome }));
  });

  readonly subtitulo = computed(() => {
    const n = this.linhas().length;
    return `${n} ${n === 1 ? 'inspeção' : 'inspeções'} · ${rotuloPeriodo(this.periodo()).toLowerCase()}`;
  });

  definir(m: Partial<EstadoTela>): void {
    if (m.filtro !== undefined) this.filtro.set(m.filtro);
    if (m.periodo !== undefined) this.periodo.set(m.periodo);
    if (m.folhaPeriodo !== undefined) this.folhaPeriodo.set(m.folhaPeriodo);
    if (m.folhaFiltros !== undefined) this.folhaFiltros.set(m.folhaFiltros);
    if (m.base !== undefined) this.idBase.set(m.base);
    if (m.tipo !== undefined) this.idTipo.set(m.tipo);
    this.sessao.definirEstadoTela({ ...m });
  }

  continuarRascunho(r: ResumoRascunho): void {
    if (r.rota) void this.router.navigateByUrl(r.rota);
  }

  quando(ms: number): string {
    const d = new Date(ms);
    return `${tituloDia(d).toLowerCase()} às ${hora(d)}`;
  }

  limpar(): void {
    this.definir({ filtro: 'todas', periodo: 'todos', base: '', tipo: '' });
  }

  abrir(l: Linha): void {
    if (l.emAndamento) void this.router.navigate(['/inspecoes/nova'], { queryParams: { editId: l.id } });
    else void this.router.navigate(['/inspecoes', l.id]);
  }

  private estadoDe(i: InspecaoLocal): { filtro: Filtro; estado: EstadoEnvio | null; rotuloEstado?: string } {
    if (i.status === 'EM_ANDAMENTO') return { filtro: 'andamento', estado: null };
    const fila = this.fila().find(o => o.id === i.id || o.payload?.id === i.id || (o.method === 'POST' && o.payload?.tempId === i.id));
    if (fila && this.outboxService.isEmTratamentoBackoffice(fila)) return { filtro: 'problema', estado: 'conflict', rotuloEstado: 'Com o escritório' };
    if (fila?.status === 'ERRO') return { filtro: 'problema', estado: 'error' };
    if (fila?.status === 'RESULTADO_INCERTO') return { filtro: 'problema', estado: 'conflict', rotuloEstado: 'A confirmar' };
    if (fila?.status === 'PENDENTE') return { filtro: 'fila', estado: 'queued' };
    if (i.status === 'CANCELADA') return { filtro: 'canceladas', estado: null, rotuloEstado: 'Cancelada' };
    return { filtro: 'enviadas', estado: 'synced' };
  }

  private async carregar(): Promise<void> {
    try {
      const dono = this.tokens.ownerId();
      // Rascunhos contínuos do formulário (inspecao:nova / inspecao:rascunho:<id>) — salvos a cada mudança.
      if (dono) this.rascunhos.set((await this.rascunhoStore.listar(dono)).filter(r => r.chave.startsWith('inspecao:') && !r.chave.includes('#conflito-') && !!r.rota));
      const [inspecoes, fila, bases, tipos] = await Promise.all([
        this.dbService.getInspecoes(), this.dbService.getPendingOutbox(), this.dbService.getBasesOperacionaisAtivas(), this.dbService.getTiposInspecaoAtivos(),
      ]);
      this.inspecoes.set(inspecoes);
      this.fila.set(fila);
      this.bases.set(bases);
      this.tipos.set(tipos);
    } catch (error) {
      console.error('Erro ao carregar inspeções:', error);
    } finally {
      this.carregando.set(false);
    }
  }
}

/** Na fila, os desvios estão no payload; depois de sincronizada, no draftData baixado do servidor. */
function contarDesvios(i: InspecaoLocal): number {
  const doPayload = (i.payload as { desvios?: unknown[] } | undefined)?.desvios;
  if (doPayload) return doPayload.length;
  if (!i.draftData) return 0;
  try {
    return (JSON.parse(i.draftData) as { desvios?: unknown[] }).desvios?.length ?? 0;
  } catch {
    return 0;
  }
}
