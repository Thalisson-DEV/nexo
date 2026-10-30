import {
  ChangeDetectionStrategy,
  Component,
  TemplateRef,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { DragDropModule } from 'primeng/dragdrop';
import { CesKanbanColumnInfoComponent, KanbanCopilotoContext } from './ces-kanban-column-info.component';
import {
  CesKanbanColumn,
  CesKanbanSubGroup,
  CesKanbanSubGroupToggleEvent,
  getSubGroupTheme,
} from './ces-kanban.types';

/**
 * Coluna individual do Kanban.
 * Cada sub-status possui sua própria coloração temática associada à macro-coluna pai (subtons).
 * Largura harmônica original preservada (280px - 320px).
 */
@Component({
  selector: 'ces-kanban-column',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, DragDropModule, CesKanbanColumnInfoComponent],
  host: {
    class: 'ces-kanban-column flex flex-col rounded-2xl overflow-hidden transition-all duration-150',
    '[class]': 'columnBorderAndBgClass()',
    '[class.ring-2]': 'isDragOver()',
    '[class.ring-sky-500]': 'isDragOver()',
  },
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-width: 275px;
      max-width: 340px;
      width: 100%;
      flex: 1 1 0px;
      min-height: calc(100vh - 220px);
    }

    /* Barra de scroll ultra fina (4px) para economizar espaço nos cards */
    .ces-kanban-scroll {
      scrollbar-width: thin;
      scrollbar-color: rgba(148, 163, 184, 0.3) transparent;
    }

    .ces-kanban-scroll::-webkit-scrollbar {
      width: 4px;
      height: 4px;
    }

    .ces-kanban-scroll::-webkit-scrollbar-track {
      background: transparent;
    }

    .ces-kanban-scroll::-webkit-scrollbar-thumb {
      background-color: rgba(148, 163, 184, 0.25);
      border-radius: 9999px;
    }

    .ces-kanban-scroll::-webkit-scrollbar-thumb:hover {
      background-color: rgba(148, 163, 184, 0.5);
    }
  `,
  template: `
    <!-- Cabeçalho da Macro-Coluna -->
    <div class="flex items-center justify-between px-4 py-3.5 select-none">
      <div class="flex items-center gap-2.5 min-w-0">
        <i
          [class]="macroIcon() + ' text-base shrink-0 ' + macroIconColorClass()"
          aria-hidden="true"
        ></i>
        <h2 class="text-sm font-bold text-slate-800 dark:text-white tracking-tight truncate">
          {{ column().label }}
        </h2>
      </div>

      <div class="flex items-center gap-1.5 shrink-0">
        <!-- Badge de contagem total da macro-coluna -->
        <span
          class="min-w-[24px] h-6 px-2 rounded-full text-xs font-bold inline-flex items-center justify-center border"
          [class]="macroBadgeClass()"
          [attr.aria-label]="column().totalCount + ' solicitações'"
        >
          {{ column().totalCount }}
        </span>

        <!-- Botão ℹ com métricas agregadas -->
        @if (column().metricas?.length || copilotoEnabled()) {
          <ces-kanban-column-info
            [column]="column()"
            [copilotoEnabled]="copilotoEnabled()"
            (copilotoClicked)="copilotoClicked.emit($event)"
          />
        }
      </div>
    </div>

    <!-- Drop Zone e Conteúdo da Coluna com Scroll Fino -->
    <div
      class="ces-kanban-scroll flex flex-col gap-2 p-3 pt-0 overflow-y-auto flex-1"
      style="max-height: calc(100vh - 220px)"
      pDroppable="ces-kanban-card"
      (onDrop)="onColumnDrop()"
      (onDragEnter)="isDragOver.set(true)"
      (onDragLeave)="isDragOver.set(false)"
    >
      @if (column().subGroups && column().subGroups!.length > 0) {
        @for (sub of column().subGroups; track sub.id) {
          @let theme = getTheme(sub.id);
          @let totalItems = sub.totalCount ?? sub.items.length;
          @let isClosed = isSubGroupCollapsed(sub.id, sub.collapsed, totalItems);

          <div
            class="flex flex-col rounded-xl transition-all duration-150"
            [class.ring-2]="activeSubGroupId() === sub.id"
            [class.ring-sky-500]="activeSubGroupId() === sub.id"
            pDroppable="ces-kanban-card"
            (onDragEnter)="onSubGroupDragEnter(sub.id)"
            (onDragLeave)="onSubGroupDragLeave(sub.id)"
            (onDrop)="onSubGroupDrop(sub.id, $event)"
          >
            <!-- Botão Sólido do Sub-Status -->
            <button
              type="button"
              class="w-full flex items-center justify-between gap-2 px-3.5 py-2.5 rounded-xl cursor-pointer select-none text-left transition-all duration-150 border-0 shadow-2xs focus-visible:outline-2 focus-visible:outline-offset-1"
              [class]="theme.headerBg + ' ' + theme.headerHoverBg"
              [attr.aria-expanded]="!isClosed"
              [attr.aria-label]="sub.label + ', ' + totalItems + ' solicitações'"
              (click)="toggleSubGroup(sub.id, isClosed, $event)"
            >
              <div class="flex items-center gap-2 min-w-0 flex-1">
                <i
                  class="text-[10px] shrink-0 text-white/90"
                  [class]="isClosed ? 'pi pi-chevron-right' : 'pi pi-chevron-down'"
                  aria-hidden="true"
                ></i>
                <span class="text-xs font-bold text-white tracking-tight truncate">
                  {{ sub.label }}
                </span>
              </div>

              <!-- Badge com contagem no sub-status -->
              <span
                class="inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full text-xs font-bold shrink-0 bg-black/25 text-white"
              >
                {{ totalItems }}
              </span>
            </button>

            <!-- Cards quando expandido -->
            @if (!isClosed) {
              <div class="flex flex-col gap-2 pt-2 pb-1">
                @if (sub.items.length === 0) {
                  <div class="flex flex-col items-center justify-center py-6 px-3 text-center gap-2">
                    <div class="w-10 h-10 rounded-full border border-dashed border-slate-300 dark:border-slate-700/80 flex items-center justify-center text-slate-400 dark:text-slate-500">
                      <i [class]="subEmptyIcon(sub.id) + ' text-base'" aria-hidden="true"></i>
                    </div>
                    <div class="flex flex-col gap-0.5">
                      <p class="text-xs font-semibold text-slate-700 dark:text-slate-200">Nenhuma solicitação</p>
                      <p class="text-[11px] text-slate-400 dark:text-slate-500">{{ subEmptySubtitle(sub.id) }}</p>
                    </div>
                  </div>
                }
                @for (item of sub.items; track $index) {
                  @if (cardTemplate(); as tpl) {
                    <ng-container
                      *ngTemplateOutlet="tpl; context: { $implicit: item, column: column(), subGroup: sub }"
                    />
                  }
                }
              </div>
            }
          </div>
        }

        <!-- Exibe estado vazio na coluna SOMENTE se todos os sub-grupos estiverem colapsados e vazios (sem duplicação) -->
        @if (shouldShowColumnEmptyState()) {
          <div class="flex flex-col items-center justify-center py-12 px-4 text-center gap-2.5 my-auto">
            <div class="w-12 h-12 rounded-full border border-dashed border-slate-300 dark:border-slate-700/80 flex items-center justify-center text-slate-400 dark:text-slate-500">
              <i [class]="columnEmptyIcon() + ' text-lg'" aria-hidden="true"></i>
            </div>
            <div class="flex flex-col gap-0.5">
              <p class="text-xs font-semibold text-slate-700 dark:text-slate-200">Nenhuma solicitação</p>
              <p class="text-[11px] text-slate-400 dark:text-slate-500">{{ columnEmptySubtitle() }}</p>
            </div>
          </div>
        }
      }

      <!-- MODO PLANO LEGADO (sem sub-grupos) -->
      @else {
        @if (column().items.length === 0) {
          <div
            class="flex flex-col items-center justify-center py-12 gap-2 text-slate-400 dark:text-slate-500"
            role="status"
          >
            <i class="pi pi-inbox text-2xl" aria-hidden="true"></i>
            <span class="text-xs font-medium">Nenhuma solicitação</span>
          </div>
        }

        @for (item of column().items; track $index) {
          @if (cardTemplate(); as tpl) {
            <ng-container
              *ngTemplateOutlet="tpl; context: { $implicit: item, column: column() }"
            />
          }
        }
      }

      <!-- "Ver mais" button -->
      @if (column().totalCount > totalCardsRenderizados()) {
        <button
          type="button"
          class="w-full py-1.5 text-xs text-sky-600 dark:text-sky-400
                 hover:underline rounded-md transition-colors
                 focus-visible:outline-2 focus-visible:outline-sky-500"
          (click)="loadMore.emit(column().id)"
        >
          + {{ column().totalCount - totalCardsRenderizados() }} mais — ver na lista
        </button>
      }
    </div>
  `,
})
export class CesKanbanColumnComponent {
  readonly column = input.required<CesKanbanColumn<unknown>>();
  readonly cardTemplate = input<TemplateRef<{ $implicit: unknown; column: CesKanbanColumn<unknown>; subGroup?: CesKanbanSubGroup<unknown> }> | null>(null);
  readonly copilotoEnabled = input(false);

  readonly itemDropped = output<{ columnId: string; subGroupId?: string }>();
  readonly subGroupToggled = output<CesKanbanSubGroupToggleEvent>();
  readonly loadMore = output<string>();
  readonly copilotoClicked = output<KanbanCopilotoContext>();

  protected readonly isDragOver = signal(false);
  protected readonly activeSubGroupId = signal<string | null>(null);
  private readonly _collapsedMap = signal<Record<string, boolean>>({});
  private _lastDropHandledAt = 0;

  protected isSubGroupCollapsed(subId: string, initialCollapsed?: boolean, totalCount?: number): boolean {
    const override = this._collapsedMap()[subId];
    if (override !== undefined) {
      return override;
    }
    // Sub-status sem registros devem vir colapsados por padrão
    if ((totalCount ?? 0) === 0) {
      return true;
    }
    return !!initialCollapsed;
  }

  protected toggleSubGroup(subId: string, currentlyCollapsed: boolean, event?: Event): void {
    event?.stopPropagation();
    const nextState = !currentlyCollapsed;
    this._collapsedMap.update((m) => ({ ...m, [subId]: nextState }));
    this.subGroupToggled.emit({
      columnId: this.column().id,
      subGroupId: subId,
      collapsed: nextState,
    });
  }

  protected onSubGroupDragEnter(subId: string): void {
    this.activeSubGroupId.set(subId);
  }

  protected onSubGroupDragLeave(subId: string): void {
    if (this.activeSubGroupId() === subId) {
      this.activeSubGroupId.set(null);
    }
  }

  protected onColumnDrop(): void {
    this.isDragOver.set(false);
    this.activeSubGroupId.set(null);
    if (Date.now() - this._lastDropHandledAt < 100) {
      return;
    }
    this.itemDropped.emit({ columnId: this.column().id });
  }

  protected onSubGroupDrop(subGroupId: string, event?: any): void {
    this._lastDropHandledAt = Date.now();
    event?.originalEvent?.stopPropagation?.();
    event?.stopPropagation?.();
    this.activeSubGroupId.set(null);
    this.isDragOver.set(false);
    this._collapsedMap.update((m) => ({ ...m, [subGroupId]: false }));
    this.itemDropped.emit({ columnId: this.column().id, subGroupId });
  }

  protected getTheme(subEtapaId: string) {
    return getSubGroupTheme(subEtapaId);
  }

  protected readonly totalCardsRenderizados = computed(() => {
    const col = this.column();
    if (col.subGroups && col.subGroups.length > 0) {
      return col.subGroups.reduce((acc, sub) => acc + sub.items.length, 0);
    }
    return col.items.length;
  });

  protected shouldShowColumnEmptyState(): boolean {
    const col = this.column();
    if (!col.subGroups || col.subGroups.length === 0) {
      return col.items.length === 0;
    }
    if (col.totalCount > 0) {
      return false;
    }
    // Retorna true somente se TODOS os sub-grupos estiverem fechados
    return col.subGroups.every((sub) => {
      const totalItems = sub.totalCount ?? sub.items.length;
      return this.isSubGroupCollapsed(sub.id, sub.collapsed, totalItems);
    });
  }

  protected subEmptyIcon(subId: string): string {
    const map: Record<string, string> = {
      PENDENTE_PLANEJAMENTO: 'pi pi-calendar-plus',
      PLANEJADO: 'pi pi-calendar',
      PENDENTE_APROVACAO: 'pi pi-clock',
      PRONTO_PARA_SEPARACAO: 'pi pi-box',
      EM_SEPARACAO: 'pi pi-cog',
      SEPARADO: 'pi pi-check-circle',
      PENDENTE_RETIRADA: 'pi pi-truck',
      PENDENTE_DEVOLUCAO: 'pi pi-trash',
      CONCLUIDO: 'pi pi-check',
      REJEITADO: 'pi pi-times-circle',
      CANCELADO: 'pi pi-ban',
    };
    return map[subId] ?? 'pi pi-inbox';
  }

  protected subEmptySubtitle(subId: string): string {
    const map: Record<string, string> = {
      PENDENTE_PLANEJAMENTO: 'Nenhuma solicitação aguardando definição de atendimento ou estoque.',
      PLANEJADO: 'Nenhuma solicitação planejada aguardando início da separação física.',
      PENDENTE_APROVACAO: 'Nenhuma solicitação comercial pendente de aprovação.',
      PRONTO_PARA_SEPARACAO: 'Nenhuma solicitação liberada para o almoxarifado separar.',
      EM_SEPARACAO: 'Nenhuma solicitação sendo separada no almoxarifado no momento.',
      SEPARADO: 'Nenhum material separado aguardando retirada pela equipe.',
      PENDENTE_RETIRADA: 'Nenhum material aguardando entrega ou saída no campo.',
      PENDENTE_DEVOLUCAO: 'Nenhuma solicitação com devolução de material pendente.',
      CONCLUIDO: 'Nenhuma solicitação concluída nesta visualização.',
      REJEITADO: 'Nenhuma solicitação com recusa formal registrada.',
      CANCELADO: 'Nenhuma solicitação cancelada nesta visualização.',
    };
    return map[subId] ?? 'Nenhuma solicitação nesta etapa no momento.';
  }

  protected columnEmptySubtitle(): string {
    const id = this.column().id;
    if (id === 'PLANEJAMENTO') return 'As solicitações de planejamento aparecerão aqui.';
    if (id === 'SEPARACAO') return 'As solicitações nesta etapa aparecerão aqui.';
    if (id === 'ENTREGA') return 'As solicitações de entrega e campo aparecerão aqui.';
    if (id === 'RECUSADO') return 'As solicitações recusadas aparecerão aqui.';
    return 'As solicitações desta etapa aparecerão aqui.';
  }

  protected columnEmptyIcon(): string {
    if (this.column().id === 'RECUSADO') return 'pi pi-ban';
    return 'pi pi-inbox';
  }

  protected readonly macroIcon = computed(() => {
    const id = this.column().id;
    if (id === 'PLANEJAMENTO') return 'pi pi-calendar';
    if (id === 'SEPARACAO') return 'pi pi-box';
    if (id === 'ENTREGA') return 'pi pi-truck';
    if (id === 'RECUSADO') return 'pi pi-ban';
    return this.column().icon || 'pi pi-folder';
  });

  protected readonly macroIconColorClass = computed(() => {
    const map: Record<string, string> = {
      warning: 'text-amber-400',
      info: 'text-sky-400',
      success: 'text-emerald-400',
      danger: 'text-rose-400',
      neutral: 'text-slate-400',
      brand: 'text-sky-400',
    };
    return map[this.column().tone] ?? 'text-slate-400';
  });

  protected readonly macroBadgeClass = computed(() => {
    const map: Record<string, string> = {
      warning: 'bg-amber-500/15 text-amber-600 dark:text-amber-300 border-amber-500/30',
      info: 'bg-sky-500/15 text-sky-600 dark:text-sky-300 border-sky-500/30',
      success: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300 border-emerald-500/30',
      danger: 'bg-rose-500/15 text-rose-600 dark:text-rose-300 border-rose-500/30',
      neutral: 'bg-slate-500/15 text-slate-600 dark:text-slate-300 border-slate-500/30',
      brand: 'bg-sky-500/15 text-sky-600 dark:text-sky-300 border-sky-500/30',
    };
    return map[this.column().tone] ?? 'bg-slate-500/15 text-slate-300 border-slate-500/30';
  });

  protected readonly columnBorderAndBgClass = computed(() => {
    const map: Record<string, string> = {
      warning: 'border border-amber-500/25 dark:border-amber-500/35 bg-slate-50/80 dark:bg-[#0b1320]',
      info: 'border border-sky-500/25 dark:border-sky-500/35 bg-slate-50/80 dark:bg-[#0b1320]',
      success: 'border border-emerald-500/25 dark:border-emerald-500/35 bg-slate-50/80 dark:bg-[#0b1320]',
      danger: 'border border-rose-500/25 dark:border-rose-500/35 bg-slate-50/80 dark:bg-[#0b1320]',
      neutral: 'border border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-[#0b1320]',
      brand: 'border border-sky-500/25 dark:border-sky-500/35 bg-slate-50/80 dark:bg-[#0b1320]',
    };
    return map[this.column().tone] ?? 'border border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-[#0b1320]';
  });
}
