import { Provider, Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';

/** Página de resposta simples (`PagedResponse`) para os testes das listagens. */
export function paginaTeste<T>(content: T[], totalElements = content.length) {
  return of({ content, page: { size: 25, number: 0, totalElements, totalPages: 1 } });
}

/**
 * Abre uma tela de listagem roteada em `caminho` com os provedores simulados informados
 * (serviços, auth), já com a primeira carga feita.
 */
export async function abrirListagem<T>(
  componente: Type<T>,
  caminho: string,
  url: string,
  providers: Provider[],
  data: Record<string, unknown> = {}
): Promise<{ harness: RouterTestingHarness; componente: T; el: HTMLElement }> {
  TestBed.configureTestingModule({
    providers: [
      provideNoopAnimations(),
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([{ path: caminho, component: componente, data }]),
      MessageService,
      ConfirmationService,
      ...providers
    ]
  });
  const harness = await RouterTestingHarness.create();
  const instancia = await harness.navigateByUrl(url, componente);
  harness.detectChanges();
  await harness.fixture.whenStable();
  harness.detectChanges();
  return { harness, componente: instancia, el: harness.routeNativeElement as HTMLElement };
}

export function chipsDoPainel(el: HTMLElement): string[] {
  return Array.from(el.querySelectorAll('.ces-filter-panel__chip')).map((c) => c.textContent!.trim());
}

/** Dispara uma tecla de atalho no documento (fora de campos). */
export function tecla(key: string): void {
  (document.activeElement as HTMLElement | null)?.blur?.();
  document.body.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
}
