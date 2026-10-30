import { computed, inject } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';

/**
 * Permissões da frota para mostrar ou esconder ações na tela. O backend continua
 * sendo quem decide; aqui só evitamos oferecer o que vai ser recusado.
 */
export function acessoFrota() {
  const auth = inject(AuthService);
  const tem = (permissao: string) => computed(() => auth.currentUser()?.permissoes?.includes(permissao) ?? false);
  return {
    editarVeiculo: tem('frota:veiculo:write'),
    editarCondutor: tem('frota:condutor:write'),
    editarDocumento: tem('frota:documento:write'),
    lancarKm: tem('frota:odometro:write'),
    anularKm: tem('frota:odometro:anular'),
    configurar: tem('frota:configuracao:write')
  };
}
