import { CommonModule, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  signal,
  untracked
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesIconButtonComponent,
  CesStatusComponent,
  CesTextInputComponent
} from '../../index';
import { getErrorMessage } from '../../../core/models/shared.models';
import {
  CesReportNavigatorCriteria,
  CesReportNavigatorDataSource,
  CesReportNavigatorItem,
  CesReportNavigatorItemId,
  CesReportNavigatorOption
} from './report-navigator.models';

@Component({
  selector: 'ces-report-navigator',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    CesAsyncStateComponent,
    CesIconButtonComponent,
    CesStatusComponent,
    CesTextInputComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-report-nav-host print:hidden',
    '[class.ces-report-nav-host--open]': 'opened()'
  },
  template: `
    <!-- Container Sidebar Fixo na Lateral Esquerda -->
    <aside
      class="ces-report-nav-sidebar"
      [class.ces-report-nav-sidebar--open]="opened()"
      [attr.aria-label]="title()"
      [attr.aria-busy]="loading()"
    >
      @if (!opened()) {
        <!-- ==================================================================
             ESTADO RECOLHIDO: TODA A SIDEBAR É CLICÁVEL COM > NO CENTRO
             ================================================================== -->
        <button
          type="button"
          class="ces-report-nav-rail"
          (click)="open()"
          aria-label="Abrir navegador rápido (Ctrl + K)"
          title="Abrir navegador rápido (Ctrl + K)"
        >
          <!-- Handle com ícone > -->
          <div class="ces-report-nav-rail__handle">
            <span class="ces-report-nav-rail__icon">
              <i class="pi pi-chevron-right" aria-hidden="true"></i>
            </span>
          </div>

          <!-- Tooltip flutuante suave no hover -->
          <div class="ces-report-nav-rail__tooltip" role="tooltip">
            <i class="pi pi-list text-xs" aria-hidden="true"></i>
            <span>Navegador rápido</span>
            <kbd class="ces-report-nav-rail__tooltip-kbd">Ctrl K</kbd>
          </div>
        </button>
      } @else {
        <!-- ==================================================================
             ESTADO EXPANDIDO: SIDEBAR COMPLETA COM SELETOR DE ESCOPO, FILTROS E LISTA
             ================================================================== -->
        <div class="ces-report-nav-expanded">
          <!-- Cabeçalho com botão < para recolher -->
          <header class="ces-report-nav-header">
            <button
              type="button"
              class="ces-report-nav-header__collapse"
              (click)="collapse()"
              aria-label="Recolher navegador rápido (Esc)"
              title="Recolher (Esc)"
            >
              <i class="pi pi-chevron-left" aria-hidden="true"></i>
            </button>

            <div class="ces-report-nav-header__title-group">
              <div class="ces-report-nav-header__icon">
                <i [class]="icon()" aria-hidden="true"></i>
              </div>
              <div class="ces-report-nav-header__text">
                <span class="ces-report-nav-header__eyebrow">{{ eyebrow() }}</span>
                <h2>{{ title() }}</h2>
              </div>
            </div>

            <div class="ces-report-nav-header__actions">
              <ces-icon-button
                icon="pi pi-refresh"
                label="Atualizar lista"
                tooltip="Atualizar"
                tooltipPosition="bottom"
                size="icon-sm"
                [loading]="loading()"
                (pressed)="load()"
              ></ces-icon-button>
            </div>
          </header>

          <!-- Seção de Filtros e Busca -->
          <section class="ces-report-nav-filters" aria-label="Filtros do navegador">
            <!-- Segmented Control de Tipo / Escopo -->
            @if (hasScopeOptions()) {
              <div
                class="ces-report-nav-type-selector"
                role="group"
                aria-label="Tipo ou escopo de exibição"
                [style.grid-template-columns]="'repeat(' + scopeOptions().length + ', 1fr)'"
              >
                @for (opt of scopeOptions(); track opt.label) {
                  <button
                    type="button"
                    class="ces-report-nav-type-btn"
                    [class.ces-report-nav-type-btn--active]="criteria().scope === opt.value"
                    (click)="changeScope(opt.value)"
                  >
                    @if (opt.icon) {
                      <i [class]="opt.icon" aria-hidden="true"></i>
                    }
                    <span>{{ opt.label }}</span>
                  </button>
                }
              </div>
            }

            <!-- Campo de Busca -->
            <div class="ces-report-nav-search">
              <ces-text-input
                [label]="searchLabel()"
                inputId="ces-report-nav-search-input"
                [placeholder]="searchPlaceholder()"
                [value]="criteria().search"
                (valueChange)="changeSearch($event)"
              ></ces-text-input>
            </div>

            <!-- Chips Rápidos de Status -->
            @if (hasStatusChips()) {
              <div class="ces-report-nav-chips" role="group" aria-label="Filtro rápido por status">
                @for (chip of statusChips(); track chip.label) {
                  <button
                    type="button"
                    class="ces-report-nav-chip"
                    [class.ces-report-nav-chip--active]="criteria().status === chip.value"
                    (click)="changeStatus(chip.value)"
                  >
                    @if (chip.icon) {
                      <i [class]="chip.icon" aria-hidden="true"></i>
                    } @else if (chip.tone) {
                      <span class="ces-report-nav-chip__dot" [attr.data-tone]="chip.tone"></span>
                    }
                    <span>{{ chip.label }}</span>
                  </button>
                }
              </div>
            }

            <!-- Linha de Controles: Ordenação e Minhas -->
            @if (hasSortOptions() || showMineToggle()) {
              <div class="ces-report-nav-controls-row">
                @if (hasSortOptions()) {
                  <div class="ces-report-nav-sort-group" role="group" aria-label="Ordenação">
                    <span class="ces-report-nav-control-label" title="Ordenar por">
                      <i class="pi pi-sort-alt" aria-hidden="true"></i>
                    </span>
                    @for (sortOpt of sortOptions(); track sortOpt.label) {
                      <button
                        type="button"
                        class="ces-report-nav-control-btn"
                        [class.ces-report-nav-control-btn--active]="criteria().sort === sortOpt.value"
                        (click)="changeSort(sortOpt.value)"
                      >
                        {{ sortOpt.label }}
                      </button>
                    }
                  </div>
                }

                @if (showMineToggle()) {
                  <button
                    type="button"
                    class="ces-report-nav-toggle-btn"
                    [class.ces-report-nav-toggle-btn--active]="criteria().mine"
                    (click)="toggleMine()"
                    [title]="mineToggleTitle()"
                  >
                    <i class="pi pi-user" aria-hidden="true"></i>
                    <span>{{ mineToggleLabel() }}</span>
                  </button>
                }
              </div>
            }

            <!-- Barra de Resumo e Ações Rápidas -->
            <div class="ces-report-nav-summary">
              <div class="ces-report-nav-count">
                <i class="pi pi-list" aria-hidden="true"></i>
                <span>{{ totalLabel() }}</span>
              </div>
              <div class="flex items-center gap-3">
                @if (hasFilters()) {
                  <button
                    type="button"
                    class="ces-report-nav-clear-btn"
                    (click)="clearFilters()"
                  >
                    <i class="pi pi-filter-slash" aria-hidden="true"></i>
                    <span>{{ clearFiltersLabel() }}</span>
                  </button>
                }
                <button
                  type="button"
                  class="ces-report-nav-link-btn"
                  (click)="navigateToListAll()"
                  [title]="listAllTooltip()"
                >
                  <span>{{ listAllLabel() }}</span>
                  <i class="pi pi-external-link" aria-hidden="true"></i>
                </button>
              </div>
            </div>
          </section>

          <!-- Lista de Registros com Scroll Próprio -->
          <section class="ces-report-nav-results" aria-label="Registros encontrados">
            @if (loading() && !loaded()) {
              <div class="ces-report-nav-skeletons" aria-hidden="true">
                @for (i of [1, 2, 3, 4, 5]; track i) {
                  <div class="ces-report-nav-skeleton-card">
                    <div class="flex justify-between items-center">
                      <div class="ces-report-nav-skeleton-line w-28 h-3.5"></div>
                      <div class="ces-report-nav-skeleton-line w-20 h-3.5"></div>
                    </div>
                    <div class="ces-report-nav-skeleton-line w-full h-4 mt-2"></div>
                    <div class="flex justify-between items-center mt-2">
                      <div class="ces-report-nav-skeleton-line w-32 h-3"></div>
                      <div class="ces-report-nav-skeleton-line w-20 h-3"></div>
                    </div>
                  </div>
                }
              </div>
            } @else if (error() && visibleItems().length === 0) {
              <div class="p-4">
                <ces-async-state
                  state="error"
                  [compact]="true"
                  title="Não foi possível carregar"
                  [description]="error()!"
                  actionLabel="Tentar novamente"
                  actionIcon="pi pi-refresh"
                  (action)="load()"
                ></ces-async-state>
              </div>
            } @else if (visibleItems().length === 0) {
              <div class="p-4">
                <ces-async-state
                  state="empty"
                  [compact]="true"
                  title="Nenhum registro encontrado"
                  description="Tente ajustar ou limpar os filtros para visualizar outros registros."
                  actionLabel="Limpar filtros"
                  actionIcon="pi pi-filter-slash"
                  (action)="clearFilters()"
                ></ces-async-state>
              </div>
            } @else {
              @if (loading()) {
                <div class="ces-report-nav-refresh-indicator" role="status">
                  <i class="pi pi-spinner pi-spin text-xs" aria-hidden="true"></i>
                  <span>Atualizando registros...</span>
                </div>
              }

              <ul class="ces-report-nav-list" role="list">
                @for (item of visibleItems(); track item.id) {
                  <li>
                    <button
                      type="button"
                      class="ces-report-nav-item"
                      [class.ces-report-nav-item--current]="isCurrent(item.id)"
                      [attr.aria-current]="isCurrent(item.id) ? 'page' : null"
                      (click)="select(item)"
                    >
                      <!-- Linha 1: Identificação, Tipo e Valor -->
                      <div class="ces-report-nav-item__row-top">
                        <div class="flex items-center gap-2 min-w-0">
                          @if (item.number) {
                            <span class="ces-report-nav-item__number">{{ item.number }}</span>
                          }
                          @if (item.typePill) {
                            <span
                              class="ces-report-nav-item__type-pill"
                              [class.ces-report-nav-item__type-pill--compra]="item.typePill.variant === 'compra'"
                              [class.ces-report-nav-item__type-pill--pagamento]="item.typePill.variant === 'pagamento'"
                              [class.ces-report-nav-item__type-pill--info]="item.typePill.variant === 'info'"
                              [class.ces-report-nav-item__type-pill--brand]="item.typePill.variant === 'brand'"
                              [class.ces-report-nav-item__type-pill--success]="item.typePill.variant === 'success'"
                              [class.ces-report-nav-item__type-pill--warning]="item.typePill.variant === 'warning'"
                              [class.ces-report-nav-item__type-pill--danger]="item.typePill.variant === 'danger'"
                              [class.ces-report-nav-item__type-pill--devolucao]="item.typePill.variant === 'devolucao' || item.typePill.variant === 'purple' || item.typePill.variant === 'indigo'"
                            >
                              @if (item.typePill.icon) {
                                <i [class]="item.typePill.icon" aria-hidden="true"></i>
                              }
                              <span>{{ item.typePill.label }}</span>
                            </span>
                          }
                        </div>

                        <!-- Valor destacado à direita -->
                        @if (item.value !== null && item.value !== undefined) {
                          <span class="ces-report-nav-item__value">
                            {{ formatValue(item.value) }}
                          </span>
                        }
                      </div>

                      <!-- Linha 2: Título Principal / Descrição -->
                      <div class="ces-report-nav-item__title">
                        {{ item.title }}
                      </div>

                      <!-- Linha 3: Status + Solicitante + Data + Badge Atual -->
                      <div class="ces-report-nav-item__row-bottom">
                        @if (item.status) {
                          <ces-status
                            [tone]="item.status.tone ?? 'neutral'"
                            emphasis="soft"
                            [class.ces-status--devolucao]="item.status.label === 'Devolução' || item.typePill?.variant === 'devolucao'"
                          >
                            {{ item.status.label }}
                          </ces-status>
                        }

                        <div class="ces-report-nav-item__meta-group">
                          @if (item.metaAuthor) {
                            <span
                              class="ces-report-nav-item__requester"
                              [title]="item.metaAuthor.title || item.metaAuthor.label"
                            >
                              <i [class]="item.metaAuthor.icon || 'pi pi-user'" aria-hidden="true"></i>
                              <span>{{ item.metaAuthor.label }}</span>
                            </span>
                          }

                          @if (item.metaAuthor && item.metaDate) {
                            <span class="ces-report-nav-item__dot-sep" aria-hidden="true">·</span>
                          }

                          @if (item.metaDate && item.metaDate.date) {
                            <time
                              class="ces-report-nav-item__date"
                              [title]="item.metaDate.title || (item.metaDate.date | date: 'dd/MM/yyyy HH:mm')"
                            >
                              <i class="pi pi-calendar" aria-hidden="true"></i>
                              <span>{{ item.metaDate.date | date: (item.metaDate.format || 'dd/MM/yy') }}</span>
                            </time>
                          }

                          @if (item.metaExtra) {
                            <span class="ces-report-nav-item__dot-sep" aria-hidden="true">·</span>
                            <span class="ces-report-nav-item__requester" [title]="item.metaExtra.title || item.metaExtra.label">
                              @if (item.metaExtra.icon) {
                                <i [class]="item.metaExtra.icon" aria-hidden="true"></i>
                              }
                              <span>{{ item.metaExtra.label }}</span>
                            </span>
                          }
                        </div>

                        @if (isCurrent(item.id)) {
                          <span class="ces-report-nav-item__active-badge">
                            <i class="pi pi-eye" aria-hidden="true"></i>
                            <span>Atual</span>
                          </span>
                        }
                      </div>
                    </button>
                  </li>
                }
              </ul>
            }
          </section>
        </div>
      }
    </aside>
  `,
  styleUrl: './report-navigator.component.css'
})
export class CesReportNavigatorComponent {
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly elementRef = inject(ElementRef);
  private searchTimer?: ReturnType<typeof setTimeout>;
  private requestId = 0;

