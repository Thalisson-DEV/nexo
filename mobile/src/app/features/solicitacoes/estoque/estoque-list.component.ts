import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { liveQuery } from 'dexie';
import { from } from 'rxjs';
import { DbService } from '../../../core/db/db.service';
import { TokenService } from '../../../core/auth/token.service';
import { SyncService } from '../../../core/sync/sync.service';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { SaldoSerieLocal } from '../../../core/db/models';
import { DIAS_PARADO, diasDesde, haDias, rotuloMaterialParado, rotuloSerieNoSaldo, tomTempoSaldo } from '../../../core/saldo/tempo-saldo';
import { BarraTopoComponent, ChipsComponent, FolhaComponent, IconeComponent, OpcaoChip, VazioComponent, dataCurta, hora, numeroBr } from '../../../ui';

interface SaldoLinha {
  id: string;
  idMaterial: string;
  quantidade: number;
  condicao: 'NOVO' | 'SUCATA';
  codigo: string;
  descricao: string;
  unidadeMedida: string;
  detalhaSerie: boolean;
  ultimaMovimentacao?: string | null;
  serieMaisAntiga?: string | null;
}

interface Linha extends SaldoLinha {
  /** Dias parado: material com série usa a série há mais tempo no caminhão. */
  dias: number | null;
}

type Filtro = 'todos' | 'NOVO' | 'SUCATA';
const FILTROS: OpcaoChip<Filtro>[] = [
  { valor: 'todos', rotulo: 'Tudo' },
  { valor: 'NOVO', rotulo: 'Novo' },
  { valor: 'SUCATA', rotulo: 'Sucata' },
];

interface EstadoTela { filtro?: Filtro; busca?: string; series?: string | null; soParados?: boolean }

