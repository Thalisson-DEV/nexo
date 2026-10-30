import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { CesContentState, CesDensity } from '../../core';
import { CesAsyncStateComponent } from '../../feedback';

@Component({
  selector: 'ces-table-shell',
  standalone: true,
  imports: [CesAsyncStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-table-shell',
    '[attr.data-density]': 'density()',
    '[attr.data-state]': 'state()',
    '[attr.aria-busy]': "state() === 'loading' || state() === 'refreshing'"
  },
  styles: `
    :host {
      display: block;
      overflow: hidden;
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-lg);
      background: var(--ces-surface);
      box-shadow: var(--ces-shadow-sm);
    }

    .ces-table-shell__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.875rem 1rem;
      border-bottom: 1px solid var(--ces-border);
    }

    .ces-table-shell__heading {
      display: grid;
      min-width: 0;
      gap: 0.2rem;
    }

    .ces-table-shell__heading h2,
    .ces-table-shell__heading p {
      margin: 0;
    }

    .ces-table-shell__heading h2 {
      color: var(--ces-foreground);
      font-size: var(--ces-font-title);
    }

    .ces-table-shell__heading p {
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption);
      line-height: 1.45;
    }

    .ces-table-shell__toolbar {
      min-width: min(100%, 18rem);
    }

    .ces-table-shell__notice {
      border-bottom: 1px solid var(--ces-border);
      background: var(--ces-surface-subtle);
    }

    .ces-table-shell__content {
      min-width: 0;
      overflow-x: auto;
    }

    .ces-table-shell__state {
      min-height: 12rem;
    }

    :host([data-density='comfortable']) .ces-table-shell__header {
      padding: 1rem 1.25rem;
    }

    @media (max-width: 48rem) {
      .ces-table-shell__header {
        align-items: stretch;
        flex-direction: column;
      }

      .ces-table-shell__toolbar {
        width: 100%;
      }
    }
  `,
  template: `
    @if (hasHeader()) {
      <header class="ces-table-shell__header">
        <div class="ces-table-shell__heading">
          @if (title()) {
            <h2>{{ title() }}</h2>
          }
          @if (description()) {
            <p>{{ description() }}</p>
          }
        </div>
        <div class="ces-table-shell__toolbar">
          <ng-content select="[table-toolbar]"></ng-content>
        </div>
      </header>
    }

    @if (noticeState()) {
      <div class="ces-table-shell__notice" aria-live="polite">
        <ces-async-state
          [state]="noticeState()!"
          [title]="stateTitle()"
          [description]="stateDescription()"
          [actionLabel]="stateActionLabel()"
          [actionIcon]="stateActionIcon()"
          [compact]="true"
          (action)="stateAction.emit()"
        ></ces-async-state>
      </div>
    }

    @if (blockingState()) {
      <div class="ces-table-shell__state" aria-live="polite">
        @if (blockingState() === 'empty') {
          <ng-content select="[table-empty]"></ng-content>
        } @else if (blockingState() === 'error') {
          <ng-content select="[table-error]"></ng-content>
        }

        @if (!customBlockingState()) {
          <ces-async-state
            [state]="blockingState()!"
            [title]="stateTitle()"
            [description]="stateDescription()"
            [actionLabel]="stateActionLabel()"
            [actionIcon]="stateActionIcon()"
            (action)="stateAction.emit()"
          ></ces-async-state>
        }
      </div>
    } @else {
      <div class="ces-table-shell__content">
        <ng-content></ng-content>
      </div>
    }

    <ng-content select="[table-footer]"></ng-content>
  `
})
export class CesTableShellComponent {
  readonly title = input<string | undefined>();
  readonly description = input<string | undefined>();
  readonly state = input<CesContentState>('ready');
  readonly density = input<CesDensity>('compact');
  readonly customEmpty = input(false);
  readonly customError = input(false);
  readonly stateTitle = input<string | undefined>();
  readonly stateDescription = input<string | undefined>();
  readonly stateActionLabel = input<string | undefined>();
  readonly stateActionIcon = input<string | undefined>();

  readonly stateAction = output<void>();

  protected readonly hasHeader = computed(() => Boolean(this.title() || this.description()));
  protected readonly blockingState = computed(() => {
    const state = this.state();
    return state === 'loading' || state === 'empty' || state === 'error' ? state : undefined;
  });
  protected readonly noticeState = computed(() => {
    const state = this.state();
    return state === 'refreshing' || state === 'offline-cached' || state === 'stale'
      ? state
      : undefined;
  });
  protected readonly customBlockingState = computed(
    () =>
      (this.blockingState() === 'empty' && this.customEmpty()) ||
      (this.blockingState() === 'error' && this.customError())
  );
}
