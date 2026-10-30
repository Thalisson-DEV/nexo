import { DestroyRef, computed, signal } from '@angular/core';
import {
  EMPTY,
  Observable,
  Subject,
  Subscription,
  catchError,
  debounceTime,
  defer,
  distinctUntilChanged,
  map,
  merge,
  switchMap,
  tap,
} from 'rxjs';

import { LookupOption } from '../models/lookup-option';
import { LookupPage } from '../models/lookup-page';
import { LookupStatus } from '../models/lookup-status';
import { normalizeLookupQuery } from '../utils/normalize-query';

export interface RemoteLookupRequest {
  query: string;
  cursor: string | null;
}

export type RemoteLookupLoader<T> = (
  request: RemoteLookupRequest,
) => Observable<LookupPage<LookupOption<T>>>;

export interface RemoteLookupOptions<T> {
  debounceMs?: number;
  /** Alias kept for callers that name this option after the RxJS operator. */
  debounceTime?: number;
  minQueryLength?: number;
  initialSelected?: LookupOption<T>[];
  selected?: LookupOption<T>[];
  /** Valores atualmente selecionados pelo formulário, inclusive quando o objeto não está na página. */
  selectedValues?: () => readonly T[];
  destroyRef?: DestroyRef;
}

export interface RemoteLookupConfig<T> extends RemoteLookupOptions<T> {
  loader: RemoteLookupLoader<T>;
}

export const REMOTE_LOOKUP_DEBOUNCE_MS = 300;
export const REMOTE_LOOKUP_MIN_QUERY_LENGTH = 2;
export const LOOKUP_RATE_LIMIT_MESSAGE =
  'Muitas consultas foram realizadas em pouco tempo. Tente novamente em alguns segundos.';

/** Erro normalizado para que a camada de apresentação possa informar o limite de consultas. */
export class LookupRateLimitError extends Error {
  constructor(readonly retryAfterMs = 5_000) {
    super(LOOKUP_RATE_LIMIT_MESSAGE);
    this.name = 'LookupRateLimitError';
  }
}

interface LookupCommand {
  request: RemoteLookupRequest;
  append: boolean;
}

/**
 * Facade sem estado de formulário para buscas remotas baseadas em cursor.
 *
 * A query pública passa por normalização, debounce, distinct e switchMap. O
 * loader só é chamado quando a query tem o comprimento mínimo configurado.
 */
export class RemoteLookup<T = string> {
  readonly query$ = new Subject<string>();

  private readonly loader: RemoteLookupLoader<T>;
  private readonly debounceMs: number;
  private readonly minQueryLength: number;
  private readonly selectedItems: LookupOption<T>[];
  private readonly selectedValues?: () => readonly T[];
  private readonly nextPage$ = new Subject<LookupCommand>();
  private readonly subscription: Subscription;
  private destroyed = false;
  private lastFailedCommand: LookupCommand | null = null;

  private readonly itemsState = signal<LookupOption<T>[]>([]);
  private readonly statusState = signal<LookupStatus>('idle');
  private readonly errorState = signal<unknown | null>(null);
  private readonly hasMoreState = signal(false);
  private readonly nextCursorState = signal<string | null>(null);
  private readonly queryState = signal('');

  readonly items = this.itemsState.asReadonly();
  readonly options = this.items;
  readonly status = this.statusState.asReadonly();
  readonly loading = computed(() => this.statusState() === 'loading');
  readonly error = this.errorState.asReadonly();
  readonly hasMore = this.hasMoreState.asReadonly();
  readonly nextCursor = this.nextCursorState.asReadonly();
  readonly query = this.queryState.asReadonly();

  constructor(config: RemoteLookupConfig<T>);
  constructor(loader: RemoteLookupLoader<T>, options?: RemoteLookupOptions<T>);
  constructor(
    loaderOrConfig: RemoteLookupConfig<T> | RemoteLookupLoader<T>,
    options: RemoteLookupOptions<T> = {},
  ) {
    const config = typeof loaderOrConfig === 'function' ? options : loaderOrConfig;
    this.loader = typeof loaderOrConfig === 'function' ? loaderOrConfig : loaderOrConfig.loader;
    this.debounceMs = config.debounceMs ?? config.debounceTime ?? REMOTE_LOOKUP_DEBOUNCE_MS;
    this.minQueryLength = config.minQueryLength ?? REMOTE_LOOKUP_MIN_QUERY_LENGTH;
    this.selectedItems = dedupeOptions(config.initialSelected ?? config.selected ?? []);
    this.selectedValues = config.selectedValues;
    this.itemsState.set(this.selectedItems);

    const searches$ = this.query$.pipe(
      map((query) => normalizeLookupQuery(query)),
      tap((query) => this.queryState.set(query)),
      debounceTime(this.debounceMs),
      distinctUntilChanged(),
      map((query): LookupCommand => ({ request: { query, cursor: null }, append: false })),
    );

    this.subscription = merge(searches$, this.nextPage$)
      .pipe(switchMap((command) => this.execute(command)))
      .subscribe();

    config.destroyRef?.onDestroy(() => this.destroy());
  }

