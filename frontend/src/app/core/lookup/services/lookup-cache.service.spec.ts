import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LOOKUP_CACHE_MAX_ENTRIES_PER_RESOURCE, LookupCacheService } from './lookup-cache.service';

describe('LookupCacheService', () => {
  let cache: LookupCacheService;

  beforeEach(() => {
    cache = new LookupCacheService();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-04T12:00:00.000Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('retorna um valor armazenado enquanto ele está válido', () => {
    const value = [{ value: '1', label: 'Um' }];
    cache.set('collaborator', 'thal', value, 5_000);

    expect(cache.get<typeof value>('collaborator', 'thal')).toBe(value);
  });

  it('retorna null para cache miss', () => {
    expect(cache.get('collaborator', 'inexistente')).toBeNull();
  });

  it('isola entradas de resources diferentes mesmo usando a mesma chave', () => {
    cache.set('collaborator', 'ativo', 'colaborador', 5_000);
    cache.set('material', 'ativo', 'material', 5_000);

    expect(cache.get('collaborator', 'ativo')).toBe('colaborador');
    expect(cache.get('material', 'ativo')).toBe('material');
  });

  it('remove a entrada quando o TTL expira', () => {
    cache.set('material', 'epi', ['resultado'], 1_000);

    vi.advanceTimersByTime(999);
    expect(cache.get('material', 'epi')).toEqual(['resultado']);

    vi.advanceTimersByTime(1);
    expect(cache.get('material', 'epi')).toBeNull();
  });

  it('não armazena entradas com TTL zero ou negativo', () => {
    cache.set('material', 'zero', 'valor', 0);
    cache.set('material', 'negativo', 'valor', -1);

    expect(cache.get('material', 'zero')).toBeNull();
    expect(cache.get('material', 'negativo')).toBeNull();
  });

  it('remove somente a entrada pedida com delete', () => {
    cache.set('material', 'a', 'A', 5_000);
    cache.set('material', 'b', 'B', 5_000);

    cache.delete('material', 'a');

    expect(cache.get('material', 'a')).toBeNull();
    expect(cache.get('material', 'b')).toBe('B');
  });

  it('limpa um resource sem afetar os demais', () => {
    cache.set('material', 'a', 'A', 5_000);
    cache.set('collaborator', 'a', 'B', 5_000);

    cache.clear('material');

    expect(cache.get('material', 'a')).toBeNull();
    expect(cache.get('collaborator', 'a')).toBe('B');
  });

  it('limpa todos os resources quando clear não recebe resource', () => {
    cache.set('material', 'a', 'A', 5_000);
    cache.set('collaborator', 'a', 'B', 5_000);

    cache.clear();

    expect(cache.get('material', 'a')).toBeNull();
    expect(cache.get('collaborator', 'a')).toBeNull();
  });

  it('remove o item menos recentemente usado por resource', () => {
    for (let index = 0; index < LOOKUP_CACHE_MAX_ENTRIES_PER_RESOURCE; index++) {
      cache.set('material', `query-${index}`, index, 5_000);
    }

    expect(cache.get('material', 'query-0')).toBe(0);
    cache.set('material', 'query-extra', 'extra', 5_000);

    expect(cache.get('material', 'query-0')).toBe(0);
    expect(cache.get('material', 'query-1')).toBeNull();
    expect(cache.get('material', 'query-extra')).toBe('extra');
  });

  it('mantém a capacidade LRU independente para cada resource', () => {
    for (let index = 0; index <= LOOKUP_CACHE_MAX_ENTRIES_PER_RESOURCE; index++) {
      cache.set('material', `material-${index}`, index, 5_000);
    }
    cache.set('collaborator', 'colaborador', 'ok', 5_000);

    expect(cache.get('material', 'material-0')).toBeNull();
    expect(cache.get('collaborator', 'colaborador')).toBe('ok');
  });
});