  readonly dataSource = input.required<CesReportNavigatorDataSource>();
  readonly currentId = input<CesReportNavigatorItemId | null>(null);
  readonly currentItem = input<CesReportNavigatorItem | null>(null);
  readonly opened = model(false);
  readonly itemSelected = output<CesReportNavigatorItem>();

  protected readonly criteria = signal<CesReportNavigatorCriteria>({
    search: '',
    scope: undefined,
    status: null,
    sort: undefined,
    mine: false
  });

  protected readonly items = signal<CesReportNavigatorItem[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly loaded = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly config = computed(() => this.dataSource().config);

  protected readonly title = computed(() => {
    const titleCfg = this.config().title;
    return typeof titleCfg === 'function' ? titleCfg(this.criteria()) : titleCfg;
  });

  protected readonly eyebrow = computed(() => this.config().eyebrow ?? 'Navegador');

  protected readonly icon = computed(() => {
    const iconCfg = this.config().icon;
    return typeof iconCfg === 'function' ? iconCfg(this.criteria()) : iconCfg;
  });

  protected readonly scopeOptions = computed(() => this.config().scopeOptions ?? []);
  protected readonly hasScopeOptions = computed(() => this.scopeOptions().length > 0);

  protected readonly searchPlaceholder = computed(
    () => this.config().searchPlaceholder ?? 'Busca rápida...'
  );
  protected readonly searchLabel = computed(
    () => this.config().searchLabel ?? 'Buscar registros'
  );

  protected readonly statusChips = computed(() => this.config().statusChips ?? []);
  protected readonly hasStatusChips = computed(() => this.statusChips().length > 0);

  protected readonly sortOptions = computed<readonly CesReportNavigatorOption<string>[]>(
    () => this.config().sortOptions ?? []
  );
  protected readonly hasSortOptions = computed(() => this.sortOptions().length > 0);

  protected readonly showMineToggle = computed(() => this.config().showMineToggle ?? false);
  protected readonly mineToggleLabel = computed(() => this.config().mineToggleLabel ?? 'Minhas');
  protected readonly mineToggleTitle = computed(
    () => this.config().mineToggleTitle ?? 'Filtrar apenas meus registros'
  );

  protected readonly clearFiltersLabel = computed(() => this.config().clearFiltersLabel ?? 'Limpar');
  protected readonly listAllLabel = computed(() => this.config().listAllLabel ?? 'Ver todas');
  protected readonly listAllTooltip = computed(
    () => this.config().listAllTooltip ?? 'Abrir página completa com tabela'
  );

  protected readonly hasFilters = computed(() => {
    const c = this.criteria();
    const ds = this.dataSource();
    const initial = ds.initialCriteria ?? {};
    const defaultScope = ds.config.scopeOptions?.[0]?.value;
    const defaultSort = ds.config.sortOptions?.[0]?.value;
    return (
      !!c.search.trim() ||
      c.status !== (initial.status ?? null) ||
      (c.scope !== undefined && c.scope !== (initial.scope ?? defaultScope)) ||
      (c.sort !== undefined && c.sort !== (initial.sort ?? defaultSort)) ||
      (c.mine && !initial.mine)
    );
  });

  protected readonly visibleItems = computed(() => {
    const loadedList = this.items();
    const currId = this.currentId();
    const currItem = this.currentItem();

    if (!currId || this.hasFilters()) return loadedList;

    const exists = loadedList.some(
      (item) => String(item.id) === String(currId)
    );
    if (exists || !currItem) return loadedList;

    return [currItem, ...loadedList];
  });

  protected readonly totalLabel = computed(() => {
    if (!this.loaded()) return 'Lista rápida';
    const totalCount = this.total();
    const totalFn = this.config().totalLabel;
    if (totalFn) return totalFn(totalCount);
    return `${totalCount} ${totalCount === 1 ? 'registro' : 'registros'}`;
  });

  constructor() {
    this.destroyRef.onDestroy(() => clearTimeout(this.searchTimer));

    // Inicializar critérios quando dataSource muda
    effect(() => {
      const ds = this.dataSource();
      untracked(() => {
        const initial = ds.initialCriteria ?? {};
        const defaultScope = ds.config.scopeOptions?.[0]?.value;
        const defaultSort = ds.config.sortOptions?.[0]?.value;
        this.criteria.set({
          search: initial.search ?? '',
          scope: initial.scope ?? defaultScope,
          status: initial.status ?? null,
          sort: initial.sort ?? defaultSort,
          mine: initial.mine ?? false
        });
        this.loaded.set(false);
        this.items.set([]);
      });
    });

    // Auto-carregamento ao abrir
    effect(() => {
      const deveCarregar = this.opened() && !this.loaded() && !this.loading();
      if (deveCarregar) {
        untracked(() => this.load());
      }
    });

    // Scroll para item atual ao carregar
    effect(() => {
      if (this.opened() && this.loaded()) {
        untracked(() => this.scrollToCurrent());
      }
    });
  }

  @HostListener('window:keydown', ['$event'])
  protected onWindowKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      if (this.opened()) {
        this.collapse();
      } else {
        this.open();
      }
      return;
    }

