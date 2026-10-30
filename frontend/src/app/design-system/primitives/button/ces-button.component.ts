import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, computed, inject, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { CesActionSize, CesActionVariant } from '../../core';

@Component({
  selector: 'ces-button',
  standalone: true,
  imports: [ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-button
      [buttonProps]="semAutofocus"
      [label]="label()"
      [icon]="primeIcon()"
      [iconPos]="iconPosition()"
      [disabled]="disabled() || loading()"
      [loading]="loading()"
      [severity]="primeSeverity()"
      [outlined]="variant() === 'outline'"
      [text]="variant() === 'ghost'"
      [link]="variant() === 'link'"
      [size]="primeSize()"
      [fluid]="fluid()"
      [type]="type()"
      [ariaLabel]="ariaLabel() || label()"
      [styleClass]="styleClass()"
      (onClick)="pressed.emit()"
    ></p-button>
  `
})
export class CesButtonComponent {
  /**
   * O `p-button` repassa `autofocus || buttonProps?.autofocus` ao `pAutoFocus`, que só remove o
   * atributo com `false` exato: sem isto todo botão sai com `autofocus` e o navegador foca o
   * primeiro que aparece depois da carga (rolando a página até ele).
   */
  protected readonly semAutofocus = { autofocus: false };

  readonly label = input<string>('');
  readonly icon = input<string | undefined>();
  readonly iconPosition = input<'left' | 'right'>('left');
  readonly variant = input<CesActionVariant>('primary');
  readonly size = input<CesActionSize>('md');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  readonly loading = input(false);
  readonly disabled = input(false);
  readonly fluid = input(false);
  readonly ariaLabel = input<string | undefined>();
  readonly className = input('');
  /** Atalho de teclado anunciado no botão (`aria-keyshortcuts`), por exemplo `N`. */
  readonly keyShortcut = input<string | undefined>();

  readonly pressed = output<void>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    // O p-button não repassa atributos arbitrários ao <button>; o atalho é aplicado após a renderização.
    afterRenderEffect(() => {
      const atalho = this.keyShortcut();
      const botao = this.host.nativeElement.querySelector('button');
      if (!botao) return;
      if (atalho) botao.setAttribute('aria-keyshortcuts', atalho);
      else botao.removeAttribute('aria-keyshortcuts');
    });
  }

  protected readonly primeIcon = computed(() => {
    const icon = this.icon()?.trim();
    if (!icon) return undefined;
    if (icon.startsWith('pi-')) return `pi ${icon}`;
    return icon;
  });

  protected readonly primeSeverity = computed(() => {
    switch (this.variant()) {
      case 'secondary':
      case 'outline':
      case 'ghost':
      case 'link':
        return 'secondary' as const;
      case 'destructive':
        return 'danger' as const;
      case 'success':
        return 'success' as const;
      default:
        return 'primary' as const;
    }
  });

  protected readonly primeSize = computed(() => {
    const size = this.size();
    if (size === 'sm' || size === 'icon-sm') return 'small' as const;
    if (size === 'lg' || size === 'icon-lg') return 'large' as const;
    return undefined;
  });

  protected readonly styleClass = computed(() => {
    const classes = ['ces-button', `ces-button--${this.variant()}`, `ces-button--${this.size()}`];
    if (this.className()) classes.push(this.className());
    return classes.join(' ');
  });
}
