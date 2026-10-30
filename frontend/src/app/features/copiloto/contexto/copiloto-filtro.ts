import type { CesColumnFilter } from '@ces/ui';
import {
  AssistenteAcao,
  ColunaPainel,
  OperadorFiltroPainel,
  TabelaPainel,
  TipoColunaPainel,
} from '../../../core/models/assistente.models';

const OPERADORES_POR_TIPO: Record<TipoColunaPainel, readonly OperadorFiltroPainel[]> = {
  numero: ['eq', 'ne', 'lt', 'lte', 'gt', 'gte'],
  percentual: ['eq', 'ne', 'lt', 'lte', 'gt', 'gte'],
  texto: ['eq', 'contains'],
  booleano: ['eq'],
};
const MAX_FILTROS = 5;
const SIMBOLO: Record<OperadorFiltroPainel, string> = {
  eq: '=', ne: '≠', lt: '<', lte: '≤', gt: '>', gte: '≥', contains: 'contém',
};

/** Texto do chip a partir da coluna declarada ("% da meta < 80%", "Inspetor contém “Ana”"). */
function rotuloFiltro(coluna: ColunaPainel, operador: OperadorFiltroPainel, valor: string | number | boolean): string {
  const texto = typeof valor === 'boolean'
    ? (valor ? 'sim' : 'não')
    : typeof valor === 'number'
      ? `${valor.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}${coluna.tipo === 'percentual' ? '%' : ''}`
      : `“${valor}”`;
  return `${coluna.rotulo ?? coluna.campo} ${SIMBOLO[operador]} ${texto}`;
}

/** Filtro sugerido pelo Copiloto, já validado contra a tabela que a tela declarou. */
export interface FiltroSugerido {
  readonly alvo: string;
  readonly rotulo: string;
  readonly filtros: CesColumnFilter[];
}

export function tabelaPainel(id: string, rotulo: string, colunas: ColunaPainel[]): TabelaPainel {
  return { id, rotulo, colunas };
}

function converter(tipo: TipoColunaPainel, valor: unknown): string | number | boolean | null {
  if (tipo === 'numero' || tipo === 'percentual') {
    const numero = typeof valor === 'number' ? valor : Number(String(valor ?? '').replace('%', '').replace(',', '.'));
    return Number.isFinite(numero) ? numero : null;
  }
  if (tipo === 'booleano') return typeof valor === 'boolean' ? valor : null;
  const texto = typeof valor === 'string' ? valor.trim() : '';
  return texto && texto.length <= 80 ? texto : null;
}

/**
 * Revalida uma ação `APPLY_FILTER` contra as tabelas declaradas pela tela aberta (o backend já validou;
 * isto protege contra ações antigas de outro recorte/painel). Qualquer divergência descarta a ação inteira.
 */
export function resolverFiltroSugerido(acao: AssistenteAcao, tabelas: readonly TabelaPainel[] | undefined): FiltroSugerido | null {
  if (acao?.tipo !== 'APPLY_FILTER' || !acao.alvo || !acao.rotulo?.trim()) return null;
  const tabela = (tabelas ?? []).find((item) => item.id === acao.alvo);
  const propostos = acao.filtros ?? [];
  if (!tabela || !propostos.length || propostos.length > MAX_FILTROS) return null;
  const filtros: CesColumnFilter[] = [];
  for (const proposto of propostos) {
    const coluna = tabela.colunas.find((item) => item.campo === proposto?.campo);
    if (!coluna) return null;
    const permitidos = coluna.operadores?.length
      ? coluna.operadores.filter((op) => OPERADORES_POR_TIPO[coluna.tipo].includes(op))
      : OPERADORES_POR_TIPO[coluna.tipo];
    const operador = proposto.operador as OperadorFiltroPainel;
    if (!permitidos.includes(operador)) return null;
    const valor = converter(coluna.tipo, proposto.valor);
    if (valor === null) return null;
    filtros.push({ key: coluna.campo, operator: operador, value: valor, label: rotuloFiltro(coluna, operador, valor) });
  }
  return { alvo: tabela.id, rotulo: acao.rotulo.trim(), filtros };
}
