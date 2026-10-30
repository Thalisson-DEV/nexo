import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { CesContentState, CesTone } from '../../core';
import { CesButtonComponent } from '../../primitives/button';

interface StatePresentation {
  icon: string;
  tone: CesTone;
  title: string;
  description: string;
}

@Component({
  selector: 'ces-async-state',
  standalone: true,
  imports: [ProgressSpinnerModule, CesButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-async-state',
    '[attr.data-state]': 'state()',
    '[attr.data-tone]': 'presentation().tone',
    '[attr.aria-busy]': "state() === 'loading' || state() === 'refreshing'"
  },
  template: `
    @if (state() === 'loading' || state() === 'refreshing') {
      <p-progress-spinner
        strokeWidth="4"
        [style]="{ width: compact() ? '1.75rem' : '2.5rem', height: compact() ? '1.75rem' : '2.5rem' }"
        ariaLabel="Carregando"
      ></p-progress-spinner>
    } @else {
      <span class="ces-async-state__icon" aria-hidden="true">
        <i class="pi {{ icon() || presentation().icon }}"></i>
      </span>
    }

    <div class="ces-async-state__copy">
      <strong>{{ title() || presentation().title }}</strong>
      <p>{{ description() || presentation().description }}</p>
    </div>

    @if (actionLabel() || secondaryActionLabel()) {
      <div class="ces-async-state__actions">
        @if (actionLabel()) {
          <ces-button
            [label]="actionLabel()!"
            [icon]="actionIcon()"
            [variant]="state() === 'error' ? 'primary' : 'secondary'"
            size="sm"
            (pressed)="action.emit()"
          ></ces-button>
        }
        @if (secondaryActionLabel()) {
          <ces-button
            [label]="secondaryActionLabel()!"
            [icon]="secondaryActionIcon()"
            variant="ghost"
            size="sm"
            (pressed)="secondaryAction.emit()"
          ></ces-button>
        }
      </div>
    }
  `
})
export class CesAsyncStateComponent {
  readonly state = input<Exclude<CesContentState, 'idle' | 'ready'>>('loading');
  readonly title = input<string | undefined>();
  readonly description = input<string | undefined>();
  readonly actionLabel = input<string | undefined>();
  readonly actionIcon = input<string | undefined>();
  readonly compact = input(false);
  /** Ícone (PrimeIcons, ex.: `pi-filter-slash`) no lugar do padrão do estado. */
  readonly icon = input<string | undefined>();
  /** Segunda ação, mais discreta (ex.: "Limpar filtros" ao lado de "Ajustar filtros"). */
  readonly secondaryActionLabel = input<string | undefined>();
  readonly secondaryActionIcon = input<string | undefined>();

  readonly action = output<void>();
  readonly secondaryAction = output<void>();

  protected readonly presentation = computed<StatePresentation>(() => {
    const presentations: Record<Exclude<CesContentState, 'idle' | 'ready'>, StatePresentation> = {
      loading: {
        icon: 'pi-spinner',
        tone: 'brand',
        title: 'Carregando dados',
        description: 'Aguarde enquanto buscamos as informações.'
      },
      refreshing: {
        icon: 'pi-sync',
        tone: 'brand',
        title: 'Atualizando dados',
        description: 'Os dados atuais continuam disponíveis durante a atualização.'
      },
      empty: {
        icon: 'pi-inbox',
        tone: 'neutral',
        title: 'Nenhum registro encontrado',
        description: 'Crie o primeiro registro ou ajuste os filtros aplicados.'
      },
      error: {
        icon: 'pi-exclamation-triangle',
        tone: 'danger',
        title: 'Não foi possível carregar',
        description: 'Verifique a conexão e tente novamente.'
      },
      'offline-cached': {
        icon: 'pi-cloud-download',
        tone: 'warning',
        title: 'Dados disponíveis offline',
        description: 'Você está vendo a última versão salva neste dispositivo.'
      },
      stale: {
        icon: 'pi-clock',
        tone: 'warning',
        title: 'Dados aguardando atualização',
        description: 'A conexão será usada para atualizar estas informações assim que possível.'
      }
    };

    return presentations[this.state()];
  });
}
