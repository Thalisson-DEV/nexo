import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { CesCalloutComponent, CesStatusComponent } from '@ces/ui';

import { ConsumoAnteriorItem, ItemSolicitacaoResponse } from '../../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { lerConsumo } from './consumo-anterior.util';
import { ConsumoAnteriorOperacoesComponent } from './solicitacao-consumo-operacoes.component';

/**
 * Uma linha por material, dentro do item da solicitação: o que o estoque pegou no último pedido
 * daquele material, quanto aplicou desde então e quanto ainda tem.
 */
@Component({
  selector: 'app-consumo-anterior-linha',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, CesStatusComponent],
  template: `
    @if (carregando()) {
      <span class="ca-esqueleto" role="status" aria-label="Carregando o consumo anterior"></span>
    } @else if (consumo(); as c) {
      @if (c.ultimaSolicitacao; as u) {
        <dl class="ca-linha" [attr.aria-label]="'Desde o último pedido, em ' + (u.dataEntrega | date: 'dd/MM/yyyy')">
          @if (c.saldoAntes != null) { <div><dt>Tinha</dt><dd>{{ c.saldoAntes | number: '1.0-3' }}</dd></div> }
          <div><dt>Pegou em {{ u.dataEntrega | date: 'dd/MM' }}</dt><dd>{{ u.quantidadeRecebida | number: '1.0-3' }} {{ unidade() }}</dd></div>
          <div><dt>Gastou</dt><dd>{{ c.quantidadeAplicada ?? 0 | number: '1.0-3' }}</dd></div>
          @if (c.quantidadeDevolvida) { <div><dt>Devolveu</dt><dd>{{ c.quantidadeDevolvida | number: '1.0-3' }}</dd></div> }
          <div><dt>Tem agora</dt><dd>{{ c.saldoAtual | number: '1.0-3' }}</dd></div>
          @if (idControlado()) {
            <button type="button" class="ca-ver" [attr.aria-expanded]="aberto()" [attr.aria-controls]="idControlado()" (click)="alternar.emit()">
              {{ aberto() ? 'Recolher operações' : 'Ver operações' }}
              <i class="pi" [class.pi-chevron-up]="aberto()" [class.pi-chevron-down]="!aberto()" aria-hidden="true"></i>
            </button>
          }
        </dl>
        @if (leitura().tom !== 'neutral') {
          <ces-status [tone]="leitura().tom" [icon]="iconeLeitura()">{{ leitura().dica }}</ces-status>
        }
      } @else {
        <span class="ca-primeiro">
          {{ leitura().dica }} Tem {{ c.saldoAtual | number: '1.0-3' }} {{ unidade() }} agora.
          @if (idControlado()) {
            <button type="button" class="ca-ver" [attr.aria-expanded]="aberto()" [attr.aria-controls]="idControlado()" (click)="alternar.emit()">
              {{ aberto() ? 'Recolher' : 'Ver como chegou a esse saldo' }}
              <i class="pi" [class.pi-chevron-up]="aberto()" [class.pi-chevron-down]="!aberto()" aria-hidden="true"></i>
            </button>
          }
        </span>
      }
    }
  `,
  styles: [`
    :host { display: grid; gap: 0.3rem; justify-items: start; }
    .ca-linha { display: flex; flex-wrap: wrap; gap: 0.25rem 0.875rem; margin: 0.15rem 0 0; font-size: var(--ces-font-caption); }
    .ca-linha div { display: flex; gap: 0.3rem; align-items: baseline; }
    .ca-linha dt { color: var(--ces-foreground-muted); }
    .ca-linha dd { margin: 0; font-weight: 600; color: var(--ces-foreground); font-variant-numeric: tabular-nums; }
    .ca-ver { display: inline-flex; align-items: center; gap: 0.3rem; padding: 0; border: 0; background: none; font: inherit; font-weight: 600;
      color: var(--ces-brand); cursor: pointer; }
    .ca-ver i { font-size: 0.65rem; }
    .ca-ver:hover { text-decoration: underline; }
    .ca-ver:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 2px; border-radius: var(--ces-radius-sm); }
    .ca-primeiro { font-size: var(--ces-font-caption); color: var(--ces-foreground-muted); }
    .ca-esqueleto { display: block; width: 14rem; max-width: 100%; height: 0.75rem; border-radius: var(--ces-radius-md); background: var(--ces-surface-muted); }
  `]
})
export class ConsumoAnteriorLinhaComponent {
  readonly consumo = input<ConsumoAnteriorItem | null>(null);
  readonly unidade = input('UN');
  readonly carregando = input(false);
  /** Id do painel com as operações; sem ele a linha não oferece o detalhamento. */
  readonly idControlado = input<string | null>(null);
  readonly aberto = input(false);
  readonly alternar = output<void>();
  protected readonly leitura = computed(() => lerConsumo(this.consumo()));
  protected readonly iconeLeitura = computed(() => iconeDaLeitura(this.leitura().tom));
}

