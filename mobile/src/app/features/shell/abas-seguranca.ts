import { computed, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { AbaSecao } from '../../ui';

/**
 * Quando o administrativo também tem almoxarifado, Inspeções e Desvios dividem a aba
 * Segurança da barra inferior: estas são as abas do topo. Sem almoxarifado, cada um
 * continua com a própria aba e nada aparece.
 */
export function abasSeguranca() {
  const auth = inject(AuthService);
  return computed<AbaSecao[]>(() => (auth.isAdministrativo() && auth.podeAcessarAlmoxarifado()
    ? [{ rota: '/inspecoes', rotulo: 'Inspeções', icone: 'inspecao' }, { rota: '/desvios', rotulo: 'Desvios', icone: 'desvio' }]
    : []));
}
