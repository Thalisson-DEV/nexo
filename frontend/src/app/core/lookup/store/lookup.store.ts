import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import {
  Observable,
  catchError,
  defer,
  finalize,
  of,
  retry,
  shareReplay,
  tap,
  throwError,
  timer,
} from 'rxjs';

import { LookupBootstrap } from '../models/lookup-bootstrap';
import { LookupOption } from '../models/lookup-option';
import { LookupPage } from '../models/lookup-page';
import { LookupCacheService } from '../services/lookup-cache.service';
import { LookupSearchRequest, LookupService } from '../services/lookup.service';
import { normalizeLookupQuery } from '../utils/normalize-query';

export type RemoteLookupResource = 'collaborators' | 'materials' | 'stocks';

export interface LookupMetric {
  requests: number;
  cacheHits: number;
  cacheMisses: number;
  deduplicated: number;
  errors: number;
  rateLimited: number;
  totalDurationMs: number;
}

export const LOOKUP_BOOTSTRAP_TTL_MS = 10 * 60 * 1000;
export const LOOKUP_SEARCH_TTL_MS = 3 * 60 * 1000;
export const LOOKUP_MIN_QUERY_LENGTH = 2;
export const LOOKUP_RATE_LIMIT_MESSAGE =
  'Muitas consultas foram realizadas em pouco tempo. Tente novamente em alguns segundos.';

const EMPTY_PAGE: LookupPage<never> = { items: [], nextCursor: null, hasMore: false };

export class LookupRateLimitError extends Error {
  constructor(readonly retryAfterMs: number) {
    super(LOOKUP_RATE_LIMIT_MESSAGE);
    this.name = 'LookupRateLimitError';
  }
}

/**
 * Coordena cache, deduplicacao, backoff e observabilidade sem conhecer formularios
 * ou componentes. As queries nunca sao persistidas nem expostas nas metricas.
 */
@Injectable({ providedIn: 'root' })
export class LookupStore {
  private readonly service = inject(LookupService);
  private readonly cache = inject(LookupCacheService);
  private readonly pending = new Map<string, Observable<unknown>>();
  private readonly cooldowns = signal<Partial<Record<RemoteLookupResource, number>>>({});
  private readonly bootstrapReady = signal(false);
  private readonly metricState = signal<Record<RemoteLookupResource | 'bootstrap', LookupMetric>>({
    bootstrap: this.emptyMetric(),
    collaborators: this.emptyMetric(),
    materials: this.emptyMetric(),
    stocks: this.emptyMetric(),
  });

  readonly operationalBases = signal<LookupOption[]>([]);
  readonly movementTypes = signal<LookupOption[]>([]);
  readonly requestTypes = signal<LookupOption[]>([]);
  readonly statuses = signal<LookupOption[]>([]);
  readonly processes = signal<LookupOption[]>([]);
  readonly metrics = this.metricState.asReadonly();
  readonly bootstrapLoaded = this.bootstrapReady.asReadonly();

