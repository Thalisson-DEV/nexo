import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Posição em ranking. Do 1º ao 3º lugar o selo ganha destaque (tokens `--ces-rank-*`) e ícone de
 * medalha; o texto "1º lugar" sempre acompanha para leitores de tela — cor não é o único indicador.
 */
@Component({
  selector: 'ces-rank-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-rank-badge',
    '[attr.data-podium]': 'podium()',
    '[attr.title]': 'label()'
  },
  styles: `
    :host {
      display: inline-grid;
      flex: none;
      place-items: center;
      width: 1.75rem;
      height: 1.75rem;
      border: 1px solid transparent;
      border-radius: var(--ces-radius-sm);
      background: var(--ces-surface-muted);
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption);
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }

    :host([data-podium='1']) { background: var(--ces-rank-1-soft); border-color: var(--ces-rank-1); color: var(--ces-rank-1-text); }
    :host([data-podium='2']) { background: var(--ces-rank-2-soft); border-color: var(--ces-rank-2); color: var(--ces-rank-2-text); }
    :host([data-podium='3']) { background: var(--ces-rank-3-soft); border-color: var(--ces-rank-3); color: var(--ces-rank-3-text); }
  `,
  template: `
    @if (podium()) {
      <i class="pi pi-trophy" aria-hidden="true"></i>
    } @else {
      <span aria-hidden="true">{{ position() }}</span>
    }
    <span class="ces-sr-only">{{ label() }}</span>
  `
})
export class CesRankBadgeComponent {
  readonly position = input.required<number>();

  protected readonly podium = computed(() => (this.position() >= 1 && this.position() <= 3 ? String(this.position()) : null));
  protected readonly label = computed(() => `${this.position()}º lugar`);
}
