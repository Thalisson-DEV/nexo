import { HttpParams } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { comSelecao, normalizarSelecao, temSelecao, toHttpParams } from './http-params.util';

describe('http-params util', () => {
  it('serializa múltipla seleção como parâmetro repetido', () => {
    const params = toHttpParams({ idBaseOperacional: ['base-a', 'base-b'] });

    expect(params.getAll('idBaseOperacional')).toEqual(['base-a', 'base-b']);
    expect(params.toString()).toBe('idBaseOperacional=base-a&idBaseOperacional=base-b');
  });

  it('mantém funcionando o caso de um valor só — o caminho de maior uso', () => {
    const params = toHttpParams({ idBaseOperacional: ['base-a'] });

    expect(params.toString()).toBe('idBaseOperacional=base-a');
  });

  it('omite o parâmetro quando a seleção está vazia ou só tem valores inválidos', () => {
    const params = toHttpParams({
      idProcesso: [],
      idEquipe: [null, undefined, ''],
      idFormulario: null,
      buscaGlobal: ''
    });

    expect(params.toString()).toBe('');
  });

  it('preserva valores escalares e converte booleano e número', () => {
    const params = toHttpParams({ apenasComDesvios: true, size: 20, buscaGlobal: 'epi' });

    expect(params.get('apenasComDesvios')).toBe('true');
    expect(params.get('size')).toBe('20');
    expect(params.get('buscaGlobal')).toBe('epi');
  });

  it('ignora as chaves pedidas e aplica a transformação por chave', () => {
    const params = toHttpParams(
      { page: 2, size: 10, sort: 'nome,asc', dataRealizacaoDe: '2026-08-01' },
      {
        ignorar: ['page', 'size', 'sort'],
        transformar: (chave, valor) => (chave === 'dataRealizacaoDe' ? `${valor}T00:00:00Z` : valor)
      }
    );

    expect(params.toString()).toBe('dataRealizacaoDe=2026-08-01T00:00:00Z');
  });

  it('comSelecao acumula sem substituir o que já existe na chave', () => {
    const params = comSelecao(new HttpParams().append('id', 'a'), 'id', ['b', '', null]);

    expect(params.getAll('id')).toEqual(['a', 'b']);
  });

  it('normalizarSelecao aceita parâmetro repetido e valores separados por vírgula', () => {
    expect(normalizarSelecao(['a', 'b'])).toEqual(['a', 'b']);
    expect(normalizarSelecao(['a,b', ' c '])).toEqual(['a', 'b', 'c']);
    expect(normalizarSelecao(['', null])).toEqual([]);
    expect(normalizarSelecao(null)).toEqual([]);
  });

  it('temSelecao só considera valores úteis', () => {
    expect(temSelecao(['a'])).toBe(true);
    expect(temSelecao([])).toBe(false);
    expect(temSelecao([null, ''])).toBe(false);
    expect(temSelecao(null)).toBe(false);
  });
});
