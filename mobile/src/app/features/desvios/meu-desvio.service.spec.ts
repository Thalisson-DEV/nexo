import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { MeuDesvioService } from './meu-desvio.service';

describe('MeuDesvioService', () => {
  let service: MeuDesvioService;
  let httpMock: HttpTestingController;
  const endpoint = `${environment.apiBaseUrl}/v1/sst/desvios/meus`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MeuDesvioService],
    });
    service = TestBed.inject(MeuDesvioService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lista apenas com os filtros suportados, sem identificar o colaborador', () => {
    service.listar({
      apenasPendentes: true,
      idBaseOperacional: 'base-1',
      page: 0,
      size: 20,
      sort: 'prazo,asc',
    }).subscribe();

    const req = httpMock.expectOne(request =>
      request.url === endpoint &&
      request.params.get('apenasPendentes') === 'true' &&
      request.params.get('idBaseOperacional') === 'base-1'
    );

    expect(req.request.params.has('idUsuario')).toBeFalse();
    expect(req.request.params.has('idColaborador')).toBeFalse();
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } });
  });

  it('usa o endpoint pessoal para tratamento individual', () => {
    service.tratar('desvio-1', { status: 'RESOLVIDO' }).subscribe();

    const req = httpMock.expectOne(`${endpoint}/desvio-1/tratamento`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'RESOLVIDO' });
    req.flush({});
  });

  it('envia o tratamento em massa no contrato esperado', () => {
    service.tratarEmMassa({
      idsDesvios: ['d-1', 'd-2'],
      status: 'EM_TRATAMENTO',
      acaoCorretiva: 'Ajuste iniciado',
    }).subscribe();

    const req = httpMock.expectOne(`${endpoint}/tratamento-em-massa`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.idsDesvios).toEqual(['d-1', 'd-2']);
    req.flush({ totalAtualizados: 2, idsAtualizados: ['d-1', 'd-2'] });
  });
});

