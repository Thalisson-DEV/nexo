import type { CesChartSeries } from './chart.component';

export interface CesChartCatalogFixture {
  readonly id: string;
  readonly name: string;
  readonly group: string;
  readonly status: 'experimental' | 'stable' | 'deprecated';
  readonly selector: string;
  readonly entrypoint: string;
  readonly objective: string;
  readonly documentationPath: string;
  readonly scenarios: readonly string[];
  readonly tests: readonly string[];
}

/** Metadados do catálogo vivo para o padrão de gráfico Nexo. */
export const CesChartCatalogFixture: CesChartCatalogFixture = {
  id: 'chart',
  name: 'CesChart',
  group: 'Patterns',
  status: 'experimental',
  selector: 'ces-chart',
  entrypoint: '@ces/ui/patterns',
  objective:
    'Exibir séries numéricas com cores de token, números pt-BR, resumo textual e alternativa em tabela.',
  documentationPath: 'docs/design-system/components/chart.md',
  scenarios: [
    'barras, barras horizontais e empilhadas',
    'linha e área',
    'pizza e rosca',
    'dispersão',
    'vazio',
    'alternativa em tabela de dados',
    'tema claro e escuro',
    'movimento reduzido',
  ],
  tests: ['contrato unitário', 'resumo acessível', 'tabela de dados', 'catálogo visual'],
};

/** Dados determinísticos usados no catálogo. */
export const CES_CHART_CATALOG_LABELS: readonly string[] = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun'];
export const CES_CHART_CATALOG_SERIES: readonly CesChartSeries[] = [
  { name: 'Entradas', values: [120, 132, 98, 145, 160, 151] },
  { name: 'Saídas', values: [90, 110, 105, 120, 138, 142] },
];
