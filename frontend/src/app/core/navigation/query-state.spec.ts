import { convertToParamMap, Params } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { compactParams, parseQueryState, qp, QuerySchema, sameParams, toQueryParams } from './query-state';

interface Filtros {
  nota: string | null | undefined;
  quantidade: number | null | undefined;
  apenasHoje: boolean | null | undefined;
  ativo: boolean | null | undefined;
  inicio: Date | null | undefined;
  status: 'INICIADO' | 'CONCLUIDO' | null | undefined;
  equipes: string[] | null | undefined;
  ids: number[] | null | undefined;
}

const schema: QuerySchema<Filtros> = {
  nota: qp.text('nota'),
  quantidade: qp.number('qtd'),
  apenasHoje: qp.boolean('hoje'),
  ativo: qp.boolean('ativo', { omitFalse: false }),
  inicio: qp.date('inicio'),
  status: qp.oneOf('status', ['INICIADO', 'CONCLUIDO'] as const),
  equipes: qp.list('equipe'),
  ids: qp.numberList('id'),
};

/** Simula a ida e volta pela URL real (serialização do Router incluída). */
function roundTrip(state: Partial<Filtros>): Partial<Filtros> {
  const params = compactParams(toQueryParams(schema, state));
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    for (const item of ([] as unknown[]).concat(value)) search.append(key, String(item));
  }
  const parsed = new URLSearchParams(search.toString());
  const map: Record<string, string[]> = {};
  parsed.forEach((value, key) => (map[key] ??= []).push(value));
  return parseQueryState(schema, convertToParamMap(map));
}

describe('query-state', () => {
  it('faz ida e volta de todos os tipos', () => {
    const inicio = new Date(2026, 9, 8, 12);
    const state: Partial<Filtros> = {
      nota: '123456',
      quantidade: 42,
      apenasHoje: true,
      ativo: false,
      inicio,
      status: 'CONCLUIDO',
      equipes: ['EQ-01', 'EQ-02'],
      ids: [1, 20],
    };
    const result = roundTrip(state);
    expect(result).toEqual({ ...state, inicio: expect.any(Date) });
    expect(result.inicio!.getFullYear()).toBe(2026);
    expect(result.inicio!.getMonth()).toBe(9);
    expect(result.inicio!.getDate()).toBe(8);
  });

  it('serializa datas como yyyy-MM-dd local e booleanos como 1/0', () => {
    const params = toQueryParams(schema, {
      inicio: new Date(2026, 0, 31, 23, 59),
      apenasHoje: true,
      ativo: false,
    });
    expect(params['inicio']).toBe('2026-01-31');
    expect(params['hoje']).toBe('1');
    expect(params['ativo']).toBe('0');
  });

  it('preserva acentos e caracteres reservados de URL', () => {
    const texto = 'São João & Cia/Ltda ?#=%+ 100%';
    expect(roundTrip({ nota: texto }).nota).toBe(texto);
  });

  it('omite vazios, espaços e falsos (salvo tri-estado)', () => {
    const params = compactParams(
      toQueryParams(schema, { nota: '   ', quantidade: null, apenasHoje: false, equipes: [], ids: [] }),
    );
    expect(params).toEqual({});
  });

  it('tri-estado com padrão: omite o padrão e representa "Todos"', () => {
    const schema: QuerySchema<{ ativo: boolean | null }> = { ativo: qp.tristate('ativo', true) };
    expect(toQueryParams(schema, { ativo: true })).toEqual({ ativo: null });
    expect(toQueryParams(schema, { ativo: false })).toEqual({ ativo: '0' });
    expect(toQueryParams(schema, { ativo: null })).toEqual({ ativo: 'todos' });
    expect(parseQueryState(schema, convertToParamMap({ ativo: 'todos' }))).toEqual({ ativo: null });
    expect(parseQueryState(schema, convertToParamMap({ ativo: 'false' }))).toEqual({ ativo: false });
    expect(parseQueryState(schema, convertToParamMap({}))).toEqual({});
  });

  it('ignora valores inválidos vindos da URL', () => {
    const parsed = parseQueryState(
      schema,
      convertToParamMap({ qtd: 'abc', inicio: '2026-02-31', status: 'HACK', hoje: 'talvez', id: ['x', '3'] }),
    );
    expect(parsed).toEqual({ ids: [3] });
  });

  it('campos ausentes ficam undefined (o default é da tela)', () => {
    expect(parseQueryState(schema, convertToParamMap({}))).toEqual({});
  });

  it('compara queryParams ignorando ordem de chaves e nulos', () => {
    const a: Params = { a: '1', b: ['x', 'y'], c: null };
    const b: Params = { b: ['x', 'y'], a: 1 };
    expect(sameParams(a, b)).toBe(true);
    expect(sameParams(a, { a: '1', b: ['y', 'x'] })).toBe(false);
    expect(sameParams({ a: '1' }, { a: '1', d: '2' })).toBe(false);
  });
});
