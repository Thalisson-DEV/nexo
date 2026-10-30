import { describe, expect, it } from 'vitest';
import {
  montarDadosTendenciaChamados,
  opcoesGraficoTendenciaChamados
} from './chamados-dashboard-chart.config';
import { SerieTemporalChamadoItem } from '../../../core/models/chamado-dashboard.models';

describe('chamados-dashboard-chart.config', () => {
  describe('montarDadosTendenciaChamados', () => {
    it('monta os dados do gráfico respeitando os rótulos e baldes do backend', () => {
      const serie: SerieTemporalChamadoItem[] = [
        { chave: '2026-03-01', rotulo: '01/03', data: '2026-03-01', totalAbertos: 5, totalResolvidos: 3, totalVencidos: 0 },
        { chave: '2026-03-02', rotulo: '02/03', data: '2026-03-02', totalAbertos: 0, totalResolvidos: 2, totalVencidos: 0 },
        { chave: '2026-03-03', rotulo: '03/03', data: '2026-03-03', totalAbertos: 8, totalResolvidos: 7, totalVencidos: 1 }
      ];

      const chartData = montarDadosTendenciaChamados(serie, 'blue');

      expect(chartData['labels']).toEqual(['01/03', '02/03', '03/03']);
      const datasets = chartData['datasets'] as Array<Record<string, unknown>>;
      expect(datasets).toHaveLength(2);

      const dsAbertos = datasets[0];
      const dsResolvidos = datasets[1];

      expect(dsAbertos['label']).toBe('Abertos');
      expect(dsAbertos['data']).toEqual([5, 0, 8]);

      expect(dsResolvidos['label']).toBe('Resolvidos');
      expect(dsResolvidos['data']).toEqual([3, 2, 7]);
    });

    it('retorna labels vazias para série vazia', () => {
      const chartData = montarDadosTendenciaChamados([], 'blue');
      expect(chartData['labels']).toEqual([]);
      const datasets = chartData['datasets'] as Array<Record<string, unknown>>;
      expect(datasets[0]['data']).toEqual([]);
      expect(datasets[1]['data']).toEqual([]);
    });
  });

  describe('opcoesGraficoTendenciaChamados', () => {
    it('retorna opções com tooltips, legenda e eixos formatados', () => {
      const options = opcoesGraficoTendenciaChamados() as Record<string, any>;

      expect(options['maintainAspectRatio']).toBe(false);
      expect(options['plugins']?.['legend']?.['display']).toBe(true);
      expect(options['scales']?.['y']?.['beginAtZero']).toBe(true);
    });
  });
});