function iconeDaLeitura(tom: string): string {
  if (tom === 'warning') return 'pi pi-exclamation-circle';
  if (tom === 'info') return 'pi pi-info-circle';
  return 'pi pi-check';
}

/** Uma linha por material: o material com série vem desmembrado em várias linhas do pedido. */
interface LinhaConsumo {
  readonly material: ItemSolicitacaoResponse['material'];
  readonly quantidade: number;
  readonly consumo: ConsumoAnteriorItem | null;
  readonly leitura: ReturnType<typeof lerConsumo>;
}

/**
 * Relatório: consumo de cada material desde a última solicitação comercial do mesmo estoque,
 * para quem aprova comparar o pedido com o uso real.
 */
@Component({
  selector: 'app-consumo-anterior-tabela',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, CesCalloutComponent, CesStatusComponent, ConsumoAnteriorOperacoesComponent],
  template: `
    <section class="ca" aria-labelledby="ca-titulo">
      <header class="mb-3 border-l-2 border-primary-500 pl-3">
        <div class="flex items-center gap-2">
          <h3 id="ca-titulo" class="m-0 text-xs font-bold uppercase tracking-tight text-slate-800 dark:text-slate-200">Consumo desde a última solicitação</h3>
          <span class="text-xs text-slate-500 dark:text-slate-400 font-semibold">({{ linhas().length }})</span>
        </div>
        <p class="mt-0.5 mb-0 text-xs text-slate-500 dark:text-slate-400">
          O que {{ estoque() || 'o estoque' }} tinha, pegou no último pedido de cada material e aplicou em campo até {{ ate() ? (ate() | date: 'dd/MM/yyyy') : 'agora' }}. Clique em um material para ver a conta do saldo e cada operação por trás dela. Só para consulta: não sai na impressão.
        </p>
      </header>
      @if (carregando()) {
        <div class="ca-esqueletos" role="status" aria-label="Carregando o consumo"><span></span><span></span><span></span></div>
      } @else if (erro()) {
        <ces-callout tone="warning" title="Não foi possível carregar o consumo">{{ erro() }}</ces-callout>
      } @else {
        <div class="ca-tabela" role="table" aria-label="Consumo por material">
          <div class="ca-tabela__linha ca-tabela__cab" role="row">
            <span role="columnheader">Material</span>
            <span role="columnheader">Último pedido</span>
            <span role="columnheader" class="ca-num">Tinha</span>
            <span role="columnheader" class="ca-num">Pegou</span>
            <span role="columnheader" class="ca-num">Gastou</span>
            <span role="columnheader" class="ca-num">Devolveu</span>
            <span role="columnheader" class="ca-num">Tem agora</span>
            <span role="columnheader" class="ca-num">Pede agora</span>
          </div>
          @for (l of linhas(); track l.material.id) {
            @let aberto = abertos().has(l.material.id);
            <div class="ca-tabela__linha ca-tabela__linha--expansivel" role="row" [class.ca-tabela__linha--aberta]="aberto" (click)="alternar(l.material.id)">
              <span role="cell" class="ca-material">
                <button type="button" class="ca-material__botao" [attr.aria-expanded]="aberto" [attr.aria-controls]="'ca-ops-' + l.material.id"
                  [attr.aria-label]="(aberto ? 'Recolher' : 'Ver') + ' as operações de ' + l.material.descricao" (click)="$event.stopPropagation(); alternar(l.material.id)">
                  <i class="pi" [class.pi-chevron-down]="aberto" [class.pi-chevron-right]="!aberto" aria-hidden="true"></i>
                  <strong>{{ l.material.descricao }}</strong>
                </button>
                <span>{{ l.material.codigo }} · {{ l.material.unidadeMedida || 'UN' }}</span>
                @if (l.leitura.tom !== 'neutral') { <ces-status [tone]="l.leitura.tom" class="ca-dica">{{ l.leitura.dica }}</ces-status> }
              </span>
              @if (l.consumo?.ultimaSolicitacao; as u) {
                <span role="cell" data-rotulo="Último pedido">{{ u.dataEntrega | date: 'dd/MM/yyyy' }}</span>
                <span role="cell" class="ca-num" data-rotulo="Tinha">{{ l.consumo?.saldoAntes ?? 0 | number: '1.0-3' }}</span>
                <span role="cell" class="ca-num" data-rotulo="Pegou">{{ u.quantidadeRecebida | number: '1.0-3' }}</span>
                <span role="cell" class="ca-num" data-rotulo="Gastou">{{ l.consumo?.quantidadeAplicada ?? 0 | number: '1.0-3' }}</span>
                <span role="cell" class="ca-num" data-rotulo="Devolveu">{{ l.consumo?.quantidadeDevolvida ?? 0 | number: '1.0-3' }}</span>
              } @else {
                <span role="cell" class="ca-muted ca-primeiro-pedido">Primeiro pedido</span>
                <span role="cell" class="ca-num ca-muted ca-so-tela-larga">—</span>
                <span role="cell" class="ca-num ca-muted ca-so-tela-larga">—</span>
                <span role="cell" class="ca-num ca-muted ca-so-tela-larga">—</span>
                <span role="cell" class="ca-num ca-muted ca-so-tela-larga">—</span>
              }
              <span role="cell" class="ca-num" data-rotulo="Tem agora">{{ l.consumo?.saldoAtual ?? 0 | number: '1.0-3' }}</span>
              <span role="cell" class="ca-num ca-forte" data-rotulo="Pede agora">{{ l.quantidade | number: '1.0-3' }}</span>
            </div>
            @if (aberto && idSolicitacao(); as id) {
              <div class="ca-tabela__detalhe" role="row" [id]="'ca-ops-' + l.material.id">
                <div role="cell">
                  <app-consumo-anterior-operacoes [idSolicitacao]="id" [idMaterial]="l.material.id" [unidade]="l.material.unidadeMedida || 'UN'" />
                </div>
              </div>
            }
          } @empty {
            <p class="ca-muted ca-vazio">Sem materiais nesta solicitação.</p>
          }
        </div>
      }
    </section>
  `,
  styles: [`
    :host { display: block; }
    .ca { display: block; }
    .ca-tabela { border: 1px solid var(--ces-border); border-radius: var(--ces-radius-lg); background: var(--ces-surface); overflow: hidden; }
    .ca-tabela__linha { display: grid; grid-template-columns: minmax(12rem, 1fr) 6.5rem repeat(6, 4.75rem); gap: 0.5rem; align-items: center;
      padding: 0.625rem 0.875rem; font-size: var(--ces-font-caption); color: var(--ces-foreground); }
    .ca-tabela__linha + .ca-tabela__linha { border-top: 1px solid var(--ces-border); }
    .ca-tabela__linha--expansivel { cursor: pointer; }
    .ca-tabela__linha--expansivel:hover { background: var(--ces-surface-subtle); }
    .ca-tabela__linha--aberta { background: var(--ces-surface-subtle); }
    .ca-tabela__detalhe { padding: 0.75rem 0.875rem 0.875rem; border-top: 1px solid var(--ces-border); background: var(--ces-surface-subtle); }
    .ca-material__botao { display: inline-flex; align-items: baseline; gap: 0.4rem; padding: 0; border: 0; background: none; color: inherit; font: inherit;
      text-align: left; cursor: pointer; }
    .ca-material__botao i { font-size: 0.7rem; color: var(--ces-brand); }
    .ca-material__botao:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 2px; border-radius: var(--ces-radius-sm); }
    .ca-tabela__cab { background: var(--ces-surface-subtle); font-weight: 700; text-transform: uppercase; letter-spacing: 0.02em; color: var(--ces-foreground-muted); }
    .ca-material { display: grid; gap: 0.15rem; justify-items: start; min-width: 0; }
    .ca-material strong { font-weight: 600; }
    .ca-material > span { color: var(--ces-foreground-muted); }
    .ca-dica { margin-top: 0.2rem; }
    .ca-num { text-align: right; font-variant-numeric: tabular-nums; }
    .ca-forte { font-weight: 700; }
    .ca-muted { color: var(--ces-foreground-muted); }
    .ca-vazio { margin: 0; padding: 0.875rem; }
    .ca-esqueletos { display: grid; gap: 0.5rem; }
    .ca-esqueletos span { height: 2.25rem; border-radius: var(--ces-radius-md); background: var(--ces-surface-muted); }
    @media (max-width: 768px) {
      .ca-tabela__cab { display: none; }
      .ca-tabela__linha { grid-template-columns: repeat(3, minmax(0, 1fr)); }
      .ca-material { grid-column: 1 / -1; }
      .ca-num { text-align: left; }
      [data-rotulo]::before { content: attr(data-rotulo); display: block; font-weight: 400; color: var(--ces-foreground-muted); }
      .ca-primeiro-pedido { grid-column: 1 / -1; }
      .ca-so-tela-larga { display: none; }
    }
    /* Feedback rápido para quem aprova: fica fora do documento impresso. */
    @media print { :host { display: none; } }
  `]
})
export class ConsumoAnteriorTabelaComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);

  readonly idSolicitacao = input<string | null>(null);
  readonly itens = input<readonly ItemSolicitacaoResponse[]>([]);
  readonly estoque = input<string | null>(null);

  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly ate = signal<string | null>(null);
  private readonly porMaterial = signal<Record<string, ConsumoAnteriorItem>>({});

  /** Materiais com a linha aberta, mostrando as operações por trás dos números. */
  protected readonly abertos = signal<ReadonlySet<string>>(new Set());

  protected readonly linhas = computed<LinhaConsumo[]>(() => {
    const porMaterial = new Map<string, { material: ItemSolicitacaoResponse['material']; quantidade: number }>();
    for (const item of this.itens()) {
      const atual = porMaterial.get(item.material.id);
      porMaterial.set(item.material.id, { material: item.material, quantidade: (atual?.quantidade ?? 0) + (Number(item.quantidade) || 0) });
    }
    return Array.from(porMaterial.values()).map(({ material, quantidade }) => {
      const consumo = this.porMaterial()[material.id] ?? null;
      return { material, quantidade: Math.round(quantidade * 1000) / 1000, consumo, leitura: lerConsumo(consumo) };
    });
  });

  constructor() {
    effect(() => {
      const id = this.idSolicitacao();
      untracked(() => this.carregar(id));
    });
  }

  protected alternar(idMaterial: string): void {
    this.abertos.update((atual) => {
      const proximo = new Set(atual);
      if (!proximo.delete(idMaterial)) proximo.add(idMaterial);
      return proximo;
    });
  }

  private carregar(id: string | null): void {
    this.porMaterial.set({});
    this.abertos.set(new Set());
    this.erro.set(null);
    if (!id) return;
    this.carregando.set(true);
    this.almoxarifado.buscarConsumoAnterior(id).subscribe({
      next: (r) => {
        this.porMaterial.set(Object.fromEntries((r.itens ?? []).map((i) => [i.idMaterial, i])));
        this.ate.set(r.consultadoAte ?? null);
        this.carregando.set(false);
      },
      error: (e) => {
        this.erro.set(getErrorMessage(e));
        this.carregando.set(false);
      }
    });
  }
}
