import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Com sessão neste aparelho o app abre, com ou sem internet e mesmo com o token vencido:
 * o encarregado precisa lançar em campo, e o envio espera a conexão. Sem sessão local,
 * tenta recuperá-la pelo cookie (o sistema pode ter limpado os dados) antes do login.
 */
export const authGuard: CanActivateFn = async () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated() || (await authService.restaurarSessao())) {
    return true;
  }

  return router.parseUrl('/login');
};
