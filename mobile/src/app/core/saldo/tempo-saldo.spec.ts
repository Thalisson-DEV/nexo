import { DIAS_PARADO, DIAS_PARADO_CRITICO, diasDesde, maisAntiga, ordenarMaisAntigas, rotuloMaterialParado, rotuloSerieNoSaldo, tomTempoSaldo } from './tempo-saldo';

describe('tempo no saldo', () => {
  const agora = new Date(2026, 9, 16, 9, 30);

  it('conta dias de calendário, não horas', () => {
    expect(diasDesde(new Date(2026, 9, 16, 0, 5).toISOString(), agora)).toBe(0);
    expect(diasDesde(new Date(2026, 9, 15, 23, 50).toISOString(), agora)).toBe(1);
    expect(diasDesde(new Date(2026, 8, 16, 12, 0).toISOString(), agora)).toBe(30);
  });

  it('sem data ou com data inválida não inventa idade', () => {
    expect(diasDesde(null, agora)).toBeNull();
    expect(diasDesde('lixo', agora)).toBeNull();
    expect(rotuloSerieNoSaldo(null)).toBe('No caminhão');
    expect(rotuloMaterialParado(null)).toBe('');
  });

  it('data no futuro (relógio do aparelho atrasado) vira hoje', () => {
    expect(diasDesde(new Date(2026, 9, 18).toISOString(), agora)).toBe(0);
  });

  it('escala o tom com o tempo parado', () => {
    expect(tomTempoSaldo(DIAS_PARADO - 1)).toBe('neutro');
    expect(tomTempoSaldo(DIAS_PARADO)).toBe('atencao');
    expect(tomTempoSaldo(DIAS_PARADO_CRITICO)).toBe('erro');
    expect(tomTempoSaldo(null)).toBe('neutro');
  });

  it('escreve os rótulos no singular e no plural', () => {
    expect(rotuloSerieNoSaldo(0)).toBe('Chegou hoje');
    expect(rotuloSerieNoSaldo(1)).toBe('Com você há 1 dia');
    expect(rotuloSerieNoSaldo(12)).toBe('Com você há 12 dias');
    expect(rotuloMaterialParado(0)).toBe('Movimentado hoje');
    expect(rotuloMaterialParado(45)).toBe('Parado há 45 dias');
  });

  it('ordena as séries da mais antiga para a mais nova, sem data no fim', () => {
    const r = ordenarMaisAntigas([
      { numSerie: 'C', noSaldoDesde: '2026-10-10T10:00:00Z' },
      { numSerie: 'B', noSaldoDesde: null },
      { numSerie: 'A', noSaldoDesde: '2026-08-01T10:00:00Z' },
    ]);
    expect(r.map(s => s.numSerie)).toEqual(['A', 'C', 'B']);
    expect(maisAntiga(['2026-10-10T10:00:00Z', null, '2026-08-01T10:00:00Z'])).toBe('2026-08-01T10:00:00Z');
  });
});
