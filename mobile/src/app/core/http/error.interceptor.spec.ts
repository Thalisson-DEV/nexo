import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { errorInterceptor, ApiError } from './error.interceptor';
import { silentContext } from './http-context';
import { AuthService } from '../auth/auth.service';
import { ToastService } from '../ui/toast.service';
import { TokenService } from '../auth/token.service';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authSpy: jasmine.SpyObj<AuthService>;
  let toastSpy: jasmine.SpyObj<ToastService>;

  beforeEach(() => {
    authSpy = jasmine.createSpyObj('AuthService', ['requireReauthentication']);
    const tokenSpy = jasmine.createSpyObj('TokenService', ['token']);
    tokenSpy.token.and.returnValue('token-atual');
    toastSpy = jasmine.createSpyObj('ToastService', ['error', 'success', 'warning', 'info']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authSpy },
        { provide: TokenService, useValue: tokenSpy },
        { provide: ToastService, useValue: toastSpy }
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function dispararErro(
    status: number,
    body: string | Object | null = null,
    opts: { silencioso?: boolean } = {}
  ): Promise<ApiError> {
    return new Promise<ApiError>(resolve => {
      const options = opts.silencioso ? { context: silentContext() } : {};
      http.get('/api/v1/teste', options).subscribe({
        next: () => fail('deveria falhar'),
        error: (e: ApiError) => resolve(e)
      });

      httpMock.expectOne('/api/v1/teste').flush(body, { status, statusText: 'Erro' });
    });
  }

  it('traduz 403 sem corpo para uma mensagem acionável em português', async () => {
    const erro = await dispararErro(403);

    expect(erro.status).toBe(403);
    expect(erro.title).toBe('Acesso não permitido');
    expect(erro.detail).toBe(
      'Seu perfil de acesso não permite esta operação. Fale com o administrador do sistema.'
    );
    // 403 significa "sem permissão", não "sessão inválida": não pode deslogar.
    expect(authSpy.requireReauthentication).not.toHaveBeenCalled();
    expect(toastSpy.error).toHaveBeenCalled();
  });

  it('prefere o detalhe do backend quando ele existe (RFC 7807)', async () => {
    const erro = await dispararErro(403, {
      title: 'Permissão ausente',
      detail: 'Requer a permissão material:ler.'
    });

    expect(erro.detail).toBe('Requer a permissão material:ler.');
    expect(erro.title).toBe('Permissão ausente');
  });

  it('401 de token (renovação impossível sem rede) não pede login nem tira da tela', async () => {
    const erro = await dispararErro(401);

    expect(erro.detail).toContain('fica salvo');
    expect(authSpy.requireReauthentication).not.toHaveBeenCalled();
  });

  it('401 SESSAO_INVALIDA marca a reautenticação, sem apagar a identidade local', async () => {
    const erro = await dispararErro(401, { code: 'SESSAO_INVALIDA', title: 'Sessão Encerrada', detail: 'x' });

    expect(erro.detail).toContain('lançamentos continuam salvos');
    expect(authSpy.requireReauthentication).toHaveBeenCalled();
  });

  it('não redireciona novamente quando as credenciais do login são rejeitadas', async () => {
    http.post('/api/v1/auth/login', { login: 'equipe', senha: 'incorreta' }).subscribe({ error: () => {} });
    httpMock.expectOne('/api/v1/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(authSpy.requireReauthentication).not.toHaveBeenCalled();
  });

  it('não exibe toast em requisições marcadas como silenciosas', async () => {
    await dispararErro(403, null, { silencioso: true });

    expect(toastSpy.error).not.toHaveBeenCalled();
  });

  it('não expõe mensagem técnica quando o corpo do erro é inútil', async () => {
    const erro = await dispararErro(403, '<html>Forbidden</html>');

    expect(erro.detail).not.toContain('Http failure');
  });
});
