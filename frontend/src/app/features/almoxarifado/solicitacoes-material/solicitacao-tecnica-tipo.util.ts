import { CodigoTipoTecnico } from '../../../core/models/solicitacao-material-tecnica.models';

export function normalizarTipoTecnico(valor?: string | null): CodigoTipoTecnico {
  if (!valor) return '' as CodigoTipoTecnico;
  const v = valor.trim();
  if (v === '221' || v === '222' || v === '921' || v === '922') {
    return v as CodigoTipoTecnico;
  }
  const limpo = v.replace(/["']/g, '').trim();
  if (['221', '222', '921', '922'].includes(limpo)) {
    return limpo as CodigoTipoTecnico;
  }
  if (limpo.startsWith('221')) return '221';
  if (limpo.startsWith('921')) return '921';
  if (limpo.startsWith('222')) return '222';
  if (limpo.startsWith('922')) return '922';

  const normalizado = limpo
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase();

  if (normalizado.includes('ADICAO') && normalizado.includes('CONCRETO')) return '921';
  if (normalizado.includes('DEVOLUCAO') && normalizado.includes('CONCRETO')) return '922';
  if (normalizado.includes('ADICAO')) return '221';
  if (normalizado.includes('DEVOLUCAO')) return '222';

  return v as CodigoTipoTecnico;
}
