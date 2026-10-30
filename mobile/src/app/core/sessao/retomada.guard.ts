import { Injector, inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessaoService } from './sessao.service';
import { AuthService } from '../auth/auth.service';

/** Rota de início do app. */
export const ROTA_INICIO = '/hoje';

/**
 * Raiz do app (`/`, que é o `start_url` do manifest). Na abertura a frio decide entre
 * voltar para onde o usuário estava, pedir login e depois voltar, ou abrir o início.
 * Sempre devolve um destino: a raiz nunca renderiza nada.
 */
export const retomadaGuard: CanActivateFn = async () => {
  const router = inject(Router);
  const sessao = inject(SessaoService);
  const injector = inject(Injector);
  await sessao.carregar();
  const decisao = sessao.consumirDecisao();

  if (decisao?.acao === 'retomar') return router.parseUrl(decisao.rota);
  if (decisao?.acao === 'login') {
    // Sem token no aparelho, a sessão ainda pode estar no cookie: recupera antes de pedir a senha.
    if (await injector.get(AuthService).restaurarSessao()) return router.parseUrl(decisao.rotaDepois ?? ROTA_INICIO);
    return router.createUrlTree(['/login'], decisao.rotaDepois ? { queryParams: { volta: decisao.rotaDepois } } : {});
  }
  return router.parseUrl(ROTA_INICIO);
};
