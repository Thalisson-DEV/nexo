import '@angular/compiler';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';

import { environment } from '../../../environments/environment';
import { SyncService } from './sync.service';
import { SstService } from './sst.service';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('SstService - dashboard de desvios', () => {
  let service: SstService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/v1/sst/dashboards/desvios`;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SyncService, useValue: {} },
      ],
    });
    service = TestBed.inject(SstService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('não envia status na consulta inicial', () => {
    service
      .gerarDashboardDesvios({
        idProcesso: [],
        idEquipe: [],
        idBaseOperacional: [],
        idCentroCusto: [],
        gravidade: [],
        status: [],
      })
      .subscribe();

    const request = http.expectOne(url);
    expect(request.request.params.has('status')).toBe(false);
    request.flush({});
  });

  it('envia granularidade e filtros explícitos como parâmetros repetidos', () => {
    service
      .gerarDashboardDesvios({
        granularidade: 'MES',
        gravidade: ['ALTA', 'CRITICA'],
        status: ['ABERTO', 'EM_TRATAMENTO'],
      })
      .subscribe();

    const request = http.expectOne((candidate) => candidate.url === url);
    expect(request.request.params.get('granularidade')).toBe('MES');
    expect(request.request.params.getAll('gravidade')).toEqual(['ALTA', 'CRITICA']);
    expect(request.request.params.getAll('status')).toEqual(['ABERTO', 'EM_TRATAMENTO']);
    request.flush({});
  });
});
