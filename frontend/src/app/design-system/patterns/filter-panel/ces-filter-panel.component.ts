import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  untracked
} from '@angular/core';
import { CES_LIST_SHORTCUTS } from '../../core/shortcuts';
import { CesButtonComponent } from '../../primitives/button';

let filterPanelId = 0;

/** Filtro aplicado, exibido como chip no resumo do painel. */
export interface CesFilterChip {
  readonly key: string;
  readonly label: string;
  /** Padrão `true`. Um chip não removível só informa (ex.: recorte obrigatório). */
  readonly removable?: boolean;
}

function readExpanded(key: string | undefined): boolean | null {
  if (!key) return null;
  try {
    const valor = localStorage.getItem(`ces.filters.${key}.expanded`);
    return valor === null ? null : valor === 'true';
  } catch {
    return null;
  }
}

function writeExpanded(key: string | undefined, value: boolean): void {
  if (!key) return;
  try {
    localStorage.setItem(`ces.filters.${key}.expanded`, String(value));
  } catch {
    // preferência apenas em memória
  }
}

const FIELD_SELECTOR =
  'input:not([type=hidden]):not([disabled]), select:not([disabled]), textarea:not([disabled]), [role=combobox]:not([aria-disabled=true])';

/**
 * Painel de filtros estruturados de listagens e painéis. Abre recolhido e mostra o resumo do que
 * está aplicado em chips removíveis; "Aplicar filtros" e Enter num campo aplicam; "Limpar filtros"
 * volta aos padrões da tela (a contagem considera só o que difere do padrão).
 */
