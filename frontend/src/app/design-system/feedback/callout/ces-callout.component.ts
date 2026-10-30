import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CesTone } from '../../core';

interface CalloutPresentation {
  icon: string;
  label: string;
}

/**
 * Bloco de mensagem contextual para conteúdo editorial e instrucional.
 *
 * Diferente de `ces-status` (rótulo inline) e de `ces-async-state` (estado de carga de
 * uma região), o callout carrega texto explicativo que precisa continuar legível em
 * papel. O tom nunca é o único indicador: um rótulo textual acompanha a cor e o ícone.
 */
@Component({
  selector: 'ces-callout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-callout',
    '[attr.data-tone]': 'tone()',
    '[class.ces-callout--dense]': 'dense()'
  },
  template: `
    <span class="ces-callout__icon" aria-hidden="true">
      <i class="pi {{ presentation().icon }}"></i>
    </span>

    <div class="ces-callout__body">
      <p class="ces-callout__heading">
        <span class="ces-callout__label">{{ label() || presentation().label }}</span>
        @if (title()) {
          <strong class="ces-callout__title">{{ title() }}</strong>
        }
      </p>

      <div class="ces-callout__content">
        <ng-content></ng-content>
      </div>
    </div>
  `
})
export class CesCalloutComponent {
  readonly tone = input<CesTone>('info');
  readonly title = input<string | undefined>();
  readonly icon = input<string | undefined>();
  readonly label = input<string | undefined>();
  readonly dense = input(false);

  protected readonly presentation = computed<CalloutPresentation>(() => {
    const presentations: Record<CesTone, CalloutPresentation> = {
      neutral: { icon: 'pi-info-circle', label: 'Nota' },
      brand: { icon: 'pi-lightbulb', label: 'Destaque' },
      info: { icon: 'pi-info-circle', label: 'Informação' },
      success: { icon: 'pi-check-circle', label: 'Confirmado' },
      warning: { icon: 'pi-exclamation-triangle', label: 'Atenção' },
      danger: { icon: 'pi-times-circle', label: 'Crítico' }
    };

    const presentation = presentations[this.tone()];
    const custom = this.icon()?.trim();
    if (!custom) return presentation;

    return { ...presentation, icon: custom.replace(/^pi\s+/, '') };
  });
}
