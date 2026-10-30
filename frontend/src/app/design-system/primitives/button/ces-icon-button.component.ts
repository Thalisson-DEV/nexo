import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { CesActionSize, CesActionVariant } from '../../core';

@Component({
  selector: 'ces-icon-button',
  standalone: true,
  imports: [ButtonModule, TooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-button
      [buttonProps]="semAutofocus"
      [icon]="primeIcon()"
      [rounded]="true"
      [disabled]="disabled() || loading()"
      [loading]="loading()"
      [severity]="primeSeverity()"
      [outlined]="variant() === 'outline'"
      [text]="variant() === 'ghost'"
      [size]="primeSize()"
      [ariaLabel]="label()"
      [styleClass]="styleClass()"
      [pTooltip]="tooltip() || label()"
      [tooltipPosition]="tooltipPosition()"
      (onClick)="pressed.emit()"
    ></p-button>
  `
})
export class CesIconButtonComponent {
  /**
   * O `p-button` repassa `autofocus || buttonProps?.autofocus` ao `pAutoFocus`, que só remove o
   * atributo com `false` exato: sem isto todo botão sai com `autofocus` e o navegador foca o
   * primeiro que aparece depois da carga (rolando a página até ele).
   */
  protected readonly semAutofocus = { autofocus: false };

  readonly icon = input<string>('');
  readonly label = input<string>('');
  readonly tooltip = input<string | undefined>();
  readonly tooltipPosition = input<'top' | 'bottom' | 'left' | 'right'>('top');
  readonly variant = input<CesActionVariant>('ghost');
  readonly size = input<Extract<CesActionSize, 'icon-sm' | 'icon-md' | 'icon-lg'>>('icon-md');
  readonly loading = input(false);
  readonly disabled = input(false);
  readonly className = input('');

  readonly pressed = output<void>();

  protected readonly primeIcon = computed(() => {
    const icon = this.icon()?.trim();
    if (!icon) return '';
    if (icon.startsWith('pi-')) return `pi ${icon}`;
    return icon;
  });

  protected readonly primeSeverity = computed(() => {
    switch (this.variant()) {
      case 'destructive':
        return 'danger' as const;
      case 'success':
        return 'success' as const;
      case 'primary':
        return 'primary' as const;
      default:
        return 'secondary' as const;
    }
  });

  protected readonly primeSize = computed(() => {
    if (this.size() === 'icon-sm') return 'small' as const;
    if (this.size() === 'icon-lg') return 'large' as const;
    return undefined;
  });

  protected readonly styleClass = computed(() => {
    const classes = [
      'ces-icon-button',
      `ces-icon-button--${this.variant()}`,
      `ces-icon-button--${this.size()}`
    ];
    if (this.className()) classes.push(this.className());
    return classes.join(' ');
  });
}
