import { describe, expect, it } from 'vitest';

import { ItemSolicitacaoResponse, MaterialResponse } from '../../../../core/models/almoxarifado.models';
import {
  ItemEntregaRascunho,
  agruparItensSolicitacao,
  montarEntregaDoRelatorio,
  rascunhoDaEntrega,
  situacaoDoGrupo,
} from './itens-solicitacao.util';

const medidor = { id: 'med', codigo: '802149', descricao: 'MEDIDOR', unidadeMedida: 'UN', detalhaSerie: true } as MaterialResponse;
const cabo = { id: 'cabo', codigo: '2230075', descricao: 'CABO', unidadeMedida: 'MT', detalhaSerie: false } as MaterialResponse;

const item = (id: string, material: MaterialResponse, quantidade: number, entregue: number, extra: Partial<ItemSolicitacaoResponse> = {}): ItemSolicitacaoResponse => ({
  id,
  material,
  quantidade,
  quantidadeEntregue: entregue,
  quantidadeEntregaLiquida: entregue,
  quantidadePendente: Math.max(0, quantidade - entregue),
  ...extra,
});

describe('agruparItensSolicitacao', () => {
  it('junta as linhas desmembradas do material com série numa só, com as séries ordenadas', () => {
    const grupos = agruparItensSolicitacao([
      item('a', medidor, 1, 1, { numSerie: '5265853090' }),
      item('b', medidor, 1, 1, { numSerie: '5265852949' }),
      item('c', medidor, 3, 0, { seriesReservadas: ['5265853104'] }),
      item('d', cabo, 200, 200),
    ]);

    expect(grupos).toHaveLength(2);
    const [g] = grupos;
    expect(g).toMatchObject({ chave: 'med', solicitada: 5, entregue: 2, pendente: 3 });
    expect(g.seriesEntregues).toEqual(['5265852949', '5265853090']);
    expect(g.seriesReservadas).toEqual(['5265853104']);
    expect(g.itemPendente?.id).toBe('c');
    expect(situacaoDoGrupo(g)).toBe('parcial');
    expect(situacaoDoGrupo(grupos[1])).toBe('atendido');
  });

  it('pendência apontada em material que ainda falta aparece como pendência', () => {
    const [g] = agruparItensSolicitacao([item('a', cabo, 10, 0, { motivoPendencia: { id: 'm', nome: 'Sem saldo' } as never })]);
    expect(situacaoDoGrupo(g)).toBe('pendencia');
  });
});

describe('rascunhoDaEntrega', () => {
  it('traz só o que falta e o material com série já com as séries definidas', () => {
    const linhas = rascunhoDaEntrega([
      item('a', cabo, 100, 40),
      item('b', medidor, 18, 0, { seriesReservadas: ['S1', 'S2'] }),
      item('c', cabo, 5, 5),
    ]);

    expect(linhas.map((l) => [l.idItem, l.pendente, l.quantidade, l.series])).toEqual([
      ['a', 60, 60, []],
      ['b', 18, 2, ['S1', 'S2']],
    ]);
  });
});

describe('montarEntregaDoRelatorio', () => {
  const linha = (l: Partial<ItemEntregaRascunho> & Pick<ItemEntregaRascunho, 'material'>): ItemEntregaRascunho => ({
    chave: l.idItem ?? 'novo',
    idItem: null,
    solicitada: 0,
    jaEntregue: 0,
    pendente: 0,
    quantidade: 0,
    series: [],
    ...l,
  });

  it('desmembra as séries, inclui o material novo e tira o item removido', () => {
    const pedido = montarEntregaDoRelatorio([
      linha({ idItem: 'a', material: cabo, solicitada: 100, pendente: 100, quantidade: 30 }),
      linha({ idItem: 'b', material: medidor, solicitada: 3, pendente: 3, series: ['S1', 'S2'] }),
      linha({ material: medidor, quantidade: 1, series: ['S9'] }),
      linha({ material: { ...cabo, id: 'fita' }, quantidade: 2 }),
      linha({ idItem: 'z', material: cabo, solicitada: 4, pendente: 4, quantidade: 4, removido: true }),
    ]);

    expect(pedido.itens).toEqual([
      { idItemSolicitacao: 'a', quantidade: 30, novaQuantidadeSolicitada: 100 },
      { idItemSolicitacao: 'b', quantidade: 0, novaQuantidadeSolicitada: 1 },
    ]);
    expect(pedido.itensAdicionados).toEqual([
      { idMaterial: 'med', quantidade: 1, quantidadeSolicitada: 1, numSerie: 'S1' },
      { idMaterial: 'med', quantidade: 1, quantidadeSolicitada: 1, numSerie: 'S2' },
      { idMaterial: 'med', quantidade: 1, quantidadeSolicitada: 1, numSerie: 'S9' },
      { idMaterial: 'fita', quantidade: 2, quantidadeSolicitada: 2 },
    ]);
    expect(pedido.itensRemovidos).toEqual(['z']);
  });
});
