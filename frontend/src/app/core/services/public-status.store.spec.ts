import '@angular/compiler';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PublicStatusStore } from './public-status.store';

describe('PublicStatusStore', () => {
  let http: HttpTestingController;
  let store: PublicStatusStore;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(true);
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(PublicStatusStore);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('consulta a fonte pública sem token e aceita uma projeção recente', () => {
    const request = http.expectOne((candidate) =>
      candidate.url.endsWith('/api/v1/public/status'),
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({
      schemaVersion: '1',
      generatedAt: new Date().toISOString(),
      overallStatus: 'DEGRADED',
      components: [{
        key: 'backend', label: 'Backend', status: 'DEGRADED',
        observedAt: new Date().toISOString(), message: 'Resposta mais lenta que o esperado'
      }]
    });

    expect(store.sourceState()).toBe('ready');
    expect(store.componentsWithIssues()).toHaveLength(1);
  });

  it('preserva a última evidência, mas troca projeção antiga por UNKNOWN operacional', () => {
    const old = new Date(Date.now() - 61_000).toISOString();
    http.expectOne((candidate) => candidate.url.endsWith('/api/v1/public/status')).flush({
      schemaVersion: '1', generatedAt: old, overallStatus: 'UP', components: []
    });

    expect(store.sourceState()).toBe('stale');
    expect(store.sourceError()).toBe('stale');
    expect(store.projection()?.overallStatus).toBeUndefined();
    expect(store.lastEvidenceAt()).toBe(old);
  });

  it('informa offline sem afirmar queda confirmada', () => {
    http
      .expectOne((candidate) => candidate.url.endsWith('/api/v1/public/status'))
      .error(new ProgressEvent('error'));
    vi.spyOn(window.navigator, 'onLine', 'get').mockReturnValue(false);
    window.dispatchEvent(new Event('offline'));

    expect(store.sourceState()).toBe('offline');
    expect(store.sourceError()).toBe('offline');
    expect(store.projection()).toBeNull();
  });
});
