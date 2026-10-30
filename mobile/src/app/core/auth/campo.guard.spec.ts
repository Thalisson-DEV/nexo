import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { campoGuard } from './campo.guard';
import { AuthService } from './auth.service';

describe('campoGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isCampo']);
    routerSpy = jasmine.createSpyObj('Router', ['createUrlTree']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  const runGuard = () => TestBed.runInInjectionContext(() => campoGuard({} as any, []));

  it('permite acesso para usuários do tipo CAMPO', () => {
    authServiceSpy.isCampo.and.returnValue(true);
    const result = runGuard();
    expect(result).toBeTrue();
  });

  it('redireciona para /dashboard se não for usuário de campo', () => {
    const mockTree = {} as UrlTree;
    authServiceSpy.isCampo.and.returnValue(false);
    routerSpy.createUrlTree.and.returnValue(mockTree);

    const result = runGuard();
    expect(result).toBe(mockTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/dashboard']);
  });
});
