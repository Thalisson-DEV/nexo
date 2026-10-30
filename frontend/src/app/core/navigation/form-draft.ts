import { DestroyRef, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, debounceTime } from 'rxjs';
import { ViewStateStore } from './view-state.store';

export interface FormDraftOptions<T> {
  /** Identificador dentro da rota (ex.: `nova`, ou o id em edição). Default: `rascunho`. */
  id?: string;
  /** Rota-base da chave. Default: caminho atual (sem query). */
  route?: string;
  /** Lê o valor serializável do formulário (sem File/Blob). */
  read: () => T;
  /** Aplica o rascunho no formulário. */
  write: (value: T) => void;
  /** Mudanças do formulário (ex.: `form.valueChanges`) para salvar com debounce. */
  changes?: Observable<unknown>;
  debounceMs?: number;
  /**
   * Espelhar em `sessionStorage` (sobrevive ao F5). Desligue quando o formulário tem dado
   * pessoal (CPF, nome, dados de saúde/vítima): o rascunho fica só em memória.
   */
  persist?: boolean;
  ttlMs?: number;
}

export interface FormDraft<T> {
  /** Aplica o rascunho salvo, se houver. Chame depois de montar o formulário. */
  restore(): boolean;
  save(): void;
  /** Descarta o rascunho (salvou com sucesso ou cancelou explicitamente). */
  clear(): void;
  peek(): T | undefined;
}

const DEFAULT_TTL_MS = 8 * 60 * 60 * 1000;

/**
 * Rascunho de formulário no `ViewStateStore`.
 *
 * Salva com debounce (quando `changes` é informado), ao sair da tela e no `pagehide`;
 * restaura no init. Depois de `clear()` nada mais é salvo por esta instância.
 * Navegação intencional (menu/breadcrumb/início) limpa o rascunho da rota.
 */
export function createFormDraft<T>(options: FormDraftOptions<T>): FormDraft<T> {
  const store = inject(ViewStateStore);
  const router = inject(Router);
  const destroyRef = inject(DestroyRef);

  const key = ViewStateStore.key(options.route ?? router.url, options.id ?? 'rascunho');
  const writeOptions = { persist: options.persist ?? true, ttlMs: options.ttlMs ?? DEFAULT_TTL_MS };
  let discarded = false;
  let restoring = false;
  // Valor do formulário recém-montado: sem alteração não há rascunho a guardar (evita que uma
  // edição só aberta e abandonada sobrescreva, depois, dados mais novos do servidor).
  let baseline: string | null = null;

  const draft: FormDraft<T> = {
    restore() {
      baseline = safeJson(options.read());
      const value = store.get<T>(key);
      if (value === undefined) return false;
      restoring = true;
      try {
        options.write(value);
      } finally {
        restoring = false;
      }
      return true;
    },
    save() {
      // Antes de restore() o formulário ainda não foi montado: nada a guardar.
      if (discarded || restoring || baseline === null) return;
      const value = options.read();
      if (safeJson(value) === baseline) {
        store.remove(key);
        return;
      }
      store.set(key, value, writeOptions);
    },
    clear() {
      discarded = true;
      store.remove(key);
    },
    peek() {
      return store.get<T>(key);
    },
  };

  const subscription = options.changes
    ?.pipe(debounceTime(options.debounceMs ?? 400))
    .subscribe(() => draft.save());
  const onPageHide = () => draft.save();
  window.addEventListener('pagehide', onPageHide);
  destroyRef.onDestroy(() => {
    subscription?.unsubscribe();
    window.removeEventListener('pagehide', onPageHide);
    draft.save();
  });

  return draft;
}

function safeJson(value: unknown): string | null {
  try {
    return JSON.stringify(value);
  } catch {
    return null;
  }
}
