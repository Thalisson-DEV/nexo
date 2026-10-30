import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { DbService } from '../db/db.service';
import { BaseOperacionalService } from './base-operacional.service';

describe('BaseOperacionalService', () => {
  let service: BaseOperacionalService;
  let httpMock: HttpTestingController;
  let dbService: jasmine.SpyObj<DbService>;

  beforeEach(() => {
    dbService = jasmine.createSpyObj('DbService', [
      'getBasesOperacionaisAtivas',
      'replaceBasesOperacionais',
      'setConfig',
      'getConfig',
    ]);
    dbService.replaceBasesOperacionais.and.resolveTo();
    dbService.setConfig.and.resolveTo();

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        BaseOperacionalService,
        { provide: DbService, useValue: dbService },
      ],
    });

    service = TestBed.inject(BaseOperacionalService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('sincroniza somente bases ativas e as persiste no cache', async () => {
    const promise = service.sincronizarAtivas();
    const req = httpMock.expectOne(request =>
      request.url === `${environment.apiBaseUrl}/v1/bases-operacionais` &&
      request.params.get('ativo') === 'true' &&
      request.params.get('size') === '100'
    );
    req.flush({
      content: [
        { id: 'b-1', nome: 'Base A', latitude: null, longitude: null, raio: null, ativo: true },
        { id: 'b-2', nome: 'Base inativa', latitude: null, longitude: null, raio: null, ativo: false },
      ],
    });

    const result = await promise;
    expect(result.map(base => base.id)).toEqual(['b-1']);
    expect(dbService.replaceBasesOperacionais).toHaveBeenCalledWith([
      jasmine.objectContaining({ id: 'b-1', nome: 'Base A', ativo: true }),
    ]);
    expect(dbService.setConfig).toHaveBeenCalledWith('basesOperacionaisAccessDenied', false);
  });

  it('registra o estado de acesso negado quando a API responde 403', async () => {
    const promise = service.sincronizarAtivas();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/bases-operacionais?ativo=true&page=0&size=100&sort=nome,asc`);
    req.flush(null, { status: 403, statusText: 'Forbidden' });

    await expectAsync(promise).toBeRejected();
    expect(dbService.setConfig).toHaveBeenCalledWith('basesOperacionaisAccessDenied', true);
  });
});
