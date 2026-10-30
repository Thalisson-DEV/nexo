import { DestroyRef, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ViewStateStore } from './view-state.store';

/**
 * Lembra as opções selecionadas em lookups remotos (equipe, material...) para que, ao voltar,
 * o select mostre o rótulo e não só o id que veio da URL. Salva ao sair da tela e no `pagehide`.
 *
 * ```ts
 * private readonly equipesSelecionadas = rememberSelection('equipe', () =>
 *   this.equipes().filter((e) => e.id === this.filters.idEquipe));
 * // ao aplicar o estado da URL:
 * this.equipeLookup.setSeed(this.equipesSelecionadas());
 * ```
 */
export function rememberSelection<T>(id: string, read: () => readonly T[]): () => T[] {
  const store = inject(ViewStateStore);
  const key = ViewStateStore.key(inject(Router).url, `selecao:${id}`);
  const save = () => {
    const items = read();
    if (items.length) store.set(key, [...items]);
    else store.remove(key);
  };
  window.addEventListener('pagehide', save);
  inject(DestroyRef).onDestroy(() => {
    window.removeEventListener('pagehide', save);
    save();
  });
  return () => store.get<T[]>(key) ?? [];
}
