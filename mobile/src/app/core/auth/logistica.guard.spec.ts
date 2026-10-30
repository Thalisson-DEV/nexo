import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { logisticaGuard } from './logistica.guard';
import { AuthService } from './auth.service';

describe('logisticaGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['podeAcessarLogistica']);
    routerSpy = jasmine.createSpyObj('Router', ['createUrlTree']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  const runGuard = () => TestBed.runInInjectionContext(() => logisticaGuard({} as any, []));

  it('permite acesso para usuários com acesso à logística (campo)', () => {
    authServiceSpy.podeAcessarLogistica.and.returnValue(true);
    const result = runGuard();
    expect(result).toBeTrue();
  });

  it('redireciona para /dashboard se for usuário administrativo', () => {
    const mockTree = {} as UrlTree;
    authServiceSpy.podeAcessarLogistica.and.returnValue(false);
    routerSpy.createUrlTree.and.returnValue(mockTree);

    const result = runGuard();
    expect(result).toBe(mockTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
  });
});
