import '@angular/compiler';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { AlmoxarifadoService } from './almoxarifado.service';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('AlmoxarifadoService - Exportações', () => {
  let service: AlmoxarifadoService;
  let http: HttpTestingController;
  const apiUrl = `${environment.apiUrl}/v1`;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [AlmoxarifadoService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(AlmoxarifadoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('exportarSolicitacoesResumo envia parâmetros corretos e retorna Blob', () => {
    const dataInicio = new Date('2026-09-01T00:00:00.000Z');
    const params = {
      contexto: 'TODOS',
      dataInicio,
      statuses: ['PENDENTE', 'APROVADO'],
      buscaGlobal: 'OC-123',
    };

    service.exportarSolicitacoesResumo(params).subscribe((blob) => {
      expect(blob).toBeTruthy();
    });

    const req = http.expectOne((r) => r.url === `${apiUrl}/solicitacoes-material/exportar/resumo`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('contexto')).toBe('TODOS');
    expect(req.request.params.get('dataInicio')).toBe(dataInicio.toISOString());
    expect(req.request.params.getAll('statuses')).toEqual(['PENDENTE', 'APROVADO']);
    expect(req.request.params.get('buscaGlobal')).toBe('OC-123');
    expect(req.request.responseType).toBe('blob');

    req.flush(new Blob(['id;status;contexto'], { type: 'text/csv' }));
  });

  it('exportarSolicitacoesDetalhado envia parâmetros corretos e retorna Blob', () => {
    const params = {
      contexto: 'COMERCIAL',
      idEstoqueOrigem: 'est-1',
      numSerie: 'SERIE999',
    };

    service.exportarSolicitacoesDetalhado(params).subscribe((blob) => {
      expect(blob).toBeTruthy();
    });

    const req = http.expectOne((r) => r.url === `${apiUrl}/solicitacoes-material/exportar/detalhado`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('contexto')).toBe('COMERCIAL');
    expect(req.request.params.get('idEstoqueOrigem')).toBe('est-1');
    expect(req.request.params.get('numSerie')).toBe('SERIE999');
    expect(req.request.responseType).toBe('blob');

    req.flush(new Blob(['id;item;quantidade'], { type: 'text/csv' }));
  });
});
