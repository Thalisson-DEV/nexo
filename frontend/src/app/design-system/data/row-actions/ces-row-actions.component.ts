import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CesActionVariant } from '../../core';
import { CesIconButtonComponent } from '../../primitives/button';

export interface CesRowAction<T> {
  id: string;
  label: string;
  icon: string;
  variant?: Extract<CesActionVariant, 'primary' | 'secondary' | 'outline' | 'ghost' | 'destructive' | 'success'>;
  disabled?: boolean | ((row: T) => boolean);
  loading?: boolean | ((row: T) => boolean);
  visible?: boolean | ((row: T) => boolean);
}

export interface CesRowActionEvent<T> {
  action: CesRowAction<T>;
  row: T;
}

@Component({
  selector: 'ces-row-actions',
  standalone: true,
  imports: [CesIconButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'ces-row-actions' },
  styles: `
    :host,
    .ces-row-actions__group {
      display: inline-flex;
      align-items: center;
      gap: 0.125rem;
    }
  `,
  template: `
    <div class="ces-row-actions__group" role="group" [attr.aria-label]="label()">
      @for (action of actions(); track action.id) {
        @if (isVisible(action)) {
          <ces-icon-button
            [icon]="action.icon"
            [label]="action.label"
            [variant]="action.variant || 'ghost'"
            [disabled]="isDisabled(action)"
            [loading]="isLoading(action)"
            size="icon-sm"
            (pressed)="activate(action)"
          ></ces-icon-button>
        }
      }
    </div>
  `
})
export class CesRowActionsComponent<T = unknown> {
  readonly row = input.required<T>();
  readonly actions = input.required<readonly CesRowAction<T>[]>();
  readonly label = input('Ações do registro');

  readonly actionSelected = output<CesRowActionEvent<T>>();

  protected isVisible(action: CesRowAction<T>): boolean {
    return this.resolve(action.visible, true);
  }

  protected isDisabled(action: CesRowAction<T>): boolean {
    return this.resolve(action.disabled, false);
  }

  protected isLoading(action: CesRowAction<T>): boolean {
    return this.resolve(action.loading, false);
  }

  protected activate(action: CesRowAction<T>): void {
    if (this.isDisabled(action) || this.isLoading(action)) return;
    this.actionSelected.emit({ action, row: this.row() });
  }

  private resolve(
    value: boolean | ((row: T) => boolean) | undefined,
    fallback: boolean
  ): boolean {
    return typeof value === 'function' ? value(this.row()) : (value ?? fallback);
  }
}
