import { ChangeDetectionStrategy, Component, computed, input, model, output } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

export type CesDialogSize = 'sm' | 'md' | 'lg' | 'xl' | 'full';

const DIALOG_WIDTHS: Record<CesDialogSize, string> = {
  sm: '28rem',
  md: '40rem',
  lg: '56rem',
  xl: '72rem',
  full: 'calc(100vw - 2rem)'
};

@Component({
  selector: 'ces-dialog-shell',
  standalone: true,
  imports: [DialogModule, ProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .ces-dialog-shell__description {
      margin: 0 0 1.25rem;
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-body);
      line-height: 1.5;
    }

    .ces-dialog-shell__body {
      position: relative;
      min-height: 3rem;
    }

    .ces-dialog-shell__loading {
      position: absolute;
      inset: 0;
      display: grid;
      place-content: center;
      justify-items: center;
      gap: 0.65rem;
      z-index: 1;
      border-radius: var(--ces-radius-sm);
      background: color-mix(in srgb, var(--ces-surface) 86%, transparent);
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-control);
      font-weight: 700;
      backdrop-filter: blur(2px);
    }

    .ces-dialog-shell__footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.5rem;
      width: 100%;
    }

    .ces-dialog-shell__footer:empty {
      display: none;
    }
  `,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [header]="title()"
      [draggable]="false"
      [resizable]="false"
      [closable]="canDismiss()"
      [closeOnEscape]="canDismiss()"
      [dismissableMask]="dismissableMask() && canDismiss()"
      [style]="dialogStyle()"
      [breakpoints]="breakpoints()"
      [styleClass]="styleClass()"
      [appendTo]="appendTo()"
      [focusOnShow]="true"
      (onHide)="closed.emit()"
    >
      @if (description()) {
        <p class="ces-dialog-shell__description">{{ description() }}</p>
      }

      <div class="ces-dialog-shell__body" [attr.aria-busy]="loading()">
        <ng-content></ng-content>
        @if (loading()) {
          <div class="ces-dialog-shell__loading" role="status" aria-live="polite">
            <p-progress-spinner
              strokeWidth="4"
              [style]="{ width: '2rem', height: '2rem' }"
              ariaLabel="Carregando"
            ></p-progress-spinner>
            <span>{{ loadingLabel() }}</span>
          </div>
        }
      </div>

      <ng-template #footer>
        <div class="ces-dialog-shell__footer">
          <ng-content select="[dialog-footer]"></ng-content>
        </div>
      </ng-template>
    </p-dialog>
  `
})
export class CesDialogShellComponent {
  readonly visible = model(false);
  readonly title = input<string>('');
  readonly description = input<string | undefined>();
  readonly size = input<CesDialogSize>('md');
  readonly loading = input(false);
  readonly loadingLabel = input('Processando');
  readonly dismissable = input(true);
  readonly dismissableMask = input(false);
  readonly appendTo = input<HTMLElement | 'body' | null>('body');
  readonly className = input('');

  readonly closed = output<void>();

  protected readonly canDismiss = computed(() => this.dismissable() && !this.loading());
  protected readonly dialogStyle = computed(() => ({
    width: `min(${DIALOG_WIDTHS[this.size()]}, calc(100vw - 2rem))`,
    maxHeight: this.size() === 'full' ? 'calc(100vh - 2rem)' : '90vh'
  }));
  protected readonly breakpoints = computed<Record<string, string>>(() => ({
    '768px': 'calc(100vw - 1rem)'
  }));
  protected readonly styleClass = computed(() => {
    const classes = ['ces-dialog', 'ces-dialog-shell', `ces-dialog-shell--${this.size()}`];
    if (this.className()) classes.push(this.className());
    return classes.join(' ');
  });
}
