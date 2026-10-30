import { Injectable, InjectionToken, Signal, signal } from '@angular/core';

const STORAGE_KEY = 'ces_single_key_shortcuts';

function readEnabled(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) !== 'off';
  } catch {
    return true;
  }
}

/**
 * Preferência "Atalhos de uma tecla" (WCAG 2.1.4), lembrada neste dispositivo como o tema.
 * Ligada por padrão; desligada, as listagens ignoram `/`, `F` e `N` e deixam de anunciá-los.
 */
@Injectable({ providedIn: 'root' })
export class CesShortcutsPreference {
  private readonly state = signal(readEnabled());
  readonly enabled = this.state.asReadonly();

  setEnabled(value: boolean): void {
    this.state.set(value);
    try {
      localStorage.setItem(STORAGE_KEY, value ? 'on' : 'off');
    } catch {
      // preferência apenas em memória
    }
  }
}

/** Contexto provido pela diretiva `cesListShortcuts` aos componentes da listagem. */
export interface CesListShortcutsContext {
  /** Atalhos ativos nesta página (preferência ligada). */
  readonly enabled: Signal<boolean>;
}

export const CES_LIST_SHORTCUTS = new InjectionToken<CesListShortcutsContext>('CES_LIST_SHORTCUTS');
