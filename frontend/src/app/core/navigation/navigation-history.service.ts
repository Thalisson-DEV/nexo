import { Injectable, inject } from '@angular/core';
import { Location } from '@angular/common';
import {
  ActivatedRouteSnapshot,
  NavigationCancel,
  NavigationEnd,
  NavigationError,
  NavigationStart,
  Router,
} from '@angular/router';

const STORAGE_KEY = 'ces.nav';
const ENTRY_STATE_KEY = 'cesEntry';

export interface HistoryEntry {
  readonly id: string;
  readonly url: string;
}

interface PendingNavigation {
  trigger: NavigationStart['navigationTrigger'];
  replaceUrl: boolean;
  skipLocationChange: boolean;
  restoredEntryId?: string;
}

interface PersistedStack {
  entries: HistoryEntry[];
  index: number;
}

/**
 * Pilha de navegação interna da aplicação.
 *
 * Cada entrada do histórico do navegador recebe um id (`history.state.cesEntry`). O Router
 * preserva chaves extras do `history.state` em popstate e no F5, então o id permite saber,
 * com segurança, se existe uma entrada anterior DENTRO do app — inclusive após recarregar.
 * A pilha é espelhada em `sessionStorage` (por aba).
 *
 * É o único lugar do app autorizado a chamar `Location.back()` / `window.history`
 * (regra verificada por `tools/state-restoration/check-back-navigation.mjs`).
 */
@Injectable({ providedIn: 'root' })
export class NavigationHistoryService {
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  private entries: HistoryEntry[] = [];
  private index = -1;
  private readonly pending = new Map<number, PendingNavigation>();
  private lastTrigger: NavigationStart['navigationTrigger'] | null = null;
  private lastPathChanged = true;
  private lastRestored = false;
  private started = false;
  private committedOnce = false;

  /** Inicializado no bootstrap (provideAppInitializer) para ver a navegação inicial. */
  start(): void {
    if (this.started) return;
    this.started = true;
    const persisted = readPersisted();
    if (persisted) {
      this.entries = persisted.entries;
      this.index = persisted.index;
    }

    this.router.events.subscribe((event) => {
      if (event instanceof NavigationStart) {
        const navigation = this.router.currentNavigation();
        const extrasState = navigation?.extras.state as Record<string, unknown> | undefined;
        const restored = (event.restoredState as Record<string, unknown> | null) ?? extrasState;
        this.pending.set(event.id, {
          trigger: event.navigationTrigger,
          replaceUrl: !!navigation?.extras.replaceUrl,
          skipLocationChange: !!navigation?.extras.skipLocationChange,
          restoredEntryId: typeof restored?.[ENTRY_STATE_KEY] === 'string'
            ? (restored[ENTRY_STATE_KEY] as string)
            : undefined,
        });
      } else if (event instanceof NavigationEnd) {
        this.commit(event);
      } else if (event instanceof NavigationCancel || event instanceof NavigationError) {
        this.pending.delete(event.id);
      }
    });
  }

  /** Id da entrada atual do histórico (estável em replaceUrl, popstate e F5). */
  currentEntryId(): string | null {
    return this.entries[this.index]?.id ?? null;
  }

  currentEntry(): HistoryEntry | null {
    return this.entries[this.index] ?? null;
  }

  previousEntry(): HistoryEntry | null {
    return this.index > 0 ? this.entries[this.index - 1] : null;
  }

  canGoBack(): boolean {
    return this.index > 0;
  }

  /** A última navegação veio do botão voltar/avançar do navegador (ou de `back()`). */
  wasPopstate(): boolean {
    return this.lastTrigger === 'popstate';
  }

  /**
   * A última navegação voltou a uma entrada conhecida (voltar/avançar do navegador ou F5
   * numa entrada verificada): telas e scroll devem reaparecer como estavam.
   */
  wasRestored(): boolean {
    return this.lastRestored;
  }

  /** A última navegação mudou o caminho (e não só os queryParams). */
  pathChanged(): boolean {
    return this.lastPathChanged;
  }

  /**
   * Voltar único da aplicação:
   * 1. há entrada anterior dentro do app → `Location.back()` (estado exato restaurado);
   * 2. a aba foi aberta pelo próprio app (relatório em nova aba) → fecha a aba e o usuário
   *    reencontra a aba de origem intacta;
   * 3. caso contrário (deep link, link colado, nova aba sem opener) → fallback.
   */
  back(fallback?: string | readonly unknown[]): void {
    if (this.canGoBack()) {
      this.location.back();
      return;
    }
    if (openedByThisApp()) {
      window.close();
      // Alguns navegadores recusam o close; nesse caso segue para o fallback.
      if (!window.closed) this.navigateFallback(fallback);
      return;
    }
    this.navigateFallback(fallback);
  }

