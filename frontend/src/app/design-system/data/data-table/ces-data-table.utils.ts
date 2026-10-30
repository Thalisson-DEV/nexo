export type CesColumnType = 'text' | 'number' | 'integer' | 'percent' | 'currency' | 'date' | 'custom';

/** Coluna de `ces-data-table`. A célula pode ser customizada com `<ng-template cesCell="key">`. */
export interface CesDataTableColumn<T = unknown> {
  readonly key: string;
  readonly header: string;
  /** Define formatação, alinhamento e ordenação padrão. `percent` espera 0–100 (92,5 = 92,5%). */
  readonly type?: CesColumnType;
  /** Padrão: `true`, exceto `custom` sem `value`. */
  readonly sortable?: boolean;
  /** Entra na busca rápida. Padrão: `true` para `text` e colunas com `display`. */
  readonly searchable?: boolean;
  /** Mobile: 1 = sempre no card; 2 = em "Mais detalhes"; 3 = só no detalhe. Padrão 2 (1 para a primeira coluna). */
  readonly priority?: 1 | 2 | 3;
  readonly width?: string;
  /** Valor usado em ordenação, busca e exportação. Padrão: `row[key]`. */
  readonly value?: (row: T) => unknown;
  /**
   * Texto exibido e exportado quando difere do valor de ordenação (ex.: ordenar gravidade pela
   * criticidade e mostrar "Crítica"). A busca também usa este texto.
   */
  readonly display?: (row: T) => string;
  /** Padrão `true`. */
  readonly exportable?: boolean;
  /**
   * Modo servidor: nome do campo aceito pelo backend para ordenar esta coluna. Sem `sortField`,
   * a coluna não é ordenável em modo servidor.
   */
  readonly sortField?: string;
}

/** Evento de carga do modo servidor: página (base 0), tamanho, ordenação (`sortField`), busca e filtros. */
export interface CesLazyLoadEvent {
  readonly page: number;
  readonly size: number;
  /** Regras já traduzidas para o `sortField` de cada coluna. */
  readonly sort: readonly CesSortRule[];
  readonly query: string;
  readonly filters: readonly CesColumnFilter[];
}

export interface CesSortRule {
  readonly key: string;
  readonly direction: 'asc' | 'desc';
}

export type CesFilterOperator = 'eq' | 'ne' | 'lt' | 'lte' | 'gt' | 'gte' | 'contains';

/** Filtro por coluna (chip removível). Pode vir do usuário ou de uma sugestão confirmada do Copiloto. */
export interface CesColumnFilter {
  readonly key: string;
  readonly operator: CesFilterOperator;
  readonly value: string | number | boolean;
  /** Texto do chip; padrão "<cabeçalho> <operador> <valor>". */
  readonly label?: string;
}

const OPERATOR_SYMBOL: Record<CesFilterOperator, string> = {
  eq: '=', ne: '≠', lt: '<', lte: '≤', gt: '>', gte: '≥', contains: 'contém',
};

function matchesFilter(value: unknown, filter: CesColumnFilter): boolean {
  if (filter.operator === 'contains') {
    return normalize(String(value ?? '')).includes(normalize(String(filter.value)));
  }
  // Ausência de valor (ex.: sem meta) nunca satisfaz uma comparação.
  if (value === null || value === undefined || value === '') return filter.operator === 'ne';
  if (typeof filter.value === 'number') {
    const numero = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(numero)) return false;
    switch (filter.operator) {
      case 'eq': return numero === filter.value;
      case 'ne': return numero !== filter.value;
      case 'lt': return numero < filter.value;
      case 'lte': return numero <= filter.value;
      case 'gt': return numero > filter.value;
      case 'gte': return numero >= filter.value;
    }
  }
  const igual = typeof filter.value === 'boolean'
    ? Boolean(value) === filter.value
    : normalize(String(value)) === normalize(String(filter.value));
  return filter.operator === 'ne' ? !igual : filter.operator === 'eq' ? igual : false;
}

/** Aplica todos os filtros (E). Filtros de colunas desconhecidas são ignorados. */
export function filterRows<T>(
  rows: readonly T[],
  filters: readonly CesColumnFilter[],
  columns: readonly CesDataTableColumn<T>[],
): T[] {
  const ativos = filters
    .map((filter) => ({ filter, column: columns.find((column) => column.key === filter.key) }))
    .filter((item): item is { filter: CesColumnFilter; column: CesDataTableColumn<T> } => !!item.column);
  if (!ativos.length) return [...rows];
  return rows.filter((row) => ativos.every(({ filter, column }) => matchesFilter(columnValue(column, row), filter)));
}

export function filterLabel<T>(filter: CesColumnFilter, columns: readonly CesDataTableColumn<T>[]): string {
  if (filter.label) return filter.label;
  const column = columns.find((item) => item.key === filter.key);
  const valor = typeof filter.value === 'boolean'
    ? (filter.value ? 'sim' : 'não')
    : typeof filter.value === 'number'
      ? formatCell(filter.value, column?.type === 'percent' ? 'percent' : 'number')
      : `“${filter.value}”`;
  return `${column?.header ?? filter.key} ${OPERATOR_SYMBOL[filter.operator]} ${valor}`;
}

export const CES_PAGE_SIZE_OPTIONS = [10, 25, 50, 100] as const;

const collator = new Intl.Collator('pt-BR', { sensitivity: 'base', numeric: true });

export function columnValue<T>(column: CesDataTableColumn<T>, row: T): unknown {
  if (column.value) return column.value(row);
  return (row as Record<string, unknown> | null)?.[column.key];
}