  loadBootstrap(forceRefresh = false): Observable<LookupBootstrap> {
    const resource = 'bootstrap';
    const key = 'bootstrap';
    if (forceRefresh) this.cache.delete(resource, key);

    const cached = this.cache.get<LookupBootstrap>(resource, key);
    if (cached) {
      this.record(resource, 'cacheHits');
      this.applyBootstrap(cached);
      return of(cached);
    }

    const pending = this.pending.get(key) as Observable<LookupBootstrap> | undefined;
    if (pending) {
      this.record(resource, 'deduplicated');
      return pending;
    }

    this.record(resource, 'cacheMisses');
    const startedAt = performance.now();
    const request$ = defer(() => {
      this.record(resource, 'requests');
      return this.service.getBootstrap();
    }).pipe(
      this.retryTransientErrors(),
      tap((value) => {
        this.cache.set(resource, key, value, LOOKUP_BOOTSTRAP_TTL_MS);
        this.applyBootstrap(value);
      }),
      catchError((error) => {
        this.record(resource, 'errors');
        return throwError(() => error);
      }),
      finalize(() => {
        this.recordDuration(resource, performance.now() - startedAt);
        this.pending.delete(key);
      }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

    this.pending.set(key, request$);
    return request$;
  }

  searchCollaborators(request: LookupSearchRequest): Observable<LookupPage<LookupOption<string>>> {
    return this.search('collaborators', request);
  }

  searchMaterials(request: LookupSearchRequest): Observable<LookupPage<LookupOption<string>>> {
    return this.search('materials', request);
  }

  searchStocks(request: LookupSearchRequest): Observable<LookupPage<LookupOption<string>>> {
    return this.search('stocks', request);
  }

  cooldownRemainingMs(resource: RemoteLookupResource): number {
    return Math.max(0, (this.cooldowns()[resource] ?? 0) - Date.now());
  }

  clear(resource?: RemoteLookupResource | 'bootstrap'): void {
    this.cache.clear(resource);
    if (!resource || resource === 'bootstrap') {
      this.operationalBases.set([]);
      this.movementTypes.set([]);
      this.requestTypes.set([]);
      this.statuses.set([]);
      this.processes.set([]);
      this.bootstrapReady.set(false);
    }
  }

  private search(
    resource: RemoteLookupResource,
    request: LookupSearchRequest,
  ): Observable<LookupPage<LookupOption<string>>> {
    const query = normalizeLookupQuery(request.query);
    if (query.length < LOOKUP_MIN_QUERY_LENGTH) {
      return of(EMPTY_PAGE as LookupPage<LookupOption<string>>);
    }

    const cooldown = this.cooldownRemainingMs(resource);
    if (cooldown > 0) return throwError(() => new LookupRateLimitError(cooldown));

    const normalizedRequest: LookupSearchRequest = {
      query,
      limit: request.limit ?? 50,
      cursor: request.cursor || null,
      types: request.types,
    };
    const cacheKey = this.searchKey(normalizedRequest);
    const pendingKey = `${resource}:${cacheKey}`;
    const cached = this.cache.get<LookupPage<LookupOption<string>>>(resource, cacheKey);
    if (cached) {
      this.record(resource, 'cacheHits');
      return of(cached);
    }

    const pending = this.pending.get(pendingKey) as
      Observable<LookupPage<LookupOption<string>>> | undefined;
    if (pending) {
      this.record(resource, 'deduplicated');
      return pending;
    }

    this.record(resource, 'cacheMisses');
    const startedAt = performance.now();
    const request$ = defer(() => {
      this.record(resource, 'requests');
      if (resource === 'collaborators') return this.service.searchCollaborators(normalizedRequest);
      if (resource === 'materials') return this.service.searchMaterials(normalizedRequest);
      return this.service.searchStocks(normalizedRequest);
    }).pipe(
      this.retryTransientErrors(),
      tap((page) => this.cache.set(resource, cacheKey, page, LOOKUP_SEARCH_TTL_MS)),
      catchError((error: unknown) => {
        this.record(resource, 'errors');
        if (error instanceof HttpErrorResponse && error.status === 429) {
          const retryAfterMs = this.retryAfterMs(error);
          this.cooldowns.update((current) => ({
            ...current,
            [resource]: Date.now() + retryAfterMs,
          }));
          this.record(resource, 'rateLimited');
          return throwError(() => new LookupRateLimitError(retryAfterMs));
        }
        return throwError(() => error);
      }),
      finalize(() => {
        this.recordDuration(resource, performance.now() - startedAt);
        this.pending.delete(pendingKey);
      }),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

    this.pending.set(pendingKey, request$);
    return request$;
  }

  private retryTransientErrors<T>() {
    return (source: Observable<T>): Observable<T> =>
      source.pipe(
        retry({
          count: 2,
          delay: (error: unknown, retryCount: number) => {
            if (!this.isTransient(error)) return throwError(() => error);
            return timer(250 * 2 ** (retryCount - 1));
          },
        }),
      );
  }

  private isTransient(error: unknown): boolean {
    return error instanceof HttpErrorResponse && [502, 503, 504].includes(error.status);
  }

  private retryAfterMs(error: HttpErrorResponse): number {
    const bodyValue = error.error?.retryAfter;
    if (typeof bodyValue === 'number' && bodyValue > 0) return bodyValue * 1000;

    const headerValue = error.headers.get('Retry-After');
    if (headerValue) {
      const seconds = Number(headerValue);
      if (Number.isFinite(seconds) && seconds > 0) return seconds * 1000;

      const date = Date.parse(headerValue);
      if (Number.isFinite(date)) return Math.max(1000, date - Date.now());
    }
    return 5000;
  }

  private searchKey(request: LookupSearchRequest): string {
    return `${request.query}:${request.limit}:${request.cursor ?? 'first'}:${(request.types ?? []).join(',')}`;
  }

  private applyBootstrap(value: LookupBootstrap): void {
    this.operationalBases.set(value.operationalBases);
    this.movementTypes.set(value.movementTypes);
    this.requestTypes.set(value.requestTypes);
    this.statuses.set(value.statuses);
    this.processes.set(value.processes);
    this.bootstrapReady.set(true);
  }

  private emptyMetric(): LookupMetric {
    return {
      requests: 0,
      cacheHits: 0,
      cacheMisses: 0,
      deduplicated: 0,
      errors: 0,
      rateLimited: 0,
      totalDurationMs: 0,
    };
  }

  private record(resource: RemoteLookupResource | 'bootstrap', field: keyof LookupMetric): void {
    this.metricState.update((current) => ({
      ...current,
      [resource]: {
        ...current[resource],
        [field]: current[resource][field] + 1,
      },
    }));
  }

  private recordDuration(resource: RemoteLookupResource | 'bootstrap', durationMs: number): void {
    this.metricState.update((current) => ({
      ...current,
      [resource]: {
        ...current[resource],
        totalDurationMs: current[resource].totalDurationMs + durationMs,
      },
    }));
  }
}
