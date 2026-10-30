import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { Observable, delay, of } from 'rxjs';
import { CesDataTableColumn, CesDataTableComponent, CesCellDirective } from '../data';
import { CesMultiSelectComponent, CesSelectComponent, CesSelectOption, CesTextInputComponent } from '../forms';
import { CesFormDrawerComponent } from '../forms/form-kit';
import { CesFilterPanelComponent } from '../patterns/filter-panel';
import { CesListShortcutsDirective } from '../patterns/list-shortcuts';
import { CesListRequest, cesFilter, cesListState } from '../patterns/list-state';
import { CesPageHeaderComponent } from '../patterns/page-header';
import { CesButtonComponent, CesStatusComponent } from '../primitives';

interface Registro {
  id: string;
  placa: string;
  base: string;
  situacao: 'OPERANDO' | 'MANUTENCAO' | 'PARADO';
  km: number;
}

const BASES: CesSelectOption<string>[] = [
  { label: 'Feira de Santana', value: 'FSA' },
  { label: 'Salvador', value: 'SSA' },
  { label: 'Vitória da Conquista', value: 'VDC' },
];
const SITUACOES: CesSelectOption<Registro['situacao']>[] = [
  { label: 'Operando', value: 'OPERANDO' },
  { label: 'Em manutenção', value: 'MANUTENCAO' },
  { label: 'Parado', value: 'PARADO' },
];
const REGISTROS: Registro[] = Array.from({ length: 240 }, (_, i) => ({
  id: `r-${i}`,
  placa: `Nexo${String(1000 + i)}`,
  base: BASES[i % 3].value,
  situacao: SITUACOES[i % 7 === 0 ? 2 : i % 5 === 0 ? 1 : 0].value,
  km: 12000 + i * 731,
}));

type Filtros = { bases: string[]; situacao: Registro['situacao'] | null };

/** Servidor simulado: filtra, busca, ordena e pagina com atraso de rede. */
function servidor(pedido: CesListRequest<Filtros>): Observable<{ content: Registro[]; page: { totalElements: number } }> {
  const termo = pedido.query.toLocaleLowerCase('pt-BR');
  let linhas = REGISTROS.filter(
    (r) =>
      (!pedido.filters.bases.length || pedido.filters.bases.includes(r.base)) &&
      (!pedido.filters.situacao || r.situacao === pedido.filters.situacao) &&
      (!termo || r.placa.toLocaleLowerCase('pt-BR').includes(termo)),
  );
  for (const regra of [...pedido.sort].reverse()) {
    const campo = regra.key as keyof Registro;
    linhas = [...linhas].sort((a, b) => (a[campo] < b[campo] ? -1 : a[campo] > b[campo] ? 1 : 0) * (regra.direction === 'asc' ? 1 : -1));
  }
  const inicio = pedido.page * pedido.size;
  return of({ content: linhas.slice(inicio, inicio + pedido.size), page: { totalElements: linhas.length } }).pipe(delay(350));
}

