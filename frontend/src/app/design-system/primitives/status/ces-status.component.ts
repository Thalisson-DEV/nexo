import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CesTone } from '../../core';

@Component({
  selector: 'ces-status',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-status',
    '[attr.data-tone]': 'tone()',
    '[attr.data-emphasis]': 'emphasis()'
  },
  template: `
    @if (primeIcon()) {
      <i [class]="'ces-status__icon ' + primeIcon()" aria-hidden="true"></i>
    }
    <span><ng-content></ng-content></span>
  `
})
export class CesStatusComponent {
  readonly tone = input<CesTone>('neutral');
  readonly emphasis = input<'soft' | 'outline' | 'solid'>('soft');
  readonly icon = input<string | undefined>();

  protected readonly primeIcon = computed(() => {
    const icon = this.icon()?.trim();
    if (!icon) return '';
    if (icon.startsWith('pi ')) return icon;
    if (icon.startsWith('pi-')) return `pi ${icon}`;
    return `pi ${icon}`;
  });
}
