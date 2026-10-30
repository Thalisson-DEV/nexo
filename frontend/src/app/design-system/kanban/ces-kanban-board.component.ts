import {
  ChangeDetectionStrategy,
  Component,
  TemplateRef,
  input,
  output,
  signal,
} from '@angular/core';
import { DragDropModule } from 'primeng/dragdrop';
import { CesKanbanColumnComponent } from './ces-kanban-column.component';
import { KanbanCopilotoContext } from './ces-kanban-column-info.component';
import {
  CesKanbanColumn,
  CesKanbanItemMovedEvent,
  CesKanbanSubGroupToggleEvent,
} from './ces-kanban.types';

/**
 * Painel Kanban genérico.
 *
 * Suporta múltiplas macro-colunas e sub-etapas expansíveis com subtons relacionados.
 * Largura harmônica com suporte a rolagem horizontal quando necessário.
 * Agnóstico de domínio.
 */
@Component({
  selector: 'ces-kanban-board',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DragDropModule, CesKanbanColumnComponent],
  host: {
    class: 'ces-kanban-board',
    role: 'region',
    '[attr.aria-label]': 'ariaLabel()',
  },
  styles: `
    :host {
      display: block;
      width: 100%;
      overflow-x: auto;
      scrollbar-width: thin;
      scrollbar-color: rgba(148, 163, 184, 0.25) transparent;
    }

    :host::-webkit-scrollbar {
      height: 4px;
    }

    :host::-webkit-scrollbar-track {
      background: transparent;
    }

    :host::-webkit-scrollbar-thumb {
      background-color: rgba(148, 163, 184, 0.25);
      border-radius: 9999px;
    }

    :host::-webkit-scrollbar-thumb:hover {
      background-color: rgba(148, 163, 184, 0.5);
    }

    .ces-kanban-board__columns {
      display: flex;
      gap: 0.875rem;
      padding-bottom: 0.5rem;
      width: 100%;
      min-width: 100%;
    }
  `,
  template: `
    <div class="ces-kanban-board__columns">
      @for (column of columns(); track column.id) {
        <ces-kanban-column
          [column]="column"
          [cardTemplate]="cardTemplate()"
          [copilotoEnabled]="copilotoEnabled()"
          (itemDropped)="onDrop(column, $event.subGroupId)"
          (subGroupToggled)="subGroupToggled.emit($event)"
          (loadMore)="loadMore.emit($event)"
          (copilotoClicked)="copilotoClicked.emit($event)"
        />
      }
    </div>
  `,
})
export class CesKanbanBoardComponent<T = any> {
  readonly columns = input<CesKanbanColumn<any>[]>([]);
  readonly cardTemplate = input<TemplateRef<any> | null>(null);
  readonly ariaLabel = input<string>('Painel Kanban');
  /** Habilita o botão "Analisar com Copiloto" no popover de métricas de cada coluna */
  readonly copilotoEnabled = input<boolean>(false);

  /** Emitido quando um card é arrastado entre colunas ou sub-grupos */
  readonly itemMoved = output<CesKanbanItemMovedEvent<any>>();
  /** Emitido quando o usuário expande ou colapsa um sub-grupo */
  readonly subGroupToggled = output<CesKanbanSubGroupToggleEvent>();
  /** Emitido quando o usuário clica em "Ver mais" de uma coluna */
  readonly loadMore = output<string>();
  /** Emitido quando o usuário clica em "Analisar com Copiloto" em uma coluna */
  readonly copilotoClicked = output<KanbanCopilotoContext>();

  /** Item sendo arrastado no momento */
  private readonly _dragItem = signal<any>(null);
  private readonly _dragColumnId = signal<string | null>(null);
  private readonly _dragSubGroupId = signal<string | null>(null);

  /**
   * Chamado pelos cards via `pDraggable` para registrar o item sendo arrastado.
   */
  startDrag(item: T, columnId: string, subGroupId?: string): void {
    this._dragItem.set(item);
    this._dragColumnId.set(columnId);
    this._dragSubGroupId.set(subGroupId ?? null);
  }

  /**
   * Chamado ao finalizar um drag (independente de sucesso).
   */
  endDrag(): void {
    this._dragItem.set(null);
    this._dragColumnId.set(null);
    this._dragSubGroupId.set(null);
  }

  protected onDrop(targetColumn: CesKanbanColumn<T>, targetSubGroupId?: string): void {
    const item = this._dragItem();
    const fromId = this._dragColumnId();
    const fromSubId = this._dragSubGroupId();

    if (item !== null && fromId !== null) {
      const mudouColuna = fromId !== targetColumn.id;
      const mudouSubGrupo = targetSubGroupId && fromSubId !== targetSubGroupId;

      if (mudouColuna || mudouSubGrupo) {
        this.itemMoved.emit({
          item,
          fromColumnId: fromId,
          toColumnId: targetColumn.id,
          fromSubGroupId: fromSubId ?? undefined,
          toSubGroupId: targetSubGroupId,
        });
      }
    }
    this.endDrag();
  }
}
