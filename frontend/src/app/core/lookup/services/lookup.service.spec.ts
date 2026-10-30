import '@angular/compiler';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { environment } from '../../../../environments/environment';
import { LookupService } from './lookup.service';

describe('LookupService', () => {
  let service: LookupService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LookupService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('carrega o bootstrap pelo endpoint dedicado', () => {
    service.getBootstrap().subscribe();

    const request = http.expectOne(`${environment.apiUrl}/lookups/bootstrap`);
    expect(request.request.method).toBe('GET');
    request.flush({
      operationalBases: [],
      movementTypes: [],
      requestTypes: [],
      statuses: [],
      processes: [],
    });
  });

  it('busca colaboradores com query, limite e cursor', () => {
    service.searchCollaborators({ query: 'maria', limit: 40, cursor: 'cursor-2' }).subscribe();

    const request = http.expectOne(
      (candidate) => candidate.url === `${environment.apiUrl}/v1/rh/colaboradores/options`,
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('q')).toBe('maria');
    expect(request.request.params.get('limit')).toBe('40');
    expect(request.request.params.get('cursor')).toBe('cursor-2');
    request.flush({ items: [], nextCursor: null, hasMore: false });
  });

  it('busca materiais com limite padrao e omite cursor vazio', () => {
    service.searchMaterials({ query: 'cabo' }).subscribe();

    const request = http.expectOne(
      (candidate) => candidate.url === `${environment.apiUrl}/v1/materiais/options`,
    );
    expect(request.request.params.get('q')).toBe('cabo');
    expect(request.request.params.get('limit')).toBe('50');
    expect(request.request.params.has('cursor')).toBe(false);
    request.flush({ items: [], nextCursor: null, hasMore: false });
  });

  it('busca estoques mantendo allowlist de tipos repetida na query', () => {
    service
      .searchStocks({ query: 'almox', types: ['OBRA', 'FISICO'], cursor: 'next' })
      .subscribe();

    const request = http.expectOne(
      (candidate) => candidate.url === `${environment.apiUrl}/v1/estoques/options`,
    );
    expect(request.request.params.getAll('tipos')).toEqual(['OBRA', 'FISICO']);
    expect(request.request.params.get('cursor')).toBe('next');
    request.flush({ items: [], nextCursor: null, hasMore: false });
  });

  it('normaliza opcoes invalidas antes de entrega-las aos seletores', () => {
    let result: unknown;
    service.searchCollaborators({ query: 'maria' }).subscribe((page) => (result = page));

    const request = http.expectOne(
      (candidate) => candidate.url === `${environment.apiUrl}/v1/rh/colaboradores/options`,
    );
    request.flush({
      items: [
        { value: ' colab-1 ', label: ' Maria Souza ' },
        { value: 'colab-2', label: 'null, null' },
        { value: null, label: 'Sem identificador' },
      ],
      nextCursor: '',
      hasMore: true,
    });

    expect(result).toEqual({
      items: [
        { value: 'colab-1', label: 'Maria Souza' },
        { value: 'colab-2', label: 'Cadastro sem descrição (colab-2)' },
      ],
      nextCursor: null,
      hasMore: false,
    });
  });

  it('falha de forma explicita quando a pagina nao segue o contrato', () => {
    let message = '';
    service.searchMaterials({ query: 'cabo' }).subscribe({
      error: (error: Error) => (message = error.message),
    });

    const request = http.expectOne(
      (candidate) => candidate.url === `${environment.apiUrl}/v1/materiais/options`,
    );
    request.flush({ content: [] });

    expect(message).toContain('formato inválido');
  });
});