  /** `data.backFallback` da rota mais profunda que o define; senão, a URL sem o último segmento. */
  resolveFallback(): string {
    let snapshot: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    let fallback: string | null = null;
    let params: Record<string, string> = {};
    while (snapshot) {
      params = { ...params, ...snapshot.params };
      const declared = snapshot.data?.['backFallback'];
      if (typeof declared === 'string') fallback = declared;
      snapshot = snapshot.firstChild;
    }
    if (fallback) return fallback.replace(/:([A-Za-z][\w]*)/g, (_, name) => encodeURIComponent(params[name] ?? ''));
    const path = this.router.url.split(/[?#]/)[0];
    const parent = path.replace(/\/[^/]+\/?$/, '');
    return parent || '/home';
  }

  /** Logout: a pilha do usuário anterior não serve de base para o "Voltar" do próximo. */
  reset(): void {
    this.entries = [];
    this.index = -1;
    this.persist();
  }

  private navigateFallback(fallback?: string | readonly unknown[]): void {
    if (Array.isArray(fallback)) {
      void this.router.navigate(fallback as unknown[]);
    } else {
      void this.router.navigateByUrl((fallback as string | undefined) ?? this.resolveFallback());
    }
  }

  private commit(event: NavigationEnd): void {
    const info = this.pending.get(event.id) ?? {
      trigger: 'imperative' as const,
      replaceUrl: false,
      skipLocationChange: false,
    };
    this.pending.delete(event.id);
    if (info.skipLocationChange) return;

    const url = event.urlAfterRedirects;
    const previousUrl = this.entries[this.index]?.url ?? null;
    this.lastTrigger = info.trigger;
    this.lastPathChanged = !previousUrl || pathOf(previousUrl) !== pathOf(url);

    const restoredIndex = info.restoredEntryId
      ? this.entries.findIndex((entry) => entry.id === info.restoredEntryId)
      : -1;
    // Primeira navegação concluída nesta carga da página (pode não ser a de id 1: redirect de guard).
    const isInitial = !this.committedOnce;
    this.committedOnce = true;
    this.lastRestored = false;

    if (restoredIndex >= 0 && (info.trigger === 'popstate' || isInitial)) {
      // Voltar/avançar do navegador, ou F5 numa entrada que a pilha conhece.
      this.index = restoredIndex;
      this.entries[restoredIndex] = { id: info.restoredEntryId!, url };
      this.lastRestored = true;
    } else if (isInitial || info.trigger !== 'imperative') {
      // Deep link, nova aba, F5 sem pilha verificável, ou popstate para fora do conhecido.
      const id = info.restoredEntryId ?? createId();
      this.entries = [{ id, url }];
      this.index = 0;
    } else if (info.replaceUrl || this.index < 0 || previousUrl === url) {
      const id = this.entries[this.index]?.id ?? createId();
      if (this.index < 0) this.index = 0;
      this.entries[this.index] = { id, url };
    } else {
      this.entries = this.entries.slice(0, this.index + 1);
      this.entries.push({ id: createId(), url });
      this.index = this.entries.length - 1;
    }

    this.stampHistoryState(this.entries[this.index].id);
    this.persist();
  }

  private stampHistoryState(id: string): void {
    const state = (this.location.getState() ?? {}) as Record<string, unknown>;
    if (state[ENTRY_STATE_KEY] === id) return;
    // replaceState do Location não dispara navegação (o Router só reage a popstate).
    this.location.replaceState(this.location.path(true), '', { ...state, [ENTRY_STATE_KEY]: id });
  }

  private persist(): void {
    try {
      const data: PersistedStack = { entries: this.entries.slice(-50), index: 0 };
      data.index = this.index - (this.entries.length - data.entries.length);
      if (data.index < 0) {
        data.entries = this.entries.slice(this.index, this.index + 50);
        data.index = 0;
      }
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch {
      // sem sessionStorage: segue em memória
    }
  }
}

function readPersisted(): PersistedStack | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const data = JSON.parse(raw) as PersistedStack;
    if (!Array.isArray(data.entries) || typeof data.index !== 'number') return null;
    return data;
  } catch {
    return null;
  }
}

function openedByThisApp(): boolean {
  try {
    return !!window.opener && window.opener.location.origin === window.location.origin;
  } catch {
    // opener de outra origem: acesso bloqueado pelo navegador.
    return false;
  }
}

function pathOf(url: string): string {
  return url.split(/[?#]/)[0];
}

function createId(): string {
  try {
    return crypto.randomUUID();
  } catch {
    return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  }
}
