import '@angular/compiler';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

const API = `${environment.apiUrl}/v1/auth`;

function jwt(payload: Record<string, unknown>): string {
  const base64 = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `header.${base64}.assinatura`;
}

const agoraEmSegundos = () => Math.floor(Date.now() / 1000);

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let router: { navigate: ReturnType<typeof vi.fn>; navigateByUrl: ReturnType<typeof vi.fn>; parseUrl: ReturnType<typeof vi.fn>; url: string };

  function criar(): void {
    router = {
      navigate: vi.fn(),
      navigateByUrl: vi.fn(),
      parseUrl: vi.fn().mockReturnValue({ queryParams: {} }),
      url: '/home'
    };
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: router }]
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  }

  async function entrar(token: string): Promise<void> {
    service.login({ login: 'ELETRICISTA', senha: 'x' }).subscribe();
    await Promise.resolve();
    await Promise.resolve();
    const req = http.expectOne(`${API}/login`);
    expect(req.request.withCredentials).toBe(true);
    req.flush({ token, sessaoExpiraEm: '2026-11-12T08:00:00-03:00' });
  }

  beforeEach(() => {
    localStorage.clear();
    criar();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('a sessão sobrevive ao fechamento do app (nova instância lê o armazenamento)', async () => {
    await entrar(jwt({ sub: 'u1', tipoUsuario: 'CAMPO', roles: ['a'], exp: agoraEmSegundos() + 3600 }));
    expect(service.isAuthenticated()).toBe(true);

    TestBed.resetTestingModule();
    criar();

    expect(service.isAuthenticated()).toBe(true);
    expect(service.currentUser()?.tipoUsuario).toBe('CAMPO');
  });

  it('token vencido não encerra a sessão local', async () => {
    await entrar(jwt({ sub: 'u1', exp: agoraEmSegundos() - 3600 }));

    expect(service.tokenVencido()).toBe(true);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('renova pelo cookie e atualiza token e permissões', async () => {
    await entrar(jwt({ sub: 'u1', roles: ['antiga'], exp: agoraEmSegundos() - 10 }));

    const renovacao = service.renovarToken(service.token());
    await Promise.resolve();
    const req = http.expectOne(`${API}/refresh`);
    expect(req.request.withCredentials).toBe(true);
    const novo = jwt({ sub: 'u1', roles: ['nova'], exp: agoraEmSegundos() + 3600 });
    req.flush({ token: novo });

    await expect(renovacao).resolves.toBe(novo);
    expect(service.currentUser()?.permissoes).toEqual(['nova']);
    expect(service.currentUser()?.login).toBe('ELETRICISTA');
  });

  it('renovações simultâneas fazem uma única chamada ao servidor', async () => {
    await entrar(jwt({ sub: 'u1', exp: agoraEmSegundos() - 10 }));
    const vencido = service.token();

    const a = service.renovarToken(vencido);
    const b = service.renovarToken(vencido);
    await Promise.resolve();
    http.expectOne(`${API}/refresh`).flush({ token: jwt({ sub: 'u1', exp: agoraEmSegundos() + 3600 }) });

    expect(await a).toBe(await b);
    http.verify();
  });

  it('SESSAO_INVALIDA na renovação marca reautenticação e mantém a sessão local', async () => {
    await entrar(jwt({ sub: 'u1', exp: agoraEmSegundos() - 10 }));

    const renovacao = service.renovarToken(service.token());
    await Promise.resolve();
    http.expectOne(`${API}/refresh`).flush(
      { code: 'SESSAO_INVALIDA' },
      { status: 401, statusText: 'Unauthorized' }
    );

    await expect(renovacao).rejects.toBeTruthy();
    expect(service.isAuthenticated()).toBe(true);
    expect(service.reautenticacaoNecessaria()).toBe(true);
    expect(service.podeRenovar()).toBe(false);
  });

  it('novo login do mesmo usuário limpa a marca de reautenticação', async () => {
    await entrar(jwt({ sub: 'u1', exp: agoraEmSegundos() + 3600 }));
    service.marcarReautenticacaoNecessaria();

    await entrar(jwt({ sub: 'u1', exp: agoraEmSegundos() + 3600 }));

    expect(service.reautenticacaoNecessaria()).toBe(false);
  });

  it('logout explícito apaga a sessão e encerra a do servidor', async () => {
    await entrar(jwt({ sub: 'u1', exp: agoraEmSegundos() + 3600 }));

    service.logout();
    await Promise.resolve();

    expect(service.isAuthenticated()).toBe(false);
    http.expectOne(`${API}/logout`).flush(null);
    await Promise.resolve();
    expect(localStorage.getItem('ces_logout_pendente')).toBeNull();
  });
});
