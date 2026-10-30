import { describe, expect, it } from 'vitest';
import { AssistenteAcao } from '../../../core/models/assistente.models';
import { resolverFiltroSugerido, tabelaPainel } from './copiloto-filtro';

const tabelas = [
  tabelaPainel('sst.inspecoes#porInspetor', 'Produtividade · Inspetores', [
    { campo: 'percentualAtingido', rotulo: '% da meta', tipo: 'percentual' },
    { campo: 'nomeInspetor', rotulo: 'Inspetor', tipo: 'texto' },
    { campo: 'realizado', rotulo: 'Realizado', tipo: 'numero', operadores: ['gte'] },
  ]),
];

function acao(parcial: Partial<AssistenteAcao>): AssistenteAcao {
  return {
    tipo: 'APPLY_FILTER',
    rotulo: 'Abaixo de 80% da meta',
    autoridade: '',
    alvo: 'sst.inspecoes#porInspetor',
    filtros: [{ campo: 'percentualAtingido', operador: 'lt', valor: 80 }],
    ...parcial,
  };
}

describe('resolverFiltroSugerido', () => {
  it('converte a ação validada em filtros da tabela', () => {
    expect(resolverFiltroSugerido(acao({}), tabelas)).toEqual({
      alvo: 'sst.inspecoes#porInspetor',
      rotulo: 'Abaixo de 80% da meta',
      filtros: [{ key: 'percentualAtingido', operator: 'lt', value: 80, label: '% da meta < 80%' }],
    });
    expect(
      resolverFiltroSugerido(acao({ filtros: [{ campo: 'nomeInspetor', operador: 'contains', valor: ' Ana ' }] }), tabelas)?.filtros,
    ).toEqual([{ key: 'nomeInspetor', operator: 'contains', value: 'Ana', label: 'Inspetor contém “Ana”' }]);
  });

  it('descarta ações de outra tabela, campos/operadores não declarados ou valores inválidos', () => {
    expect(resolverFiltroSugerido(acao({ alvo: 'outro#tabela' }), tabelas)).toBeNull();
    expect(resolverFiltroSugerido(acao({}), undefined)).toBeNull();
    expect(resolverFiltroSugerido(acao({ filtros: [{ campo: 'senha', operador: 'eq', valor: 1 }] }), tabelas)).toBeNull();
    expect(resolverFiltroSugerido(acao({ filtros: [{ campo: 'realizado', operador: 'lt', valor: 3 }] }), tabelas)).toBeNull();
    expect(resolverFiltroSugerido(acao({ filtros: [{ campo: 'percentualAtingido', operador: 'lt', valor: 'muito' }] }), tabelas)).toBeNull();
    expect(resolverFiltroSugerido(acao({ filtros: [] }), tabelas)).toBeNull();
    expect(resolverFiltroSugerido({ ...acao({}), tipo: 'NAVIGATE' }, tabelas)).toBeNull();
  });
});