/** Template de referência do padrão de listagem (docs/design-system/components/list-page.md). */
@Component({
  selector: 'ces-catalog-list-page',
  standalone: true,
  imports: [
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesListShortcutsDirective,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo.set(true)">
      <ces-page-header
        title="Veículos"
        icon="pi pi-car"
        description="Abre direto nos dados. Filtros recolhidos com resumo, busca única no servidor e cadastro em drawer."
        helpLabel="Ajuda sobre a listagem"
        [favoritable]="false"
        [breadcrumb]="[{ label: 'Catálogo' }, { label: 'Listagem' }]"
      >
        <ces-status status tone="info" icon="pi-info-circle">Servidor simulado</ces-status>
        <ces-button actions icon="pi pi-plus" label="Novo veículo" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo.set(true)"></ces-button>
      </ces-page-header>

      <ces-filter-panel
        #painel
        storageKey="catalogo-listagem"
        [activeCount]="lista.activeCount()"
        [summary]="lista.summary()"
        [loading]="resultado.loading()"
        (applied)="lista.apply()"
        (cleared)="lista.reset()"
        (chipRemoved)="lista.remove($any($event))"
      >
        <div class="ces-filter-grid">
          <ces-multiselect label="Base" [options]="bases" [(value)]="lista.filters.bases" placeholder="Todas"></ces-multiselect>
          <ces-select label="Situação" [options]="situacoes" [(value)]="lista.filters.situacao" [clearable]="true" placeholder="Todas"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Veículos"
        mode="server"
        storageKey="catalogo-listagem"
        exportFileName="catalogo-veiculos"
        searchPlaceholder="Placa"
        rowActionLabel="Abrir ficha"
        emptyActionLabel="Novo veículo"
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os veículos' : 'Nenhum veículo cadastrado'"
        [stateDescription]="resultado.error() ?? undefined"
        [filtered]="lista.filtered()"
        [pageSizeOptions]="[10, 25, 50, 100]"
        [selectable]="true"
        [selectionLimit]="100"
        [(selection)]="selecionados"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="novo.set(true)"
        (rowActivate)="novo.set(true)"
      >
        <ces-button selection-actions size="sm" variant="outline" icon="pi pi-wrench" label="Enviar para manutenção" (pressed)="selecionados.set([])"></ces-button>
        <ng-template cesCell="placa" let-row>
          <span class="ces-cell-strong">{{ row.placa }}</span>
          <span class="ces-cell-muted">{{ nomeBase(row.base) }}</span>
        </ng-template>
        <ng-template cesCell="situacao" let-row>
          <ces-status [tone]="row.situacao === 'OPERANDO' ? 'success' : row.situacao === 'PARADO' ? 'danger' : 'warning'">
            {{ nomeSituacao(row.situacao) }}
          </ces-status>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="novo" title="Novo veículo" description="Exemplo de cadastro em drawer." size="md">
      <div class="ces-form-grid">
        <ces-text-input label="Placa" [required]="true"></ces-text-input>
        <ces-select label="Base" [options]="bases" [required]="true"></ces-select>
      </div>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="novo.set(false)"></ces-button>
        <ces-button label="Cadastrar veículo" (pressed)="novo.set(false)"></ces-button>
      </div>
    </ces-form-drawer>
  `,
})
export class DesignSystemCatalogListPageComponent {
  protected readonly bases = BASES;
  protected readonly situacoes = SITUACOES;
  protected readonly novo = signal(false);
  protected readonly selecionados = signal<readonly Registro[]>([]);

  protected readonly lista = cesListState({
    storageKey: 'catalogo-listagem',
    url: false,
    filters: {
      bases: cesFilter.list<string>([], { label: 'Base', itemLabel: (v) => this.nomeBase(v), countLabel: (n) => `${n} selecionadas` }),
      situacao: cesFilter.value<Registro['situacao'] | null>(null, { label: 'Situação', options: () => SITUACOES }),
    },
    sort: [{ key: 'placa', direction: 'asc' }],
  });
  protected readonly resultado = this.lista.results<Registro>(servidor);

  protected readonly colunas: CesDataTableColumn<Registro>[] = [
    { key: 'placa', header: 'Placa', type: 'custom', priority: 1, sortField: 'placa', value: (r) => r.placa },
    { key: 'situacao', header: 'Situação', type: 'custom', priority: 1, sortField: 'situacao', value: (r) => r.situacao, display: (r) => this.nomeSituacao(r.situacao) },
    { key: 'km', header: 'KM atual', type: 'integer', sortField: 'km' },
  ];

  protected nomeBase(valor: string): string {
    return BASES.find((b) => b.value === valor)?.label ?? valor;
  }

  protected nomeSituacao(valor: string): string {
    return SITUACOES.find((s) => s.value === valor)?.label ?? valor;
  }
}
