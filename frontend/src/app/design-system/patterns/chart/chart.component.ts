import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { ChartModule } from 'primeng/chart';
import type { ChartData, ChartOptions, ChartType, TooltipItem } from 'chart.js';
import { CesButtonComponent } from '../../primitives/button';

export type CesChartKind =
  | 'bar'
  | 'bar-horizontal'
  | 'bar-stacked'
  | 'line'
  | 'area'
  | 'pie'
  | 'doughnut'
  | 'scatter';

export type CesChartValueFormat = 'number' | 'integer' | 'currency' | 'percent';

export interface CesChartSeries {
  readonly name: string;
  readonly values: readonly (number | null)[];
}

/** Ponto escolhido pelo usuário num gráfico `selectable` (clique, toque ou linha da tabela de dados). */
export interface CesChartPointSelection {
  /** Índice da categoria (ou do ponto, no `scatter`). */
  readonly index: number;
  /** Rótulo da categoria (ou do ponto). */
  readonly label: string;
  /** Série tocada; `null` quando a seleção veio da tabela de dados (linha inteira). */
  readonly seriesIndex: number | null;
}

export interface CesChartPoint {
  readonly x: number;
  readonly y: number;
  readonly label?: string;
}

const SERIES_TOKENS = [
  '--ces-chart-1',
  '--ces-chart-2',
  '--ces-chart-3',
  '--ces-chart-4',
  '--ces-chart-5',
  '--ces-chart-6',
  '--ces-chart-7',
  '--ces-chart-8',
] as const;

const KIND_LABEL: Record<CesChartKind, string> = {
  bar: 'Gráfico de barras',
  'bar-horizontal': 'Gráfico de barras horizontais',
  'bar-stacked': 'Gráfico de barras empilhadas',
  line: 'Gráfico de linhas',
  area: 'Gráfico de área',
  pie: 'Gráfico de pizza',
  doughnut: 'Gráfico de rosca',
  scatter: 'Gráfico de dispersão',
};

let chartSequence = 0;

/**
 * Gráfico Nexo sobre Chart.js (via `primeng/chart`). Cores vêm dos tokens `--ces-chart-*` (tema claro e
 * escuro), números no padrão pt-BR, resumo textual para leitores de tela e alternativa em tabela de
 * dados acessível por botão — o gráfico nunca é a única forma de ler a informação.
 */
@Component({
  selector: 'ces-chart',
  standalone: true,
  imports: [ChartModule, CesButtonComponent],
  templateUrl: './chart.component.html',
  styleUrl: './chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-chart',
    '[attr.data-kind]': 'kind()',
  },
})
export class CesChartComponent {
  private readonly document = inject(DOCUMENT);
  private readonly themeVersion = signal(0);
  protected readonly id = `ces-chart-${++chartSequence}`;

  readonly kind = input<CesChartKind>('bar');
  readonly title = input.required<string>();
  readonly showTitle = input(true);
  readonly description = input<string | undefined>();
  readonly labels = input<readonly string[]>([]);
  readonly series = input<readonly CesChartSeries[]>([]);
  readonly points = input<readonly CesChartPoint[]>([]);
  readonly valueFormat = input<CesChartValueFormat>('number');
  readonly xAxisLabel = input<string | undefined>();
  readonly yAxisLabel = input<string | undefined>();
  readonly height = input(280);
  readonly note = input<string | undefined>();
  /** Animação de entrada; sempre desligada com `prefers-reduced-motion` (e no catálogo visual). */
  readonly animated = input(true);
  /** Alterna entre o gráfico e a tabela de dados equivalente. */
  readonly dataTableVisible = model(false);
  /**
   * Torna categorias selecionáveis (drill-down): clique/toque no gráfico e botão em cada linha da
   * tabela de dados — o caminho por teclado e leitor de tela.
   */
  readonly selectable = input(false);
  /** Verbo do botão de cada linha na tabela de dados quando `selectable` (ex.: "Detalhar"). */
  readonly selectLabel = input('Detalhar');

  readonly pointSelect = output<CesChartPointSelection>();

  protected readonly chartType = computed<ChartType>(() => {
    switch (this.kind()) {
      case 'line':
      case 'area':
        return 'line';
      case 'pie':
        return 'pie';
      case 'doughnut':
        return 'doughnut';
      case 'scatter':
        return 'scatter';
      default:
        return 'bar';
    }
  });

  protected readonly empty = computed(() =>
    this.kind() === 'scatter'
      ? this.points().length === 0
      : this.labels().length === 0 || this.series().length === 0,
  );

  protected readonly palette = computed(() => {
    this.themeVersion();
    const styles = this.document.defaultView?.getComputedStyle(this.document.documentElement);
    const read = (token: string, fallback: string) => styles?.getPropertyValue(token).trim() || fallback;
    return {
      series: SERIES_TOKENS.map((token) => read(token, '#2563eb')),
      grid: read('--ces-border', '#e2e8f0'),
      text: read('--ces-foreground-muted', '#475569'),
      surface: read('--ces-surface', '#ffffff'),
    };
  });

