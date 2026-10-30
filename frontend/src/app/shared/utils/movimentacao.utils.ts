import { CesTone } from '@ces/ui';
import { MaterialResponse, MovimentacaoItemResponse, TipoMovimentacao } from '../../core/models/almoxarifado.models';

interface ApresentacaoTipo {
  readonly rotulo: string;
  readonly tom: CesTone;
  readonly icone: string;
}

/** Rótulo, tom e ícone de cada tipo de movimentação: mesmos no relatório, no histórico e na prévia. */
export const TIPOS_MOVIMENTACAO: Record<TipoMovimentacao, ApresentacaoTipo> = {
  ENTRADA_AVULSA: { rotulo: 'Entrada avulsa', tom: 'success', icone: 'pi pi-arrow-down-left' },
  BAIXA_AVULSA: { rotulo: 'Baixa avulsa', tom: 'danger', icone: 'pi pi-arrow-up-right' },
  TRANSFERENCIA: { rotulo: 'Transferência', tom: 'brand', icone: 'pi pi-arrow-right-arrow-left' },
  ENTREGA: { rotulo: 'Entrega', tom: 'info', icone: 'pi pi-box' },
  DEVOLUCAO: { rotulo: 'Devolução', tom: 'brand', icone: 'pi pi-undo' },
  ESTORNO: { rotulo: 'Estorno', tom: 'warning', icone: 'pi pi-replay' },
  RETIRADA: { rotulo: 'Retirada', tom: 'info', icone: 'pi pi-sign-out' },
  APLICACAO: { rotulo: 'Aplicação', tom: 'neutral', icone: 'pi pi-wrench' }
};

export function rotuloTipoMovimentacao(tipo?: string | null): string {
  return TIPOS_MOVIMENTACAO[tipo as TipoMovimentacao]?.rotulo ?? (tipo ? tipo.replace(/_/g, ' ') : '—');
}

export function tomTipoMovimentacao(tipo?: string | null): CesTone {
  return TIPOS_MOVIMENTACAO[tipo as TipoMovimentacao]?.tom ?? 'neutral';
}

export function iconeTipoMovimentacao(tipo?: string | null): string {
  return TIPOS_MOVIMENTACAO[tipo as TipoMovimentacao]?.icone ?? 'pi pi-tag';
}

const TOM_CONDICAO: Record<string, CesTone> = {
  NOVO: 'neutral',
  USADO: 'info',
  RECUPERADO: 'brand',
  SUCATA: 'warning'
};

/** Item sem condição é material novo (padrão do backend). */
export function rotuloCondicao(condicao?: string | null): string {
  const valor = condicao || 'NOVO';
  return valor.charAt(0) + valor.slice(1).toLowerCase();
}

export function tomCondicao(condicao?: string | null): CesTone {
  return TOM_CONDICAO[condicao || 'NOVO'] ?? 'neutral';
}

/**
 * Material de uma operação: o backend devolve um item por número de série, então um material
 * seriado vira várias linhas. Aqui elas viram uma só, com a quantidade somada e as séries juntas.
 */
export interface ItemMovimentacaoAgrupado {
  readonly chave: string;
  readonly material: MaterialResponse;
  readonly condicao: string;
  readonly quantidade: number;
  readonly series: readonly string[];
  readonly itens: readonly MovimentacaoItemResponse[];
}

export function agruparItensPorMaterial(itens: readonly MovimentacaoItemResponse[] | null | undefined): ItemMovimentacaoAgrupado[] {
  const grupos = new Map<string, { material: MaterialResponse; condicao: string; quantidade: number; series: string[]; itens: MovimentacaoItemResponse[] }>();
  for (const item of itens ?? []) {
    const condicao = item.condicao || 'NOVO';
    const chave = `${item.material?.id ?? item.id}|${condicao}`;
    const grupo = grupos.get(chave) ?? { material: item.material, condicao, quantidade: 0, series: [], itens: [] };
    grupo.quantidade += item.quantidade || 0;
    if (item.numSerie) grupo.series.push(item.numSerie);
    grupo.itens.push(item);
    grupos.set(chave, grupo);
  }
  return Array.from(grupos, ([chave, g]) => ({
    chave,
    material: g.material,
    condicao: g.condicao,
    quantidade: g.quantidade,
    series: [...g.series].sort((a, b) => a.localeCompare(b, 'pt-BR', { numeric: true })),
    itens: g.itens
  }));
}