export function isSortable(column: CesDataTableColumn<unknown>): boolean {
  if (column.sortable !== undefined) return column.sortable;
  return column.type !== 'custom' || !!column.value;
}

export function isSearchable(column: CesDataTableColumn<unknown>): boolean {
  if (column.searchable !== undefined) return column.searchable;
  return (column.type ?? 'text') === 'text' || !!column.display;
}

function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === '';
}

function compareValues(a: unknown, b: unknown): number {
  const vazioA = isEmpty(a);
  const vazioB = isEmpty(b);
  // Ausência sempre por último, independentemente da direção.
  if (vazioA || vazioB) return vazioA === vazioB ? 0 : vazioA ? 1 : -1;
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  if (typeof a === 'boolean' && typeof b === 'boolean') return Number(a) - Number(b);
  return collator.compare(String(a), String(b));
}

/** Ordenação estável por várias regras, na ordem em que foram adicionadas. */
export function sortRows<T>(
  rows: readonly T[],
  rules: readonly CesSortRule[],
  columns: readonly CesDataTableColumn<T>[],
): T[] {
  const ativas = rules
    .map((rule) => ({ rule, column: columns.find((column) => column.key === rule.key) }))
    .filter((item): item is { rule: CesSortRule; column: CesDataTableColumn<T> } => !!item.column);
  if (!ativas.length) return [...rows];
  return rows
    .map((row, index) => ({ row, index }))
    .sort((a, b) => {
      for (const { rule, column } of ativas) {
        const va = columnValue(column, a.row);
        const vb = columnValue(column, b.row);
        const resultado = compareValues(va, vb);
        if (!resultado) continue;
        return rule.direction === 'asc' || isEmpty(va) || isEmpty(vb) ? resultado : -resultado;
      }
      return a.index - b.index;
    })
    .map((item) => item.row);
}

function normalize(value: string): string {
  return value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLocaleLowerCase('pt-BR');
}

/** Busca rápida: todos os termos precisam aparecer em alguma coluna pesquisável (sem acento/caixa). */
export function searchRows<T>(rows: readonly T[], query: string, columns: readonly CesDataTableColumn<T>[]): T[] {
  const termos = normalize(query.trim()).split(/\s+/).filter(Boolean);
  if (!termos.length) return [...rows];
  const pesquisaveis = columns.filter((column) => isSearchable(column as CesDataTableColumn<unknown>));
  return rows.filter((row) => {
    const texto = normalize(
      pesquisaveis.map((column) => (column.display ? column.display(row) : String(columnValue(column, row) ?? ''))).join(' '),
    );
    return termos.every((termo) => texto.includes(termo));
  });
}

/** Próximo estado de ordenação: clique simples substitui; `additive` (Shift) acrescenta. asc → desc → sem. */
export function nextSortRules(rules: readonly CesSortRule[], key: string, additive: boolean): CesSortRule[] {
  const atual = rules.find((rule) => rule.key === key);
  const proxima: CesSortRule | null = !atual
    ? { key, direction: 'asc' }
    : atual.direction === 'asc'
      ? { key, direction: 'desc' }
      : null;
  if (!additive) return proxima ? [proxima] : [];
  if (!atual) return [...rules, proxima!];
  return proxima
    ? rules.map((rule) => (rule.key === key ? proxima : rule))
    : rules.filter((rule) => rule.key !== key);
}

const numero = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 });
const inteiro = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 });
const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const percentual = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

/** Texto da célula no padrão pt-BR; ausência vira "—". */
export function formatCell(value: unknown, type: CesColumnType = 'text'): string {
  if (value === null || value === undefined || value === '') return '—';
  switch (type) {
    case 'number':
      return typeof value === 'number' ? numero.format(value) : String(value);
    case 'integer':
      return typeof value === 'number' ? inteiro.format(value) : String(value);
    case 'currency':
      return typeof value === 'number' ? moeda.format(value) : String(value);
    case 'percent':
      return typeof value === 'number' ? `${percentual.format(value)}%` : String(value);
    case 'date': {
      const data = value instanceof Date ? value : new Date(String(value));
      if (Number.isNaN(data.getTime())) return String(value);
      const somenteData = typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);
      return somenteData
        ? data.toLocaleDateString('pt-BR', { timeZone: 'UTC' })
        : data.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
    }
    default:
      return String(value);
  }
}

function csvCell(value: string): string {
  return /[";\n\r]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

/**
 * CSV para Excel pt-BR: separador `;`, BOM UTF-8 e números com vírgula decimal. Exporta as linhas
 * recebidas (a "visão": busca e ordenação aplicadas, todas as páginas).
 */
export function toCsv<T>(rows: readonly T[], columns: readonly CesDataTableColumn<T>[]): string {
  const exportaveis = columns.filter((column) => column.exportable !== false);
  const cabecalho = exportaveis.map((column) => csvCell(column.header)).join(';');
  const linhas = rows.map((row) =>
    exportaveis
      .map((column) => {
        if (column.display) return csvCell(column.display(row));
        const valor = columnValue(column, row);
        if (valor === null || valor === undefined) return '';
        if (typeof valor === 'number') return csvCell(String(valor).replace('.', ','));
        const tipo = column.type === 'date' ? 'date' : 'text';
        return csvCell(formatCell(valor, tipo));
      })
      .join(';'),
  );
  return '﻿' + [cabecalho, ...linhas].join('\r\n');
}
