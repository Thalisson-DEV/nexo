import { DOCUMENT } from '@angular/common';
import { DestroyRef, Signal, inject, signal } from '@angular/core';

/** Largura abaixo da qual o Nexo troca layouts densos por versões de toque (cards, folhas inferiores). */
export const CES_COMPACT_VIEWPORT_QUERY = '(max-width: 39.99rem)';

/**
 * Signal que acompanha uma media query. Precisa rodar em contexto de injeção; sem `matchMedia`
 * (SSR, testes) devolve sempre `false`.
 */
export function cesMediaQuery(query: string): Signal<boolean> {
  const view = inject(DOCUMENT).defaultView;
  const media = view?.matchMedia?.(query);
  const matches = signal(media?.matches ?? false);
  if (media) {
    const listener = (event: MediaQueryListEvent) => matches.set(event.matches);
    media.addEventListener?.('change', listener);
    inject(DestroyRef).onDestroy(() => media.removeEventListener?.('change', listener));
  }
  return matches.asReadonly();
}
