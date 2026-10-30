import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CesTone } from '../../core';

/** Limiar a partir do qual o atingimento deixa de ser crítico. Compartilhado com o Copiloto. */
export const CES_ATTAINMENT_WARNING_THRESHOLD = 80;

export type CesAttainmentBand = 'success' | 'warning' | 'danger';

/** Faixa de atingimento de meta: ≥ 100% sucesso; 80–99% atenção; < 80% crítico. */
export function cesAttainmentBand(percent: number): CesAttainmentBand {
  if (percent >= 100) return 'success';
  if (percent >= CES_ATTAINMENT_WARNING_THRESHOLD) return 'warning';
  return 'danger';
}

const BAND_ICON: Record<CesAttainmentBand, string> = {
  success: 'pi pi-check-circle',
  warning: 'pi pi-exclamation-circle',
  danger: 'pi pi-times-circle'
};

const BAND_TEXT: Record<CesAttainmentBand, string> = {
  success: 'meta atingida',
  warning: 'abaixo da meta',
  danger: 'crítico'
};

/**
 * Medidor de atingimento (realizado/meta) com tom semântico, ícone e texto — cor nunca é o único
 * indicador. A barra satura em 100%; o percentual exibido não.
 */
@Component({
  selector: 'ces-meter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-meter',
    '[attr.data-tone]': 'tone()'
  },
  styles: `
    :host {
      display: grid;
      gap: 0.25rem;
      min-width: 0;
      --ces-meter-color: var(--ces-info);
      --ces-meter-text: var(--ces-info-text);
    }

    :host([data-tone='success']) { --ces-meter-color: var(--ces-success); --ces-meter-text: var(--ces-success-text); }
    :host([data-tone='warning']) { --ces-meter-color: var(--ces-warning); --ces-meter-text: var(--ces-warning-text); }
    :host([data-tone='danger']) { --ces-meter-color: var(--ces-danger); --ces-meter-text: var(--ces-danger-text); }
    :host([data-tone='neutral']) { --ces-meter-color: var(--ces-foreground-muted); --ces-meter-text: var(--ces-foreground-muted); }

    .ces-meter__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      font-size: var(--ces-font-caption);
      font-variant-numeric: tabular-nums;
    }

    .ces-meter__detail {
      color: var(--ces-foreground-muted);
    }

    .ces-meter__value {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      color: var(--ces-meter-text);
      font-weight: 700;
    }

    .ces-meter__track {
      height: 0.375rem;
      overflow: hidden;
      border-radius: 999px;
      background: var(--ces-surface-muted);
    }

    .ces-meter__bar {
      display: block;
      height: 100%;
      border-radius: inherit;
      background: var(--ces-meter-color);
      transition: width 200ms ease;
    }

    @media (prefers-reduced-motion: reduce) {
      .ces-meter__bar { transition: none; }
    }
  `,
  template: `
    <div class="ces-meter__head">
      @if (detail()) {
        <span class="ces-meter__detail">{{ detail() }}</span>
      }
      <span class="ces-meter__value">
        <i [class]="icon()" aria-hidden="true"></i>
        {{ percentLabel() }}
        <span class="ces-sr-only">— {{ bandText() }}</span>
      </span>
    </div>
    <div
      class="ces-meter__track"
      role="meter"
      aria-valuemin="0"
      aria-valuemax="100"
      [attr.aria-valuenow]="clamped()"
      [attr.aria-valuetext]="percentLabel() + ' — ' + bandText()"
      [attr.aria-label]="label()"
    >
      <span class="ces-meter__bar" [style.width.%]="clamped()"></span>
    </div>
  `
})
export class CesMeterComponent {
  /** Percentual atingido (0–100+). */
  readonly value = input.required<number>();
  /** Nome acessível do medidor (ex.: "Atingimento da meta de Maria"). */
  readonly label = input('Atingimento da meta');
  /** Texto auxiliar à esquerda (ex.: "42/40"). */
  readonly detail = input<string | undefined>();
  /** Força um tom; padrão = faixa de atingimento. */
  readonly toneOverride = input<CesTone | undefined>(undefined, { alias: 'tone' });

  protected readonly band = computed(() => cesAttainmentBand(this.value() ?? 0));
  protected readonly tone = computed<CesTone>(() => this.toneOverride() ?? this.band());
  protected readonly clamped = computed(() => Math.max(0, Math.min(100, Math.round(this.value() ?? 0))));
  protected readonly percentLabel = computed(
    () => `${(this.value() ?? 0).toLocaleString('pt-BR', { maximumFractionDigits: 0 })}%`
  );
  protected readonly icon = computed(() => BAND_ICON[this.band()]);
  protected readonly bandText = computed(() => BAND_TEXT[this.band()]);
}
