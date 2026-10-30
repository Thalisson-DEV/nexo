import { describe, expect, it } from 'vitest';
import { MovimentacaoItemResponse } from '../../core/models/almoxarifado.models';
import { agruparItensPorMaterial, rotuloCondicao, rotuloTipoMovimentacao, tomTipoMovimentacao } from './movimentacao.utils';

const item = (id: string, idMaterial: string, quantidade: number, numSerie?: string, condicao?: string) =>
  ({ id, quantidade, numSerie, condicao, material: { id: idMaterial, codigo: idMaterial, descricao: idMaterial } }) as unknown as MovimentacaoItemResponse;

describe('movimentacao.utils', () => {
  it('junta as linhas por série de um material numa só, somando a quantidade', () => {
    const grupos = agruparItensPorMaterial([
      item('1', 'medidor', 1, 'SN-10'),
      item('2', 'cabo', 12.5),
      item('3', 'medidor', 1, 'SN-2'),
      item('4', 'medidor', 1, 'SN-9', 'SUCATA'),
    ]);

    expect(grupos.map((g) => [g.material.id, g.condicao, g.quantidade])).toEqual([
      ['medidor', 'NOVO', 2],
      ['cabo', 'NOVO', 12.5],
      ['medidor', 'SUCATA', 1],
    ]);
    expect(grupos[0].series).toEqual(['SN-2', 'SN-10']);
    expect(grupos[1].series).toEqual([]);
  });

  it('rotula tipos e condições, com fallback para valores desconhecidos', () => {
    expect(rotuloTipoMovimentacao('ENTRADA_AVULSA')).toBe('Entrada avulsa');
    expect(rotuloTipoMovimentacao('NOVO_TIPO_X')).toBe('NOVO TIPO X');
    expect(tomTipoMovimentacao(undefined)).toBe('neutral');
    expect(rotuloCondicao(null)).toBe('Novo');
    expect(rotuloCondicao('SUCATA')).toBe('Sucata');
  });
});
