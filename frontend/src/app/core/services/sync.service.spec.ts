import { TestBed } from '@angular/core/testing';
import { SyncService } from './sync.service';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { DatabaseService } from './database.service';
import { ConnectivityService } from './connectivity.service';
import { AuthService } from './auth.service';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

describe('SyncService', () => {
  let service: SyncService;
  let mockHttp: { request: ReturnType<typeof vi.fn> };
  let mockDb: {
    syncQueue: {
      add: ReturnType<typeof vi.fn>;
      delete: ReturnType<typeof vi.fn>;
      update: ReturnType<typeof vi.fn>;
      orderBy: ReturnType<typeof vi.fn>;
    };
  };
  let mockConnectivity: { isOnline: ReturnType<typeof signal> };
  let queueArray: any[];
  const usuario = signal<{ id: string } | null>({ id: 'usuario-a' });
  const reautenticacaoNecessaria = signal(false);

  beforeEach(() => {
    mockHttp = {
      request: vi.fn(),
    };

    queueArray = [];
    usuario.set({ id: 'usuario-a' });
    reautenticacaoNecessaria.set(false);
    mockDb = {
      syncQueue: {
        add: vi.fn().mockImplementation((item) => {
          queueArray.push(item);
          return Promise.resolve(1);
        }),
        delete: vi.fn().mockResolvedValue(undefined),
        update: vi.fn().mockResolvedValue(undefined),
        orderBy: vi.fn().mockReturnValue({
          toArray: vi.fn().mockResolvedValue(queueArray),
        }),
      },
    };

    mockConnectivity = {
      isOnline: signal(true),
    };

    TestBed.configureTestingModule({
      providers: [
        SyncService,
        { provide: HttpClient, useValue: mockHttp },
        { provide: DatabaseService, useValue: mockDb },
        { provide: ConnectivityService, useValue: mockConnectivity },
        {
          provide: AuthService,
          useValue: { currentUser: usuario, reautenticacaoNecessaria },
        },
      ],
    });

    service = TestBed.inject(SyncService);
  });

  it('deve enfileirar mutação imediatamente sem chamar HTTP quando estiver offline', async () => {
    mockConnectivity.isOnline.set(false);

    const result = await service.execute('POST', '/v1/apontamentos', { nota: '123' });

    expect(result).toBeNull();
    expect(mockHttp.request).not.toHaveBeenCalled();
    expect(mockDb.syncQueue.add).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'POST',
        url: '/v1/apontamentos',
        body: { nota: '123' },
      })
    );
  });

  it('deve enviar via HTTP quando estiver online e retornar o resultado', async () => {
    mockConnectivity.isOnline.set(true);
    mockHttp.request.mockReturnValue(of({ id: 'criado-1' }));

    const result = await service.execute('POST', '/v1/apontamentos', { nota: '123' });

    expect(result).toEqual({ id: 'criado-1' });
    expect(mockHttp.request).toHaveBeenCalled();
  });

  it('deve enfileirar quando a chamada falhar com status 0 (queda de sinal / bloqueio)', async () => {
    mockConnectivity.isOnline.set(true);
    mockHttp.request.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' }))
    );

    const result = await service.execute('POST', '/v1/apontamentos', { nota: '123' });

    expect(result).toBeNull();
    expect(mockDb.syncQueue.add).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'POST',
        url: '/v1/apontamentos',
        body: { nota: '123' },
      })
    );
  });

  const processar = () => (service as any).processQueue() as Promise<void>;
  const erro = (status: number) => throwError(() => new HttpErrorResponse({ status }));

  it('enfileira, sem perder o lançamento, quando o servidor responde 401 (sessão a renovar)', async () => {
    mockHttp.request.mockReturnValue(erro(401));

    const result = await service.execute('POST', '/v1/apontamentos', { nota: '123' });

    expect(result).toBeNull();
    expect(mockDb.syncQueue.add).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/v1/apontamentos', usuarioId: 'usuario-a' })
    );
  });

  it('mantém o item na fila em 401 e 429, sem queimar tentativas', async () => {
    for (const status of [401, 429]) {
      queueArray.splice(0, queueArray.length, {
        id: 1, method: 'POST', url: '/v1/x', body: {}, timestamp: 1, chaveIdempotencia: 'k', usuarioId: 'usuario-a',
      });
      mockDb.syncQueue.delete.mockClear();
      mockDb.syncQueue.update.mockClear();
      mockHttp.request.mockReturnValue(erro(status));

      await processar();

      expect(mockDb.syncQueue.delete).not.toHaveBeenCalled();
      expect(mockDb.syncQueue.update).not.toHaveBeenCalled();
    }
  });

  it('continua descartando recusas definitivas (400)', async () => {
    queueArray.push({ id: 1, method: 'POST', url: '/v1/x', body: {}, timestamp: 1, chaveIdempotencia: 'k' });
    mockHttp.request.mockReturnValue(erro(400));

    await processar();

    expect(mockDb.syncQueue.delete).toHaveBeenCalledWith(1);
  });

  it('não envia lançamentos de outro usuário do mesmo aparelho', async () => {
    queueArray.push(
      { id: 1, method: 'POST', url: '/v1/de-b', body: {}, timestamp: 1, chaveIdempotencia: 'k1', usuarioId: 'usuario-b' },
      { id: 2, method: 'POST', url: '/v1/de-a', body: {}, timestamp: 2, chaveIdempotencia: 'k2', usuarioId: 'usuario-a' }
    );
    mockHttp.request.mockReturnValue(of({}));

    await processar();

    expect(mockHttp.request).toHaveBeenCalledTimes(1);
    expect(mockHttp.request).toHaveBeenCalledWith('POST', '/v1/de-a', expect.anything());
    expect(mockDb.syncQueue.delete).toHaveBeenCalledWith(2);
  });

  it('não tenta sincronizar enquanto a sessão aguarda novo login', async () => {
    queueArray.push({ id: 1, method: 'POST', url: '/v1/x', body: {}, timestamp: 1, chaveIdempotencia: 'k' });
    reautenticacaoNecessaria.set(true);

    await processar();

    expect(mockHttp.request).not.toHaveBeenCalled();
  });
});
