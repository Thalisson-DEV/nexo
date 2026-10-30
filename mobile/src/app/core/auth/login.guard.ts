import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { rotaInternaSegura } from '../sessao/snapshot';
import { ROTA_INICIO } from '../sessao/retomada.guard';

/**
 * Quem tem sessão neste aparelho não vê o formulário de login.
 *
 * O Android reabre o app instalado na última página carregada, e não pelo ícone: se ela
 * foi o login, toda reabertura mostrava o formulário com a sessão ainda válida, e a
 * pessoa entrava de novo (e a próxima abertura caía no login outra vez). Com sessão
 * local, segue para onde ia (`volta`) ou para o início; sem token, tenta o cookie.
 *
 * O formulário aparece só sem sessão nenhuma (inclusive depois de "Sair") ou quando o
 * servidor encerrou a sessão e a pessoa tocou em "Entrar" no aviso.
 */
export const loginGuard: CanActivateFn = async (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.reautenticacaoNecessaria()) return true;

  if (authService.isAuthenticated() || (await authService.restaurarSessao())) {
    return router.parseUrl(rotaInternaSegura(route.queryParamMap.get('volta')) ?? ROTA_INICIO);
  }
  return true;
};
