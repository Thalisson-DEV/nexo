import { describe, expect, it } from 'vitest';
import {
  competenciaDaData,
  competenciaDe,
  diasUteisDaJanela,
  feriadoNacional,
  janelaDaCompetencia,
  nomeCompetencia,
  processoUsaCiclo
} from './meta-faturamento.models';

describe('meta-faturamento.models', () => {
  it('o ciclo comercial vai de 26 a 25 e leva o nome do mês em que termina', () => {
    expect(janelaDaCompetencia('CICLO_COMERCIAL', '2026-10')).toEqual({ inicio: '2026-09-26', fim: '2026-10-25', competencia: '2026-10' });
    expect(janelaDaCompetencia('CICLO_COMERCIAL', '2027-01')).toEqual({ inicio: '2026-12-26', fim: '2027-01-25', competencia: '2027-01' });
    expect(nomeCompetencia('CICLO_COMERCIAL', '2026-10')).toBe('Ciclo de outubro/2026');
  });

  it('a partir do dia 26 a data já pertence ao ciclo seguinte', () => {
    expect(competenciaDe('CICLO_COMERCIAL', new Date(2026, 9, 25))).toBe('2026-10');
    expect(competenciaDe('CICLO_COMERCIAL', new Date(2026, 9, 26))).toBe('2026-11');
    expect(competenciaDe('CICLO_COMERCIAL', new Date(2026, 11, 30))).toBe('2027-01');
    expect(competenciaDaData('CICLO_COMERCIAL', '2026-10-25')).toBe('2026-10');
  });

  it('a meta mensal usa o mês civil', () => {
    expect(janelaDaCompetencia('MENSAL', '2026-02')).toEqual({ inicio: '2026-02-01', fim: '2026-02-28', competencia: '2026-02' });
    expect(competenciaDe('MENSAL', new Date(2026, 9, 30))).toBe('2026-10');
  });

  it('conta os dias úteis da janela como o backend', () => {
    // 12/10 (Nossa Senhora Aparecida) cai numa segunda e não conta
    expect(diasUteisDaJanela(janelaDaCompetencia('CICLO_COMERCIAL', '2026-10'))).toBe(19);
    expect(diasUteisDaJanela(janelaDaCompetencia('MENSAL', '2026-10'))).toBe(21);
  });

  it('reconhece os feriados nacionais, inclusive a Sexta-feira Santa', () => {
    expect(feriadoNacional(new Date(2026, 10, 2))).toBe(true);
    expect(feriadoNacional(new Date(2026, 10, 20))).toBe(true);
    expect(feriadoNacional(new Date(2026, 3, 3))).toBe(true);
    expect(feriadoNacional(new Date(2027, 2, 26))).toBe(true);
    expect(feriadoNacional(new Date(2026, 1, 17))).toBe(false); // Carnaval é ponto facultativo
    expect(feriadoNacional(new Date(2026, 9, 13))).toBe(false);
  });

  it('só o processo comercial apura por ciclo', () => {
    expect(processoUsaCiclo('COMERCIAL')).toBe(true);
    expect(processoUsaCiclo('LINHA VIVA')).toBe(false);
    expect(processoUsaCiclo(null)).toBe(false);
  });
});
