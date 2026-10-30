import { DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  TemplateRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Observable, Subscription } from 'rxjs';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDrawerComponent,
} from '@ces/ui';

/** Indicador curto exibido no topo do recorte (vem do agregado que o dashboard já tem). */
export interface DrillKpi {
  readonly rotulo: string;
  readonly valor: string;
  readonly detalhe?: string;
}

/**
 * Recorte declarativo da camada 2 do drill-down: o dashboard informa o que mostrar e de onde carregar
 * (sempre uma listagem existente, com as mesmas permissões); o componente cuida de drawer, estados,
 * tabela analítica, CSV e "Ver tudo".
 */
export interface DrillRecorte<T = unknown> {
  /** Identidade do recorte: mudar a chave recarrega a lista. */
  readonly chave: string;
  readonly eyebrow: string;
  readonly titulo: string;
  readonly descricao?: string;
  readonly kpis?: readonly DrillKpi[];
  readonly colunas: readonly CesDataTableColumn<T>[];
  /** Primeira página da listagem filtrada (até ~100 linhas) e o total real. */
  readonly carregar: () => Observable<{ linhas: T[]; total: number }>;
  readonly rotuloLinha?: (linha: T) => string;
  readonly chaveLinha?: (linha: T, indice: number) => string;
  readonly rotuloAcao?: string;
  readonly abrirLinha?: (linha: T) => void;
  readonly rotuloVerTudo?: string;
  readonly verTudo?: () => void;
  /** Aviso sobre o recorte (ex.: filtro do painel que a listagem não suporta). */
  readonly aviso?: string;
  readonly vazio?: string;
  readonly nomeArquivo?: string;
  /** Células customizadas por chave de coluna. */
  readonly celulas?: Readonly<Record<string, TemplateRef<{ $implicit: T }>>>;
}

@Component({
  selector: 'app-dashboard-drill',
  standalone: true,
  imports: [
    DecimalPipe, NgTemplateOutlet, CesAsyncStateComponent, CesButtonComponent, CesCellDirective,
    CesDataTableComponent, CesDrawerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .drill-kpis {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(7.5rem, 1fr));
      gap: 0.5rem;
    }
    .drill-kpi {
      display: grid;
      gap: 0.15rem;
      padding: 0.625rem 0.75rem;
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-md);
      background: var(--ces-surface-subtle);
    }
    .drill-kpi span, .drill-kpi small { color: var(--ces-foreground-muted); font-size: var(--ces-font-caption); }
    .drill-kpi strong { color: var(--ces-foreground); font-size: 1.125rem; font-variant-numeric: tabular-nums; }
    .drill-nota {
      display: flex;
      gap: 0.5rem;
      margin: 0;
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption);
      line-height: 1.45;
    }
  `,
  template: `
    @if (recorte(); as r) {
      <ces-drawer
        [visible]="true"
        (visibleChange)="!$event && fechar.emit()"
        [eyebrow]="r.eyebrow"
        [title]="r.titulo"
        [description]="r.descricao ?? 'Recorte com os filtros aplicados no painel.'"
        size="lg"
      >
        @if (r.kpis?.length) {
          <section class="drill-kpis" aria-label="Indicadores do recorte">
            @for (kpi of r.kpis; track kpi.rotulo) {
              <div class="drill-kpi">
                <span>{{ kpi.rotulo }}</span>
                <strong>{{ kpi.valor }}</strong>
                @if (kpi.detalhe) { <small>{{ kpi.detalhe }}</small> }
              </div>
            }
          </section>
        }
        @if (r.aviso) {
          <p class="drill-nota"><i class="pi pi-filter" aria-hidden="true"></i>{{ r.aviso }}</p>
        }

        @if (erro()) {
          <ces-async-state state="error" title="Não foi possível carregar o recorte"
            description="Tente novamente; os filtros do painel continuam aplicados."
            actionLabel="Tentar novamente" actionIcon="pi pi-refresh" (action)="carregar()"></ces-async-state>
        } @else {
          <ces-data-table
            [caption]="r.titulo"
            [columns]="$any(r.colunas)"
            [rows]="linhas()"
            [state]="carregando() ? 'loading' : 'ready'"
            [stateTitle]="r.vazio ?? 'Nenhum registro neste recorte'"
            [rowKey]="$any(r.chaveLinha ?? chavePadrao)"
            [rowLabel]="$any(r.rotuloLinha)"
            [rowActionLabel]="r.abrirLinha ? (r.rotuloAcao ?? 'Abrir') : undefined"
            [exportFileName]="r.nomeArquivo"
            (rowActivate)="r.abrirLinha?.($any($event))"
          >
            @for (coluna of colunasCustomizadas(); track coluna) {
              <ng-template [cesCell]="coluna" let-row>
                <ng-container *ngTemplateOutlet="r.celulas![coluna]; context: { $implicit: row }"></ng-container>
              </ng-template>
            }
          </ces-data-table>
          @if (total() > linhas().length) {
            <p class="drill-nota">Mostrando {{ linhas().length }} de {{ total() | number }}. Use “{{ r.rotuloVerTudo ?? 'Ver todos na listagem' }}” para o restante.</p>
          }
        }

        @if (r.verTudo) {
          <ces-button drawer-footer variant="outline" icon="pi pi-external-link"
            [label]="r.rotuloVerTudo ?? 'Ver todos na listagem'" (pressed)="r.verTudo!()" />
        }
        <ces-button drawer-footer label="Fechar" (pressed)="fechar.emit()" />
      </ces-drawer>
    }
  `,
})
export class DashboardDrillComponent {
  private assinatura?: Subscription;

  readonly recorte = input<DrillRecorte<any> | null>(null);
  readonly fechar = output<void>();

  protected readonly carregando = signal(false);
  protected readonly erro = signal(false);
  protected readonly linhas = signal<unknown[]>([]);
  protected readonly total = signal(0);
  protected readonly chavePadrao = (linha: { id?: unknown } | null, indice: number) => String(linha?.id ?? indice);
  protected readonly colunasCustomizadas = computed(() => Object.keys(this.recorte()?.celulas ?? {}));

  private ultimaChave: string | null = null;

  constructor() {
    effect(() => {
      const chave = this.recorte()?.chave ?? null;
      untracked(() => {
        if (chave === this.ultimaChave) return;
        this.ultimaChave = chave;
        if (chave) this.carregar();
        else this.assinatura?.unsubscribe();
      });
    });
    inject(DestroyRef).onDestroy(() => this.assinatura?.unsubscribe());
  }

  carregar(): void {
    const recorte = this.recorte();
    if (!recorte) return;
    this.assinatura?.unsubscribe();
    this.carregando.set(true);
    this.erro.set(false);
    this.linhas.set([]);
    this.total.set(0);
    this.assinatura = recorte.carregar().subscribe({
      next: ({ linhas, total }) => {
        this.linhas.set(linhas);
        this.total.set(total);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }
}
