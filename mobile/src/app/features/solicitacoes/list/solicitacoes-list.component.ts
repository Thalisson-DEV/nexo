import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DbService } from '../../../core/db/db.service';
import { OutboxService } from '../../../core/sync/outbox.service';
import { SyncService } from '../../../core/sync/sync.service';
import { TurnoService } from '../../../core/turno/turno.service';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { EstoqueLocal, MaterialLocal, OutboxItem, SolicitacaoLocal } from '../../../core/db/models';
import { PeriodFilter, DEFAULT_PERIOD_FILTER, isWithinPeriod, periodStartTime } from '../../../shared/components/filter-sheet/period-filter.util';
import {
  BarraTopoComponent, ChipsComponent, IconeComponent, OpcaoChip, PeriodoComponent, VazioComponent, agruparPorDia, dataCurta, rotuloPeriodo,
} from '../../../ui';
import { FiltroPedido, SituacaoPedido, situacaoPedido } from '../situacao-pedido';

const FILTROS: OpcaoChip<FiltroPedido>[] = [
  { valor: 'todos', rotulo: 'Todos' },
  { valor: 'andamento', rotulo: 'Em andamento' },
  { valor: 'entregues', rotulo: 'Entregues' },
  { valor: 'recusados', rotulo: 'Recusados' },
  { valor: 'problema', rotulo: 'Com problema' },
];

interface Linha {
  id: string;
  origem: string;
  resumo: string;
  itens: number;
  retirada: string;
  data: string;
  situacao: SituacaoPedido;
}

interface EstadoTela { filtro?: FiltroPedido; periodo?: PeriodFilter; folhaPeriodo?: boolean }

