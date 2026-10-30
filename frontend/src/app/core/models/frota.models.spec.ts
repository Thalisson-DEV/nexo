import { describe, expect, it } from 'vitest';
import { contar, deDataIso, descreverPrazo, formatarMedida, formatarPlaca, paraDataIso, paraInstanteIso } from './frota.models';

describe('frota.models', () => {
  it('descreve o prazo de vencimento em linguagem simples', () => {
    expect(descreverPrazo(0)).toBe('Vence hoje');
    expect(descreverPrazo(1)).toBe('Vence em 1 dia');
    expect(descreverPrazo(7)).toBe('Vence em 7 dias');
    expect(descreverPrazo(-1)).toBe('Venceu há 1 dia');
    expect(descreverPrazo(-30)).toBe('Venceu há 30 dias');
    expect(descreverPrazo(null)).toBe('Sem vencimento');
  });

  it('converte datas sem deslocar pelo fuso', () => {
    const data = new Date(2026, 9, 9);
    expect(paraDataIso(data)).toBe('2026-10-09');
    expect(deDataIso('2026-10-09')?.getDate()).toBe(9);
    expect(deDataIso('2026-10-09T00:00:00')?.getMonth()).toBe(9);
    expect(paraDataIso(null)).toBeNull();
    expect(deDataIso(null)).toBeNull();
  });

  it('gera instante ISO com o deslocamento do navegador', () => {
    const instante = paraInstanteIso(new Date(2026, 9, 9, 14, 5));
    expect(instante).toMatch(/^2026-10-09T14:05:00[+-]\d{2}:\d{2}$/);
  });

  it('formata placa antiga com hífen e mantém a Mercosul', () => {
    expect(formatarPlaca('abc1234')).toBe('ABC-1234');
    expect(formatarPlaca('ABC1D23')).toBe('ABC1D23');
    expect(formatarPlaca(null)).toBe('');
  });

  it('formata medida com unidade e trata valor ausente', () => {
    expect(formatarMedida(123456, 'km')).toBe('123.456 km');
    expect(formatarMedida(8.25, 'km/l', 2)).toBe('8,25 km/l');
    expect(formatarMedida(null, 'km')).toBe('—');
    expect(formatarMedida(undefined, 'km', 0, 'Sem leitura')).toBe('Sem leitura');
  });

  it('conta com o plural certo', () => {
    expect(contar(1, 'veículo', 'veículos')).toBe('1 veículo');
    expect(contar(1200, 'veículo', 'veículos')).toBe('1.200 veículos');
    expect(contar(0, 'veículo', 'veículos', 'Nenhum veículo')).toBe('Nenhum veículo');
  });
});
