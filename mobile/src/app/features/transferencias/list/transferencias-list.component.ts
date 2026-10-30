import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DbService } from '../../../core/db/db.service';
import { SyncService } from '../../../core/sync/sync.service';
import { TurnoService } from '../../../core/turno/turno.service';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { EstoqueLocal, MaterialLocal, TransferenciaLocal } from '../../../core/db/models';
import { PeriodFilter, DEFAULT_PERIOD_FILTER, isWithinPeriod, periodStartTime } from '../../../shared/components/filter-sheet/period-filter.util';
import {
  BarraTopoComponent, ChipsComponent, EstadoEnvio, IconeComponent, OpcaoChip, PeriodoComponent, StatusEnvioComponent, VazioComponent,
  agruparPorDia, hora, numeroBr, rotuloPeriodo,
} from '../../../ui';

type Filtro = 'todas' | 'fila' | 'enviadas' | 'problema';

const FILTROS: OpcaoChip<Filtro>[] = [
  { valor: 'todas', rotulo: 'Todas' },
  { valor: 'fila', rotulo: 'Na fila' },
  { valor: 'enviadas', rotulo: 'Enviadas' },
  { valor: 'problema', rotulo: 'Com problema' },
];

interface Linha {
  id: string;
  destino: string;
  resumo: string;
  total: string;
  quando: string;
  data: string;
  estado: EstadoEnvio;
  rotuloEstado?: string;
}

interface EstadoTela { filtro?: Filtro; periodo?: PeriodFilter; folhaPeriodo?: boolean }