  protected readonly data = computed<ChartData>(() => {
    const cores = this.palette().series;
    const kind = this.kind();
    if (kind === 'scatter') {
      return {
        datasets: [
          {
            label: this.yAxisLabel() ?? this.title(),
            data: this.points().map((point) => ({ x: point.x, y: point.y })),
            backgroundColor: alpha(cores[0], 0.7),
            borderColor: cores[0],
            pointRadius: 4,
          },
        ],
      } as ChartData;
    }
    const circular = kind === 'pie' || kind === 'doughnut';
    return {
      labels: [...this.labels()],
      datasets: this.series().map((serie, index) => {
        const cor = cores[index % cores.length];
        if (circular) {
          return {
            label: serie.name,
            data: [...serie.values],
            backgroundColor: this.labels().map((_, i) => cores[i % cores.length]),
            borderColor: this.palette().surface,
            borderWidth: 2,
          };
        }
        if (kind === 'line' || kind === 'area') {
          return {
            label: serie.name,
            data: [...serie.values],
            borderColor: cor,
            backgroundColor: kind === 'area' ? alpha(cor, 0.18) : cor,
            fill: kind === 'area' ? 'origin' : false,
            tension: 0.25,
            pointRadius: this.labels().length > 40 ? 0 : 3,
            pointHoverRadius: this.selectable() ? 7 : 4,
            pointHitRadius: this.selectable() ? 12 : 1,
            spanGaps: true,
          };
        }
        return {
          label: serie.name,
          data: [...serie.values],
          backgroundColor: cor,
          borderRadius: 4,
          maxBarThickness: 48,
        };
      }),
    } as ChartData;
  });

  protected readonly options = computed<ChartOptions>(() => {
    const { grid, text } = this.palette();
    const kind = this.kind();
    const formato = this.valueFormat();
    const circular = kind === 'pie' || kind === 'doughnut';
    const horizontal = kind === 'bar-horizontal';
    const stacked = kind === 'bar-stacked';
    const multiplas = this.series().length > 1;
    const reduzirMovimento =
      this.document.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    const tick = (value: number | string) => formatCesChartValue(Number(value), formato, true);
    const eixoValor = {
      grid: { color: grid },
      // Contagens não têm meio: sem `precision`, eixos pequenos repetem rótulos ("0, 0, 1, 1, 2").
      ticks: { color: text, callback: tick, ...(formato === 'integer' ? { precision: 0 } : {}) },
      stacked,
      beginAtZero: true,
      title: this.yAxisLabel() ? { display: true, text: this.yAxisLabel(), color: text } : undefined,
    };
    const eixoCategoria = {
      grid: { display: false },
      ticks: { color: text, autoSkip: true, maxRotation: 45 },
      stacked,
      title: this.xAxisLabel() ? { display: true, text: this.xAxisLabel(), color: text } : undefined,
    };
    const selecionavel = this.selectable();
    // Selecionável: a categoria inteira (a coluna do dia, a faixa da equipe) é área de clique — acertar
    // um ponto de 3px não pode ser a única forma de detalhar.
    const porCategoria = selecionavel && !circular && kind !== 'scatter';
    return {
      responsive: true,
      maintainAspectRatio: false,
      interaction: porCategoria ? { mode: 'index', intersect: false, axis: horizontal ? 'y' : 'x' } : undefined,
      onClick: selecionavel
        ? (_event: unknown, elements: { index: number; datasetIndex: number }[]) => {
            const alvo = elements?.[0];
            if (alvo) this.emitSelection(alvo.index, alvo.datasetIndex);
          }
        : undefined,
      onHover: selecionavel
        ? (event: { native?: { target?: unknown } | null }, elements: unknown[]) => {
            const alvo = event?.native?.target as HTMLElement | undefined;
            if (alvo?.style) alvo.style.cursor = elements?.length ? 'pointer' : 'default';
          }
        : undefined,
      animation: reduzirMovimento || !this.animated() ? false : { duration: 250 },
      indexAxis: horizontal ? 'y' : 'x',
      plugins: {
        legend: {
          display: circular || multiplas,
          position: circular ? 'right' : 'bottom',
          labels: { color: text, boxWidth: 12 },
        },
        tooltip: {
          callbacks: {
            label: (item: TooltipItem<ChartType>) => {
              const raw = item.raw as number | { x: number; y: number } | null;
              if (raw && typeof raw === 'object') {
                return `${formatCesChartValue(raw.x, 'number')} ; ${formatCesChartValue(raw.y, formato)}`;
              }
              const valor = formatCesChartValue(Number(raw), formato);
              return item.dataset.label && !circular ? `${item.dataset.label}: ${valor}` : valor;
            },
          },
        },
      },
      scales: circular
        ? undefined
        : kind === 'scatter'
          ? {
              x: { ...eixoCategoria, type: 'linear', ticks: { color: text } },
              y: eixoValor,
            }
          : horizontal
            ? { x: eixoValor, y: eixoCategoria }
            : { x: eixoCategoria, y: eixoValor },
    } as ChartOptions;
  });

