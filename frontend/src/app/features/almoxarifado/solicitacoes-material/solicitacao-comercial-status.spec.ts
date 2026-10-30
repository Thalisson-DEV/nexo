import { describe, expect, it } from 'vitest';

import { isoCivil, prazoRelativo, saqueAtrasado, statusComercial } from './solicitacao-comercial-status';

describe('solicitacao-comercial-status', () => {
  const hoje = new Date(2026, 9, 12, 15, 30);

  it('traduz status com tom e ícone, e cai num neutro para valores desconhecidos', () => {
    expect(statusComercial('PENDENTE')).toMatchObject({ label: 'Pendente', tone: 'warning', atalho: 'Aguardando aprovação' });
    expect(statusComercial('XYZ')).toMatchObject({ label: 'XYZ', tone: 'neutral' });
  });

  it('saque atrasado só conta com trabalho em aberto e data anterior a hoje', () => {
    expect(saqueAtrasado({ dataPrevista: '2026-10-11', status: 'APROVADO' }, hoje)).toBe(true);
    expect(saqueAtrasado({ dataPrevista: '2026-10-12', status: 'APROVADO' }, hoje)).toBe(false);
    expect(saqueAtrasado({ dataPrevista: '2026-10-01', status: 'ENTREGUE' }, hoje)).toBe(false);
    expect(saqueAtrasado({ dataPrevista: null, status: 'PENDENTE' }, hoje)).toBe(false);
    // Parcial com saldo encerrado não tem mais o que retirar.
    expect(saqueAtrasado({ dataPrevista: '2026-10-01', status: 'ENTREGUE_PARCIAL', encerrada: true }, hoje)).toBe(false);
  });

  it('prazo relativo em linguagem natural e data civil sem deslocamento de fuso', () => {
    expect(prazoRelativo('2026-10-12', hoje)).toBe('hoje');
    expect(prazoRelativo('2026-10-13', hoje)).toBe('amanhã');
    expect(prazoRelativo('2026-10-15', hoje)).toBe('em 3 dias');
    expect(prazoRelativo('2026-10-10', hoje)).toBe('há 2 dias');
    expect(isoCivil(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05');
  });
});
