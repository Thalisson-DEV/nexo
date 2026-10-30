import { DestroyRef, computed, signal } from '@angular/core';
import {
  Observable,
  Subject,
  Subscription,
  catchError,
  defer,
  map,
  of,
  switchMap,
  tap,
  timer,
} from 'rxjs';
import { PagedResponse } from '../models/shared.models';

export const REMOTE_LOOKUP_PAGE_SIZE = 30;
export const REMOTE_LOOKUP_DEBOUNCE_MS = 300;

export interface RemoteLookupRequest {
  query: string;
  page: number;
  size: number;
}

export interface RemoteLookupConfig<T, TKey> {
  load: (request: RemoteLookupRequest) => Observable<PagedResponse<T>>;
  identify: (item: T) => TKey;
  selectedKeys?: () => readonly TKey[];
  initialItems?: readonly T[];
  pageSize?: number;
  debounceMs?: number;
}

interface LookupCommand {
  query: string;
  page: number;
  append: boolean;
  immediate: boolean;
}

interface LookupResult<T> {
  command: LookupCommand;
  response?: PagedResponse<T>;
  error?: unknown;
}

/**
 * Estado reutilizável para catálogos consultados no servidor.
 *
 * O controlador não conhece PrimeNG nem domínio. Ele garante que uma nova digitação
 * cancele a consulta anterior, mantém a seleção fora da página atual e nunca exige
 * carregar o catálogo inteiro no navegador.
 */
export class RemoteLookup<T, TKey = string> {
  readonly items = signal<T[]>([]);
  readonly loading = signal(false);
  readonly error = signal<unknown | null>(null);
  readonly totalElements = signal(0);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly hasMore = computed(() => this.currentPage() + 1 < this.totalPages());

  private readonly commands = new Subject<LookupCommand>();
  private readonly subscription: Subscription;
  private initialized = false;
  private currentQuery = '';

  constructor(
    private readonly config: RemoteLookupConfig<T, TKey>,
    destroyRef?: DestroyRef,
  ) {
    this.setSeed(config.initialItems ?? []);

    this.subscription = this.commands
      .pipe(
        tap(() => this.loading.set(false)),
        switchMap((command) =>
          timer(command.immediate ? 0 : (config.debounceMs ?? REMOTE_LOOKUP_DEBOUNCE_MS)).pipe(
            switchMap(() =>
              defer(() => {
                this.loading.set(true);
                this.error.set(null);
                return config.load({
                  query: command.query,
                  page: command.page,
                  size: config.pageSize ?? REMOTE_LOOKUP_PAGE_SIZE,
                });
              }).pipe(
                map((response): LookupResult<T> => ({ command, response })),
                catchError((error): Observable<LookupResult<T>> => of({ command, error })),
              ),
            ),
          ),
        ),
      )
      .subscribe((result) => this.applyResult(result));

    destroyRef?.onDestroy(() => this.subscription.unsubscribe());
  }

  open(): void {
    if (!this.initialized) this.search('', true);
  }

  search(query: string, immediate = false): void {
    this.initialized = true;
    this.currentQuery = query.trim();
    this.commands.next({
      query: this.currentQuery,
      page: 0,
      append: false,
      immediate,
    });
  }

  loadNext(): void {
    if (this.loading() || !this.hasMore()) return;
    this.commands.next({
      query: this.currentQuery,
      page: this.currentPage() + 1,
      append: true,
      immediate: true,
    });
  }

  retry(): void {
    this.commands.next({
      query: this.currentQuery,
      page: this.currentPage(),
      append: this.currentPage() > 0,
      immediate: true,
    });
  }

  setSeed(items: readonly T[]): void {
    // A semente também é usada para hidratar um resumo com o registro detalhado.
    // Por isso o item novo tem precedência sobre a versão já presente.
    this.items.update((current) => this.unique([...items, ...current]));
  }

  destroy(): void {
    this.subscription.unsubscribe();
    this.commands.complete();
  }

  private applyResult(result: LookupResult<T>): void {
    this.loading.set(false);
    if (result.error !== undefined) {
      this.error.set(result.error);
      return;
    }

    const response = result.response;
    if (!response) return;

    const incoming = response.content ?? [];
    const nextItems = result.command.append
      ? [...this.items(), ...incoming]
      : [...this.selectedItems(), ...incoming];

    this.items.set(this.unique(nextItems));
    this.currentPage.set(response.page.number);
    this.totalPages.set(response.page.totalPages);
    this.totalElements.set(response.page.totalElements);
  }

  private selectedItems(): T[] {
    const selected = new Set(this.config.selectedKeys?.() ?? []);
    if (selected.size === 0) return [];
    return this.items().filter((item) => selected.has(this.config.identify(item)));
  }

  private unique(items: readonly T[]): T[] {
    const seen = new Set<TKey>();
    return items.filter((item) => {
      const key = this.config.identify(item);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }
}