  /** Resumo textual equivalente ao gráfico (leitores de tela). */
  protected readonly summary = computed(() => {
    const kind = this.kind();
    const formato = this.valueFormat();
    if (this.empty()) return `${KIND_LABEL[kind]} sem dados.`;
    if (kind === 'scatter') return `${KIND_LABEL[kind]} com ${this.points().length} pontos.`;
    const labels = this.labels();
    const partes = [`${KIND_LABEL[kind]} com ${labels.length} categorias`];
    const series = this.series();
    if (series.length > 1) partes.push(`${series.length} séries: ${series.map((s) => s.name).join(', ')}`);
    const primeira = series[0];
    let maior = -1;
    let menor = -1;
    primeira.values.forEach((valor, indice) => {
      if (valor === null || valor === undefined) return;
      if (maior < 0 || valor > (primeira.values[maior] ?? -Infinity)) maior = indice;
      if (menor < 0 || valor < (primeira.values[menor] ?? Infinity)) menor = indice;
    });
    if (maior >= 0) {
      partes.push(
        `maior valor${series.length > 1 ? ` em ${primeira.name}` : ''}: ${labels[maior]} (${formatCesChartValue(primeira.values[maior] ?? 0, formato)})`,
      );
    }
    if (menor >= 0 && menor !== maior) {
      partes.push(`menor: ${labels[menor]} (${formatCesChartValue(primeira.values[menor] ?? 0, formato)})`);
    }
    return partes.join('; ') + '.';
  });

  protected readonly tableColumns = computed(() =>
    this.kind() === 'scatter'
      ? [this.xAxisLabel() ?? 'X', this.yAxisLabel() ?? 'Y']
      : [this.xAxisLabel() ?? 'Categoria', ...this.series().map((serie) => serie.name)],
  );

  protected readonly tableRows = computed<string[][]>(() => {
    const formato = this.valueFormat();
    if (this.kind() === 'scatter') {
      return this.points().map((point) => [
        point.label ? `${point.label} (${formatCesChartValue(point.x, 'number')})` : formatCesChartValue(point.x, 'number'),
        formatCesChartValue(point.y, formato),
      ]);
    }
    return this.labels().map((label, indice) => [
      label,
      ...this.series().map((serie) => {
        const valor = serie.values[indice];
        return valor === null || valor === undefined ? '—' : formatCesChartValue(valor, formato);
      }),
    ]);
  });

  constructor() {
    const view = this.document.defaultView;
    if (view && 'MutationObserver' in view) {
      const observer = new view.MutationObserver(() => this.themeVersion.update((versao) => versao + 1));
      observer.observe(this.document.documentElement, { attributes: true, attributeFilter: ['class', 'style'] });
      inject(DestroyRef).onDestroy(() => observer.disconnect());
    }
  }

  /** Rótulo da linha `indice` (categoria ou ponto do scatter). */
  protected rowLabel(indice: number): string {
    if (this.kind() === 'scatter') {
      const ponto = this.points()[indice];
      return ponto?.label ?? formatCesChartValue(ponto?.x ?? Number.NaN, 'number');
    }
    return this.labels()[indice] ?? '';
  }

  protected emitSelection(index: number, seriesIndex: number | null): void {
    if (!this.selectable()) return;
    this.pointSelect.emit({ index, label: this.rowLabel(index), seriesIndex });
  }

  protected toggleTable(): void {
    this.dataTableVisible.update((visivel) => !visivel);
  }
}

/** Formata valores no padrão brasileiro; `compact` abrevia eixos (1,2 mil; 3,4 mi). */
export function formatCesChartValue(value: number, format: CesChartValueFormat, compact = false): string {
  if (!Number.isFinite(value)) return '—';
  const abs = Math.abs(value);
  const compactar = compact && abs >= 10_000;
  switch (format) {
    case 'currency':
      return new Intl.NumberFormat('pt-BR', {
        style: 'currency',
        currency: 'BRL',
        notation: compactar ? 'compact' : 'standard',
        maximumFractionDigits: compactar ? 1 : 2,
      }).format(value);
    case 'percent':
      return new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 1 }).format(value);
    case 'integer':
      return new Intl.NumberFormat('pt-BR', {
        notation: compactar ? 'compact' : 'standard',
        maximumFractionDigits: compactar ? 1 : 0,
      }).format(value);
    default:
      return new Intl.NumberFormat('pt-BR', {
        notation: compactar ? 'compact' : 'standard',
        maximumFractionDigits: abs >= 100 ? (compactar ? 1 : 0) : 2,
      }).format(value);
  }
}

function alpha(cor: string, opacidade: number): string {
  const hex = cor.trim().replace('#', '');
  if (/^[0-9a-f]{6}$/i.test(hex)) {
    const r = parseInt(hex.slice(0, 2), 16);
    const g = parseInt(hex.slice(2, 4), 16);
    const b = parseInt(hex.slice(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${opacidade})`;
  }
  return cor;
}
