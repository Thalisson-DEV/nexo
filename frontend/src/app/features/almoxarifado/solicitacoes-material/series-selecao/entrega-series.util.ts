import { EntregaSolicitacaoRequest } from '../../../../core/models/almoxarifado.models';

/** O que sai de um item nesta entrega: quantidade (material comum) ou as séries (material com série). */
export interface SaidaItemEntrega {
  readonly idItemSolicitacao: string;
  readonly idMaterial: string;
  readonly detalhaSerie: boolean;
  readonly quantidadeSolicitada: number;
  readonly jaEntregue: number;
  readonly quantidade: number;
  readonly series: readonly string[];
}

type ItensDaEntrega = Pick<EntregaSolicitacaoRequest, 'itens' | 'itensAdicionados' | 'itensRemovidos'>;

/**
 * Monta os itens do pedido de entrega. O backend guarda uma série por item, então o material com
 * série que sai com mais de uma unidade é desmembrado: cada série vira um item de 1 unidade e o
 * item original fica com o que ainda falta (ou sai, se não sobrar nada e nada tiver sido entregue).
 */
export function montarItensEntrega(saidas: readonly SaidaItemEntrega[]): ItensDaEntrega {
  const itens: EntregaSolicitacaoRequest['itens'] = [];
  const itensAdicionados: NonNullable<EntregaSolicitacaoRequest['itensAdicionados']> = [];
  const itensRemovidos: string[] = [];

  for (const s of saidas) {
    if (!s.detalhaSerie) {
      if (s.quantidade > 0) {
        itens.push({ idItemSolicitacao: s.idItemSolicitacao, quantidade: s.quantidade, novaQuantidadeSolicitada: s.quantidadeSolicitada });
      }
      continue;
    }
    const series = s.series.map((n) => n.trim()).filter(Boolean);
    if (!series.length) continue;
    if (series.length === 1 && s.quantidadeSolicitada === 1 && s.jaEntregue === 0) {
      itens.push({ idItemSolicitacao: s.idItemSolicitacao, quantidade: 1, novaQuantidadeSolicitada: 1, numSerie: series[0] });
      continue;
    }
    for (const numSerie of series) {
      itensAdicionados.push({ idMaterial: s.idMaterial, quantidade: 1, quantidadeSolicitada: 1, numSerie });
    }
    const restante = s.quantidadeSolicitada - series.length;
    if (restante > 0) {
      itens.push({ idItemSolicitacao: s.idItemSolicitacao, quantidade: 0, novaQuantidadeSolicitada: restante });
    } else {
      itensRemovidos.push(s.idItemSolicitacao);
    }
  }
  return {
    itens,
    ...(itensAdicionados.length ? { itensAdicionados } : {}),
    ...(itensRemovidos.length ? { itensRemovidos } : {})
  };
}
