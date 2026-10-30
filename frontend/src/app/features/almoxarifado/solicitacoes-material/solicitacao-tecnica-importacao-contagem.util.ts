import { SolicitacaoTecnicaImportRow } from '../../../core/models/solicitacao-material-tecnica.models';

export interface ContagemTecnicaPrevista {
  totalItens: number;
  totalValidos: number;
  totalComErro: number;
  totalSolicitacoes: number;
  totalGrupos: number;
}

export function contarSolicitacoesTecnicasPrevistas(
  registros: SolicitacaoTecnicaImportRow[],
): ContagemTecnicaPrevista {
  const solicitacoesSet = new Set<string>();
  const gruposSet = new Set<string>();
  let totalValidos = 0;
  let totalComErro = 0;

  for (const item of registros) {
    if (item.status === 'COM_ERRO') {
      totalComErro += 1;
      continue;
    }
    totalValidos += 1;

    const obra = item.numeroObra?.trim().toUpperCase() || '';
    const almox = item.almoxarifado?.trim().toUpperCase() || '';
    const equipe = item.codigoEquipe?.trim().toUpperCase() || '';
    const reserva = item.numeroReserva?.trim().toUpperCase() || '';
    const data = item.dataPrevista?.trim() || '';
    const tipo = item.tipoMovimentacao?.trim() || '';

    // Agrupamento pai de solicitação técnica (obra + almoxarifado + equipe + reserva + data)
    const chaveSolicitacao = JSON.stringify([obra, almox, equipe, reserva, data]);
    solicitacoesSet.add(chaveSolicitacao);

    // Agrupamento derivado / subgrupo de movimentação
    const chaveGrupo = item.chave || JSON.stringify([obra, almox, equipe, reserva, tipo, data]);
    gruposSet.add(chaveGrupo);
  }

  return {
    totalItens: registros.length,
    totalValidos,
    totalComErro,
    totalSolicitacoes: solicitacoesSet.size,
    totalGrupos: gruposSet.size,
  };
}