@Component({
  selector: 'ces-filter-panel',
  standalone: true,
  imports: [CesButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'ces-filter-panel' },
  styles: `
    :host {
      display: block;
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-lg);
      background: var(--ces-surface);
      box-shadow: var(--ces-shadow-sm);
    }

    .ces-filter-panel__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.75rem 1rem;
    }

    .ces-filter-panel__heading,
    .ces-filter-panel__header-actions,
    .ces-filter-panel__title-row {
      display: flex;
      align-items: center;
    }

    .ces-filter-panel__heading {
      min-width: 0;
      gap: 0.75rem;
    }

    .ces-filter-panel__header-actions,
    .ces-filter-panel__title-row {
      gap: 0.5rem;
    }

    .ces-filter-panel__icon {
      display: grid;
      width: 2rem;
      height: 2rem;
      place-items: center;
      flex: 0 0 auto;
      border-radius: var(--ces-radius-sm);
      background: var(--ces-brand-soft);
      color: var(--ces-brand);
    }

    h2,
    p {
      margin: 0;
    }

    h2 {
      color: var(--ces-foreground);
      font-size: var(--ces-font-title);
    }

    p {
      margin-top: 0.2rem;
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption);
      line-height: 1.45;
    }

    .ces-filter-panel__toggle {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      min-height: var(--ces-control-height-sm);
      padding: 0 0.625rem;
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-sm);
      background: transparent;
      color: var(--ces-foreground-muted);
      font: inherit;
      font-size: var(--ces-font-control);
      font-weight: 600;
      cursor: pointer;
    }

    .ces-filter-panel__toggle:hover {
      background: var(--ces-surface-muted);
      color: var(--ces-foreground);
    }

    .ces-filter-panel__toggle:focus-visible {
      outline: 2px solid var(--ces-focus-ring);
      outline-offset: 2px;
    }

    .ces-filter-panel__count {
      border-radius: 999px;
      padding: 0.2rem 0.5rem;
      background: var(--ces-brand-soft);
      color: var(--ces-brand);
      font-size: var(--ces-font-caption);
      font-weight: 700;
    }

    .ces-filter-panel__summary {
      display: flex;
      flex-wrap: wrap;
      gap: 0.375rem;
      margin: 0;
      padding: 0 1rem 0.75rem;
      list-style: none;
    }

    .ces-filter-panel__chip {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      min-height: 1.75rem;
      max-width: 100%;
      padding: 0 0.625rem;
      border: 1px solid var(--ces-border);
      border-radius: 999px;
      background: var(--ces-surface-subtle);
      color: var(--ces-foreground);
      font-size: var(--ces-font-caption);
      font-weight: 600;
    }

    .ces-filter-panel__chip[data-removable='true'] {
      padding-right: 0.125rem;
      border-color: color-mix(in srgb, var(--ces-brand) 35%, var(--ces-border));
      background: var(--ces-brand-soft);
    }

    .ces-filter-panel__chip span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .ces-filter-panel__chip button {
      display: inline-grid;
      flex: 0 0 auto;
      place-items: center;
      width: 1.5rem;
      height: 1.5rem;
      border: 0;
      border-radius: 999px;
      background: none;
      color: var(--ces-foreground-muted);
      cursor: pointer;
    }

    .ces-filter-panel__chip button:hover {
      background: var(--ces-surface-muted);
      color: var(--ces-foreground);
    }

    .ces-filter-panel__chip button:focus-visible {
      outline: 2px solid var(--ces-focus-ring);
      outline-offset: 1px;
    }

    .ces-filter-panel__content {
      border-top: 1px solid var(--ces-border);
      padding: 1rem;
    }

    .ces-filter-panel__fields {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr));
      gap: 0.875rem;
    }

    .ces-filter-panel__footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.5rem;
      margin-top: 1rem;
      padding-top: 0.875rem;
      border-top: 1px solid var(--ces-border);
    }

    @media (max-width: 40rem) {
      .ces-filter-panel__header {
        align-items: flex-start;
      }

      .ces-filter-panel__title-row {
        align-items: flex-start;
        flex-direction: column;
      }

      .ces-filter-panel__toggle-text {
        display: none;
      }

      .ces-filter-panel__chip button {
        width: 2.25rem;
        height: 2.25rem;
      }

      .ces-filter-panel__footer {
        align-items: stretch;
        flex-direction: column-reverse;
      }
    }
  `,
  template: `
    <section [attr.aria-labelledby]="headingId()" [attr.aria-busy]="loading()">
      <header class="ces-filter-panel__header">
        <div class="ces-filter-panel__heading">
          <span class="ces-filter-panel__icon" aria-hidden="true"><i class="pi pi-filter"></i></span>
          <div>
            <div class="ces-filter-panel__title-row">
              <h2 [id]="headingId()">{{ title() }}</h2>
              @if (activeCount() > 0) {
                <span class="ces-filter-panel__count">
                  {{ activeCount() }} {{ activeCount() === 1 ? 'filtro ativo' : 'filtros ativos' }}
                </span>
              }
            </div>
            @if (description()) {
              <p>{{ description() }}</p>
            }
          </div>
        </div>

        <div class="ces-filter-panel__header-actions">
          <ng-content select="[filter-actions]"></ng-content>
          @if (collapsible()) {
            <button
              type="button"
              class="ces-filter-panel__toggle"
              [attr.aria-label]="toggleLabel()"
              [attr.aria-expanded]="expanded()"
              [attr.aria-controls]="contentId()"
              [attr.aria-keyshortcuts]="keyShortcut() || null"
              [title]="keyShortcut() ? toggleLabel() + ' (' + keyShortcut() + ')' : toggleLabel()"
              (click)="toggle()"
            >
              <i [class]="expanded() ? 'pi pi-chevron-up' : 'pi pi-sliders-h'" aria-hidden="true"></i>
              <span class="ces-filter-panel__toggle-text" aria-hidden="true">{{ toggleLabel() }}</span>
            </button>
          }
        </div>
      </header>

      @if (summary().length) {
        <ul class="ces-filter-panel__summary" aria-label="Filtros aplicados">
          @for (chip of summary(); track chip.key; let i = $index) {
            <li class="ces-filter-panel__chip" [attr.data-removable]="chip.removable !== false">
              <span [title]="chip.label">{{ chip.label }}</span>
              @if (chip.removable !== false) {
                <button
                  type="button"
                  class="ces-filter-panel__chip-remove"
                  [attr.aria-label]="'Remover filtro ' + chip.label"
                  (click)="removeChip(chip.key, i)"
                >
                  <i class="pi pi-times" aria-hidden="true"></i>
                </button>
              }
            </li>
          }
        </ul>
      }

      @if (expanded() || !collapsible()) {
        <div [id]="contentId()" class="ces-filter-panel__content" (keydown.enter)="onEnter($event)">
          <div class="ces-filter-panel__fields">
            <ng-content></ng-content>
          </div>
          @if (showFooter()) {
            <footer class="ces-filter-panel__footer">
              <ces-button
                [label]="clearLabel()"
                icon="pi pi-filter-slash"
                variant="ghost"
                size="sm"
                [disabled]="activeCount() === 0 || loading()"
                (pressed)="cleared.emit()"
              ></ces-button>
              <ces-button
                [label]="applyLabel()"
                icon="pi pi-search"
                size="sm"
                [loading]="loading()"
                (pressed)="applied.emit()"
              ></ces-button>
            </footer>
          }
        </div>
      }
    </section>
  `
})
export class CesFilterPanelComponent {
  private readonly instanceId = ++filterPanelId;
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly shortcuts = inject(CES_LIST_SHORTCUTS, { optional: true });

