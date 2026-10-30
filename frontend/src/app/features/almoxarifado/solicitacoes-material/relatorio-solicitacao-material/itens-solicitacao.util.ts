import { CesTone } from '@ces/ui';

import { EntregaSolicitacaoRequest, ItemSolicitacaoResponse, MaterialResponse } from '../../../../core/models/almoxarifado.models';
import { montarItensEntrega } from '../series-selecao/entrega-series.util';

/**
 * Um material da solicitação numa linha só. O material com série é desmembrado em várias linhas
 * de uma unidade na entrega; aqui elas voltam a ser um material, com as séries juntas.
 */
export interface GrupoItemSolicitacao {
  readonly chave: string;
  readonly material: MaterialResponse;
  readonly itens: readonly ItemSolicitacaoResponse[];
  readonly solicitada: number;
  readonly entregue: number;
  readonly estornada: number;
  readonly pendente: number;
  /** Séries que já saíram nesta solicitação. */
  readonly seriesEntregues: readonly string[];
  /** Séries que o almoxarifado definiu antes da entrega para o que ainda falta. */
  readonly seriesReservadas: readonly string[];
  /** A linha que ainda tem saldo a entregar: é nela que as séries são definidas. */
  readonly itemPendente: ItemSolicitacaoResponse | null;
  /** A primeira linha com pendência apontada. */
  readonly itemComPendencia: ItemSolicitacaoResponse | null;
}

export type SituacaoGrupo = 'atendido' | 'parcial' | 'pendente' | 'pendencia';

export const entregueDoItem = (i: ItemSolicitacaoResponse): number => Number(i.quantidadeEntregaLiquida ?? i.quantidadeEntregue ?? 0) || 0;

export const pendenteDoItem = (i: ItemSolicitacaoResponse): number =>
  Number(i.quantidadePendente ?? Math.max(0, (Number(i.quantidade) || 0) - entregueDoItem(i))) || 0;

const arredondar = (n: number) => Math.round(n * 1000) / 1000;
const ordenarSeries = (series: Iterable<string>) => [...new Set(series)].sort((a, b) => a.localeCompare(b, 'pt-BR', { numeric: true }));

export function agruparItensSolicitacao(itens: readonly ItemSolicitacaoResponse[] | null | undefined): GrupoItemSolicitacao[] {
  const grupos = new Map<string, ItemSolicitacaoResponse[]>();
  for (const item of itens ?? []) {
    const lista = grupos.get(item.material.id) ?? [];
    lista.push(item);
    grupos.set(item.material.id, lista);
  }
  return Array.from(grupos.entries()).map(([chave, lista]) => ({
    chave,
    material: lista[0].material,
    itens: lista,
    solicitada: arredondar(lista.reduce((t, i) => t + (Number(i.quantidade) || 0), 0)),
    entregue: arredondar(lista.reduce((t, i) => t + entregueDoItem(i), 0)),
    estornada: arredondar(lista.reduce((t, i) => t + (Number(i.quantidadeEntregaEstornada) || 0), 0)),
    pendente: arredondar(lista.reduce((t, i) => t + pendenteDoItem(i), 0)),
    seriesEntregues: ordenarSeries(lista.filter((i) => i.numSerie && entregueDoItem(i) > 0).map((i) => i.numSerie!)),
    seriesReservadas: ordenarSeries(lista.flatMap((i) => i.seriesReservadas ?? [])),
    itemPendente: lista.find((i) => pendenteDoItem(i) > 0) ?? null,
    itemComPendencia: lista.find((i) => !!i.motivoPendencia) ?? null,
  }));
}

export function situacaoDoGrupo(g: GrupoItemSolicitacao): SituacaoGrupo {
  if (g.itemComPendencia && g.pendente > 0) return 'pendencia';
  if (g.pendente <= 0) return 'atendido';
  return g.entregue > 0 ? 'parcial' : 'pendente';
}

