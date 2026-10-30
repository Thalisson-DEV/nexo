import '@angular/compiler';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { MessageService } from 'primeng/api';

describe('authInterceptor', () => {
  let http: HttpClient;
  let mock: HttpTestingController;
  let authServiceMock: {
    token: ReturnType<typeof vi.fn>;
    isAuthenticated: ReturnType<typeof vi.fn>;
    tokenVencido: ReturnType<typeof vi.fn>;
    podeRenovar: ReturnType<typeof vi.fn>;
    tokenAtualizado: ReturnType<typeof vi.fn>;
    renovarToken: ReturnType<typeof vi.fn>;
    marcarReautenticacaoNecessaria: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let messageServiceMock: { add: ReturnType<typeof vi.fn> };

  const flushMicrotasks = () => new Promise((resolve) => setTimeout(resolve, 0));

  beforeEach(() => {
    authServiceMock = {
      token: vi.fn().mockReturnValue(null),
      isAuthenticated: vi.fn().mockReturnValue(true),
      tokenVencido: vi.fn().mockReturnValue(false),
      podeRenovar: vi.fn().mockReturnValue(true),
      tokenAtualizado: vi.fn(),
      renovarToken: vi.fn(),
      marcarReautenticacaoNecessaria: vi.fn(),
      logout: vi.fn()
    };
    messageServiceMock = {
      add: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceMock },
        { provide: MessageService, useValue: messageServiceMock }
      ]
    });
    http = TestBed.inject(HttpClient);
    mock = TestBed.inject(HttpTestingController);
  });

  it('anexa o header X-Requested-With para suprimir o modal de HTTP Basic Auth do navegador', () => {
    http.get('/api/v1/solicitacoes').subscribe();

    const req = mock.expectOne('/api/v1/solicitacoes');
    expect(req.request.headers.get('X-Requested-With')).toBe('XMLHttpRequest');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
    mock.verify();
  });

  it('anexa o Bearer token quando o usuário está autenticado', () => {
    authServiceMock.token.mockReturnValue('jwt-token-123');

    http.get('/api/v1/solicitacoes').subscribe();

    const req = mock.expectOne('/api/v1/solicitacoes');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token-123');
    req.flush([]);
    mock.verify();
  });

  it('não envia Bearer nas rotas de sessão e manda o cookie', () => {
    authServiceMock.token.mockReturnValue('jwt-vencido');

    http.post('/api/v1/auth/refresh', null).subscribe();

    const req = mock.expectOne('/api/v1/auth/refresh');
    expect(req.request.headers.has('Authorization')).toBe(false);
    expect(req.request.withCredentials).toBe(true);
    req.flush({ token: 'novo' });
    mock.verify();
  });

  it('em 401 de token vencido renova e repete a requisição, sem deslogar', async () => {
    authServiceMock.token.mockReturnValue('jwt-vencido');
    authServiceMock.renovarToken.mockResolvedValue('jwt-novo');
    const resposta = vi.fn();

    http.get('/api/v1/solicitacoes').subscribe(resposta);

    mock.expectOne('/api/v1/solicitacoes').flush(
      { code: 'TOKEN_EXPIRADO', type: 'https://app.exemplo.com/errors/token-expired' },
      { status: 401, statusText: 'Unauthorized' }
    );
    await flushMicrotasks();

    const repetida = mock.expectOne('/api/v1/solicitacoes');
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer jwt-novo');
    repetida.flush(['ok']);

    expect(authServiceMock.renovarToken).toHaveBeenCalledWith('jwt-vencido');
    expect(resposta).toHaveBeenCalledWith(['ok']);
    expect(authServiceMock.logout).not.toHaveBeenCalled();
    mock.verify();
  });

  it('se a renovação falhar (sem rede), devolve o 401 original e mantém a sessão', async () => {
    authServiceMock.token.mockReturnValue('jwt-vencido');
    authServiceMock.renovarToken.mockRejectedValue({ status: 0 });
    const erro = vi.fn();

    http.get('/api/v1/solicitacoes').subscribe({ error: erro });

    mock.expectOne('/api/v1/solicitacoes').flush({}, { status: 401, statusText: 'Unauthorized' });
    await flushMicrotasks();

    expect(erro).toHaveBeenCalledWith(expect.objectContaining({ status: 401 }));
    expect(authServiceMock.logout).not.toHaveBeenCalled();
    mock.verify();
  });

  it('em SESSAO_INVALIDA marca a sessão para novo login, sem apagar a sessão local', () => {
    authServiceMock.token.mockReturnValue('jwt');

    http.get('/api/v1/solicitacoes').subscribe({ error: () => {} });

    mock.expectOne('/api/v1/solicitacoes').flush(
      { code: 'SESSAO_INVALIDA' },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(authServiceMock.marcarReautenticacaoNecessaria).toHaveBeenCalled();
    expect(authServiceMock.renovarToken).not.toHaveBeenCalled();
    expect(authServiceMock.logout).not.toHaveBeenCalled();
    mock.verify();
  });

  it('renova antes de enviar quando o token já venceu', async () => {
    authServiceMock.token.mockReturnValue('jwt-vencido');
    authServiceMock.tokenVencido.mockReturnValue(true);
    authServiceMock.tokenAtualizado.mockResolvedValue('jwt-novo');

    http.get('/api/v1/solicitacoes').subscribe();
    await flushMicrotasks();

    const req = mock.expectOne('/api/v1/solicitacoes');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-novo');
    req.flush([]);
    mock.verify();
  });

  it('exibe toast de acesso negado e não mexe na sessão quando o 401 for por falta de permissão', () => {
    authServiceMock.token.mockReturnValue('jwt-token-valido');

    http.get('/api/v1/admin').subscribe({
      error: () => {}
    });

    const req = mock.expectOne('/api/v1/admin');
    req.flush(
      {
        type: 'https://app.exemplo.com/errors/access-denied',
        title: 'Acesso Negado',
        detail: 'Permissão insuficiente'
      },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(authServiceMock.logout).not.toHaveBeenCalled();
    expect(authServiceMock.renovarToken).not.toHaveBeenCalled();
    expect(messageServiceMock.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        summary: 'Acesso Negado'
      })
    );
    mock.verify();
  });
});
