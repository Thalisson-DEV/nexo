import { SerieEventosItem } from '../../../core/models/sst-evento.models';
import { CesPrimaryColor } from '../../../core/services/theme.service';

export interface DatasetEvolucao {
  type: 'bar' | 'line';
  label: string;
  data: number[];
  order: number;
  [chave: string]: unknown;
}

export interface DadosGraficoEvolucao {
  labels: string[];
  datasets: DatasetEvolucao[];
}

export interface PaletaGradientePrimaria {
  claro: string;
  forte: string;
  borda: string;
  hover: string;
}

export const PALETAS_GRADIENTE_PRIMARIA: Record<CesPrimaryColor, PaletaGradientePrimaria> = {
  blue: {
    claro: '#60a5fa', // blue-400
    forte: '#1d4ed8', // blue-700
    borda: '#1e40af', // blue-800
    hover: '#2563eb'  // blue-600
  },
  emerald: {
    claro: '#34d399', // emerald-400
    forte: '#047857', // emerald-700
    borda: '#065f46', // emerald-800
    hover: '#059669'  // emerald-600
  },
  violet: {
    claro: '#a78bfa', // violet-400
    forte: '#6d28d9', // violet-700
    borda: '#5b21b6', // violet-800
    hover: '#7c3aed'  // violet-600
  },
  amber: {
    claro: '#fbbf24', // amber-400
    forte: '#b45309', // amber-700
    borda: '#92400e', // amber-800
    hover: '#d97706'  // amber-600
  },
  rose: {
    claro: '#fb7185', // rose-400
    forte: '#be123c', // rose-700
    borda: '#9f1239', // rose-800
    hover: '#e11d48'  // rose-600
  },
  slate: {
    claro: '#94a3b8', // slate-400
    forte: '#334155', // slate-700
    borda: '#1e293b', // slate-800
    hover: '#475569'  // slate-600
  }
};

export function obterPaletaPrimaria(cor?: CesPrimaryColor | string | null): PaletaGradientePrimaria {
  if (!cor) return PALETAS_GRADIENTE_PRIMARIA.blue;
  const chave = cor.toLowerCase().trim() as CesPrimaryColor;
  return PALETAS_GRADIENTE_PRIMARIA[chave] ?? PALETAS_GRADIENTE_PRIMARIA.blue;
}

export function criarGradienteBarra(
  ctx: CanvasRenderingContext2D,
  yTopo: number,
  yBase: number,
  corClara: string,
  corForte: string
): CanvasGradient {
  const topo = Math.min(yTopo, yBase);
  const base = Math.max(yTopo, yBase);
  const gradient = ctx.createLinearGradient(0, topo, 0, base);
  gradient.addColorStop(0, corClara);
  gradient.addColorStop(1, corForte);
  return gradient;
}

/**
 * O total contém a série de lesões. Usar formas distintas evita que valores iguais
 * virem duas linhas sobrepostas e visualmente indistinguíveis.
 * As barras utilizam uma cor sólida com degradê vertical do tom claro ao forte da cor
 * primária ativa configurada pelo usuário no ThemeService.
 */
export function montarDadosGraficoEvolucao(
  serie: readonly SerieEventosItem[],
  corPrimaria?: CesPrimaryColor | string | null
): DadosGraficoEvolucao {
  const paleta = obterPaletaPrimaria(corPrimaria);

  return {
    labels: serie.map((item) => item.rotulo),
    datasets: [
      {
        type: 'bar',
        label: 'Total de eventos',
        data: serie.map((item) => item.total),
        order: 2,
        backgroundColor: (context: any) => {
          const chart = context?.chart;
          if (!chart || !chart.ctx) {
            return paleta.forte;
          }
          const { ctx, chartArea } = chart;
          if (!chartArea) {
            return paleta.forte;
          }

          const element = context.element;
          if (
            element &&
            typeof element.y === 'number' &&
            typeof element.base === 'number' &&
            Math.abs(element.base - element.y) > 0.5
          ) {
            return criarGradienteBarra(ctx, element.y, element.base, paleta.claro, paleta.forte);
          }

          return criarGradienteBarra(ctx, chartArea.top, chartArea.bottom, paleta.claro, paleta.forte);
        },
        hoverBackgroundColor: (context: any) => {
          const chart = context?.chart;
          if (!chart || !chart.ctx) {
            return paleta.hover;
          }
          const { ctx, chartArea } = chart;
          if (!chartArea) {
            return paleta.hover;
          }

          const element = context.element;
          if (
            element &&
            typeof element.y === 'number' &&
            typeof element.base === 'number' &&
            Math.abs(element.base - element.y) > 0.5
          ) {
            return criarGradienteBarra(ctx, element.y, element.base, paleta.claro, paleta.hover);
          }

          return paleta.hover;
        },
        borderColor: paleta.borda,
        borderWidth: 1,
        borderRadius: 6,
        borderSkipped: false,
        barPercentage: 0.72,
        categoryPercentage: 0.82,
        maxBarThickness: 42
      },
      {
        type: 'line',
        label: 'Com lesão',
        data: serie.map((item) => item.comLesao),
        order: 1,
        borderColor: '#dc2626',
        backgroundColor: 'rgba(220, 38, 38, 0.08)',
        borderWidth: 2.5,
        pointRadius: 4,
        pointHoverRadius: 6,
        pointHitRadius: 18,
        pointBackgroundColor: '#ffffff',
        pointBorderColor: '#dc2626',
        pointBorderWidth: 2.5,
        tension: 0.3,
        cubicInterpolationMode: 'monotone',
        fill: true
      }
    ]
  };
}

export function calcularEventosSemLesao(total: number, comLesao: number): number {
  return Math.max(0, total - comLesao);
}