/** Transferências feitas por este aparelho, por dia, com o estado de envio. */
@Component({
  selector: 'app-transferencias-list',
  standalone: true,
  imports: [BarraTopoComponent, ChipsComponent, IconeComponent, PeriodoComponent, RouterLink, StatusEnvioComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-barra-topo titulo="Transferências" [subtitulo]="subtitulo()" (voltar)="voltar()">
      <ces-periodo [valor]="periodo()" (valorChange)="definirPeriodo($event)" [aberto]="folhaPeriodo()" (abertoChange)="definirFolha($event)" />
    </ces-barra-topo>
    <ces-chips rotulo="Filtrar por envio" [opcoes]="filtros" [contagem]="contagem()" [valor]="filtro()" (valorChange)="definirFiltro($any($event))" />

    @if (carregando()) {
      <div class="ces-esqueletos" aria-busy="true" aria-label="Carregando">
        @for (i of [1, 2, 3]; track i) { <div class="ces-esqueleto" style="height: 72px"></div> }
      </div>
    } @else if (grupos().length) {
      @for (g of grupos(); track g.titulo) {
        <h2 class="ces-secao">{{ g.titulo }}</h2>
        <ul class="ces-grupo">
          @for (l of g.itens; track l.id) {
            <li class="ces-linha-lista" [attr.data-ancora]="l.id">
              <a class="ces-linha ces-linha--acao" [routerLink]="['/transferencias', l.id]">
                <span class="ces-selo ces-selo--destaque"><ces-icone nome="transferencia" /></span>
                <span class="ces-linha__texto">
                  <span class="ces-linha__titulo">Para {{ l.destino }}</span>
                  <span class="ces-linha__apoio ces-num">{{ l.quando }} · {{ l.resumo }}</span>
                </span>
                <span class="direita">
                  <span class="ces-qtd">{{ l.total }}</span>
                  <ces-status-envio [estado]="l.estado" [rotulo]="l.rotuloEstado" />
                </span>
              </a>
            </li>
          }
        </ul>
      }
      <div class="ces-respiro"></div>
    } @else {
      <ces-vazio icone="transferencia" [titulo]="filtro() === 'todas' ? 'Nenhuma transferência no período' : 'Nada com esse filtro'"
        [texto]="filtro() === 'todas' ? 'As transferências feitas neste aparelho aparecem aqui.' : 'Volte para Todas para ver o resto.'">
        @if (filtro() !== 'todas' || periodo() !== 'todos') {
          <button type="button" class="ces-btn ces-btn--tonal" (click)="limpar()">Mostrar todas</button>
        }
      </ces-vazio>
    }

    @if (turno.turnoAtivo()) {
      <a class="ces-fab" routerLink="/transferencias/nova"><ces-icone nome="mais" />Transferir</a>
    }
  `,
  styles: [`
    :host { display: block; position: relative; min-height: 100%; }
    .direita { display: grid; justify-items: end; gap: 6px; }
  `],
})
export class TransferenciasListComponent implements OnInit {
  private readonly dbService = inject(DbService);
  private readonly syncService = inject(SyncService);
  private readonly sessao = inject(SessaoService);
  private readonly router = inject(Router);
  readonly turno = inject(TurnoService);

  readonly filtros = FILTROS;
  private readonly estadoTela = this.sessao.estadoTela<EstadoTela>();
  readonly filtro = signal<Filtro>(this.estadoTela.filtro ?? 'todas');
  readonly periodo = signal<PeriodFilter>(this.estadoTela.periodo ?? DEFAULT_PERIOD_FILTER);
  readonly folhaPeriodo = signal(!!this.estadoTela.folhaPeriodo);
  readonly carregando = signal(true);
  private readonly transferencias = signal<TransferenciaLocal[]>([]);
  private readonly estoques = signal<Map<string, EstoqueLocal>>(new Map());
  private readonly materiais = signal<Map<string, MaterialLocal>>(new Map());

  constructor() {
    effect(() => {
      this.syncService.outboxSyncVersion();
      untracked(() => void this.carregar());
    });
  }

  async ngOnInit(): Promise<void> {
    await this.turno.loadTurnoAtivo();
  }

  private readonly linhas = computed<Linha[]>(() => {
    const p = this.periodo();
    const inicio = periodStartTime(p);
    const est = this.estoques();
    const mats = this.materiais();
    return this.transferencias()
      .filter(t => inicio === null || isWithinPeriod(t.dataTransferencia, p, inicio))
      .sort((a, b) => (b.dataTransferencia ?? '').localeCompare(a.dataTransferencia ?? ''))
      .map(t => {
        const primeiro = mats.get(t.itens[0]?.idMaterial)?.descricao ?? 'Material';
        const outros = t.itens.length - 1;
        const unidades = new Set(t.itens.map(i => mats.get(i.idMaterial)?.unidadeMedida ?? 'UN'));
        const soma = t.itens.reduce((s, i) => s + i.quantidade, 0);
        return {
          id: t.id,
          destino: est.get(t.idEstoqueDestino)?.nomeIdentificador ?? 'Estoque',
          resumo: outros > 0 ? `${primeiro} e mais ${outros}` : primeiro,
          // Soma só faz sentido com uma unidade; senão mostra quantos itens.
          total: unidades.size === 1 ? `${numeroBr(soma)} ${[...unidades][0]}` : `${t.itens.length} itens`,
          quando: t.dataTransferencia ? hora(t.dataTransferencia) : '',
          data: t.dataTransferencia ?? new Date(0).toISOString(),
          ...estadoDe(t),
        };
      });
  });

  readonly contagem = computed(() => {
    const c: Partial<Record<string, number>> = {};
    for (const l of this.linhas()) c[filtroDe(l.estado)] = (c[filtroDe(l.estado)] ?? 0) + 1;
    return c;
  });

  readonly grupos = computed(() => {
    const f = this.filtro();
    return agruparPorDia(this.linhas().filter(l => f === 'todas' || filtroDe(l.estado) === f), l => l.data);
  });

  readonly subtitulo = computed(() => {
    const n = this.linhas().length;
    return `${n} ${n === 1 ? 'transferência' : 'transferências'} · ${rotuloPeriodo(this.periodo()).toLowerCase()}`;
  });

  definirFiltro(f: Filtro): void {
    this.filtro.set(f);
    this.sessao.definirEstadoTela({ filtro: f });
  }

  definirPeriodo(p: PeriodFilter): void {
    this.periodo.set(p);
    this.sessao.definirEstadoTela({ periodo: p });
  }

  definirFolha(aberta: boolean): void {
    this.folhaPeriodo.set(aberta);
    this.sessao.definirEstadoTela({ folhaPeriodo: aberta });
  }

  limpar(): void {
    this.definirFiltro('todas');
    this.definirPeriodo('todos');
  }

  voltar(): void {
    const anterior = this.sessao.rotaAnterior();
    void this.router.navigateByUrl(anterior && !anterior.startsWith('/transferencias') ? anterior : '/material');
  }

  private async carregar(): Promise<void> {
    try {
      const [transfs, ests, mats] = await Promise.all([this.dbService.getTransferencias(), this.dbService.getEstoques(), this.dbService.getMateriais()]);
      this.transferencias.set(transfs);
      this.estoques.set(new Map(ests.map(e => [e.id, e])));
      this.materiais.set(new Map(mats.map(m => [m.id, m])));
    } catch (error) {
      console.error('Erro ao carregar transferências:', error);
    } finally {
      this.carregando.set(false);
    }
  }
}

function estadoDe(t: TransferenciaLocal): { estado: EstadoEnvio; rotuloEstado?: string } {
  switch (t.statusSync) {
    case 'SINCRONIZADO': return { estado: 'synced' };
    case 'ERRO': return { estado: 'error' };
    case 'RESULTADO_INCERTO': return { estado: 'conflict', rotuloEstado: 'A confirmar' };
    case 'RECEBIDO_BACKOFFICE': return { estado: 'conflict', rotuloEstado: 'Com o escritório' };
    default: return { estado: 'queued' };
  }
}

function filtroDe(e: EstadoEnvio): Filtro {
  if (e === 'synced') return 'enviadas';
  if (e === 'error' || e === 'conflict') return 'problema';
  return 'fila';
}