  search(query: string): void {
    if (!this.destroyed) this.query$.next(query);
  }

  /** Injeta opções selecionadas, por exemplo valores carregados ao editar um registro. */
  seedSelected(options: LookupOption<T> | LookupOption<T>[]): void {
    if (this.destroyed) return;
    const additions = dedupeOptions(Array.isArray(options) ? options : [options]);
    this.selectedItems.splice(
      0,
      this.selectedItems.length,
      ...dedupeOptions([...this.selectedItems, ...additions]),
    );
    this.itemsState.update((items) => dedupeOptions([...this.selectedItems, ...items]));
  }

  setSelected(options: LookupOption<T> | LookupOption<T>[]): void {
    if (this.destroyed) return;
    this.selectedItems.splice(
      0,
      this.selectedItems.length,
      ...dedupeOptions(Array.isArray(options) ? options : [options]),
    );
    this.itemsState.update((items) => dedupeOptions([...this.selectedItems, ...items]));
  }

  loadNext(): void {
    if (
      this.destroyed ||
      this.status() === 'loading' ||
      !this.hasMore() ||
      !this.nextCursor() ||
      this.query().length < this.minQueryLength
    ) {
      return;
    }

    this.nextPage$.next({
      request: { query: this.query(), cursor: this.nextCursor() },
      append: true,
    });
  }

  /** Refaz a última consulta que falhou sem exigir uma nova alteração na query. */
  retry(): void {
    if (this.destroyed || !this.lastFailedCommand) return;
    this.nextPage$.next(this.lastFailedCommand);
  }

  retryLast(): void {
    this.retry();
  }

  destroy(): void {
    if (this.destroyed) return;
    this.destroyed = true;
    this.query$.complete();
    this.nextPage$.complete();
    this.subscription.unsubscribe();
  }

  private execute(command: LookupCommand) {
    if (command.request.query.length < this.minQueryLength) {
      this.lastFailedCommand = null;
      this.statusState.set('idle');
      this.errorState.set(null);
      this.hasMoreState.set(false);
      this.nextCursorState.set(null);
      this.itemsState.set(this.preservedSelectedItems(this.itemsState()));
      return EMPTY;
    }

    this.statusState.set('loading');
    this.errorState.set(null);
    this.lastFailedCommand = null;

    return defer(() => this.loader(command.request)).pipe(
      tap((page) => this.applyPage(page, command.append)),
      catchError((error: unknown) => {
        this.lastFailedCommand = command;
        this.errorState.set(normalizeLookupError(error));
        this.statusState.set('error');
        // Os itens anteriores permanecem disponíveis durante uma falha transitória.
        return EMPTY;
      }),
    );
  }

  private applyPage(page: LookupPage<LookupOption<T>>, append: boolean): void {
    const pageItems = dedupeOptions(page.items ?? []);
    const hadItems = this.itemsState().length > 0;
    const items = append
      ? dedupeOptions([...this.itemsState(), ...pageItems])
      : dedupeOptions([...this.preservedSelectedItems(this.itemsState()), ...pageItems]);

    this.itemsState.set(items);
    this.hasMoreState.set(page.hasMore);
    this.nextCursorState.set(page.hasMore ? page.nextCursor : null);
    this.statusState.set(pageItems.length === 0 && (!append || !hadItems) ? 'empty' : 'success');
    this.errorState.set(null);
    this.lastFailedCommand = null;
  }

  private preservedSelectedItems(currentItems: LookupOption<T>[]): LookupOption<T>[] {
    const selectedValues = this.selectedValues?.() ?? [];
    return dedupeOptions([
      ...this.selectedItems,
      ...currentItems.filter((item) =>
        selectedValues.some((value) => Object.is(value, item.value)),
      ),
    ]);
  }
}

function dedupeOptions<T>(options: LookupOption<T>[]): LookupOption<T>[] {
  const result: LookupOption<T>[] = [];
  for (const option of options) {
    if (!result.some((item) => Object.is(item.value, option.value))) result.push(option);
  }
  return result;
}

function normalizeLookupError(error: unknown): unknown {
  if (isRateLimitError(error)) return new LookupRateLimitError(rateLimitDelay(error));
  return error;
}

function isRateLimitError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  const candidate = error as { status?: unknown; name?: unknown };
  return candidate.status === 429 || candidate.name === 'LookupRateLimitError';
}

function rateLimitDelay(error: unknown): number {
  if (!error || typeof error !== 'object') return 5_000;
  const candidate = error as { retryAfterMs?: unknown; error?: { retryAfter?: unknown } };
  if (typeof candidate.retryAfterMs === 'number' && candidate.retryAfterMs > 0) {
    return candidate.retryAfterMs;
  }
  if (typeof candidate.error?.retryAfter === 'number' && candidate.error.retryAfter > 0) {
    return candidate.error.retryAfter * 1_000;
  }
  return 5_000;
}
