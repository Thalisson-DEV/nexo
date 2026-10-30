import { computed, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { AbaSecao } from '../../ui';

/** Abas do topo do Almoxarifado, conforme as permissões (só o que a pessoa pode abrir). */
export function abasAlmox(contagemAbertas: () => number | null = () => null) {
  const auth = inject(AuthService);
  return computed<AbaSecao[]>(() => [
    ...(auth.podeVerSolicitacoesAlmox() ? [{ rota: '/almox/solicitacoes', rotulo: 'Solicitações', icone: 'documento' as const, contagem: contagemAbertas() }] : []),
    ...(auth.podeVerMovimentacoes() ? [{ rota: '/almox/movimentacoes', rotulo: 'Movimentações', icone: 'transferencia' as const }] : []),
  ]);
}
