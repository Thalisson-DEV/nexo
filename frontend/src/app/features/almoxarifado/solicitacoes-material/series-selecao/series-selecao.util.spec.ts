import { describe, expect, it } from 'vitest';

import { SaldoSerieResponse } from '../../../../core/models/almoxarifado.models';
import { montarItensEntrega } from './entrega-series.util';
import { lerSerie, separarSeries } from './series-selecao.util';

const ctx = { idOrigem: 'origem', nomeOrigem: 'CENTRAL', idMaterial: 'm-med', idSolicitacao: 'sol-1' };
const achada = (parcial: Partial<SaldoSerieResponse>): SaldoSerieResponse =>
  ({
    id: 'x',
    numSerie: 'MD-1',
    ativo: true,
    condicao: 'NOVO',
    estoque: { id: 'origem', nomeIdentificador: 'CENTRAL' },
    material: { id: 'm-med', codigo: 'MED' },
    ...parcial
  }) as SaldoSerieResponse;

describe('separarSeries', () => {
  it('aceita várias séries coladas ou bipadas, sem repetir', () => {
    expect(separarSeries(' MD-1, MD-2;MD-3\nMD-1 ')).toEqual(['MD-1', 'MD-2', 'MD-3']);
    expect(separarSeries('')).toEqual([]);
  });
});

describe('lerSerie', () => {
  it('série nova do material na origem está disponível', () => {
    expect(lerSerie('MD-1', [achada({})], ctx).situacao).toBe('disponivel');
  });

  it('a reserva da própria solicitação não impede', () => {
    expect(lerSerie('MD-1', [achada({ reservadaParaSolicitacao: 'sol-1' })], ctx).situacao).toBe('disponivel');
  });

  it('aponta o motivo quando a série não pode sair', () => {
    expect(lerSerie('MD-1', [achada({ reservadaParaSolicitacao: 'outra-solicitacao' })], ctx)).toEqual({
      situacao: 'reservada',
      mensagem: 'Reservada para a solicitação #outra-so.'
    });
    expect(lerSerie('MD-1', [achada({ estoque: { id: 'eq', nomeIdentificador: 'EQUIPE 7' } as never })], ctx)).toEqual({
      situacao: 'outro-estoque',
      mensagem: 'Está em EQUIPE 7, não em CENTRAL.'
    });
    expect(lerSerie('MD-1', [achada({ material: { id: 'm-trafo', codigo: 'TRAFO' } as never })], ctx).mensagem).toBe('É de outro material (TRAFO).');
    expect(lerSerie('MD-1', [achada({ condicao: 'SUCATA' })], ctx).situacao).toBe('sucata');
  });

  it('série que não consta em lugar nenhum pode ganhar entrada na origem', () => {
    expect(lerSerie('NOVA-9', [], ctx)).toEqual({
      situacao: 'fora-do-sistema',
      mensagem: 'Não consta em nenhum estoque. Dá para registrar a entrada dela em CENTRAL.'
    });
  });
});

describe('montarItensEntrega', () => {
  const base = { idMaterial: 'm-med', detalhaSerie: true, jaEntregue: 0, quantidade: 0 };

  it('material comum sai pela quantidade', () => {
    expect(montarItensEntrega([{ ...base, idItemSolicitacao: 'i1', detalhaSerie: false, quantidadeSolicitada: 10, quantidade: 4, series: [] }])).toEqual({
      itens: [{ idItemSolicitacao: 'i1', quantidade: 4, novaQuantidadeSolicitada: 10 }]
    });
  });

  it('item de uma unidade com uma série sai direto', () => {
    expect(montarItensEntrega([{ ...base, idItemSolicitacao: 'i1', quantidadeSolicitada: 1, series: ['MD-1'] }])).toEqual({
      itens: [{ idItemSolicitacao: 'i1', quantidade: 1, novaQuantidadeSolicitada: 1, numSerie: 'MD-1' }]
    });
  });

  it('desmembra: cada série vira um item e o original fica com o que falta', () => {
    expect(montarItensEntrega([{ ...base, idItemSolicitacao: 'i1', quantidadeSolicitada: 5, series: ['MD-1', 'MD-2'] }])).toEqual({
      itens: [{ idItemSolicitacao: 'i1', quantidade: 0, novaQuantidadeSolicitada: 3 }],
      itensAdicionados: [
        { idMaterial: 'm-med', quantidade: 1, quantidadeSolicitada: 1, numSerie: 'MD-1' },
        { idMaterial: 'm-med', quantidade: 1, quantidadeSolicitada: 1, numSerie: 'MD-2' }
      ]
    });
  });

  it('quando todas as séries saem e nada tinha sido entregue, o item original sai da solicitação', () => {
    expect(montarItensEntrega([{ ...base, idItemSolicitacao: 'i1', quantidadeSolicitada: 2, series: ['MD-1', 'MD-2'] }])).toMatchObject({
      itens: [],
      itensRemovidos: ['i1']
    });
  });

  it('com entrega anterior, o original fica com o que já foi entregue', () => {
    expect(montarItensEntrega([{ ...base, idItemSolicitacao: 'i1', quantidadeSolicitada: 3, jaEntregue: 1, series: ['MD-1', 'MD-2'] }]).itens).toEqual([
      { idItemSolicitacao: 'i1', quantidade: 0, novaQuantidadeSolicitada: 1 }
    ]);
  });

  it('item com série e nenhuma série escolhida não entra', () => {
    expect(montarItensEntrega([{ ...base, idItemSolicitacao: 'i1', quantidadeSolicitada: 2, series: [] }])).toEqual({ itens: [] });
  });
});