export const SITUACOES: Record<SituacaoGrupo, { rotulo: string; tom: CesTone; icone: string }> = {
  atendido: { rotulo: 'Atendido', tom: 'success', icone: 'pi pi-check' },
  parcial: { rotulo: 'Parcial', tom: 'warning', icone: 'pi pi-truck' },
  pendente: { rotulo: 'A entregar', tom: 'neutral', icone: 'pi pi-clock' },
  pendencia: { rotulo: 'Pendência', tom: 'warning', icone: 'pi pi-flag' },
};

/** Um material do que sai nesta entrega; `idItem` nulo quando foi incluído na hora. */
export interface ItemEntregaRascunho {
  readonly chave: string;
  readonly idItem: string | null;
  readonly material: MaterialResponse;
  readonly solicitada: number;
  readonly jaEntregue: number;
  readonly pendente: number;
  readonly quantidade: number | null;
  readonly series: string[];
  /** Tirado da solicitação nesta entrega (só o que ainda não teve entrega nenhuma). */
  readonly removido?: boolean;
}

/** O que falta de cada material vira uma linha da entrega; o com série já vem com as séries definidas. */
export function rascunhoDaEntrega(itens: readonly ItemSolicitacaoResponse[] | null | undefined): ItemEntregaRascunho[] {
  return (itens ?? [])
    .filter((i) => pendenteDoItem(i) > 0)
    .map((i) => {
      const pendente = pendenteDoItem(i);
      const series = (i.seriesReservadas ?? []).slice(0, Math.ceil(pendente));
      return {
        chave: i.id,
        idItem: i.id,
        material: i.material,
        solicitada: Number(i.quantidade) || 0,
        jaEntregue: entregueDoItem(i),
        pendente,
        quantidade: i.material.detalhaSerie ? series.length : pendente,
        series,
      };
    });
}

/** Quanto sai da linha: o material com série sai pelas séries escolhidas. */
export const quantidadeDaLinha = (l: ItemEntregaRascunho): number =>
  l.removido ? 0 : l.material.detalhaSerie ? l.series.length : Number(l.quantidade) || 0;

type ItensDaEntrega = Pick<EntregaSolicitacaoRequest, 'itens' | 'itensAdicionados' | 'itensRemovidos'>;

/**
 * Itens do pedido de entrega a partir do rascunho do relatório. Os itens da solicitação seguem a
 * mesma regra do drawer (série desmembrada em itens de uma unidade); o incluído na hora entra como
 * item adicionado, um por série quando o material tem série.
 */
export function montarEntregaDoRelatorio(linhas: readonly ItemEntregaRascunho[]): ItensDaEntrega {
  const existentes = linhas.filter((l) => l.idItem && !l.removido && quantidadeDaLinha(l) > 0);
  const base = montarItensEntrega(
    existentes.map((l) => ({
      idItemSolicitacao: l.idItem!,
      idMaterial: l.material.id,
      detalhaSerie: !!l.material.detalhaSerie,
      quantidadeSolicitada: l.solicitada,
      jaEntregue: l.jaEntregue,
      quantidade: quantidadeDaLinha(l),
      series: l.series,
    })),
  );
  const adicionados = [...(base.itensAdicionados ?? [])];
  for (const l of linhas.filter((x) => !x.idItem && quantidadeDaLinha(x) > 0)) {
    if (l.material.detalhaSerie) {
      l.series.forEach((numSerie) => adicionados.push({ idMaterial: l.material.id, quantidade: 1, quantidadeSolicitada: 1, numSerie }));
    } else {
      const quantidade = quantidadeDaLinha(l);
      adicionados.push({ idMaterial: l.material.id, quantidade, quantidadeSolicitada: quantidade });
    }
  }
  const removidos = [...(base.itensRemovidos ?? []), ...linhas.filter((l) => l.idItem && l.removido).map((l) => l.idItem!)];
  return {
    itens: base.itens,
    ...(adicionados.length ? { itensAdicionados: adicionados } : {}),
    ...(removidos.length ? { itensRemovidos: removidos } : {}),
  };
}
