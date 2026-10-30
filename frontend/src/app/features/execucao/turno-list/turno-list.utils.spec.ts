import { describe, expect, it } from 'vitest';
import { situacaoDoTurno } from './turno-list.utils';

describe('situacaoDoTurno', () => {
  const base = { abertoDesdeOutroDia: false, dataInicio: '2026-10-13T08:00:00-03:00' };

  it('destaca o turno que o sistema encerrou no horário de corte', () => {
    expect(situacaoDoTurno({ ...base, status: 'FECHADO', encerradoAutomaticamente: true })).toEqual({
      rotulo: 'Encerrado pelo sistema',
      tom: 'warning',
      icone: 'pi pi-clock'
    });
  });

  it('mantém neutro o turno encerrado pela equipe', () => {
    expect(situacaoDoTurno({ ...base, status: 'FECHADO', encerradoAutomaticamente: false }).rotulo).toBe('Encerrado');
    expect(situacaoDoTurno({ ...base, status: 'FECHADO' }).tom).toBe('neutral');
  });
});
