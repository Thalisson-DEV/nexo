import { pode, prazoRelativo, saqueAtrasado } from './almox-status';

describe('almox-status', () => {
  const hoje = new Date(2026, 9, 12);

  it('saque atrasado só com trabalho em aberto e saldo não encerrado', () => {
    expect(saqueAtrasado({ dataPrevista: '2026-10-11', status: 'APROVADO' }, hoje)).toBeTrue();
    expect(saqueAtrasado({ dataPrevista: '2026-10-12', status: 'APROVADO' }, hoje)).toBeFalse();
    expect(saqueAtrasado({ dataPrevista: '2026-10-01', status: 'ENTREGUE' }, hoje)).toBeFalse();
    expect(saqueAtrasado({ dataPrevista: '2026-10-01', status: 'ENTREGUE_PARCIAL', encerrada: true }, hoje)).toBeFalse();
  });

  it('prazo relativo em português', () => {
    expect(prazoRelativo('2026-10-12', hoje)).toBe('hoje');
    expect(prazoRelativo('2026-10-13', hoje)).toBe('amanhã');
    expect(prazoRelativo('2026-10-09', hoje)).toBe('há 3 dias');
  });

  it('a política do backend manda; sem ela vale a regra pelo status', () => {
    expect(pode({ status: 'PENDENTE', acoesPermitidas: ['REJEITAR'] }, 'APROVAR')).toBeFalse();
    expect(pode({ status: 'PENDENTE' }, 'APROVAR')).toBeTrue();
    expect(pode({ status: 'ENTREGUE_PARCIAL', encerrada: true }, 'ENTREGAR')).toBeFalse();
    expect(pode({ status: 'APROVADO' }, 'ENTREGAR')).toBeTrue();
  });
});