/** Saldo do caminhão: o que a equipe tem agora, com quantidade em destaque e séries em folha. */
@Component({
  selector: 'app-estoque-list',
  standalone: true,
  imports: [BarraTopoComponent, ChipsComponent, FolhaComponent, IconeComponent, NgTemplateOutlet, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-barra-topo titulo="Saldo do caminhão" [subtitulo]="subtitulo()" (voltar)="goBack()" />

    <div class="busca">
      <ces-icone nome="busca" [tamanho]="18" />
      <input type="search" placeholder="Nome ou código" enterkeyhint="search" aria-label="Buscar no saldo"
        [value]="busca()" (input)="definirBusca($any($event.target).value)">
    </div>
    <ces-chips rotulo="Condição" [opcoes]="filtros" [contagem]="contagem()" [valor]="filtro()" (valorChange)="definirFiltro($any($event))" />

    @if (parados() || soParados()) {
      <button type="button" class="parados" [attr.aria-pressed]="soParados()" (click)="alternarParados()">
        <ces-icone nome="relogio" [tamanho]="20" />
        <span class="parados__texto">
          @if (soParados()) {
            <strong>Mostrando só os parados</strong><span>Toque para ver todo o saldo</span>
          } @else {
            <strong>{{ parados() }} {{ parados() === 1 ? 'material parado' : 'materiais parados' }} há {{ diasParado }} dias ou mais</strong>
            <span>Aplique ou devolva ao almoxarifado. Toque para ver.</span>
          }
        </span>
        <ces-icone [nome]="soParados() ? 'fechar' : 'avancar'" [tamanho]="18" />
      </button>
    }

    @if (materiais() === undefined) {
      <div class="ces-esqueletos" aria-busy="true" aria-label="Carregando">
        @for (i of [1, 2, 3, 4]; track i) { <div class="ces-esqueleto" style="height: 64px"></div> }
      </div>
    } @else if (filtrados().length) {
      <ul class="ces-grupo lista" aria-label="Materiais no caminhão">
        @for (m of filtrados(); track m.id) {
          <li class="ces-linha-lista">
            @if (m.detalhaSerie) {
              <button type="button" class="ces-linha ces-linha--acao" (click)="abrirSeries(m)" [attr.aria-label]="m.descricao + ', ' + num(m.quantidade) + ' ' + m.unidadeMedida + ', ver séries'">
                <ng-container *ngTemplateOutlet="conteudo; context: { $implicit: m }" />
                <ces-icone nome="avancar" [tamanho]="18" />
              </button>
            } @else {
              <div class="ces-linha"><ng-container *ngTemplateOutlet="conteudo; context: { $implicit: m }" /></div>
            }
          </li>
        }
      </ul>
      <div class="ces-respiro"></div>
    } @else {
      <ces-vazio icone="estoque" [titulo]="busca() || filtro() !== 'todos' || soParados() ? 'Nada encontrado' : 'Caminhão sem saldo'"
        [texto]="busca() || filtro() !== 'todos' || soParados() ? 'Confira o nome, o código ou o filtro.' : 'Quando o almoxarifado entregar material, ele aparece aqui.'" />
    }

    <ng-template #conteudo let-m>
      <span class="ces-linha__texto">
        <span class="ces-linha__titulo">{{ m.descricao }}</span>
        <span class="ces-linha__apoio ces-num"><span class="ces-cond" [class.ces-cond--sucata]="m.condicao !== 'NOVO'">{{ m.condicao === 'NOVO' ? 'Novo' : 'Sucata' }}</span> · Cód. {{ m.codigo }}</span>
        @if (m.dias != null) {
          <span class="tempo" [attr.data-tom]="tom(m.dias)"><ces-icone nome="relogio" [tamanho]="14" />{{ rotuloTempo(m) }}</span>
        }
      </span>
      <span class="ces-qtd">{{ num(m.quantidade) }}<small>{{ m.unidadeMedida }}</small></span>
    </ng-template>

    <ces-folha [aberta]="!!seriesDe()" (fechar)="fecharSeries()" [titulo]="seriesDe()?.descricao ?? ''"
      [descricao]="descricaoSeries()">
      @if (carregandoSeries()) {
        <div class="ces-esqueletos"><div class="ces-esqueleto" style="height: 48px"></div><div class="ces-esqueleto" style="height: 48px"></div></div>
      } @else {
        <ul class="ces-grupo series" aria-label="Séries">
          @for (s of seriais(); track s.id) {
            <li><div class="ces-linha"><span class="ces-linha__texto"><span class="ces-linha__titulo ces-num">{{ s.numSerie }}</span>
              <span class="tempo" [attr.data-tom]="tom(dias(s.noSaldoDesde))"><ces-icone nome="relogio" [tamanho]="14" />{{ rotuloSerie(s.noSaldoDesde) }}</span></span></div></li>
          } @empty { <li><div class="ces-linha"><span class="ces-linha__apoio">Nenhuma série registrada</span></div></li> }
        </ul>
      }
    </ces-folha>
  `,
  styles: [`
    :host { display: block; min-height: 100%; }
    .busca { margin: var(--ces-e1) var(--ces-e3) 0; display: flex; align-items: center; gap: var(--ces-e2); padding: 0 var(--ces-e4); min-height: 48px;
      border-radius: var(--ces-r-pilula); background: var(--ces-superficie); color: var(--ces-texto-2); }
    .busca input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ces-texto); font: var(--ces-t-corpo); font-size: 1rem; }
    .busca input:focus { outline: none; }
    .lista { margin-top: var(--ces-e2); }
    .series { background: var(--ces-fundo); }
    .tempo { display: inline-flex; align-items: center; gap: 4px; margin-top: 2px; font: 600 .8125rem/1.3 var(--ces-fonte); color: var(--ces-texto-2); }
    .tempo[data-tom='atencao'] { color: var(--ces-atencao); }
    .tempo[data-tom='erro'] { color: var(--ces-erro); }
    .parados { display: flex; align-items: center; gap: var(--ces-e3); width: calc(100% - 2 * var(--ces-e3)); min-height: 56px; margin: var(--ces-e2) var(--ces-e3) 0;
      padding: var(--ces-e2) var(--ces-e4); border: 0; border-radius: var(--ces-r-grupo); background: var(--ces-atencao-suave); color: var(--ces-atencao);
      text-align: left; cursor: pointer; transition: transform var(--ces-m-entrar); }
    .parados:active { transform: scale(.98); }
    .parados[aria-pressed='true'] { background: var(--ces-superficie); color: var(--ces-texto-2); }
    .parados__texto { flex: 1; min-width: 0; display: grid; gap: 2px; }
    .parados__texto strong { font: 600 .9375rem/1.3 var(--ces-fonte); color: var(--ces-texto); }
    .parados__texto span { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
  `],
})
export class EstoqueListComponent {
  private readonly dbService = inject(DbService);
  private readonly tokenService = inject(TokenService);
  private readonly syncService = inject(SyncService);
  private readonly sessao = inject(SessaoService);
  private readonly router = inject(Router);

  // Consulta reativa ao IndexedDB: a cada alteração no banco, a lista atualiza sozinha.
  private readonly saldos = toSignal(from(liveQuery(() => {
    const idEstoque = this.tokenService.estoqueId();
    return idEstoque ? this.dbService.getSaldosComMateriais(idEstoque) : Promise.resolve([]);
  })), { initialValue: undefined }) as () => SaldoLinha[] | undefined;

  readonly materiais = computed<Linha[] | undefined>(() => this.saldos()?.map(m => ({
    ...m,
    dias: diasDesde(m.detalhaSerie && m.serieMaisAntiga ? m.serieMaisAntiga : m.ultimaMovimentacao),
  })));
  readonly diasParado = DIAS_PARADO;

  readonly filtros = FILTROS;
  private readonly estadoTela = this.sessao.estadoTela<EstadoTela>();
  readonly filtro = signal<Filtro>(this.estadoTela.filtro ?? 'todos');
  readonly busca = signal(this.estadoTela.busca ?? '');
  readonly soParados = signal(this.estadoTela.soParados ?? false);
  readonly seriesDe = signal<Linha | null>(null);
  readonly seriais = signal<SaldoSerieLocal[]>([]);
  readonly carregandoSeries = signal(false);
  private seriesPendente = this.estadoTela.series ?? null;

  readonly filtrados = computed(() => {
    const q = this.busca().toLowerCase().trim();
    const f = this.filtro();
    const lista = this.materiais() ?? [];
    this.restaurarSeries(lista);
    const parados = this.soParados();
    const filtrada = lista.filter(m => (f === 'todos' || m.condicao === f) && (!q || m.descricao.toLowerCase().includes(q) || m.codigo.toLowerCase().includes(q))
      && (!parados || (m.dias ?? 0) >= DIAS_PARADO));
    // Só os parados: o mais antigo primeiro, que é o que precisa de atenção.
    return parados ? filtrada.sort((a, b) => (b.dias ?? 0) - (a.dias ?? 0)) : filtrada;
  });

  readonly parados = computed(() => (this.materiais() ?? []).filter(m => (m.dias ?? 0) >= DIAS_PARADO).length);

  readonly descricaoSeries = computed(() => {
    if (!this.seriesDe()) return null;
    const n = this.seriais().length;
    const total = `${n} ${n === 1 ? 'série' : 'séries'} no caminhão`;
    const dias = diasDesde(this.seriais()[0]?.noSaldoDesde);
    return dias ? `${total} · a mais antiga chegou ${haDias(dias)}` : total;
  });

  readonly contagem = computed(() => {
    const c: Partial<Record<string, number>> = {};
    for (const m of this.materiais() ?? []) c[m.condicao] = (c[m.condicao] ?? 0) + 1;
    return c;
  });

  readonly subtitulo = computed(() => {
    const n = this.materiais()?.length ?? 0;
    const em = this.syncService.lastSyncAt();
    const atualizado = em ? ` · atualizado ${dataCurta(em)} ${hora(em)}` : '';
    return `${n} ${n === 1 ? 'material' : 'materiais'}${atualizado}`;
  });

  num(n: number): string {
    return numeroBr(n);
  }

  definirFiltro(f: Filtro): void {
    this.filtro.set(f);
    this.sessao.definirEstadoTela({ filtro: f });
  }

  alternarParados(): void {
    this.soParados.update(v => !v);
    this.sessao.definirEstadoTela({ soParados: this.soParados() });
  }

  dias(iso: string | null | undefined): number | null {
    return diasDesde(iso);
  }

  tom(dias: number | null): string {
    return tomTempoSaldo(dias);
  }

  rotuloTempo(m: Linha): string {
    if (m.detalhaSerie && m.serieMaisAntiga) return m.dias === 0 ? 'Chegou hoje' : `A mais antiga está com você ${haDias(m.dias ?? 0)}`;
    return rotuloMaterialParado(m.dias);
  }

  rotuloSerie(iso: string | null | undefined): string {
    return rotuloSerieNoSaldo(diasDesde(iso));
  }

  definirBusca(q: string): void {
    this.busca.set(q);
    this.sessao.definirEstadoTela({ busca: q });
  }

  async abrirSeries(m: Linha): Promise<void> {
    this.seriesDe.set(m);
    this.sessao.definirEstadoTela({ series: m.id });
    this.carregandoSeries.set(true);
    this.seriais.set([]);
    try {
      const idEstoque = this.tokenService.estoqueId();
      if (idEstoque) this.seriais.set(await this.dbService.getSeriaisPorMaterial(idEstoque, m.idMaterial, m.condicao));
    } catch (error) {
      console.error('Erro ao buscar séries:', error);
    } finally {
      this.carregandoSeries.set(false);
    }
  }

  fecharSeries(): void {
    this.seriesDe.set(null);
    this.sessao.definirEstadoTela({ series: null });
  }

  goBack(): void {
    const anterior = this.sessao.rotaAnterior();
    void this.router.navigateByUrl(anterior && !anterior.startsWith('/solicitacoes/estoque') ? anterior : '/material');
  }

  /** Folha de séries aberta antes do app fechar: reabre quando o saldo chega. */
  private restaurarSeries(lista: Linha[]): void {
    if (!this.seriesPendente || !lista.length) return;
    const alvo = lista.find(m => m.id === this.seriesPendente);
    this.seriesPendente = null;
    if (alvo) queueMicrotask(() => void this.abrirSeries(alvo));
  }
}
