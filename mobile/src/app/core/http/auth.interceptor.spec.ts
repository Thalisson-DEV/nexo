import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { authInterceptor } from './auth.interceptor';
import { TokenService } from '../auth/token.service';
import { AuthService } from '../auth/auth.service';

describe('authInterceptor (mobile)', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  const token = signal<string | null>('jwt');
  let vencido = false;
  let auth: jasmine.SpyObj<AuthService>;

  const umTique = () => new Promise(resolve => setTimeout(resolve, 0));

  beforeEach(() => {
    token.set('jwt');
    vencido = false;
    auth = jasmine.createSpyObj('AuthService', ['podeRenovar', 'renovarToken', 'tokenAtualizado']);
    auth.podeRenovar.and.returnValue(true);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: TokenService, useValue: { token, isTokenExpired: () => vencido } },
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('rotas de sessão vão com cookie e sem Bearer (o token pode estar vencido)', () => {
    http.post('/api/v1/auth/refresh', null).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/refresh');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ token: 'novo' });
  });

  it('em 401 de token vencido renova e repete a requisição', async () => {
    auth.renovarToken.and.resolveTo('jwt-novo');
    const resposta = jasmine.createSpy('resposta');

    http.get('/api/v1/turnos').subscribe(resposta);
    httpMock.expectOne('/api/v1/turnos').flush({ code: 'TOKEN_EXPIRADO' }, { status: 401, statusText: 'Unauthorized' });
    await umTique();

    const repetida = httpMock.expectOne('/api/v1/turnos');
    expect(repetida.request.headers.get('Authorization')).toBe('Bearer jwt-novo');
    repetida.flush({ ok: true });
    expect(resposta).toHaveBeenCalledWith({ ok: true });
  });

  it('sem rede para renovar, devolve o 401 original (a fila guarda o item)', async () => {
    auth.renovarToken.and.rejectWith({ status: 0 });
    const erro = jasmine.createSpy('erro');

    http.get('/api/v1/turnos').subscribe({ error: erro });
    httpMock.expectOne('/api/v1/turnos').flush({}, { status: 401, statusText: 'Unauthorized' });
    await umTique();

    expect(erro).toHaveBeenCalledWith(jasmine.objectContaining({ status: 401 }));
  });

  it('SESSAO_INVALIDA não tenta renovar', () => {
    http.get('/api/v1/turnos').subscribe({ error: () => {} });
    httpMock.expectOne('/api/v1/turnos').flush({ code: 'SESSAO_INVALIDA' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.renovarToken).not.toHaveBeenCalled();
  });

  it('token já vencido: renova antes de enviar', async () => {
    vencido = true;
    auth.tokenAtualizado.and.resolveTo('jwt-novo');

    http.get('/api/v1/turnos').subscribe();
    await umTique();

    const req = httpMock.expectOne('/api/v1/turnos');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-novo');
    req.flush({});
  });
});
