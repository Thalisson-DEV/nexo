import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, model, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Subscription, debounceTime, distinctUntilChanged, skip } from 'rxjs';
import { CesButtonComponent, CesTextInputComponent } from '@ces/ui';

import { SaldoSerieResponse } from '../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { SeriesGradeComponent, SerieGradeItem } from '../series-grade/series-grade.component';

/** Números de série por página: cabem em poucas linhas da grade sem empurrar o resto do relatório. */
const TAMANHO_PAGINA = 60;

/**
 * Números de série de um material seriado no estoque, mostrados dentro da linha expandida do
 * relatório de saldo (antes era um diálogo). Carrega ao abrir, filtra no servidor e pagina com
 * "Mostrar mais".
 */
@Component({
  selector: 'app-saldo-seriais-painel',
  standalone: true,
  imports: [DecimalPipe, CesButtonComponent, CesTextInputComponent, SeriesGradeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './saldo-seriais-painel.component.css',
  template: `
    <section class="seriais" [attr.aria-label]="'Números de série de ' + descricao()" [attr.aria-busy]="carregando()">
      <header class="seriais__topo">
        <div class="seriais__titulo">
          <i class="pi pi-barcode" aria-hidden="true"></i>
          <span>Números de série</span>
          @if (total() !== null) {
            <span class="seriais__contagem">
              {{ total() }} {{ busca() ? (total() === 1 ? 'encontrado' : 'encontrados') : (total() === 1 ? 'registrado' : 'registrados') }}
              @if (!busca() && disponiveis() !== null && disponiveis() !== total()) {
                · {{ disponiveis() }} {{ disponiveis() === 1 ? 'disponível' : 'disponíveis' }}
              }
            </span>
          }
        </div>
        @if (mostrarBusca()) {
          <div class="seriais__busca print:hidden">
            <ces-text-input
              label="Filtrar números de série"
              [labelVisible]="false"
              placeholder="Filtrar número de série..."
              [(value)]="busca"
            />
          </div>
        }
      </header>

      @if (divergencia(); as d) {
        <p class="seriais__aviso" role="note">
          <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
          <span>
            O saldo é de <strong>{{ d.quantidade | number: '1.0-3' }}</strong>, mas há
            <strong>{{ d.series }}</strong> {{ d.series === 1 ? 'número de série disponível' : 'números de série disponíveis' }}.
            Confira as entradas e baixas deste material.
          </span>
        </p>
      }

      @if (erro()) {
        <div class="seriais__estado" role="alert">
          <span>{{ erro() }}</span>
          <ces-button label="Tentar novamente" icon="pi pi-refresh" size="sm" variant="outline" (pressed)="recarregar()" />
        </div>
      } @else if (carregando() && !seriais().length) {
        <ul class="seriais__esqueleto" aria-hidden="true">
          @for (i of esqueleto; track i) {
            <li></li>
          }
        </ul>
      } @else if (!seriais().length) {
        <p class="seriais__estado">
          {{ busca() ? 'Nenhum número de série contém "' + busca() + '".' : 'Nenhum número de série registrado para este material no estoque.' }}
        </p>
      } @else {
        <app-series-grade [series]="grade()" [rotulo]="'Números de série de ' + descricao()" />
        @if (restantes() > 0) {
          <div class="seriais__mais print:hidden">
            <ces-button
              [label]="'Mostrar mais ' + proximos() + ' de ' + restantes()"
              icon="pi pi-angle-down"
              size="sm"
              variant="ghost"
              [loading]="carregando()"
              (pressed)="carregarMais()"
            />
          </div>
        }
      }
    </section>
  `,
})
export class SaldoSeriaisPainelComponent implements OnInit {
  private readonly service = inject(AlmoxarifadoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly idEstoque = input.required<string>();
  readonly idMaterial = input.required<string>();
  readonly descricao = input('');
  /** Condição do saldo (NOVO/SUCATA): cada linha do relatório tem as próprias séries. */
  readonly condicao = input<string | undefined>();
  /** Quantidade em saldo, para apontar quando não bate com as séries disponíveis. */
  readonly quantidade = input<number | null>(null);

  protected readonly busca = model('');
  protected readonly seriais = signal<SaldoSerieResponse[]>([]);
  protected readonly total = signal<number | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly esqueleto = [1, 2, 3, 4, 5, 6];

  private pagina = 0;
  private totalSemBusca: number | null = null;
  private requisicao?: Subscription;

  protected readonly grade = computed<SerieGradeItem[]>(() =>
    this.seriais().map((s) => ({ numSerie: s.numSerie, indisponivel: !s.ativo }))
  );
  protected readonly restantes = computed(() => Math.max((this.total() ?? 0) - this.seriais().length, 0));
  protected readonly proximos = computed(() => Math.min(this.restantes(), TAMANHO_PAGINA));
  /** Séries disponíveis (ativas) do material nesta condição, independente da busca. */
  protected readonly disponiveis = signal<number | null>(null);
  /** A busca só ajuda quando a lista não cabe de relance. */
  protected readonly mostrarBusca = signal(false);

  protected readonly divergencia = computed(() => {
    const quantidade = this.quantidade();
    const series = this.disponiveis();
    if (quantidade === null || series === null || quantidade === series) return null;
    return { quantidade, series };
  });

  constructor() {
    toObservable(this.busca)
      .pipe(skip(1), debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => this.carregar(0));
  }

  ngOnInit(): void {
    this.carregar(0);
    this.service
      .listarSaldosSeriais({ ...this.filtroBase(), ativo: true, page: 0, size: 1 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (res) => this.disponiveis.set(res.page.totalElements), error: () => this.disponiveis.set(null) });
  }

  private filtroBase() {
    return { idEstoque: this.idEstoque(), idMaterial: this.idMaterial(), condicao: this.condicao() || undefined };
  }

  protected carregarMais(): void {
    this.carregar(this.pagina + 1);
  }

  protected recarregar(): void {
    this.carregar(this.seriais().length ? this.pagina + 1 : 0);
  }

  private carregar(pagina: number): void {
    this.requisicao?.unsubscribe();
    this.carregando.set(true);
    this.erro.set(null);
    const termo = this.busca().trim();

    this.requisicao = this.service
      .listarSaldosSeriais({
        ...this.filtroBase(),
        numSerie: termo || undefined,
        page: pagina,
        size: TAMANHO_PAGINA,
        sort: 'numSerie,asc',
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.pagina = pagina;
          this.seriais.update((atuais) => (pagina === 0 ? res.content : [...atuais, ...res.content]));
          this.total.set(res.page.totalElements);
          if (!termo) this.totalSemBusca = res.page.totalElements;
          this.mostrarBusca.set((this.totalSemBusca ?? 0) > 12);
          this.carregando.set(false);
        },
        error: (err) => {
          this.erro.set(getErrorMessage(err) || 'Não foi possível carregar os números de série.');
          this.carregando.set(false);
        },
      });
  }
}