/** Pedidos de material da equipe, por dia, com a situação de cada um em palavras simples. */
@Component({
  selector: 'app-solicitacoes-list',
  standalone: true,
  imports: [BarraTopoComponent, ChipsComponent, IconeComponent, PeriodoComponent, RouterLink, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-barra-topo titulo="Meus pedidos" [subtitulo]="subtitulo()" (voltar)="voltar()">
      <ces-periodo [valor]="periodo()" (valorChange)="definirPeriodo($event)" [aberto]="folhaPeriodo()" (abertoChange)="definirFolha($event)" />
    </ces-barra-topo>
    <ces-chips rotulo="Filtrar pedidos" [opcoes]="filtros" [contagem]="contagem()" [valor]="filtro()" (valorChange)="definirFiltro($any($event))" />

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
              <a class="ces-linha ces-linha--acao" [routerLink]="['/solicitacoes', l.id]">
                <span class="ces-selo" [class]="'ces-selo ces-selo--' + seloDe(l.situacao.tom)"><ces-icone nome="documento" /></span>
                <span class="ces-linha__texto">
                  <span class="ces-linha__titulo">{{ l.resumo }}</span>
                  <span class="ces-linha__apoio ces-num">{{ l.origem }}{{ l.retirada ? ' · retirar ' + l.retirada : '' }}</span>
                </span>
                <span class="direita">
                  <span class="ces-qtd">{{ l.itens }}<small>{{ l.itens === 1 ? 'item' : 'itens' }}</small></span>
                  <span class="ces-etiqueta" [class]="'ces-etiqueta ces-etiqueta--' + l.situacao.tom">{{ l.situacao.rotulo }}</span>
                </span>
              </a>
            </li>
          }
        </ul>
      }
      <div class="ces-respiro"></div>
    } @else {
      <ces-vazio icone="documento" [titulo]="filtro() === 'todos' ? 'Nenhum pedido no período' : 'Nada com esse filtro'"
        [texto]="filtro() === 'todos' ? 'Peça material do almoxarifado pelo botão Pedir.' : 'Volte para Todos para ver o resto.'">
        @if (filtro() !== 'todos' || periodo() !== 'todos') {
          <button type="button" class="ces-btn ces-btn--tonal" (click)="limpar()">Mostrar todos</button>
        }
      </ces-vazio>
    }

    @if (turno.turnoAtivo()) {
      <a class="ces-fab" routerLink="/solicitacoes/nova"><ces-icone nome="mais" />Pedir</a>
    }
  `,
  styles: [`
    :host { display: block; position: relative; min-height: 100%; }
    .direita { display: grid; justify-items: end; gap: 6px; }
  `],
})
export class SolicitacoesListComponent implements OnInit {
  private readonly dbService = inject(DbService);
  private readonly outboxService = inject(OutboxService);
  private readonly syncService = inject(SyncService);
  private readonly sessao = inject(SessaoService);
  private readonly router = inject(Router);
  readonly turno = inject(TurnoService);

  readonly filtros = FILTROS;
  private readonly estadoTela = this.sessao.estadoTela<EstadoTela>();
  readonly filtro = signal<FiltroPedido>(this.estadoTela.filtro ?? 'todos');
  readonly periodo = signal<PeriodFilter>(this.estadoTela.periodo ?? DEFAULT_PERIOD_FILTER);
  readonly folhaPeriodo = signal(!!this.estadoTela.folhaPeriodo);
  readonly carregando = signal(true);
  private readonly solicitacoes = signal<SolicitacaoLocal[]>([]);
  private readonly fila = signal<OutboxItem[]>([]);
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
    return this.solicitacoes()
      .filter(s => inicio === null || isWithinPeriod(s.dataSolicitacao, p, inicio))
      .sort((a, b) => (b.dataSolicitacao ?? '').localeCompare(a.dataSolicitacao ?? ''))
      .map(s => {
        const fila = this.itemDaFila(s.id);
        const primeiro = mats.get(s.itens[0]?.idMaterial)?.descricao ?? 'Material';
        return {
          id: s.id,
          origem: est.get(s.idEstoqueOrigem)?.nomeIdentificador ?? 'Almoxarifado',
          resumo: s.itens.length > 1 ? `${primeiro} e mais ${s.itens.length - 1}` : primeiro,
          itens: s.itens.length,
          retirada: dataCurta(s.dataPrevista),
          data: s.dataSolicitacao ?? new Date(0).toISOString(),
          situacao: situacaoPedido(s, fila, {
            backoffice: this.outboxService.isEmTratamentoBackoffice(fila ?? null),
            incerto: this.outboxService.isResultadoIncerto(fila ?? null),
          }),
        };
      });
  });

  readonly contagem = computed(() => {
    const c: Partial<Record<string, number>> = {};
    for (const l of this.linhas()) c[l.situacao.filtro] = (c[l.situacao.filtro] ?? 0) + 1;
    return c;
  });

  readonly grupos = computed(() => {
    const f = this.filtro();
    return agruparPorDia(this.linhas().filter(l => f === 'todos' || l.situacao.filtro === f), l => l.data);
  });

  readonly subtitulo = computed(() => {
    const n = this.linhas().length;
    return `${n} ${n === 1 ? 'pedido' : 'pedidos'} · ${rotuloPeriodo(this.periodo()).toLowerCase()}`;
  });

  seloDe(tom: string): string {
    return tom === 'neutro' ? 'neutro' : tom === 'erro' ? 'erro' : tom === 'ok' ? 'ok' : tom === 'atencao' ? 'atencao' : 'destaque';
  }

  definirFiltro(f: FiltroPedido): void {
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
    this.definirFiltro('todos');
    this.definirPeriodo('todos');
  }

  voltar(): void {
    const anterior = this.sessao.rotaAnterior();
    void this.router.navigateByUrl(anterior && !anterior.startsWith('/solicitacoes') ? anterior : '/material');
  }

  private itemDaFila(id: string): OutboxItem | undefined {
    return this.fila().find(o => o.payload?.id === id || (o.method === 'POST' && o.payload?.tempId === id) || o.id === id);
  }

  private async carregar(): Promise<void> {
    try {
      const [sols, fila, ests, mats] = await Promise.all([
        this.dbService.getSolicitacoes(), this.dbService.getPendingOutbox(), this.dbService.getEstoques(), this.dbService.getMateriais(),
      ]);
      this.solicitacoes.set(sols);
      this.fila.set(fila);
      this.estoques.set(new Map(ests.map(e => [e.id, e])));
      this.materiais.set(new Map(mats.map(m => [m.id, m])));
    } catch (error) {
      console.error('Erro ao carregar pedidos:', error);
    } finally {
      this.carregando.set(false);
    }
  }
}