    if (event.key === 'Escape' && this.opened()) {
      event.preventDefault();
      this.collapse();
    }
  }

  @HostListener('document:pointerdown', ['$event'])
  protected onDocumentClick(event: PointerEvent): void {
    if (!this.opened()) return;
    const target = event.target as HTMLElement | null;
    if (!target) return;

    if (this.elementRef.nativeElement.contains(target)) return;

    if (
      target.closest(
        '.p-overlay, .p-select-overlay, .p-datepicker-panel, .p-dialog-mask, .p-tooltip, .cdk-overlay-container, .p-component-overlay'
      )
    ) {
      return;
    }

    this.collapse();
  }

  open(): void {
    this.opened.set(true);
    this.scrollToCurrent();
    setTimeout(() => {
      const searchInput = this.elementRef.nativeElement.querySelector('input');
      if (searchInput) {
        searchInput.focus();
      }
    }, 120);
  }

  collapse(): void {
    this.opened.set(false);
  }

  changeScope(scope: unknown): void {
    this.criteria.update((c) => ({ ...c, scope }));
    this.load();
  }

  changeSearch(search: string): void {
    this.criteria.update((c) => ({ ...c, search }));
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(), 300);
  }

  changeStatus(status: unknown): void {
    this.criteria.update((c) => ({ ...c, status }));
    this.load();
  }

  changeSort(sort: string | undefined): void {
    this.criteria.update((c) => ({ ...c, sort }));
    this.load();
  }

  toggleMine(): void {
    this.criteria.update((c) => ({ ...c, mine: !c.mine }));
    this.load();
  }

  clearFilters(): void {
    clearTimeout(this.searchTimer);
    const initial = this.dataSource().initialCriteria ?? {};
    const defaultScope = this.config().scopeOptions?.[0]?.value;
    const defaultSort = this.config().sortOptions?.[0]?.value;

    this.criteria.set({
      search: '',
      scope: initial.scope ?? defaultScope,
      status: initial.status ?? null,
      sort: initial.sort ?? defaultSort,
      mine: false
    });
    this.load();
  }

  navigateToListAll(): void {
    this.collapse();
    const route = this.dataSource().getListRoute();
    if (Array.isArray(route)) {
      this.router.navigate(route);
    } else if (typeof route === 'string') {
      this.router.navigateByUrl(route);
    }
  }

  load(): void {
    const currentRequest = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);

    this.dataSource()
      .load(this.criteria())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          if (currentRequest !== this.requestId) return;
          const mappedItems = (page.items || []).map((raw) => this.dataSource().toItem(raw));
          this.items.set(mappedItems);
          this.total.set(page.totalElements ?? mappedItems.length);
          this.loaded.set(true);
          this.loading.set(false);
          this.scrollToCurrent();
        },
        error: (err: unknown) => {
          if (currentRequest !== this.requestId) return;
          this.error.set(getErrorMessage(err));
          this.loaded.set(true);
          this.loading.set(false);
        }
      });
  }

  select(item: CesReportNavigatorItem): void {
    this.itemSelected.emit(item);
    const route = this.dataSource().getRouteForItem(item);
    if (Array.isArray(route)) {
      this.router.navigate(route);
    } else if (typeof route === 'string') {
      this.router.navigateByUrl(route);
    }
  }

  isCurrent(itemId: CesReportNavigatorItemId): boolean {
    const currId = this.currentId();
    if (currId === null || currId === undefined) return false;
    return String(itemId) === String(currId);
  }

  formatValue(val: string | number | null | undefined): string {
    if (val === null || val === undefined) return '';
    if (typeof val === 'number') {
      return val.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    }
    return String(val);
  }

  private scrollToCurrent(): void {
    setTimeout(() => {
      const currentEl = this.elementRef.nativeElement.querySelector(
        '.ces-report-nav-item--current'
      );
      if (currentEl && typeof currentEl.scrollIntoView === 'function') {
        currentEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }, 150);
  }
}
