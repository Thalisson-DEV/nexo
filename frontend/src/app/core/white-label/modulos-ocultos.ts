import { inject } from '@angular/core';
import { CanMatchFn, Route, Router, UrlSegment } from '@angular/router';
import { MenuItem } from 'primeng/api';

/**
 * Módulos fora desta distribuição. Saem do menu lateral (e, com ele, da busca de
 * telas) e, se alguém abrir o endereço direto, a navegação volta ao início.
 * Para devolver um módulo, basta tirá-lo daqui.
 */
export const MODULOS_OCULTOS = {
  /** Grupos do menu lateral, pelo rótulo. */
  menu: ['Comunicação', 'CIPA', 'Brigada de Incêndio', 'Treinamento'],
  /** Prefixos de rota que não abrem. */
  rotas: [
    '/manuais',
    '/central-treinamento',
    '/treinamento',
    '/manual-descontos',
    '/manual-sst',
    '/sst/governanca',
    '/mural',
    '/notificacoes/admin',
    '/notificacoes/automacoes'
  ]
} as const;

export function rotaOculta(url: string): boolean {
  const caminho = '/' + url.split(/[?#]/)[0].replace(/^\/+/, '');
  return MODULOS_OCULTOS.rotas.some((prefixo) => caminho === prefixo || caminho.startsWith(prefixo + '/'));
}

export function removerModulosOcultos(items: MenuItem[]): MenuItem[] {
  const ocultos: readonly string[] = MODULOS_OCULTOS.menu;
  return items
    .filter((item) => !ocultos.includes(item.label ?? ''))
    .map((item) => (item.items ? { ...item, items: removerModulosOcultos(item.items) } : item));
}

const moduloVisivel: CanMatchFn = (_route: Route, segmentos: UrlSegment[]) =>
  rotaOculta(segmentos.map((s) => s.path).join('/')) ? inject(Router).parseUrl('/') : true;

/**
 * Aplica o bloqueio nas rotas de primeiro nível, onde o caminho chega inteiro. Rota de
 * redirecionamento não aceita guard; o destino dela é que fica bloqueado.
 */
export function comModulosOcultos(rotas: Route[]): Route[] {
  return rotas.map((rota) =>
    rota.redirectTo !== undefined ? rota : { ...rota, canMatch: [moduloVisivel, ...(rota.canMatch ?? [])] }
  );
}
