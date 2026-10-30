import { SerieTemporalChamadoItem } from '../../../core/models/chamado-dashboard.models';
import { CesPrimaryColor } from '../../../core/services/theme.service';
import { PALETAS_GRADIENTE_PRIMARIA, PaletaGradientePrimaria } from '../../sst/dashboards/dashboard-eventos-chart.config';

export function obterPaletaChamados(corPrimaria?: CesPrimaryColor | null): PaletaGradientePrimaria {
  const chave = corPrimaria && corPrimaria in PALETAS_GRADIENTE_PRIMARIA ? corPrimaria : 'blue';
  return PALETAS_GRADIENTE_PRIMARIA[chave];
}

export function montarDadosTendenciaChamados(
  serie: SerieTemporalChamadoItem[],
  corPrimaria?: CesPrimaryColor | null
): Record<string, unknown> {
  const paleta = obterPaletaChamados(corPrimaria);

  return {
    labels: serie.map((item) => item.rotulo),
    datasets: [
      {
        label: 'Abertos',
        data: serie.map((item) => item.totalAbertos),
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
      },
      {
        label: 'Resolvidos',
        data: serie.map((item) => item.totalResolvidos),
        borderColor: '#059669', // emerald-600
        backgroundColor: 'transparent',
        borderWidth: 2,
        borderDash: [4, 4],
        pointRadius: 0,
        pointHoverRadius: 5,
        pointHitRadius: 16,
        pointHoverBackgroundColor: '#fff',
        pointHoverBorderColor: '#059669',
        pointHoverBorderWidth: 3,
        tension: 0.32,
        cubicInterpolationMode: 'monotone'
      }
    ]
  };
}

export function opcoesGraficoTendenciaChamados(): Record<string, unknown> {
  return {
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: {
        display: true,
        position: 'top',
        align: 'end',
        labels: {
          boxWidth: 12,
          boxHeight: 12,
          color: '#64748b',
          font: { size: 11, weight: '600' },
          usePointStyle: true,
          pointStyle: 'circle'
        }
      },
      tooltip: {
        mode: 'index',
        intersect: false,
        displayColors: true,
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
        ticks: { color: '#829ab1', font: { size: 10 }, maxTicksLimit: 8, maxRotation: 0, padding: 8 }
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
