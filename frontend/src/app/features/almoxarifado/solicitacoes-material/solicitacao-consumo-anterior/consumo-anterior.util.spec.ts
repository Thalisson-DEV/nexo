import { describe, expect, it } from 'vitest';

import { ConsumoAnteriorItem } from '../../../../core/models/almoxarifado.models';
import { lerConsumo } from './consumo-anterior.util';

const item = (recebida: number, aplicada: number, saldo: number): ConsumoAnteriorItem => ({
  idMaterial: 'm',
  ultimaSolicitacao: { id: 's', dataSolicitacao: '2026-09-01', dataEntrega: '2026-09-02', quantidadeSolicitada: recebida, quantidadeRecebida: recebida },
  quantidadeAplicada: aplicada,
  quantidadeDevolvida: 0,
  saldoAtual: saldo
});

describe('lerConsumo', () => {
  it('aponta quando a equipe usou pouco do que pegou e ainda tem saldo', () => {
    expect(lerConsumo(item(50, 12, 38))).toEqual({ aproveitamento: 0.24, tom: 'warning', dica: 'Usou 24% do que pegou e ainda tem saldo.' });
  });

  it('uso baixo sem saldo sobrando não é alerta (pode ter devolvido ou perdido)', () => {
    expect(lerConsumo(item(50, 10, 0)).tom).toBe('neutral');
  });

  it('marca o uso alto como bom sinal', () => {
    expect(lerConsumo(item(40, 38, 2))).toMatchObject({ tom: 'success', dica: 'Usou 95% do que pegou.' });
  });

  it('gastar mais do que pegou é informação, não sucesso', () => {
    expect(lerConsumo(item(1, 13, 7))).toMatchObject({ tom: 'info', dica: 'Gastou mais do que pegou: o restante saiu do saldo que já tinha.' });
  });

  it('sem pedido anterior diz que é o primeiro', () => {
    expect(lerConsumo({ idMaterial: 'm', ultimaSolicitacao: null, quantidadeAplicada: null, quantidadeDevolvida: null, saldoAtual: 3 })).toMatchObject({
      aproveitamento: null,
      tom: 'neutral',
      dica: 'Primeiro pedido deste material para o estoque.'
    });
    expect(lerConsumo(null).aproveitamento).toBeNull();
  });
});
