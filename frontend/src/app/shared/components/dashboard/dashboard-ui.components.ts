import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Peças compartilhadas pelos dashboards executivos (SST e Descontos).
 *
 * Nasceram no domínio de SST e foram promovidas para cá quando o terceiro dashboard
 * precisou delas — o estilo mora em `shared/styles/dashboard-shell.css`, importado
 * globalmente, e é o que garante que as três telas leiam como a mesma ferramenta.
 */

export type InsightTone = 'critical' | 'warning' | 'positive' | 'neutral';

/**
 * Uma leitura em linguagem de gestão. Os três campos são separados de propósito: o que
 * foi encontrado (`title`), por que aquilo importa (`description`) e o que fazer a
 * respeito (`action`). Um cartão que só mostra o número deixa a decisão inteira para
 * quem lê.
 */
export interface DashboardInsight {
  title: string;
  description: string;
  action: string;
  icon: string;
  tone: InsightTone;
}

export type MetricTone = 'primary' | 'amber' | 'red' | 'green' | 'slate';

@Component({
  selector: 'app-dashboard-metric-card',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="dash-metric" [attr.data-tone]="tone()" [class.dash-metric--interativo]="!!actionLabel()">
      <div class="dash-metric__topline">
        <span class="dash-metric__label">{{ label() }}</span>
        <span class="dash-metric__corner">
          <span class="dash-metric__extra"><ng-content select="[metric-action]"></ng-content></span>
          <span class="dash-metric__icon" aria-hidden="true"><i class="pi {{ icon() }}"></i></span>
        </span>
      </div>
      <strong class="dash-metric__value">{{ value() }}</strong>
      <p class="dash-metric__detail">{{ detail() }}</p>
      @if (progress() !== null) {
        <div class="dash-metric__track" aria-hidden="true">
          <span [style.width.%]="boundedProgress()"></span>
        </div>
      }
      @if (actionLabel()) {
        <!-- Link esticado: o cartão inteiro é a área de clique; ações extras ficam acima dele. -->
        <button type="button" class="dash-metric__hit" [attr.aria-label]="actionLabel() + ': ' + label() + ', ' + value()"
          (click)="activate.emit()"></button>
        <span class="dash-metric__cta" aria-hidden="true">{{ actionLabel() }} <i class="pi pi-arrow-right"></i></span>
      }
    </article>
  `
})
export class DashboardMetricCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly detail = input.required<string>();
  readonly icon = input.required<string>();
  readonly tone = input<MetricTone>('slate');
  readonly progress = input<number | null>(null);
  /** Torna o cartão clicável (drill-down) com este verbo ("Analisar"). */
  readonly actionLabel = input<string | undefined>();
  readonly activate = output<void>();

  protected boundedProgress(): number {
    return Math.max(0, Math.min(100, this.progress() ?? 0));
  }
}

@Component({
  selector: 'app-dashboard-insight-panel',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dash-briefing" [attr.aria-labelledby]="headingId()">
      <div class="dash-briefing__intro">
        <span class="dash-eyebrow dash-eyebrow--light">{{ eyebrow() }}</span>
        <h2 [id]="headingId()">{{ title() }}</h2>
        <p>{{ description() }}</p>
      </div>
      <div class="dash-briefing__signals">
        @for (insight of insights(); track insight.title) {
          <article class="dash-signal" [attr.data-tone]="insight.tone">
            <span class="dash-signal__marker" aria-hidden="true"><i class="pi {{ insight.icon }}"></i></span>
            <div>
              <span class="dash-signal__type">{{ toneLabel(insight.tone) }}</span>
              <h3>{{ insight.title }}</h3>
              <p>{{ insight.description }}</p>
              @if (actionable()) {
                <button type="button" class="dash-signal__action" (click)="actionSelected.emit($index)">
                  {{ insight.action }} <i class="pi pi-arrow-right"></i>
                </button>
              } @else {
                <strong>{{ insight.action }}</strong>
              }
            </div>
          </article>
        }
      </div>
    </section>
  `
})
export class DashboardInsightPanelComponent {
  readonly headingId = input.required<string>();
  readonly eyebrow = input.required<string>();
  readonly title = input.required<string>();
  readonly description = input.required<string>();
  readonly insights = input.required<DashboardInsight[]>();
  readonly actionable = input(false);
  readonly actionSelected = output<number>();

  protected toneLabel(tone: InsightTone): string {
    return {
      critical: 'Ação imediata',
      warning: 'Acompanhar',
      positive: 'Sinal positivo',
      neutral: 'Contexto'
    }[tone];
  }
}
