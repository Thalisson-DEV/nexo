import { ParamMap, Params } from '@angular/router';
import { parseLocalDate, toLocalDate } from '../utils/local-date.util';

/**
 * Serialização centralizada do estado de consulta na URL.
 *
 * Convenções (ver docs/state-restoration/GUIA.md):
 * - datas: `yyyy-MM-dd` (data local, sem passar por UTC);
 * - booleanos: `1`/`0`;
 * - listas: parâmetro repetido (`?status=A&status=B`), o mesmo formato que os dashboards já usam;
 * - vazio ou igual ao default: o parâmetro é omitido.
 *
 * Nunca declare aqui campos com dado pessoal (CPF, nome, login): eles ficam fora da URL.
 */
/**
 * Campo de URL. Os campos são declarados pelo tipo "cheio" (`QueryField<string>`), mas o
 * `serialize` aceita `null`/`undefined` em tempo de execução — por isso servem para filtros
 * tipados como `string | null`, `boolean | null` etc. (parâmetro de método é bivariante).
 */
export interface QueryField<T> {
  readonly param: string;
  parse(values: readonly string[]): T | undefined;
  serialize(value: T | null | undefined): string | string[] | null;
}

/**
 * Campos de filtro que vão para a URL. Pode cobrir só parte dos filtros da tela: o que fica de
 * fora (dado pessoal, por exemplo) não é serializado.
 */
export type QuerySchema<T> = { [K in keyof T]?: QueryField<T[K]> };

function first(values: readonly string[]): string | undefined {
  const value = values[0];
  return value === undefined || value === '' ? undefined : value;
}

function text(param: string): QueryField<string> {
  return {
    param,
    parse: (values) => first(values),
    serialize: (value) => {
      const trimmed = typeof value === 'string' ? value.trim() : '';
      return trimmed ? trimmed : null;
    },
  };
}

function number(param: string): QueryField<number> {
  return {
    param,
    parse: (values) => {
      const raw = first(values);
      if (raw === undefined) return undefined;
      const parsed = Number(raw);
      return Number.isFinite(parsed) ? parsed : undefined;
    },
    serialize: (value) => (typeof value === 'number' && Number.isFinite(value) ? String(value) : null),
  };
}

/** `1`/`0`. `false` só é escrito quando `omitFalse` é desligado (filtros tri-estado). */
function boolean(param: string, options: { omitFalse?: boolean } = {}): QueryField<boolean> {
  const omitFalse = options.omitFalse ?? true;
  return {
    param,
    parse: (values) => {
      const raw = first(values);
      if (raw === '1' || raw === 'true') return true;
      if (raw === '0' || raw === 'false') return false;
      return undefined;
    },
    serialize: (value) => {
      if (value === true) return '1';
      if (value === false && !omitFalse) return '0';
      return null;
    },
  };
}

/**
 * Booleano com valor padrão em que "Todos" (`null`, select limpo) também é uma escolha
 * (ex.: `ativo`, que abre como `true`). O padrão é omitido; `null` vira `todos`.
 */
function tristate(param: string, padrao: boolean): QueryField<boolean> {
  return {
    param,
    parse: (values) => {
      const raw = first(values);
      if (raw === 'todos') return null as unknown as boolean;
      if (raw === '1' || raw === 'true') return true;
      if (raw === '0' || raw === 'false') return false;
      return undefined;
    },
    serialize: (value) => {
      if (value === padrao) return null;
      if (value === true) return '1';
      if (value === false) return '0';
      return 'todos';
    },
  };
}

function date(param: string): QueryField<Date> {
  return {
    param,
    parse: (values) => parseLocalDate(first(values)) ?? undefined,
    serialize: (value) => toLocalDate(value ?? null),
  };
}

function oneOf<T extends string>(param: string, allowed: readonly T[]): QueryField<T> {
  return {
    param,
    parse: (values) => {
      const raw = first(values);
      return raw !== undefined && (allowed as readonly string[]).includes(raw) ? (raw as T) : undefined;
    },
    serialize: (value) => (value && (allowed as readonly string[]).includes(value) ? value : null),
  };
}

function list(param: string): QueryField<string[]> {
  return {
    param,
    parse: (values) => {
      const items = values.filter((value) => value !== '');
      return items.length ? [...items] : undefined;
    },
    serialize: (value) => {
      const items = (value ?? []).map((item) => String(item).trim()).filter(Boolean);
      return items.length ? items : null;
    },
  };
}

function numberList(param: string): QueryField<number[]> {
  return {
    param,
    parse: (values) => {
      const items = values.filter((value) => value !== '').map(Number).filter(Number.isFinite);
      return items.length ? items : undefined;
    },
    serialize: (value) => {
      const items = (value ?? []).filter((item) => Number.isFinite(item)).map(String);
      return items.length ? items : null;
    },
  };
}

/** Fábrica dos campos de URL. */
export const qp = { text, number, boolean, tristate, date, oneOf, list, numberList };

/** Lê da URL apenas os campos declarados e válidos. Campos ausentes ficam `undefined`. */
export function parseQueryState<T>(schema: QuerySchema<T>, params: ParamMap): Partial<T> {
  const state: Partial<T> = {};
  for (const key of Object.keys(schema) as (keyof T)[]) {
    const field = schema[key]!;
    const parsed = field.parse(params.getAll(field.param));
    if (parsed !== undefined) state[key] = parsed;
  }
  return state;
}

/**
 * Converte o estado em queryParams. Campos vazios saem como `null`, o que, com
 * `queryParamsHandling: 'merge'`, remove o parâmetro da URL sem tocar nos demais.
 */
export function toQueryParams<T>(schema: QuerySchema<T>, state: Partial<T>): Params {
  const params: Params = {};
  for (const key of Object.keys(schema) as (keyof T)[]) {
    const field = schema[key]!;
    const value = state[key];
    params[field.param] = value === undefined ? null : field.serialize(value as T[keyof T]);
  }
  return params;
}

/** Remove as entradas `null`/vazias — útil para comparar e para montar URLs. */
export function compactParams(params: Params): Params {
  const compact: Params = {};
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === '') continue;
    if (Array.isArray(value) && value.length === 0) continue;
    compact[key] = value;
  }
  return compact;
}

/** Igualdade estrutural de queryParams (ordem das chaves irrelevante; valores comparados como texto). */
export function sameParams(a: Params, b: Params): boolean {
  const left = compactParams(a);
  const right = compactParams(b);
  const keys = new Set([...Object.keys(left), ...Object.keys(right)]);
  for (const key of keys) {
    const l = ([] as unknown[]).concat(left[key] ?? []).map(String);
    const r = ([] as unknown[]).concat(right[key] ?? []).map(String);
    if (l.length !== r.length || l.some((value, index) => value !== r[index])) return false;
  }
  return true;
}
