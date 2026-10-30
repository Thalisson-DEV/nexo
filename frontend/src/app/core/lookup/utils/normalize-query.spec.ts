import { describe, expect, it } from 'vitest';

import { normalizeLookupQuery } from './normalize-query';

describe('normalizeLookupQuery', () => {
  it('remove espaços nas extremidades e normaliza a caixa', () => {
    expect(normalizeLookupQuery('  ThAlIsSoN  ')).toBe('thalisson');
  });

  it('faz com que variações de uma mesma consulta tenham a mesma chave', () => {
    const queries = ['Thal', 'thal', '  THAL  '].map(normalizeLookupQuery);

    expect(new Set(queries).size).toBe(1);
    expect(queries[0]).toBe('thal');
  });

  it('preserva uma consulta vazia como vazia', () => {
    expect(normalizeLookupQuery('   ')).toBe('');
  });

  it('normaliza caixa de caracteres do locale pt-BR', () => {
    expect(normalizeLookupQuery('AÇÃO')).toBe('ação');
  });
});
