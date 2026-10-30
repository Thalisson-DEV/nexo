import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SyncErrorCasesService } from './sync-error-cases.service';

describe('SyncErrorCasesService', () => {
  let service: SyncErrorCasesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [SyncErrorCasesService, provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(SyncErrorCasesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('maps the Portuguese backend case contract to the desktop view model', () => {
    service.get('case-1').subscribe((item) => {
      expect(item.operationId).toBe('local-1');
      expect(item.operationType).toBe('TURNO');
      expect(item.status).toBe('AGUARDANDO_REPROCESSAMENTO');
      expect(item.originalPayload).toEqual({ turno: 1 });
      expect(item.correction?.justification).toBe('Corrigido pelo backoffice');
      expect(item.receivedAt).toBe('2026-09-25T13:07:43-03:00');
      expect(item.attempts).toBe(2);
      expect(item.attachments[0].sha256).toBe('abc');
      expect(item.history[0].createdAt).toBe('2026-09-25T14:00:00-03:00');
    });

    const request = http.expectOne((req) => req.url.endsWith('/v1/sincronizacao/casos/case-1'));
    request.flush({
      id: 'case-1', idOperacaoLocal: 'local-1', tipo: 'TURNO', status: 'AGUARDANDO_REPROCESSAMENTO',
      motivo: 'Falha de validação', idResponsavel: null, original: { turno: 1 },
      recebidoEm: '2026-09-25T13:07:43-03:00', tentativas: 2,
      anexos: [{ id: 'a-1', idLocal: 'l-1', nome: 'foto.jpg', contentType: 'image/jpeg', sha256: 'abc', tamanhoBytes: 42 }],
      ultimaVersao: { turno: 2 }, versoes: [{ numero: 1, payload: { turno: 2 }, justificativa: 'Corrigido pelo backoffice', hash: 'abc', idUsuario: 'u-1', criadaEm: '2026-09-25T14:00:00-03:00' }]
    });
  });

  it('downloads an attachment as a blob', () => {
    const mockBlob = new Blob(['test-data'], { type: 'image/jpeg' });
    service.downloadAttachment('case-1', 'anexo-1').subscribe((blob) => {
      expect(blob).toBeTruthy();
      expect(blob.type).toBe('image/jpeg');
    });

    const req = http.expectOne((r) => r.url.endsWith('/v1/sincronizacao/casos/case-1/anexos/anexo-1'));
    expect(req.request.method).toBe('GET');
    req.flush(mockBlob);
  });

  it('descarta o caso com justificativa e mostra o motivo persistido', () => {
    service.discard('case-1', 'Registro de teste').subscribe(item => {
      expect(item.status).toBe('REJEITADO');
      expect(item.justificativaUltima).toBe('Registro de teste');
    });
    const req = http.expectOne(r => r.url.endsWith('/v1/sincronizacao/casos/case-1/descarte'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ justificativa: 'Registro de teste' });
    req.flush({ id: 'case-1', idOperacaoLocal: 'local-1', tipo: 'SOLICITACAO', status: 'REJEITADO', motivo: 'Quantidade inválida', justificativaUltima: 'Registro de teste', original: {}, anexos: [] });
  });
});