  readonly title = input('Filtros');
  readonly description = input<string | undefined>();
  /** Filtros diferentes do padrão da tela (use `cesListState().activeCount()`). */
  readonly activeCount = input(0);
  readonly collapsible = input(true);
  readonly showFooter = input(true);
  readonly loading = input(false);
  readonly applyLabel = input('Aplicar filtros');
  readonly clearLabel = input('Limpar filtros');
  /** Recolhido por padrão: o resumo de chips mostra o que está aplicado. */
  readonly expanded = model(false);
  /** Chave para lembrar, neste dispositivo, se o painel fica expandido. */
  readonly storageKey = input<string | undefined>();
  /** Resumo dos filtros aplicados (inclusive padrões), exibido em chips. */
  readonly summary = input<readonly CesFilterChip[]>([]);

  readonly applied = output<void>();
  readonly cleared = output<void>();
  /** Chip removido pelo "x": a tela volta o filtro ao padrão e recarrega. */
  readonly chipRemoved = output<string>();

  protected readonly headingId = computed(() => `ces-filter-panel-${this.instanceId}-heading`);
  protected readonly contentId = computed(() => `ces-filter-panel-${this.instanceId}-content`);
  /** Atalho anunciado no botão do painel quando a página usa `cesListShortcuts`. */
  protected readonly keyShortcut = computed(() => (this.shortcuts?.enabled() ? 'F' : undefined));
  protected readonly toggleLabel = computed(() => (this.expanded() ? 'Recolher filtros' : 'Expandir filtros'));

  constructor() {
    effect(() => {
      const lembrado = readExpanded(this.storageKey());
      if (lembrado !== null) untracked(() => this.expanded.set(lembrado));
    });
  }

  toggle(): void {
    this.setExpanded(!this.expanded());
  }

  /** Expande o painel e leva o foco ao primeiro campo ("Ajustar filtros"). */
  expandAndFocus(): void {
    this.setExpanded(true);
    afterNextRender(
      () => {
        const campo = this.host.nativeElement.querySelector<HTMLElement>(
          FIELD_SELECTOR.split(', ')
            .map((seletor) => `.ces-filter-panel__fields ${seletor}`)
            .join(', ')
        );
        campo?.focus();
      },
      { injector: this.injector }
    );
  }

  focusToggle(): void {
    this.host.nativeElement.querySelector<HTMLElement>('.ces-filter-panel__toggle')?.focus();
  }

  protected setExpanded(value: boolean): void {
    this.expanded.set(value);
    writeExpanded(this.storageKey(), value);
  }

  protected removeChip(key: string, index: number): void {
    this.chipRemoved.emit(key);
    // O resumo é recalculado pela tela; o foco segue para o próximo chip ou para o botão do painel.
    afterNextRender(
      () => {
        const botoes = this.host.nativeElement.querySelectorAll<HTMLElement>('.ces-filter-panel__chip-remove');
        const proximo = botoes[Math.min(index, botoes.length - 1)];
        if (proximo) proximo.focus();
        else this.focusToggle();
      },
      { injector: this.injector }
    );
  }

  /** Enter num campo aplica os filtros, exceto em áreas de texto, botões e listas abertas. */
  protected onEnter(event: Event): void {
    const alvo = event.target as HTMLElement | null;
    if (event.defaultPrevented || !alvo) return;
    if (alvo.closest('textarea, button, [contenteditable=""], [contenteditable="true"]')) return;
    if (alvo.getAttribute('aria-expanded') === 'true') return;
    event.preventDefault();
    this.applied.emit();
  }
}
