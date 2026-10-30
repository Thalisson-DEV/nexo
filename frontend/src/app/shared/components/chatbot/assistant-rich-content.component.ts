import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { ChartData, ChartOptions, ChartType, TooltipItem } from 'chart.js';
import { UIChart } from 'primeng/chart';

import {
  AssistenteMensagem,
  AssistenteUnidadeSerie,
  AssistenteVisualizacao
} from '../../../core/models/assistente.models';
import { AssistantMarkdownComponent } from './assistant-markdown.component';
import {
  AssistantRenderableBlock,
  buildAssistantPresentation,
  formatVisualizationValue
} from './assistant-presentation.util';

interface RichTextBlock extends Extract<AssistantRenderableBlock, { kind: 'text' }> {}

interface RichVisualizationBlock extends Extract<AssistantRenderableBlock, { kind: 'visualization' }> {
  chartType: 'bar' | 'line' | 'doughnut' | null;
  chartData: ChartData | null;
  chartOptions: ChartOptions | null;
}

type RichBlock = RichTextBlock | RichVisualizationBlock;

const SERIES_COLORS = ['#2563eb', '#0f9f80', '#f59e0b'];
const CATEGORY_COLORS = [
  '#2563eb', '#0f9f80', '#f59e0b', '#7c3aed', '#e11d48', '#0891b2', '#65a30d',
  '#ea580c', '#4f46e5', '#059669', '#ca8a04', '#9333ea', '#be123c', '#0e7490',
  '#4d7c0f', '#c2410c', '#1d4ed8', '#047857', '#a16207', '#6d28d9'
];

@Component({
  selector: 'app-assistant-rich-content',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AssistantMarkdownComponent, UIChart],
  templateUrl: './assistant-rich-content.component.html',
  styleUrl: './assistant-rich-content.component.css'
})
export class AssistantRichContentComponent {
  readonly message = input.required<AssistenteMensagem>();
  protected readonly blocks = computed<RichBlock[]>(() =>
    buildAssistantPresentation(this.message()).map(block =>
      block.kind === 'text' ? block : this.enrichVisualization(block)
    )
  );
  protected readonly formatValue = formatVisualizationValue;

  private enrichVisualization(
    block: Extract<AssistantRenderableBlock, { kind: 'visualization' }>
  ): RichVisualizationBlock {
    const chartType = this.chartType(block.visualization.tipo);
    return {
      ...block,
      chartType,
      chartData: chartType ? this.chartData(block.visualization, chartType) : null,
      chartOptions: chartType ? this.chartOptions(block.visualization, chartType) : null
    };
  }

  private chartType(type: string): RichVisualizationBlock['chartType'] {
    if (type === 'BARRAS') return 'bar';
    if (type === 'LINHA') return 'line';
    if (type === 'ROSCA') return 'doughnut';
    return null;
  }

  private chartData(visualization: AssistenteVisualizacao, type: ChartType): ChartData {
    const doughnut = type === 'doughnut';
    return {
      labels: [...visualization.categorias],
      datasets: visualization.series.map((series, index) => ({
        label: series.nome,
        data: [...series.valores],
        backgroundColor: doughnut
          ? CATEGORY_COLORS.slice(0, visualization.categorias.length)
          : colorWithOpacity(SERIES_COLORS[index], type === 'line' ? 0.14 : 0.78),
        borderColor: doughnut
          ? CATEGORY_COLORS.slice(0, visualization.categorias.length)
          : SERIES_COLORS[index],
        borderWidth: type === 'line' ? 2 : 1,
        pointStyle: ['circle', 'rectRot', 'triangle'][index],
        pointRadius: type === 'line' ? 3 : undefined,
        tension: type === 'line' ? 0.28 : undefined,
        fill: type === 'line'
      }))
    };
  }

  private chartOptions(visualization: AssistenteVisualizacao, type: ChartType): ChartOptions {
    const firstUnit = visualization.series[0].unidade;
    const options: ChartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 320 },
      plugins: {
        legend: {
          display: visualization.series.length > 1 || type === 'doughnut',
          position: 'bottom',
          labels: { usePointStyle: true, boxWidth: 8, font: { size: 10 } }
        },
        tooltip: {
          callbacks: {
            label: context => this.tooltipLabel(context, visualization)
          }
        }
      }
    };
    if (type !== 'doughnut') {
      options.scales = {
        x: { grid: { display: false }, ticks: { color: '#64748b', font: { size: 9 } } },
        y: {
          beginAtZero: true,
          grid: { color: '#e8edf3' },
          ticks: {
            color: '#64748b',
            font: { size: 9 },
            callback: value => formatVisualizationValue(Number(value), firstUnit)
          }
        }
      };
    }
    return options;
  }

  private tooltipLabel(context: TooltipItem<ChartType>, visualization: AssistenteVisualizacao): string {
    const series = visualization.series[context.datasetIndex];
    const rawValue = typeof context.raw === 'number'
      ? context.raw
      : Number((context.raw as { y?: number })?.y ?? context.parsed);
    return `${series.nome}: ${formatVisualizationValue(rawValue, series.unidade)}`;
  }
}

function colorWithOpacity(hex: string, opacity: number): string {
  const value = hex.replace('#', '');
  const red = Number.parseInt(value.slice(0, 2), 16);
  const green = Number.parseInt(value.slice(2, 4), 16);
  const blue = Number.parseInt(value.slice(4, 6), 16);
  return `rgba(${red}, ${green}, ${blue}, ${opacity})`;
}
