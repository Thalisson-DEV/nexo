import { SolicitacaoComercialImportRow } from '../../../core/models/almoxarifado.models';

export function contarSolicitacoesComerciaisPrevistas(registros: SolicitacaoComercialImportRow[]): number {
  const grupos = new Set<string>();
  for (const item of registros) {
    if (item.status === 'COM_ERRO') continue;
    const chave = JSON.stringify([
      item.idEstoqueDestino || item.nomeEstoqueDestino?.trim().toUpperCase() || '',
      item.idEstoqueOrigem || item.nomeEstoqueOrigem?.trim().toUpperCase() || '',
      item.dataPrevista || ''
    ]);
    grupos.add(chave);
  }
  return grupos.size;
}
