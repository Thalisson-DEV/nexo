import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('AuthGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;
  const arvoreLogin = {} as UrlTree;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'restaurarSessao']);
    authServiceSpy.restaurarSessao.and.resolveTo(false);
    routerSpy = jasmine.createSpyObj('Router', ['parseUrl']);
    routerSpy.parseUrl.and.returnValue(arvoreLogin);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  const runGuard = () => TestBed.runInInjectionContext(() => authGuard({} as any, {} as any)) as Promise<boolean | UrlTree>;

  it('abre o app com a sessão do aparelho, com ou sem rede e mesmo com o token vencido', async () => {
    authServiceSpy.isAuthenticated.and.returnValue(true);

    expect(await runGuard()).toBeTrue();
    expect(authServiceSpy.restaurarSessao).not.toHaveBeenCalled();
  });

  it('sem sessão local, tenta recuperá-la pelo cookie antes do login', async () => {
    authServiceSpy.isAuthenticated.and.returnValue(false);
    authServiceSpy.restaurarSessao.and.resolveTo(true);

    expect(await runGuard()).toBeTrue();
  });

  it('sem sessão nenhuma, vai para o login', async () => {
    authServiceSpy.isAuthenticated.and.returnValue(false);

    expect(await runGuard()).toBe(arvoreLogin);
    expect(routerSpy.parseUrl).toHaveBeenCalledWith('/login');
  });
});
