import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { linkToUrl, normalizeRoute, ViewStateStore } from './view-state.store';

describe('ViewStateStore', () => {
  let store: ViewStateStore;

  beforeEach(() => {
    sessionStorage.clear();
    store = new ViewStateStore();
  });

  afterEach(() => {
    vi.useRealTimers();
    sessionStorage.clear();
  });

  it('guarda em memória e espelha em sessionStorage (sobrevive a uma nova instância = F5)', () => {
    const key = ViewStateStore.key('/obras/nova?x=1');
    store.set(key, { nome: 'Obra', inicio: new Date(2026, 9, 8, 12) });

    const aposF5 = new ViewStateStore();
    const valor = aposF5.get<{ nome: string; inicio: Date }>(key);
    expect(valor?.nome).toBe('Obra');
    expect(valor?.inicio).toBeInstanceOf(Date);
    expect(valor?.inicio.getDate()).toBe(8);
  });

  it('persist:false mantém só em memória (dado pessoal não vai para o storage)', () => {
    const key = ViewStateStore.key('/dp/descontos/novo');
    store.set(key, { cpf: '00000000000' }, { persist: false });
    expect(store.get(key)).toEqual({ cpf: '00000000000' });
    expect(JSON.stringify(sessionStorage)).not.toContain('00000000000');
    expect(new ViewStateStore().get(key)).toBeUndefined();
  });

  it('expira pelo TTL', () => {
    vi.useFakeTimers();
    const key = ViewStateStore.key('/a');
    store.set(key, 1, { ttlMs: 1000 });
    vi.advanceTimersByTime(999);
    expect(store.get(key)).toBe(1);
    vi.advanceTimersByTime(2);
    expect(store.get(key)).toBeUndefined();
    expect(new ViewStateStore().get(key)).toBeUndefined();
  });

  it('clearRoute remove a rota e sub-rotas, sem tocar nas vizinhas', () => {
    store.set(ViewStateStore.key('/obras'), 1);
    store.set(ViewStateStore.key('/obras/nova'), 2);
    store.set(ViewStateStore.key('/obras-cadastros'), 3);
    store.clearRoute('/obras');
    expect(store.get(ViewStateStore.key('/obras'))).toBeUndefined();
    expect(store.get(ViewStateStore.key('/obras/nova'))).toBeUndefined();
    expect(store.get(ViewStateStore.key('/obras-cadastros'))).toBe(3);
    expect(new ViewStateStore().get(ViewStateStore.key('/obras/nova'))).toBeUndefined();
  });

  it('clear (logout) apaga tudo, inclusive o espelho', () => {
    store.set('a::1', 1);
    store.set('b::1', 2);
    sessionStorage.setItem('outra-chave', 'mantida');
    store.clear();
    expect(store.get('a::1')).toBeUndefined();
    expect(new ViewStateStore().get('b::1')).toBeUndefined();
    expect(sessionStorage.getItem('outra-chave')).toBe('mantida');
  });

  it('normaliza rotas e links de menu', () => {
    expect(normalizeRoute('/obras/?a=1#x')).toBe('/obras');
    expect(normalizeRoute('/')).toBe('/');
    expect(linkToUrl(['/sst', 'inspecoes'])).toBe('/sst/inspecoes');
    expect(linkToUrl('/home')).toBe('/home');
  });
});
