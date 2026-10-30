import { inject } from '@angular/core';
import { Router, CanMatchFn } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Bloqueia módulos administrativos (inspeções, descontos) para o usuário de campo.
 *
 * Esconder o item na bottom-nav não basta: sem isto a rota continua acessível
 * por URL direta e por navegação programática, e o encarregado cairia numa tela
 * que dispara chamadas negadas (403) para funcionalidades que nem deveria
 * saber que existem.
 *
 * `CanMatchFn` (e não `CanActivateFn`) para que o chunk lazy do módulo sequer
 * seja baixado por quem não tem acesso.
 */
export const adminGuard: CanMatchFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.podeAcessarInspecoes()) {
    return true;
  }

  return router.createUrlTree(['/dashboard']);
};
