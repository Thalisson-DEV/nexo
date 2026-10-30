import { FilterOption } from './filter.models';

/** Janelas de tempo suportadas pelo filtro de período das listagens. */
export type PeriodFilter = 'hoje' | '7dias' | '30dias' | 'todos';

/**
 * Período aplicado ao abrir uma listagem. Mantém a lista leve em campo
 * (aparelhos com muitos registros locais) sem esconder o histórico, que
 * continua acessível em "Todo o Período".
 */
export const DEFAULT_PERIOD_FILTER: PeriodFilter = '30dias';

/** Opções do grupo "Período", idênticas em todos os domínios. */
export const PERIOD_FILTER_OPTIONS: readonly FilterOption<PeriodFilter>[] = [
  { value: 'hoje', label: 'Hoje', activeClass: 'filter-todos-active' },
  { value: '7dias', label: 'Últimos 7 dias', activeClass: 'filter-todos-active' },
  { value: '30dias', label: 'Últimos 30 dias', activeClass: 'filter-todos-active' },
  { value: 'todos', label: 'Todo o Período', activeClass: 'filter-todos-active' },
];

/** Quantidade de dias corridos (incluindo hoje) que cada período abrange. */
const PERIOD_DAYS: Record<Exclude<PeriodFilter, 'todos'>, number> = {
  hoje: 1,
  '7dias': 7,
  '30dias': 30,
};

/**
 * Retorna o instante (timestamp local) em que a janela do período começa.
 * Exposto para permitir o cálculo uma única vez por filtragem de lista.
 */
export function periodStartTime(period: PeriodFilter): number | null {
  if (period === 'todos') return null;

  const start = new Date();
  start.setHours(0, 0, 0, 0);
  start.setDate(start.getDate() - (PERIOD_DAYS[period] - 1));
  return start.getTime();
}

/**
 * Indica se uma data pertence ao período selecionado.
 *
 * A comparação é feita em horário local (evita o deslocamento de fuso que
 * ocorre ao comparar strings ISO em UTC). Datas futuras são consideradas
 * dentro da janela — registros criados com relógio adiantado não somem da
 * lista. Registros sem data ficam visíveis apenas em "Todo o Período".
 */
export function isWithinPeriod(
  value: string | Date | null | undefined,
  period: PeriodFilter,
  startTime: number | null = periodStartTime(period)
): boolean {
  if (startTime === null) return true;
  if (!value) return false;

  const time = value instanceof Date ? value.getTime() : new Date(value).getTime();
  if (Number.isNaN(time)) return false;

  return time >= startTime;
}
