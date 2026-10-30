import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TurnoService } from './turno.service';
import { DbService } from '../db/db.service';
import { AuthService } from '../auth/auth.service';
import { environment } from '../../../environments/environment';
import { TurnoLocal } from '../db/models';

describe('TurnoService', () => {
  let service: TurnoService;
  let httpMock: HttpTestingController;
  let dbSpy: jasmine.SpyObj<DbService>;
  let authSpy: jasmine.SpyObj<AuthService>;
  let onLineSpy: jasmine.Spy;

  beforeEach(() => {
    dbSpy = jasmine.createSpyObj('DbService', ['getTurnoAtivo', 'saveTurno', 'fecharTurnoLocal', 'getUltimoTurno']);
    dbSpy.getUltimoTurno.and.resolveTo(undefined);
    authSpy = jasmine.createSpyObj('AuthService', ['currentUser']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        TurnoService,
        { provide: DbService, useValue: dbSpy },
        { provide: AuthService, useValue: authSpy }
      ]
    });

    service = TestBed.inject(TurnoService);
    httpMock = TestBed.inject(HttpTestingController);

    // Mock window.navigator property just in case
    onLineSpy = spyOnProperty(window.navigator, 'onLine').and.returnValue(true);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('loadTurnoAtivo()', () => {
    it('should load local turno and set signal, and trigger sync if online', async () => {
      const mockTurno: TurnoLocal = { id: 't-1', idEquipe: 'e-1', idUsuarioAbertura: 'u-1', status: 'ABERTO', dataInicio: new Date().toISOString() };
      dbSpy.getTurnoAtivo.and.resolveTo(mockTurno);
      authSpy.currentUser.and.returnValue({ id: 'u-1', equipeId: 'e-1' } as any);

      await service.loadTurnoAtivo();

      expect(service.turnoAtivo()).toEqual(mockTurno);

      // Since online is true, it should have triggered a sync request
      const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/equipe/e-1/ativo`);
      expect(req.request.method).toBe('GET');
      req.flush(null); // No active turno on server for this test
    });

    it('encerra no aparelho, mesmo sem sinal, o turno que passou do corte das 22h', async () => {
      onLineSpy.and.returnValue(false);
      const ontem: TurnoLocal = { id: 't-ontem', idEquipe: 'e-1', idUsuarioAbertura: 'u-1', status: 'ABERTO', dataInicio: '2026-10-20T09:10:00-03:00' };
      dbSpy.getTurnoAtivo.and.resolveTo(ontem);
      dbSpy.fecharTurnoLocal.and.resolveTo();
      dbSpy.getUltimoTurno.and.resolveTo({ ...ontem, status: 'FECHADO', encerradoAutomaticamente: true });

      await service.loadTurnoAtivo();

      expect(dbSpy.fecharTurnoLocal).toHaveBeenCalledWith('t-ontem', '2026-10-21T01:00:00.000Z',
        { encerradoAutomaticamente: true, motivoEncerramento: 'Turno aberto além do horário de corte.' });
      expect(service.turnoAtivo()).toBeNull();
      expect(service.encerramentoAutomatico()?.id).toBe('t-ontem');
    });
  });

  describe('syncTurnoAtivoComServidor()', () => {
    it('should save server turno if local is null and server has ABERTO', async () => {
      authSpy.currentUser.and.returnValue({ id: 'u-1', equipeId: 'e-1' } as any);
      service.turnoAtivo.set(null); // Local is null

      const syncPromise = service.syncTurnoAtivoComServidor();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/equipe/e-1/ativo`);
      req.flush({ id: 't-server', idEquipe: 'e-1', status: 'ABERTO' }); // Server says opened

      await syncPromise;

      expect(dbSpy.saveTurno).toHaveBeenCalledWith(jasmine.objectContaining({ id: 't-server', status: 'ABERTO' }));
      expect(service.turnoAtivo()?.id).toBe('t-server');
    });

    it('should close local turno if server confirms no active turno', fakeAsync(() => {
      authSpy.currentUser.and.returnValue({ id: 'u-1', equipeId: 'e-1' } as any);
      service.turnoAtivo.set({ id: 't-local', idEquipe: 'e-1', idUsuarioAbertura: 'u-1', status: 'ABERTO', dataInicio: '' });

      let done = false;
      service.syncTurnoAtivoComServidor().then(() => done = true);

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/equipe/e-1/ativo`);
      // Simula backend retornando 404 (nenhum turno ativo)
      req.flush('Not Found', { status: 404, statusText: 'Not Found' });
      tick();
      httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/t-local`)
        .flush({ id: 't-local', status: 'FECHADO', dataFim: '2026-10-13T17:00:00-03:00', encerradoAutomaticamente: false });
      tick();

      expect(done).toBeTrue();
      expect(dbSpy.fecharTurnoLocal).toHaveBeenCalledWith('t-local', '2026-10-13T17:00:00-03:00', undefined);
      expect(service.turnoAtivo()).toBeNull();
      expect(service.encerramentoAutomatico()).toBeNull();
    }));

    it('guarda o informativo quando o servidor encerrou o turno automaticamente', fakeAsync(() => {
      authSpy.currentUser.and.returnValue({ id: 'u-1', equipeId: 'e-1' } as any);
      service.turnoAtivo.set({ id: 't-local', idEquipe: 'e-1', idUsuarioAbertura: 'u-1', status: 'ABERTO', dataInicio: '2026-10-13T08:00:00-03:00' });
      const encerrado: TurnoLocal = {
        id: 't-local', idEquipe: 'e-1', idUsuarioAbertura: 'u-1', status: 'FECHADO',
        dataInicio: '2026-10-13T08:00:00-03:00', dataFim: '2026-10-13T16:40:00-03:00',
        encerradoAutomaticamente: true, motivoEncerramento: 'Encerrado no corte das 22:00'
      };
      dbSpy.getUltimoTurno.and.resolveTo(encerrado);

      service.syncTurnoAtivoComServidor();
      httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/equipe/e-1/ativo`)
        .flush('Not Found', { status: 404, statusText: 'Not Found' });
      tick();
      httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/t-local`).flush({
        id: 't-local', status: 'FECHADO', dataFim: '2026-10-13T16:40:00-03:00',
        encerradoAutomaticamente: true, motivoEncerramento: 'Encerrado no corte das 22:00'
      });
      tick();

      expect(dbSpy.fecharTurnoLocal).toHaveBeenCalledWith('t-local', '2026-10-13T16:40:00-03:00',
        { encerradoAutomaticamente: true, motivoEncerramento: 'Encerrado no corte das 22:00' });
      expect(service.encerramentoAutomatico()?.id).toBe('t-local');
    }));

    it('fecha localmente mesmo sem conseguir consultar o turno encerrado', fakeAsync(() => {
      authSpy.currentUser.and.returnValue({ id: 'u-1', equipeId: 'e-1' } as any);
      service.turnoAtivo.set({ id: 't-local', idEquipe: 'e-1', idUsuarioAbertura: 'u-1', status: 'ABERTO', dataInicio: '' });

      service.syncTurnoAtivoComServidor();
      httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/equipe/e-1/ativo`)
        .flush('Not Found', { status: 404, statusText: 'Not Found' });
      tick();
      httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/t-local`)
        .flush(null, { status: 500, statusText: 'Server Error' });
      tick();

      expect(dbSpy.fecharTurnoLocal).toHaveBeenCalledWith('t-local', jasmine.any(String), undefined);
      expect(service.turnoAtivo()).toBeNull();
    }));

    it('preserva o turno local quando a consulta falha por sessão expirada', fakeAsync(() => {
      authSpy.currentUser.and.returnValue({ id: 'u-1', equipeId: 'e-1' } as any);
      service.turnoAtivo.set({ id: 't-local', idEquipe: 'e-1', idUsuarioAbertura: 'u-1', status: 'ABERTO', dataInicio: '' });

      let erro: unknown;
      service.syncTurnoAtivoComServidor().catch(e => erro = e);
      httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/equipe/e-1/ativo`)
        .flush(null, { status: 401, statusText: 'Unauthorized' });
      tick();

      expect(dbSpy.fecharTurnoLocal).not.toHaveBeenCalled();
      expect(service.turnoAtivo()?.id).toBe('t-local');
      expect((erro as { status: number }).status).toBe(401);
    }));
  });

  describe('abrirTurno()', () => {
    it('should throw error if no equipeId', async () => {
      authSpy.currentUser.and.returnValue(null);
      await expectAsync(service.abrirTurno()).toBeRejectedWithError(/Usuário ou equipe não identificado/);
    });

    it('should just save local if server already has an active turno for this equipe', async () => {
      authSpy.currentUser.and.returnValue({ id: 'u-1', equipeId: 'e-1' } as any);

      const promise = service.abrirTurno();

      // 1. Checagem inicial
      const reqAtivo = httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/equipe/e-1/ativo`);
      reqAtivo.flush({ id: 't-already-open', idEquipe: 'e-1', status: 'ABERTO' });

      await promise;

      // Não deve fazer o POST
      httpMock.expectNone(`${environment.apiBaseUrl}/v1/turnos/abrir`);
      
      expect(dbSpy.saveTurno).toHaveBeenCalledWith(jasmine.objectContaining({ id: 't-already-open' }));
      expect(service.turnoAtivo()?.id).toBe('t-already-open');
    });

    it('should POST /abrir if server does not have an active turno', fakeAsync(() => {
      authSpy.currentUser.and.returnValue({ id: 'u-1', equipeId: 'e-1' } as any);

      let done = false;
      service.abrirTurno().then(() => done = true);

      // 1. Checagem inicial
      const reqAtivo = httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/equipe/e-1/ativo`);
      reqAtivo.flush('Not Found', { status: 404, statusText: 'Not Found' }); // Ninguém abriu ainda

      tick(); // resolve o await do GET

      // 2. Criação
      const reqAbrir = httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/abrir`);
      // Sem conferência: a composição cadastrada é mantida (campos nulos).
      expect(reqAbrir.request.body).toEqual({ idEquipe: 'e-1', idVeiculo: null, idsColaboradores: null });
      reqAbrir.flush({ id: 't-new', idEquipe: 'e-1', status: 'ABERTO' });

      tick(); // resolve o await do POST

      expect(done).toBeTrue();
      expect(dbSpy.saveTurno).toHaveBeenCalledWith(jasmine.objectContaining({ id: 't-new' }));
      expect(service.turnoAtivo()?.id).toBe('t-new');
    }));
  });

  describe('abrirTurno() com conferência da equipe', () => {
    it('envia o veículo e os parceiros escolhidos pelo líder', fakeAsync(() => {
      authSpy.currentUser.and.returnValue({ id: 'u-1', equipeId: 'e-1' } as any);
      spyOn(service, 'sincronizarComposicao').and.returnValue(Promise.resolve({} as any));

      service.abrirTurno({ idVeiculo: 'v-2', idsColaboradores: ['c-2', 'c-3'] });

      httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/equipe/e-1/ativo`)
        .flush('Not Found', { status: 404, statusText: 'Not Found' });
      tick();

      const reqAbrir = httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/abrir`);
      expect(reqAbrir.request.body).toEqual({ idEquipe: 'e-1', idVeiculo: 'v-2', idsColaboradores: ['c-2', 'c-3'] });
      reqAbrir.flush({ id: 't-2', idEquipe: 'e-1', status: 'ABERTO', idVeiculo: 'v-2', idsColaboradores: ['c-1', 'c-2', 'c-3'] });
      tick();

      expect(dbSpy.saveTurno).toHaveBeenCalledWith(jasmine.objectContaining({ id: 't-2', idVeiculo: 'v-2' }));
      expect(service.sincronizarComposicao).toHaveBeenCalled();
    }));
  });

  describe('fecharTurno()', () => {
    it('should POST /fechar and close locally', async () => {
      service.turnoAtivo.set({ id: 't-1', idEquipe: 'e-1', idUsuarioAbertura: 'u-1', status: 'ABERTO', dataInicio: '' });

      const promise = service.fecharTurno();

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/t-1/fechar`);
      expect(req.request.method).toBe('POST');
      req.flush({ success: true });

      await promise;

      expect(dbSpy.fecharTurnoLocal).toHaveBeenCalledWith('t-1', jasmine.any(String));
      expect(service.turnoAtivo()).toBeNull();
    });

    it('should preserve the open turno if backend returns 400', fakeAsync(() => {
      authSpy.currentUser.and.returnValue({ id: 'u-1', equipeId: 'e-1' } as any);
      service.turnoAtivo.set({ id: 't-1', idEquipe: 'e-1', idUsuarioAbertura: 'u-1', status: 'ABERTO', dataInicio: '' });

      let done = false;
      service.fecharTurno().then(() => done = true).catch(() => done = true);

      const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/turnos/t-1/fechar`);
      req.flush('Bad Request', { status: 400, statusText: 'Bad Request' }); // Turno já havia sido fechado via web, por exemplo

      tick();

      // Como deu erro 400, ele tenta reconciliar
      // Nenhuma reconciliação destrutiva é feita após a rejeição.

      tick();

      expect(dbSpy.fecharTurnoLocal).not.toHaveBeenCalled();
      expect(service.turnoAtivo()?.status).toBe('ABERTO');
    }));
  });
});
