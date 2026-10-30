import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Almoxarifado do perfil administrativo. `CanMatchFn` para o chunk nem ser baixado por quem
 * não tem acesso; cada parte confere a permissão que o backend exige (sem tela que dá 403).
 */
export const almoxGuard: CanMatchFn = () => {
  const auth = inject(AuthService);
  return auth.podeAcessarAlmoxarifado() ? true : inject(Router).createUrlTree(['/hoje']);
};

export const almoxSolicitacoesGuard: CanMatchFn = () => {
  const auth = inject(AuthService);
  if (auth.podeVerSolicitacoesAlmox()) return true;
  return inject(Router).createUrlTree([auth.podeVerMovimentacoes() ? '/almox/movimentacoes' : '/hoje']);
};

export const almoxEntregaGuard: CanMatchFn = () => {
  const auth = inject(AuthService);
  return auth.podeGerirSolicitacoesAlmox() ? true : inject(Router).createUrlTree(['/almox/solicitacoes']);
};

export const almoxMovimentacoesGuard: CanMatchFn = () => {
  const auth = inject(AuthService);
  if (auth.podeVerMovimentacoes()) return true;
  return inject(Router).createUrlTree([auth.podeVerSolicitacoesAlmox() ? '/almox/solicitacoes' : '/hoje']);
};

export const almoxMovimentarGuard: CanMatchFn = () => {
  const auth = inject(AuthService);
  return auth.podeMovimentarEstoque() ? true : inject(Router).createUrlTree(['/almox/movimentacoes']);
};
