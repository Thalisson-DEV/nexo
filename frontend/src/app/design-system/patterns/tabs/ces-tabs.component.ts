import {
  ChangeDetectionStrategy,
  Component,
  contentChildren,
  input,
  model
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { CesTabPanelDirective, CesTabValue } from './ces-tab-panel.directive';

export interface CesTabItem {
  value: CesTabValue;
  label: string;
  icon?: string;
  badge?: string | number;
  disabled?: boolean;
}

@Component({
  selector: 'ces-tabs',
  standalone: true,
  imports: [NgTemplateOutlet, Tabs, TabList, Tab, TabPanels, TabPanel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'ces-tabs' },
  styles: `
    :host {
      display: block;
      min-width: 0;
    }

    .ces-tabs__label {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      font-weight: 700;
    }

    .ces-tabs__badge {
      min-width: 1.35rem;
      border-radius: 999px;
      padding: 0.15rem 0.4rem;
      background: var(--ces-surface-muted);
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption);
      line-height: 1.1;
      text-align: center;
    }

    .ces-tabs__empty-panel {
      padding: 1rem;
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-body);
    }
  `,
  template: `
    <p-tabs
      [value]="value()"
      (valueChange)="value.set($event)"
      [scrollable]="scrollable()"
      [lazy]="lazy()"
      [selectOnFocus]="selectOnFocus()"
      [showNavigators]="showNavigators()"
    >
      <p-tablist [attr.aria-label]="ariaLabel()">
        @for (tab of tabs(); track tab.value) {
          <p-tab [value]="tab.value" [disabled]="tab.disabled || false">
            <span class="ces-tabs__label">
              @if (tab.icon) {
                <i [class]="tab.icon" aria-hidden="true"></i>
              }
              <span>{{ tab.label }}</span>
              @if (tab.badge !== undefined) {
                <span class="ces-tabs__badge">{{ tab.badge }}</span>
              }
            </span>
          </p-tab>
        }
      </p-tablist>

      <p-tabpanels>
        @for (tab of tabs(); track tab.value) {
          <p-tabpanel [value]="tab.value" [lazy]="lazy()">
            @if (panelFor(tab.value); as panel) {
              <ng-container [ngTemplateOutlet]="panel.template"></ng-container>
            } @else {
              <div class="ces-tabs__empty-panel">
                Nenhum conteúdo foi definido para {{ tab.label }}.
              </div>
            }
          </p-tabpanel>
        }
      </p-tabpanels>
    </p-tabs>
  `
})
export class CesTabsComponent {
  readonly value = model<CesTabValue | undefined>(undefined);
  readonly tabs = input.required<readonly CesTabItem[]>();
  readonly ariaLabel = input('Seções do conteúdo');
  readonly lazy = input(true);
  readonly scrollable = input(true);
  readonly selectOnFocus = input(false);
  readonly showNavigators = input(true);

  private readonly panels = contentChildren(CesTabPanelDirective);

  protected panelFor(value: CesTabValue): CesTabPanelDirective | undefined {
    return this.panels().find((panel) => panel.value() === value);
  }
}
