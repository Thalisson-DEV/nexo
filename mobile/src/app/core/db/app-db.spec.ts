import { migrarExecucaoParaLancamentoUnico } from './app-db';

describe('migração local v8 — lançamento de etapa única', () => {
  it('converte o registro antigo para data de lançamento e localização única', () => {
    const antigo: Record<string, unknown> = {
      id: 'e-1',
      dataInicio: '2026-10-03T10:00:00Z',
      dataFim: '2026-10-03T10:00:00Z',
      latitudeInicio: -12.9,
      longitudeInicio: -38.5,
      latitudeFim: -12.8,
      longitudeFim: -38.4,
      statusExecucao: 'CONCLUIDO'
    };

    migrarExecucaoParaLancamentoUnico(antigo);

    expect(antigo).toEqual({
      id: 'e-1',
      dataLancamento: '2026-10-03T10:00:00Z',
      latitude: -12.9,
      longitude: -38.5,
      statusExecucao: 'CONCLUIDO'
    });
  });

  it('usa a data de fim quando o registro antigo não tem início', () => {
    const antigo: Record<string, unknown> = { id: 'e-2', dataFim: '2026-10-04T08:00:00Z' };

    migrarExecucaoParaLancamentoUnico(antigo);

    expect(antigo['dataLancamento']).toBe('2026-10-04T08:00:00Z');
    expect('dataFim' in antigo).toBeFalse();
  });

  it('não altera um registro que já está no formato novo', () => {
    const novo: Record<string, unknown> = {
      id: 'e-3', dataLancamento: '2026-10-05T09:00:00Z', latitude: -3.7, longitude: -38.5
    };

    migrarExecucaoParaLancamentoUnico(novo);

    expect(novo).toEqual({ id: 'e-3', dataLancamento: '2026-10-05T09:00:00Z', latitude: -3.7, longitude: -38.5 });
  });
});
