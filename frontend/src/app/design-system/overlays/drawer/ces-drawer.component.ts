import { ChangeDetectionStrategy, Component, computed, input, model, output } from '@angular/core';
import { DrawerModule } from 'primeng/drawer';
import { CES_COMPACT_VIEWPORT_QUERY, cesMediaQuery } from '../../core/media';

export type CesDrawerSize = 'sm' | 'md' | 'lg' | 'xl';

const DRAWER_WIDTHS: Record<CesDrawerSize, string> = {
  sm: '26rem',
  md: '34rem',
  lg: '42rem',
  xl: '56rem'
};

let drawerSequence = 0;

/**
 * Painel de detalhe sem sair da tela. No desktop abre à direita; em telas compactas vira folha
 * inferior (até 92% da altura) com o botão Fechar sempre visível — arrastar nunca é o único jeito
 * de fechar. Foco preso, `Esc` e retorno de foco vêm do `p-drawer`.
 */
@Component({
  selector: 'ces-drawer',
  standalone: true,
  imports: [DrawerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .ces-drawer__header {
      display: grid;
      gap: 0.2rem;
      min-width: 0;
    }

    .ces-drawer__eyebrow {
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption);
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    .ces-drawer__title {
      margin: 0;
      color: var(--ces-foreground);
      font-size: var(--ces-font-title);
      line-height: 1.3;
    }

    .ces-drawer__description {
      margin: 0;
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption);
      line-height: 1.45;
    }

    /* Alça da folha inferior: centrada no painel inteiro, não só na coluna do título (que divide a linha com o Fechar). */
    .ces-drawer__handle {
      position: absolute;
      top: 0.5rem;
      left: 50%;
      width: 2.5rem;
      height: 0.25rem;
      border-radius: 999px;
      background: var(--ces-border-strong, var(--ces-border));
      transform: translateX(-50%);
    }

    .ces-drawer__header--compacto {
      padding-top: 0.5rem;
    }

    .ces-drawer__body {
      display: grid;
      gap: 1rem;
      min-width: 0;
    }

    .ces-drawer__footer {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: flex-end;
      gap: 0.5rem;
      width: 100%;
    }

    .ces-drawer__footer:empty {
      display: none;
    }
  `,
  template: `
    <p-drawer
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [position]="compact() ? 'bottom' : 'right'"
      [modal]="true"
      [blockScroll]="true"
      [dismissible]="true"
      [closeOnEscape]="true"
      [style]="drawerStyle()"
      [ariaCloseLabel]="closeLabel()"
      appendTo="body"
      (onHide)="closed.emit()"
    >
      <ng-template #header>
        <div class="ces-drawer__header" [class.ces-drawer__header--compacto]="compact()">
          @if (compact()) {
            <span class="ces-drawer__handle" aria-hidden="true"></span>
          }
          @if (eyebrow()) {
            <span class="ces-drawer__eyebrow">{{ eyebrow() }}</span>
          }
          <h2 class="ces-drawer__title" [id]="titleId">{{ title() }}</h2>
          @if (description()) {
            <p class="ces-drawer__description">{{ description() }}</p>
          }
        </div>
      </ng-template>

      <section class="ces-drawer__body" [attr.aria-labelledby]="titleId">
        <ng-content></ng-content>
      </section>

      <ng-template #footer>
        <div class="ces-drawer__footer">
          <ng-content select="[drawer-footer]"></ng-content>
        </div>
      </ng-template>
    </p-drawer>
  `
})
export class CesDrawerComponent {
  readonly visible = model(false);
  readonly title = input.required<string>();
  readonly eyebrow = input<string | undefined>();
  readonly description = input<string | undefined>();
  readonly size = input<CesDrawerSize>('md');
  readonly closeLabel = input('Fechar painel');

  readonly closed = output<void>();

  protected readonly titleId = `ces-drawer-title-${++drawerSequence}`;
  protected readonly compact = cesMediaQuery(CES_COMPACT_VIEWPORT_QUERY);

  /**
   * Superfície e texto vêm dos tokens Nexo: o overlay do Lara usa `surface.900`, que no esquema
   * escuro do preset (escala invertida) é claro — sem isto o painel ficava claro com texto claro.
   */
  private readonly surfaceStyle: Record<string, string> = {
    background: 'var(--ces-surface)',
    color: 'var(--ces-foreground)',
    borderColor: 'var(--ces-border)'
  };

  protected readonly drawerStyle = computed<Record<string, string>>((): Record<string, string> =>
    this.compact()
      ? {
          ...this.surfaceStyle,
          height: 'auto',
          maxHeight: '92dvh',
          borderTopLeftRadius: 'var(--ces-radius-lg)',
          borderTopRightRadius: 'var(--ces-radius-lg)'
        }
      : { ...this.surfaceStyle, width: `min(${DRAWER_WIDTHS[this.size()]}, 100vw)` }
  );
}
