import { describe, it, expect } from 'vitest';
import {
  possuiEntregaRealizada,
  possuiMovimentacaoAtendimento,
  possuiSaldoPendenteEntrega,
  separacaoLabel,
  statusLabel,
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel,
} from './solicitacao-tecnica-presentation';

describe('apresentação de solicitação técnica', () => {
  it('não confunde atendimento, separação e tipo derivado', () => {
    expect(statusLabel('APROVADO')).toBe('Aprovada');
    expect(separacaoLabel('EM_SEPARACAO')).toBe('Em separação');
    expect(tipoTecnicoLabel('ADICAO_COMUM')).toBe('221');
    expect(tipoTecnicoLabel('ADICAO_CONCRETO')).toBe('921');
    expect(tipoTecnicoLabel('DEVOLUCAO_COMUM')).toBe('222');
    expect(tipoTecnicoLabel('DEVOLUCAO_CONCRETO')).toBe('922');
  });

  it('classifica corretamente códigos 221 e 921 (final 1) como adição (verde) e 222 e 922 (final 2) como devolução (vermelho)', () => {
    // Enum
    expect(tipoTecnicoEhAdicao('ADICAO_COMUM')).toBe(true);
    expect(tipoTecnicoEhAdicao('ADICAO_CONCRETO')).toBe(true);
    expect(tipoTecnicoEhAdicao('DEVOLUCAO_COMUM')).toBe(false);
    expect(tipoTecnicoEhAdicao('DEVOLUCAO_CONCRETO')).toBe(false);

    // Códigos numéricos
    expect(tipoTecnicoEhAdicao('221')).toBe(true);
    expect(tipoTecnicoEhAdicao('921')).toBe(true);
    expect(tipoTecnicoEhAdicao('222')).toBe(false);
    expect(tipoTecnicoEhAdicao('922')).toBe(false);

    // Ícones: final 1 (adição) usa carrinho de saque, final 2 (devolução) usa lixeira
    expect(tipoTecnicoIcon('221')).toBe('pi pi-shopping-cart');
    expect(tipoTecnicoIcon('921')).toBe('pi pi-shopping-cart');
    expect(tipoTecnicoIcon('222')).toBe('pi pi-trash');
    expect(tipoTecnicoIcon('922')).toBe('pi pi-trash');
    expect(tipoTecnicoIcon('ADICAO_COMUM')).toBe('pi pi-shopping-cart');
    expect(tipoTecnicoIcon('ADICAO_CONCRETO')).toBe('pi pi-shopping-cart');
    expect(tipoTecnicoIcon('DEVOLUCAO_COMUM')).toBe('pi pi-trash');
    expect(tipoTecnicoIcon('DEVOLUCAO_CONCRETO')).toBe('pi pi-trash');
    expect(tipoTecnicoIcon(null)).toBe('pi pi-box');
  });

  it('detecta movimentação de entrega realizada em operações, totais, itens ou status', () => {
    // Sem entrega
    const semEntrega: any = {
      status: 'APROVADO',
      itens: [{ quantidade: 5, quantidadeEntregue: 0 }],
      tecnico: { totais: { entregaBruta: 0 }, operacoes: [] },
    };
    expect(possuiEntregaRealizada(semEntrega)).toBe(false);
    expect(possuiMovimentacaoAtendimento(semEntrega)).toBe(false);

    // Por operação de entrega
    const comOpEntrega: any = {
      ...semEntrega,
      tecnico: { ...semEntrega.tecnico, operacoes: [{ tipo: 'ENTREGA' }] },
    };
    expect(possuiEntregaRealizada(comOpEntrega)).toBe(true);
    expect(possuiMovimentacaoAtendimento(comOpEntrega)).toBe(true);

    // Por totais de entrega bruta
    const comTotalEntrega: any = {
      ...semEntrega,
      tecnico: { ...semEntrega.tecnico, totais: { entregaBruta: 2 } },
    };
    expect(possuiEntregaRealizada(comTotalEntrega)).toBe(true);

    // Por quantidade entregue no item
    const comItemEntregue: any = {
      ...semEntrega,
      itens: [{ quantidade: 5, quantidadeEntregue: 1 }],
    };
    expect(possuiEntregaRealizada(comItemEntregue)).toBe(true);

    // Por status
    const comStatusEntregue: any = {
      ...semEntrega,
      status: 'ENTREGUE_PARCIAL',
    };
    expect(possuiEntregaRealizada(comStatusEntregue)).toBe(true);
  });

  it('detecta se há saldo pendente de entrega para a solicitação', () => {
    // 1. Solicitação com item pendente
    const comPendente: any = {
      status: 'APROVADO',
      itens: [{ quantidade: 10, quantidadeEntregue: 6, quantidadePendente: 4 }],
      tecnico: { totais: { solicitada: 10, entregaLiquida: 6, pendente: 4 } },
    };
    expect(possuiSaldoPendenteEntrega(comPendente)).toBe(true);

    // 2. Solicitação onde todo o saldo já foi entregue (pendente = 0)
    const semPendente: any = {
      status: 'APROVADO',
      itens: [{ quantidade: 10, quantidadeEntregue: 10, quantidadePendente: 0 }],
      tecnico: { totais: { solicitada: 10, entregaLiquida: 10, pendente: 0 } },
    };
    expect(possuiSaldoPendenteEntrega(semPendente)).toBe(false);

    // 3. Solicitação já em status ENTREGUE
    const statusEntregue: any = {
      status: 'ENTREGUE',
      itens: [{ quantidade: 10, quantidadeEntregue: 10 }],
      tecnico: { totais: { solicitada: 10, entregaLiquida: 10, pendente: 0 } },
    };
    expect(possuiSaldoPendenteEntrega(statusEntregue)).toBe(false);

    // 4. Sem quantidadePendente explícita nos itens, calculado por (quantidade - entregue)
    const calculado: any = {
      status: 'APROVADO',
      itens: [{ quantidade: 5, quantidadeEntregaLiquida: 5 }],
      tecnico: { totais: { solicitada: 5, entregaLiquida: 5 } },
    };
    expect(possuiSaldoPendenteEntrega(calculado)).toBe(false);
  });
});
