import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CesButtonComponent, CesCalloutComponent, CesStatusComponent } from '@ces/ui';

import { ConsumoAnteriorDetalheResponse } from '../../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { SeriesGradeComponent } from '../../../../shared/components/series-grade/series-grade.component';
import { agruparOperacoesConsumo } from './consumo-anterior.util';

/**
 * Como o consumo de um material foi calculado: a conta do saldo novo no período e cada operação
 * por trás dela (entregas, notas, devoluções, transferências), com as séries de cada uma.
 */
@Component({
  selector: 'app-consumo-anterior-operacoes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, RouterLink, CesButtonComponent, CesCalloutComponent, CesStatusComponent, SeriesGradeComponent],
  template: `
    @if (carregando()) {
      <div class="co-esqueletos" role="status" aria-label="Carregando as operações do material"><span></span><span></span><span></span></div>
    } @else if (erro()) {
      <ces-callout tone="warning" title="Não foi possível carregar as operações">
        {{ erro() }}
        <ces-button label="Tentar de novo" icon="pi pi-refresh" variant="ghost" size="sm" (pressed)="carregar()" />
      </ces-callout>
    } @else if (detalhe(); as d) {
      @if (!d.inicio) {
        <p class="co-vazio">Primeiro pedido deste material para o estoque: não há período anterior para detalhar.</p>
      } @else {
        @if (d.semSolicitacaoAnterior) {
          <p class="co-nota">Primeiro pedido deste material para o estoque. Abaixo, como o saldo chegou ao que é nos últimos 90 dias.</p>
        }
        <dl class="co-conta" [attr.aria-label]="'Conta do saldo novo de ' + (d.inicio | date: 'dd/MM/yyyy') + ' a ' + (d.fim | date: 'dd/MM/yyyy')">
          <div class="co-conta__parcela">
            <dt>{{ d.semSolicitacaoAnterior ? 'Saldo em' : 'Saldo antes de' }} {{ d.inicio | date: 'dd/MM' }}</dt>
            <dd>{{ d.saldoInicial ?? 0 | number: '1.0-3' }}</dd>
          </div>
          @for (s of naConta(); track s.grupo) {
            <div class="co-conta__parcela">
              <dt>{{ s.titulo }}</dt>
              <dd>{{ s.total > 0 ? '+' : '−' }}{{ abs(s.total) | number: '1.0-3' }}</dd>
            </div>
          }
          <div class="co-conta__parcela co-conta__parcela--total">
            <dt>Saldo em {{ d.fim | date: 'dd/MM' }}</dt>
            <dd>{{ d.saldoFinal ?? 0 | number: '1.0-3' }} {{ unidade() }}</dd>
          </div>
        </dl>
        @if (d.saldoFinal !== d.saldoAtual) {
          <p class="co-nota">Hoje o saldo é {{ d.saldoAtual | number: '1.0-3' }} {{ unidade() }}: inclui o que entrou ou saiu depois de {{ d.fim | date: 'dd/MM/yyyy HH:mm' }}, como a entrega desta solicitação.</p>
        }

        @for (s of secoes(); track s.grupo) {
          <section class="co-secao" [attr.aria-label]="s.titulo">
            <header class="co-secao__cab">
              <i [class]="s.icone" aria-hidden="true"></i>
              <h4>{{ s.titulo }}</h4>
              <span class="co-muted">{{ s.linhas.length }} {{ s.linhas.length === 1 ? 'operação' : 'operações' }}</span>
              @if (s.foraDaConta) {
                <ces-status tone="neutral" icon="pi pi-info-circle">Fora da conta do saldo novo</ces-status>
              }
              <strong class="co-secao__total">{{ s.total > 0 ? '+' : '−' }}{{ abs(s.total) | number: '1.0-3' }} {{ unidade() }}</strong>
            </header>
            <ul class="co-linhas">
              @for (l of s.linhas; track l.chave) {
                <li class="co-linha">
                  <span class="co-linha__data">{{ l.dataHora | date: 'dd/MM/yy HH:mm' }}</span>
                  <span class="co-linha__desc">
                    @if (l.link) {
                      <a [routerLink]="l.link" target="_blank" rel="noopener" [attr.aria-label]="l.titulo + ' (abre em nova aba)'">{{ l.titulo }}<i class="pi pi-external-link" aria-hidden="true"></i></a>
                    } @else {
                      <span>{{ l.titulo }}</span>
                    }
                    @if (l.detalhe) { <small>{{ l.detalhe }}</small> }
                  </span>
                  <span class="co-linha__series">
                    @if (l.series.length === 1) {
                      <span class="co-serie"><i class="pi pi-barcode" aria-hidden="true"></i>{{ l.series[0] }}</span>
                    } @else if (l.series.length > 1) {
                      <button type="button" class="co-serie co-serie--botao" [attr.aria-expanded]="seriesAbertas().has(l.chave)" (click)="alternarSeries(l.chave)">
                        <i class="pi pi-barcode" aria-hidden="true"></i>{{ l.series.length }} séries
                        <i class="pi" [class.pi-chevron-up]="seriesAbertas().has(l.chave)" [class.pi-chevron-down]="!seriesAbertas().has(l.chave)" aria-hidden="true"></i>
                      </button>
                    }
                  </span>
                  <span class="co-linha__qtd">{{ l.quantidade > 0 ? '+' : '−' }}{{ abs(l.quantidade) | number: '1.0-3' }}</span>
                  @if (l.series.length > 1) {
                    <div class="co-linha__grade" [class.co-linha__grade--aberta]="seriesAbertas().has(l.chave)">
                      <app-series-grade [series]="grade(l.series)" [rotulo]="'Números de série de ' + l.titulo" />
                    </div>
                  }
                </li>
              }
            </ul>
          </section>
        } @empty {
          <p class="co-vazio">Nenhuma movimentação deste material no período.</p>
        }
      }
    }
  `,
  styles: [`
    :host { display: grid; gap: 0.75rem; }
    .co-muted { color: var(--ces-foreground-muted); }
    .co-vazio, .co-nota { margin: 0; font-size: var(--ces-font-caption); color: var(--ces-foreground-muted); }
    .co-conta { display: flex; flex-wrap: wrap; gap: 0.375rem; margin: 0; }
    .co-conta__parcela { display: grid; gap: 0.1rem; padding: 0.375rem 0.625rem; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface); }
    .co-conta__parcela dt { font-size: var(--ces-font-caption); color: var(--ces-foreground-muted); }
    .co-conta__parcela dd { margin: 0; font-weight: 700; font-variant-numeric: tabular-nums; color: var(--ces-foreground); }
    .co-conta__parcela--total { border-color: var(--ces-brand); background: var(--ces-brand-soft); }
    .co-secao { border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface); overflow: hidden; }
    .co-secao__cab { display: flex; flex-wrap: wrap; align-items: center; gap: 0.25rem 0.5rem; padding: 0.5rem 0.75rem; background: var(--ces-surface-subtle);
      border-bottom: 1px solid var(--ces-border); font-size: var(--ces-font-caption); }
    .co-secao__cab > i { color: var(--ces-foreground-muted); }
    .co-secao__cab h4 { margin: 0; font-size: var(--ces-font-caption); font-weight: 700; text-transform: uppercase; letter-spacing: 0.02em; color: var(--ces-foreground); }
    .co-secao__total { margin-left: auto; font-variant-numeric: tabular-nums; color: var(--ces-foreground); }
    .co-linhas { list-style: none; margin: 0; padding: 0; }
    .co-linha { display: grid; grid-template-columns: 6.75rem minmax(0, 1fr) auto 4.5rem; gap: 0.25rem 0.75rem; align-items: center;
      padding: 0.5rem 0.75rem; font-size: var(--ces-font-caption); color: var(--ces-foreground); }
    .co-linha + .co-linha { border-top: 1px solid var(--ces-border); }
    .co-linha__data { color: var(--ces-foreground-muted); font-variant-numeric: tabular-nums; }
    .co-linha__desc { display: grid; gap: 0.1rem; min-width: 0; }
    .co-linha__desc a { display: inline-flex; align-items: center; gap: 0.3rem; width: fit-content; font-weight: 600; color: var(--ces-brand); text-decoration: none; }
    .co-linha__desc a:hover { text-decoration: underline; }
    .co-linha__desc a:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 2px; border-radius: var(--ces-radius-sm); }
    .co-linha__desc a i { font-size: 0.65rem; }
    .co-linha__desc small { color: var(--ces-foreground-muted); font-size: var(--ces-font-caption); }
    .co-linha__qtd { text-align: right; font-weight: 700; font-variant-numeric: tabular-nums; }
    .co-serie { display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.1rem 0.45rem; border-radius: var(--ces-radius-sm);
      background: var(--ces-surface-muted); color: var(--ces-foreground); font-variant-numeric: tabular-nums; font-size: var(--ces-font-caption); }
    .co-serie--botao { border: 1px solid var(--ces-border); cursor: pointer; font: inherit; font-size: var(--ces-font-caption); }
    .co-serie--botao:hover { background: var(--ces-surface-subtle); }
    .co-serie--botao:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 2px; }
    .co-linha__grade { grid-column: 1 / -1; display: none; padding-top: 0.25rem; }
    .co-linha__grade--aberta { display: block; }
    .co-esqueletos { display: grid; gap: 0.5rem; }
    .co-esqueletos span { height: 2rem; border-radius: var(--ces-radius-md); background: var(--ces-surface-muted); }
    @media (max-width: 640px) {
      .co-linha { grid-template-columns: minmax(0, 1fr) auto; }
      .co-linha__data { grid-column: 1 / -1; }
      .co-linha__series { grid-column: 1 / -1; grid-row: 3; }
    }
    @media print {
      .co-linha__grade { display: block; }
      .co-serie--botao i.pi-chevron-up, .co-serie--botao i.pi-chevron-down { display: none; }
    }
  `]
})
export class ConsumoAnteriorOperacoesComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);

  readonly idSolicitacao = input.required<string>();
  readonly idMaterial = input.required<string>();
  readonly unidade = input('UN');

  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly detalhe = signal<ConsumoAnteriorDetalheResponse | null>(null);
  protected readonly seriesAbertas = signal<ReadonlySet<string>>(new Set());

  protected readonly secoes = computed(() => agruparOperacoesConsumo(this.detalhe()?.movimentacoes));
  protected readonly naConta = computed(() => this.secoes().filter((s) => !s.foraDaConta && s.total !== 0));

  constructor() {
    effect(() => {
      this.idSolicitacao();
      this.idMaterial();
      untracked(() => this.carregar());
    });
  }

  protected carregar(): void {
    this.erro.set(null);
    this.carregando.set(true);
    this.almoxarifado.buscarConsumoAnteriorDetalhe(this.idSolicitacao(), this.idMaterial()).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.carregando.set(false);
      },
      error: (e) => {
        this.erro.set(getErrorMessage(e));
        this.carregando.set(false);
      }
    });
  }

  protected alternarSeries(chave: string): void {
    this.seriesAbertas.update((atual) => {
      const proximo = new Set(atual);
      if (!proximo.delete(chave)) proximo.add(chave);
      return proximo;
    });
  }

  protected grade(series: readonly string[]) {
    return series.map((numSerie) => ({ numSerie }));
  }

  protected abs(n: number): number {
    return Math.abs(n);
  }
}
