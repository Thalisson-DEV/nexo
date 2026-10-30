import { describe, expect, it } from 'vitest';

import { montarDadosTendenciaDesvios } from './dashboard-desvios-chart.config';

describe('montarDadosTendenciaDesvios', () => {
  it('usa o rótulo e o total entregues pelo backend, inclusive em baldes zerados', () => {
    const data = montarDadosTendenciaDesvios([
      { chave: '2026-01', rotulo: 'jan/2026', inicio: '2026-01-01', totalDesvios: 14 },
      { chave: '2026-02', rotulo: 'fev/2026', inicio: '2026-02-01', totalDesvios: 0 }
    ]);

    expect(data['labels']).toEqual(['jan/2026', 'fev/2026']);
    expect((data['datasets'] as Array<Record<string, unknown>>)[0]['data']).toEqual([14, 0]);
    expect((data['datasets'] as Array<Record<string, unknown>>)[0]['label']).toBe(
      'Desvios identificados'
    );
  });

  it('não usa a data civil para montar o texto do eixo', () => {
    const data = montarDadosTendenciaDesvios([
      { chave: '2026-01-01', rotulo: '01/01', inicio: '2026-01-01', totalDesvios: 1 }
    ]);

    expect(data['labels']).toEqual(['01/01']);
  });
});
