import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject, input } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';
import { destacarMarcacoes } from './mencoes.util';

/**
 * Exibe um texto escrito por usuário destacando as marcações (`@LOGIN`).
 *
 * O HTML é escapado antes do destaque (ver `destacarMarcacoes`) e só produz `<span class="...">`,
 * que o sanitizador do Angular preserva — não há `bypassSecurityTrust` aqui, de propósito.
 *
 * Encapsulamento desligado porque o conteúdo entra por `innerHTML` e, assim, não recebe o atributo
 * de escopo do componente. Os nomes de classe são específicos o bastante para não colidir.
 */
@Component({
  selector: 'app-texto-marcacoes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  template: `<span [innerHTML]="html()"></span>`,
  styles: [
    `
      .marcacao {
        font-weight: 600;
        color: var(--p-primary-600);
      }

      /* "Isto é com você": realce mais forte para quem reabre o chamado depois. */
      .marcacao-voce {
        border-radius: 0.25rem;
        padding: 0.05rem 0.25rem;
        background: var(--p-primary-100);
        color: var(--p-primary-800);
      }

      .app-dark .marcacao {
        color: var(--p-primary-400);
      }

      .app-dark .marcacao-voce {
        background: color-mix(in srgb, var(--p-primary-500) 25%, transparent);
        color: var(--p-primary-200);
      }
    `
  ]
})
export class TextoMarcacoesComponent {
  private readonly auth = inject(AuthService);

  readonly texto = input<string | null | undefined>('');

  protected readonly html = computed(() =>
    destacarMarcacoes(this.texto(), this.auth.currentUser()?.login)
  );
}
