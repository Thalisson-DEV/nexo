import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Mantém quem já tem sessão fora da tela de login, com duas exceções:
 * - a sessão precisa de novo login (o servidor a encerrou): o login é o caminho de volta;
 * - não há sessão local mas há cookie de sessão (o navegador limpou o armazenamento):
 *   tenta restaurar antes de mostrar o formulário.
 */
export const publicGuard: CanActivateFn = async () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.reautenticacaoNecessaria()) {
    return true;
  }

  if (authService.isAuthenticated() || (await authService.restaurarSessao())) {
    return router.parseUrl('/home');
  }

  return true;
};
