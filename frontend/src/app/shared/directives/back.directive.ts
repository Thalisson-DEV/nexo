import { Directive, inject, input } from '@angular/core';
import { NavigationHistoryService } from '../../core/navigation/navigation-history.service';

/**
 * Voltar único da aplicação. Use em qualquer botão/link "Voltar" ou "Cancelar" que só
 * retorna à tela anterior:
 *
 * ```html
 * <p-button label="Voltar" appBack />                          <!-- fallback: data.backFallback ou rota pai -->
 * <p-button label="Voltar" appBack="/execucao/comercial" />    <!-- fallback explícito -->
 * ```
 *
 * Com histórico interno, volta exatamente ao estado anterior (`Location.back()`); em aba aberta
 * pelo app, fecha a aba; sem histórico (deep link/F5 sem pilha), navega para o fallback.
 */
@Directive({
  selector: '[appBack]',
  standalone: true,
  host: { '(click)': 'onClick($event)' },
})
export class BackDirective {
  private readonly history = inject(NavigationHistoryService);

  /** Fallback opcional (URL ou comandos do Router). Vazio = `data.backFallback` / rota pai. */
  readonly appBack = input<string | readonly unknown[] | null | undefined>('');

  protected onClick(event: Event): void {
    event.preventDefault();
    const fallback = this.appBack();
    this.history.back(fallback === '' || fallback == null ? undefined : fallback);
  }
}
