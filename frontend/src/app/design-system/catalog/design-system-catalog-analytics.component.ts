import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ThemeService } from '../../core/services/theme.service';
import { CesCellDirective, CesDataTableColumn, CesDataTableComponent } from '../data/data-table';
import { CesDrawerComponent } from '../overlays';
import { CesChartComponent, CesChartPointSelection } from '../patterns/chart';
import {
  CES_CHART_CATALOG_LABELS,
  CES_CHART_CATALOG_SERIES,
} from '../patterns/chart/chart.catalog.fixture';
import {
  CesButtonComponent,
  CesMeterComponent,
  CesRankBadgeComponent,
  CesSurfaceComponent,
} from '../primitives';

interface CatalogInspector {
  id: string;
  posicao: number;
  nome: string;
  realizado: number;
  meta: number;
  percentual: number;
  desvios: number;
}

/**
 * Página do catálogo dedicada aos padrões analíticos (drill-down, tabela analítica, medidores).
 * Fica fora da página principal para não alterar a regressão visual existente.
 */
@Component({
  selector: 'ces-design-system-catalog-analytics',
  standalone: true,
  imports: [
    CesButtonComponent,
    CesCellDirective,
    CesChartComponent,
    CesDataTableComponent,
    CesDrawerComponent,
    CesMeterComponent,
    CesRankBadgeComponent,
    CesSurfaceComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './design-system-catalog.component.css',
  styles: `
    .ces-catalog-analytics {
      display: grid;
      gap: 1.5rem;
      max-width: 72rem;
      margin: 0 auto;
      padding: 1.5rem 1rem 3rem;
    }

    .ces-catalog-analytics__header {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      justify-content: space-between;
      gap: 1rem;
    }

    .ces-catalog-analytics__header h1 {
      margin: 0.25rem 0 0;
      color: var(--ces-foreground);
      font-size: 1.5rem;
    }

    .ces-catalog-analytics__header p {
      margin: 0.35rem 0 0;
      color: var(--ces-foreground-muted);
    }

    .ces-catalog-analytics__grid {
      display: grid;
      gap: 1rem;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 20rem), 1fr));
    }

    .ces-catalog-analytics__meters {
      display: grid;
      gap: 0.875rem;
    }

    .ces-catalog-analytics__podium {
      display: flex;
      gap: 0.5rem;
    }
  `,
  template: `
    <main class="ces-catalog-analytics">
      <header class="ces-catalog-analytics__header">
        <div>
          <span class="ces-catalog__eyebrow">Design System Nexo · BI</span>
          <h1>Analítico e drill-down</h1>
          <p>
            Gráfico selecionável abrindo painel de detalhe, tabela analítica com ordenação
            multicoluna (Shift+clique), busca, paginação lembrada, CSV e cards no celular.
          </p>
        </div>
        <ces-button
          [label]="theme.isDarkMode() ? 'Usar tema claro' : 'Usar tema escuro'"
          [icon]="theme.isDarkMode() ? 'pi pi-sun' : 'pi pi-moon'"
          variant="outline"
          (pressed)="theme.setDarkMode(!theme.isDarkMode())"
        ></ces-button>
      </header>

      <div class="ces-catalog-analytics__grid">
        <ces-surface variant="outlined" [padded]="true">
          <ces-chart
            kind="bar"
            title="Inspeções por mês"
            description="Clique numa barra ou use “Ver dados” para detalhar."
            [labels]="chartLabels"
            [series]="chartSeries"
            valueFormat="integer"
            [height]="220"
            [animated]="false"
            [selectable]="true"
            (pointSelect)="openDrill($event)"
          ></ces-chart>
        </ces-surface>
        <ces-surface variant="outlined" [padded]="true">
          <div class="ces-catalog-analytics__meters">
            <ces-meter [value]="112" detail="45/40" label="Atingimento acima da meta"></ces-meter>
            <ces-meter [value]="91" detail="36/40" label="Atingimento em atenção"></ces-meter>
            <ces-meter [value]="54" detail="21/40" label="Atingimento crítico"></ces-meter>
            <div class="ces-catalog-analytics__podium">
              <ces-rank-badge [position]="1"></ces-rank-badge>
              <ces-rank-badge [position]="2"></ces-rank-badge>
              <ces-rank-badge [position]="3"></ces-rank-badge>
              <ces-rank-badge [position]="4"></ces-rank-badge>
            </div>
          </div>
        </ces-surface>
      </div>

      <ces-data-table
        title="Produtividade · Inspetores"
        description="Ranking de realização e atingimento da meta individual."
        storageKey="catalogo-inspetores"
        exportFileName="inspetores-catalogo"
        rowActionLabel="Ver histórico"
        [columns]="tableColumns"
        [rows]="tableRows"
        [rowLabel]="nomeInspetor"
        (rowActivate)="openInspector($event)"
      >
        <ng-template cesCell="posicao" let-row>
          <ces-rank-badge [position]="row.posicao"></ces-rank-badge>
        </ng-template>
        <ng-template cesCell="percentual" let-row>
          <ces-meter
            [value]="row.percentual"
            [detail]="row.realizado + '/' + row.meta"
            [label]="'Atingimento de ' + row.nome"
          ></ces-meter>
        </ng-template>
      </ces-data-table>
    </main>

    <!-- Criado já aberto dentro de @if, como nos dashboards (drill pela URL). -->
    @if (drillOpen()) {
    <ces-drawer
      [visible]="true"
      (visibleChange)="drillOpen.set($event)"
      eyebrow="Detalhe"
      [title]="drillLabel()"
      description="Recorte com os filtros aplicados no painel."
    >
      <p>Conteúdo do recorte: mini-KPIs, lista paginada e quebras.</p>
      <ces-button drawer-footer variant="outline" label="Ver todas na listagem" icon="pi pi-external-link"></ces-button>
      <ces-button drawer-footer label="Fechar" (pressed)="drillOpen.set(false)"></ces-button>
    </ces-drawer>
    }
  `,
})
export class DesignSystemCatalogAnalyticsComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly chartLabels = CES_CHART_CATALOG_LABELS;
  protected readonly chartSeries = CES_CHART_CATALOG_SERIES.slice(0, 1);
  protected readonly drillOpen = signal(false);
  protected readonly drillLabel = signal('');
  protected readonly nomeInspetor = (row: CatalogInspector) => row.nome;

  protected readonly tableColumns: CesDataTableColumn<CatalogInspector>[] = [
    { key: 'posicao', header: 'Posição', type: 'custom', value: (row) => row.posicao, width: '5rem', priority: 1, exportable: false },
    { key: 'nome', header: 'Inspetor', priority: 1 },
    { key: 'realizado', header: 'Realizado', type: 'integer', priority: 1 },
    { key: 'percentual', header: '% atingido', type: 'custom', value: (row) => row.percentual, width: '11rem', priority: 1 },
    { key: 'desvios', header: 'Desvios', type: 'integer' },
  ];

  protected readonly tableRows: CatalogInspector[] = [
    'Ana Ribeiro', 'Bruno Costa', 'Carla Nunes', 'Diego Alves', 'Elisa Prado', 'Fábio Reis',
    'Gabriela Lins', 'Hugo Matos', 'Íris Campos', 'João Teles', 'Karen Duarte', 'Luís Pires',
  ].map((nome, indice) => {
    const realizado = 48 - indice * 3;
    return {
      id: `insp-${indice}`,
      posicao: indice + 1,
      nome,
      realizado,
      meta: 40,
      percentual: (realizado / 40) * 100,
      desvios: (indice * 7) % 5,
    };
  });

  protected openDrill(selection: CesChartPointSelection): void {
    this.drillLabel.set(`Inspeções em ${selection.label}`);
    this.drillOpen.set(true);
  }

  protected openInspector(row: CatalogInspector): void {
    this.drillLabel.set(`Histórico de ${row.nome}`);
    this.drillOpen.set(true);
  }
}
