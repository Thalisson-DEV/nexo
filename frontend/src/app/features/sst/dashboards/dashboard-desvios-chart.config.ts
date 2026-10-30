import { TendenciaDesviosItem } from '../../../core/models/sst-dashboard.models';
import { obterPaletaPrimaria } from './dashboard-eventos-chart.config';

export function montarDadosTendenciaDesvios(
  serie: TendenciaDesviosItem[],
  corPrimaria?: string | null
): Record<string, unknown> {
  const paleta = obterPaletaPrimaria(corPrimaria);

  return {
    labels: serie.map((item) => item.rotulo),
    datasets: [
      {
        label: 'Desvios identificados',
        data: serie.map((item) => item.totalDesvios),
        borderColor: paleta.forte,
        backgroundColor: (context: any) => {
          const chart = context?.chart;
          if (!chart?.ctx || !chart.chartArea) return paleta.claro;
          const { ctx, chartArea } = chart;
          const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
          gradient.addColorStop(0, paleta.claro);
          gradient.addColorStop(0.85, 'rgba(255, 255, 255, 0.02)');
          gradient.addColorStop(1, 'rgba(255, 255, 255, 0)');
          return gradient;
        },
        fill: true,
        borderWidth: 2.5,
        pointRadius: 0,
        pointHoverRadius: 5,
        pointHitRadius: 16,
        pointHoverBackgroundColor: '#fff',
        pointHoverBorderColor: paleta.forte,
        pointHoverBorderWidth: 3,
        tension: 0.32,
        cubicInterpolationMode: 'monotone'
      }
    ]
  };
}

export function opcoesGraficoTendenciaDesvios(): Record<string, unknown> {
  return {
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: {
        mode: 'index',
        intersect: false,
        displayColors: false,
        backgroundColor: '#102a43',
        titleColor: '#d9e2ec',
        bodyColor: '#fff',
        padding: 11,
        cornerRadius: 8
      }
    },
    scales: {
      x: {
        grid: { display: false },
        border: { display: false },
        ticks: { color: '#829ab1', font: { size: 10 }, maxTicksLimit: 7, maxRotation: 0, padding: 8 }
      },
      y: {
        beginAtZero: true,
        grace: '8%',
        border: { display: false },
        grid: { color: '#edf2f7', drawTicks: false },
        ticks: { color: '#9fb3c8', precision: 0, maxTicksLimit: 5, padding: 10 }
      }
    }
  };
}
