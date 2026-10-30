import '@angular/compiler';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject, defer, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LookupBootstrap } from '../models/lookup-bootstrap';
import { LookupOption } from '../models/lookup-option';
import { LookupPage } from '../models/lookup-page';
import { LookupCacheService } from '../services/lookup-cache.service';
import { LookupSearchRequest, LookupService } from '../services/lookup.service';
import { LOOKUP_RATE_LIMIT_MESSAGE, LookupRateLimitError, LookupStore } from './lookup.store';

const emptyPage: LookupPage<LookupOption<string>> = {
  items: [],
  nextCursor: null,
  hasMore: false,
};

describe('LookupStore', () => {
  let store: LookupStore;
  let service: {
    getBootstrap: ReturnType<typeof vi.fn>;
    searchCollaborators: ReturnType<typeof vi.fn>;
    searchMaterials: ReturnType<typeof vi.fn>;
    searchStocks: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    service = {
      getBootstrap: vi.fn(() => of({})),
      searchCollaborators: vi.fn(() => of(emptyPage)),
      searchMaterials: vi.fn(() => of(emptyPage)),
      searchStocks: vi.fn(() => of(emptyPage)),
    };
    TestBed.configureTestingModule({
      providers: [LookupStore, LookupCacheService, { provide: LookupService, useValue: service }],
    });
    store = TestBed.inject(LookupStore);
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('nao consulta antes do comprimento minimo', () => {
    let result: LookupPage<LookupOption<string>> | undefined;
    store.searchCollaborators({ query: ' a ' }).subscribe((page) => (result = page));

    expect(service.searchCollaborators).not.toHaveBeenCalled();
    expect(result).toEqual(emptyPage);
  });

  it('normaliza a query e reutiliza o cache dentro do TTL', () => {
    const response: LookupPage<LookupOption<string>> = {
      items: [{ value: '1', label: 'Maria' }],
      nextCursor: null,
      hasMore: false,
    };
    service.searchCollaborators.mockReturnValue(of(response));

    store.searchCollaborators({ query: '  MARIA  ' }).subscribe();
    store.searchCollaborators({ query: 'maria' }).subscribe();

    expect(service.searchCollaborators).toHaveBeenCalledOnce();
    expect(service.searchCollaborators).toHaveBeenCalledWith({
      query: 'maria',
      limit: 50,
      cursor: null,
    });
    expect(store.metrics().collaborators.cacheHits).toBe(1);
  });

  it('deduplica subscriptions simultaneas para a mesma busca', () => {
    const response$ = new Subject<LookupPage<LookupOption<string>>>();
    service.searchCollaborators.mockReturnValue(response$);
    const first$ = store.searchCollaborators({ query: 'maria' });
    const second$ = store.searchCollaborators({ query: 'maria' });
    const results: LookupPage<LookupOption<string>>[] = [];

    first$.subscribe((page) => results.push(page));
    second$.subscribe((page) => results.push(page));
    response$.next(emptyPage);
    response$.complete();

    expect(first$).toBe(second$);
    expect(service.searchCollaborators).toHaveBeenCalledOnce();
    expect(results).toEqual([emptyPage, emptyPage]);
    expect(store.metrics().collaborators.deduplicated).toBe(1);
  });

  it('mantem paginas por cursor em chaves separadas', () => {
    store.searchMaterials({ query: 'cabo', cursor: null }).subscribe();
    store.searchMaterials({ query: 'cabo', cursor: 'cursor-2' }).subscribe();

    expect(service.searchMaterials).toHaveBeenCalledTimes(2);
  });

  it('isola o cache de estoques por tipos permitidos', () => {
    store.searchStocks({ query: 'central', types: ['OBRA', 'FISICO'] }).subscribe();
    store.searchStocks({ query: 'central', types: ['CAMPO'] }).subscribe();
    store.searchStocks({ query: 'central', types: ['OBRA', 'FISICO'] }).subscribe();

    expect(service.searchStocks).toHaveBeenCalledTimes(2);
    expect(store.metrics().stocks.cacheHits).toBe(1);
  });

  it('respeita Retry-After e bloqueia novas chamadas apos 429', () => {
    const response = new HttpErrorResponse({
      status: 429,
      headers: new HttpHeaders({ 'Retry-After': '3' }),
    });
    service.searchCollaborators.mockReturnValue(throwError(() => response));
    const errors: unknown[] = [];

    store
      .searchCollaborators({ query: 'maria' })
      .subscribe({ error: (error) => errors.push(error) });
    store.searchCollaborators({ query: 'maria nova' }).subscribe({
      error: (error) => errors.push(error),
    });

    expect(service.searchCollaborators).toHaveBeenCalledOnce();
    expect(errors).toHaveLength(2);
    expect(errors[0]).toBeInstanceOf(LookupRateLimitError);
    expect((errors[0] as Error).message).toBe(LOOKUP_RATE_LIMIT_MESSAGE);
    expect((errors[0] as LookupRateLimitError).retryAfterMs).toBe(3000);
    expect(store.metrics().collaborators.rateLimited).toBe(1);
  });

  it('repete apenas erros transitorios com backoff limitado', async () => {
    vi.useFakeTimers();
    let attempt = 0;
    service.searchMaterials.mockReturnValue(
      defer(() => {
        attempt += 1;
        return attempt < 3
          ? throwError(() => new HttpErrorResponse({ status: 503 }))
          : of(emptyPage);
      }),
    );
    let completed = false;

    store.searchMaterials({ query: 'cabo' }).subscribe({ complete: () => (completed = true) });
    await vi.advanceTimersByTimeAsync(750);

    expect(attempt).toBe(3);
    expect(completed).toBe(true);
  });

  it('compartilha o bootstrap e publica os catalogos em signals', () => {
    const response: LookupBootstrap = {
      operationalBases: [{ value: 'base-1', label: 'Base Norte' }],
      movementTypes: [],
      requestTypes: [],
      statuses: [],
      processes: [],
    };
    const bootstrap$ = new Subject<LookupBootstrap>();
    service.getBootstrap.mockReturnValue(bootstrap$);
    const first$ = store.loadBootstrap();
    const second$ = store.loadBootstrap();

    first$.subscribe();
    second$.subscribe();
    bootstrap$.next(response);
    bootstrap$.complete();

    expect(service.getBootstrap).toHaveBeenCalledOnce();
    expect(store.operationalBases()).toEqual(response.operationalBases);
    expect(store.bootstrapLoaded()).toBe(true);
  });
});
