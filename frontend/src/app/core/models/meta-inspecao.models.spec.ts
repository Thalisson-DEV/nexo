import { describe, expect, it } from 'vitest';
import { calcularJanela, descreverMeta } from './meta-inspecao.models';

describe('calcularJanela (prévia do formulário de metas)', () => {
  it('ancora a primeira janela mensal na data de início', () => {
    expect(calcularJanela('MENSAL', '2026-08-01', null, new Date(2026, 7, 15)))
      .toEqual({ inicio: '2026-08-01', fim: '2026-08-31' });
  });

  it('repete as janelas mensais sem gap nem sobreposição', () => {
    expect(calcularJanela('MENSAL', '2026-08-01', null, new Date(2026, 8, 10)))
      .toEqual({ inicio: '2026-09-01', fim: '2026-09-30' });
  });

  it('preserva o dia de ancoragem quando a vigência não começa no dia 1', () => {
    expect(calcularJanela('MENSAL', '2026-08-15', null, new Date(2026, 7, 20)))
      .toEqual({ inicio: '2026-08-15', fim: '2026-09-14' });
  });

  it('trunca o dia de ancoragem em meses mais curtos', () => {
    // Ancorada em 31/01, a janela seguinte começa em 28/02 (fevereiro não tem dia 31).
    expect(calcularJanela('MENSAL', '2026-01-31', null, new Date(2026, 1, 10)))
      .toEqual({ inicio: '2026-01-31', fim: '2026-02-27' });
    expect(calcularJanela('MENSAL', '2026-01-31', null, new Date(2026, 2, 10)))
      .toEqual({ inicio: '2026-02-28', fim: '2026-03-30' });
  });

  it('conta janelas semanais em dias corridos', () => {
    expect(calcularJanela('SEMANAL', '2026-08-03', null, new Date(2026, 7, 5)))
      .toEqual({ inicio: '2026-08-03', fim: '2026-08-09' });
  });

  it('encerra a janela na vigência quando ela termina antes do fim natural', () => {
    expect(calcularJanela('MENSAL', '2026-08-01', '2026-08-20', new Date(2026, 7, 10)))
      .toEqual({ inicio: '2026-08-01', fim: '2026-08-20' });
  });

  it('trata PERSONALIZADO como janela única igual à vigência', () => {
    expect(calcularJanela('PERSONALIZADO', '2026-08-01', '2026-09-15', new Date(2026, 7, 20)))
      .toEqual({ inicio: '2026-08-01', fim: '2026-09-15' });
  });

  it('devolve null antes do início e depois do fim da vigência', () => {
    expect(calcularJanela('MENSAL', '2026-08-01', null, new Date(2026, 6, 31))).toBeNull();
    expect(calcularJanela('MENSAL', '2026-08-01', '2026-08-31', new Date(2026, 8, 1))).toBeNull();
  });
});

describe('descreverMeta', () => {
  it('monta a frase de meta indeterminada', () => {
    expect(descreverMeta(4, 'MENSAL', '2026-08-01', null))
      .toBe('4 inspeções por mês, a cada janela iniciada em 01/08/2026, por prazo indeterminado.');
  });

  it('usa o singular e a data de fim quando há prazo', () => {
    expect(descreverMeta(1, 'SEMANAL', '2026-08-03', '2026-12-31'))
      .toBe('1 inspeção por semana, a cada janela iniciada em 03/08/2026, até 31/12/2026.');
  });

  it('descreve PERSONALIZADO como janela única', () => {
    expect(descreverMeta(10, 'PERSONALIZADO', '2026-08-01', '2026-08-31'))
      .toBe('10 inspeções em uma janela única de 01/08/2026 até 31/08/2026.');
  });

  it('não monta frase com campos incompletos', () => {
    expect(descreverMeta(null, 'MENSAL', '2026-08-01', null)).toBeNull();
    expect(descreverMeta(4, 'MENSAL', null, null)).toBeNull();
  });
});
