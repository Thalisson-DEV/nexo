import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CesSurfaceVariant } from '../../core';

@Component({
  selector: 'ces-surface',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-surface',
    '[attr.data-variant]': 'variant()',
    '[class.ces-surface--padded]': 'padded()',
    '[attr.tabindex]': "variant() === 'interactive' ? 0 : null"
  },
  template: `<ng-content></ng-content>`
})
export class CesSurfaceComponent {
  readonly variant = input<CesSurfaceVariant>('outlined');
  readonly padded = input(true);
}
