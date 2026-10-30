import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  CesAutocompleteComponent,
  CesButtonComponent,
  CesCellDirective,
  CesRowDetailDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesDialogShellComponent,
  CesFilterPanelComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  CesTone,
  cesFilter,
  cesListState
} from '@ces/ui';

import { RemoteLookup as BuscaMateriais } from '../../../core/lookup/store/remote-lookup';
import { LookupStore } from '../../../core/lookup/store/lookup.store';
import {
  EstoqueResponse,
  MaterialResponse,
  MovimentacaoOperacaoResponse,
  MovimentacaoOperacaoSummaryResponse,
  TipoMovimentacao
} from '../../../core/models/almoxarifado.models';
import { BaseOperacionalResponse } from '../../../core/models/rh.models';
import { generateReferenceOptions, getErrorMessage } from '../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { RhService } from '../../../core/services/rh.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { AnalisarComCopilotoComponent } from '../../../shared/components/analisar-com-copiloto/analisar-com-copiloto.component';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { SaldoEstoqueDialogComponent } from '../../../shared/components/saldo-estoque-dialog/saldo-estoque-dialog.component';
import { SeriesGradeComponent, SerieGradeItem } from '../../../shared/components/series-grade/series-grade.component';
import { ItemMovimentacaoAgrupado, agruparItensPorMaterial, rotuloCondicao, tomCondicao } from '../../../shared/utils/movimentacao.utils';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { MovimentacoesImportacaoComponent } from './movimentacoes-importacao.component';

export interface OperacaoCardOption {
  tipo: TipoMovimentacao | 'DEVOLUCAO_COMERCIAL' | 'IMPORTACAO';
  titulo: string;
  subtitulo: string;
  tag: string;
  descricao: string;
  rota: string;
  icone: string;
  badgeTone: CesTone;
}

const TIPOS: CesSelectOption<TipoMovimentacao | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Entrada avulsa', value: 'ENTRADA_AVULSA' },
  { label: 'Baixa avulsa', value: 'BAIXA_AVULSA' },
  { label: 'Transferência', value: 'TRANSFERENCIA' },
  { label: 'Entrega', value: 'ENTREGA' },
  { label: 'Devolução', value: 'DEVOLUCAO' },
  { label: 'Estorno', value: 'ESTORNO' },
  { label: 'Retirada', value: 'RETIRADA' },
  { label: 'Aplicação', value: 'APLICACAO' }
];
const TOM_TIPO: Record<string, CesTone> = {
  ENTRADA: 'success',
  ENTRADA_AVULSA: 'success',
  SAIDA: 'danger',
  BAIXA_AVULSA: 'danger',
  TRANSFERENCIA: 'brand',
  ENTREGA: 'info',
  DEVOLUCAO: 'brand',
  ESTORNO: 'warning',
  RETIRADA: 'info',
  APLICACAO: 'neutral'
};
const OPERACOES: OperacaoCardOption[] = [
  {
    tipo: 'TRANSFERENCIA',
    titulo: 'Transferência entre estoques',
    subtitulo: 'Movimentação',
    tag: 'Transferência',
    descricao: 'Remanejamento de materiais entre bases ou almoxarifados, com conferência física e validação de saldos.',
    rota: '/almoxarifado/transferencia-lote',
    icone: 'pi pi-arrow-right-arrow-left',
    badgeTone: 'brand'
  },
  {
    tipo: 'ENTRADA_AVULSA',
    titulo: 'Entrada avulsa',
    subtitulo: 'Ajuste positivo',
    tag: 'Entrada',
    descricao: 'Incorporação extraordinária por ajuste de inventário, devolução de equipe, suprimento emergencial ou bonificação.',
    rota: '/almoxarifado/entrada-avulsa',
    icone: 'pi pi-plus-circle',
    badgeTone: 'success'
  },
  {
    tipo: 'BAIXA_AVULSA',
    titulo: 'Baixa avulsa',
    subtitulo: 'Ajuste negativo',
    tag: 'Baixa / descarte',
    descricao: 'Desincorporação definitiva por sucateamento, perda, avaria irreparável ou correção de inventário.',
    rota: '/almoxarifado/baixa-avulsa',
    icone: 'pi pi-trash',
    badgeTone: 'danger'
  },
  {
    tipo: 'DEVOLUCAO_COMERCIAL',
    titulo: 'Devolução comercial',
    subtitulo: 'Processo comercial',
    tag: '222 — Comercial',
    descricao: 'Devolução do estoque de campo de uma equipe comercial para o almoxarifado físico (novo ou sucata).',
    rota: '/almoxarifado/devolucao-comercial',
    icone: 'pi pi-undo',
    badgeTone: 'brand'
  },
  {
    tipo: 'IMPORTACAO',
    titulo: 'Importação em lote (planilha)',
    subtitulo: 'Entrada / saída / transferência',
    tag: 'CSV',
    descricao: 'Movimentações em lote por planilha CSV, com vários números de série na mesma linha e conferência prévia.',
    rota: 'IMPORT_MODAL',
    icone: 'pi pi-file-import',
    badgeTone: 'info'
  }
];

@Component({
  selector: 'app-movimentacoes-estoque',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    CesAutocompleteComponent,
    CesButtonComponent,
    CesCellDirective,
    CesRowDetailDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesDialogShellComponent,
    CesFilterPanelComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AnalisarComCopilotoComponent,
    AuditDialogComponent,
    SaldoEstoqueDialogComponent,
    SeriesGradeComponent,
    AjudaListagemComponent,
    MovimentacoesImportacaoComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novaOperacaoAberta.set(true)">
      <ces-page-header
        title="Movimentações de estoque"
        icon="pi pi-arrows-h"
        [breadcrumb]="[{ label: 'Almoxarifado' }, { label: 'Movimentações de estoque' }]"
        description="Entradas, baixas, transferências, entregas e devoluções, agrupadas por operação. Clique na linha para ver os itens."
        helpLabel="Ajuda sobre movimentações de estoque"
        (help)="ajuda.show()"
      >
        <app-analisar-com-copiloto actions origem="Movimentações de estoque" tipo="movimentacoes" size="md" [filtros]="filtrosCopiloto()"
          pergunta="Quais materiais tiveram mais saídas neste mês? Compare com o mês anterior." />
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacaoAberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Nova movimentação" [keyShortcut]="atalhos.newShortcut()" (pressed)="novaOperacaoAberta.set(true)"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="almox-movimentacoes" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Estoque" [options]="estoqueOptions()" [(value)]="lista.filters.idEstoque" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-autocomplete label="Material" placeholder="Digite para buscar" [options]="materiaisFiltro.items()" [ngModel]="lista.filters.idMaterial()"
            (ngModelChange)="lista.filters.idMaterial.set($event)" [loading]="materiaisFiltro.loading()" [status]="materiaisFiltro.status()" [minLength]="2" [clearable]="true"
            (search)="materiaisFiltro.search($event)" (loadMore)="materiaisFiltro.loadNext()" (retry)="materiaisFiltro.retry()"></ces-autocomplete>
          <ces-select label="Base de origem" [options]="baseOptions()" [(value)]="lista.filters.idBaseOrigem" [clearable]="true" [filter]="true" placeholder="Todas"
            [loading]="bases.loading()" (opened)="bases.open()" (search)="bases.search($event)"></ces-select>
          <ces-select label="Base de destino" [options]="baseOptions()" [(value)]="lista.filters.idBaseDestino" [clearable]="true" [filter]="true" placeholder="Todas"
            [loading]="bases.loading()" (opened)="bases.open()" (search)="bases.search($event)"></ces-select>
          <ces-select label="Tipo" [options]="tipos" [(value)]="lista.filters.tipo"></ces-select>
          <ces-select label="Referência" [options]="referencias" [(value)]="lista.filters.referencia" [clearable]="true" [filter]="true" placeholder="Mês/ano"></ces-select>
          <ces-text-input label="Número de série" [(value)]="lista.filters.numSerie" hint="Não fica salvo no endereço da página."></ces-text-input>
          <ces-datepicker label="A partir de" [value]="dataLocal(lista.filters.periodo().from)" [clearable]="true" (valueChange)="periodo('from', $event)"></ces-datepicker>
          <ces-datepicker label="Até" [value]="dataLocal(lista.filters.periodo().to)" [clearable]="true" (valueChange)="periodo('to', $event)"></ces-datepicker>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Operações"
        mode="server"
        storageKey="almox-movimentacoes"
        searchPlaceholder="Operação, material ou número de série"
        rowActionLabel="Ver itens"
        rowActionIcon="pi pi-eye"
        emptyActionLabel="Nova movimentação"
        noResultsTitle="Nenhuma movimentação encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as movimentações' : 'Nenhuma movimentação registrada'"
        [stateDescription]="resultado.error() ?? 'Entradas, baixas e transferências aparecem aqui.'"
        [filtered]="lista.filtered()"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="novaOperacaoAberta.set(true)"
        (rowActivate)="verDetalhes($event)"
      >
        <ng-template cesCell="operacao" let-mov><span class="ces-cell-strong">#{{ mov.idReferenciaOperacao?.substring(0, 8) }}</span></ng-template>
        <ng-template cesCell="tipo" let-mov><ces-status [tone]="tipoTone(mov.tipo)">{{ tipoLabel(mov.tipo) }}</ces-status></ng-template>
        <ng-template cesRowActions let-mov>
          <span class="ces-table-actions">
            <ces-icon-button icon="pi pi-print" [label]="'Imprimir relatório da operação #' + mov.idReferenciaOperacao?.substring(0, 8)" size="icon-sm" (pressed)="imprimir(mov)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações da operação #' + mov.idReferenciaOperacao?.substring(0, 8)" size="icon-sm" (pressed)="showLogs(mov)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-dialog-shell title="Nova operação de estoque" size="lg" [visible]="novaOperacaoAberta()" (visibleChange)="novaOperacaoAberta.set($event)">
      <div class="ces-card-grid">
        @for (op of operacoes; track op.tipo) {
          <button type="button" class="ces-choice-card" (click)="navigateToOperation(op.rota)">
            <span class="ces-choice-card__head">
              <i [class]="op.icone" aria-hidden="true"></i>
              <ces-status [tone]="op.badgeTone">{{ op.tag }}</ces-status>
            </span>
            <span class="ces-cell-muted">{{ op.subtitulo }}</span>
            <strong>{{ op.titulo }}</strong>
            <span class="ces-cell-muted">{{ op.descricao }}</span>
          </button>
        }
      </div>
      <div dialog-footer>
        <ces-button label="Fechar" variant="secondary" size="sm" (pressed)="novaOperacaoAberta.set(false)"></ces-button>
      </div>
    </ces-dialog-shell>

    <ces-dialog-shell title="Prévia da movimentação" size="lg" [visible]="detalheVisible()" (visibleChange)="detalheVisible.set($event)"
      [loading]="loadingDetalhe()" loadingLabel="Buscando os itens da operação…">
      @if (operacaoSelecionada(); as op) {
        <dl class="ces-details-grid">
          <div><dt>Operação</dt><dd class="ces-cell-strong">#{{ op.idReferenciaOperacao.slice(0, 8) }}</dd></div>
          <div><dt>Data</dt><dd>{{ op.dataHora | date: 'dd/MM/yyyy HH:mm:ss' }}</dd></div>
          <div><dt>Tipo</dt><dd><ces-status [tone]="tipoTone(op.tipo)">{{ tipoLabel(op.tipo) }}</ces-status></dd></div>
          <div><dt>Operador</dt><dd>{{ op.operadorNome || 'Não identificado' }}</dd></div>
          <div>
            <dt>Origem</dt>
            <dd>
              {{ op.estoqueOrigem?.nomeIdentificador || '—' }}
              @if (op.estoqueOrigem; as origem) {
                <ces-icon-button icon="pi pi-search" [label]="'Ver saldo de ' + origem.nomeIdentificador" size="icon-sm" (pressed)="abrirSaldoEstoque(origem.id, origem.nomeIdentificador)"></ces-icon-button>
              }
            </dd>
          </div>
          <div>
            <dt>Destino</dt>
            <dd>
              {{ op.estoqueDestino?.nomeIdentificador || '—' }}
              @if (op.estoqueDestino; as destino) {
                <ces-icon-button icon="pi pi-search" [label]="'Ver saldo de ' + destino.nomeIdentificador" size="icon-sm" (pressed)="abrirSaldoEstoque(destino.id, destino.nomeIdentificador)"></ces-icon-button>
              }
            </dd>
          </div>
        </dl>
        <ces-data-table [caption]="itensAgrupados().length + (itensAgrupados().length === 1 ? ' material' : ' materiais') + ' · total ' + (totalSelecionado() | number: '1.0-3')"
          searchPlaceholder="Buscar por código, material ou série" [searchable]="itensAgrupados().length > 8"
          [columns]="colunasItens" [rows]="itensAgrupados()" [rowKey]="chaveItem" [rowExpandable]="temSeries" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="descricao" let-g>
            <span class="ces-cell-strong">{{ g.material?.descricao }}</span>
            @if (g.series.length) {
              <ces-status tone="brand" icon="pi pi-barcode">{{ g.series.length }} {{ g.series.length === 1 ? 'série' : 'séries' }}</ces-status>
            }
          </ng-template>
          <ng-template cesCell="condicao" let-g><ces-status [tone]="tomCondicao(g.condicao)">{{ rotuloCondicao(g.condicao) }}</ces-status></ng-template>
          <ng-template cesRowDetail let-g>
            <app-series-grade [series]="g.grade" [rotulo]="'Números de série de ' + g.material?.descricao" />
          </ng-template>
        </ces-data-table>
      }
      <div dialog-footer class="ces-actions-row">
        @if (operacaoSelecionada(); as op) {
          <ces-button variant="link" icon="pi pi-external-link" label="Abrir relatório completo" (pressed)="abrirRelatorio(op.idReferenciaOperacao)"></ces-button>
        }
        <ces-button label="Fechar" variant="secondary" size="sm" (pressed)="detalheVisible.set(false)"></ces-button>
      </div>
    </ces-dialog-shell>

    <app-movimentacoes-importacao [(visible)]="importacaoAberta" [estoques]="estoques()" [materiais]="materiais()" (processado)="lista.reload()"></app-movimentacoes-importacao>
    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-saldo-estoque-dialog #saldo></app-saldo-estoque-dialog>
    <app-ajuda-listagem #ajuda titulo="Movimentações de estoque" artigoPlural="as" entidadePlural="movimentações" busca="operação, material ou número de série"
      rotuloNovo="Nova movimentação" [importacao]="true">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-bolt"></i> Operações</h4>
        <p class="help-text-desc">"Nova movimentação" leva à transferência entre estoques, entrada avulsa, baixa avulsa, devolução comercial ou à importação em lote.</p>
      </div>
    </app-ajuda-listagem>
  `,
  styles: `
    .ces-card-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
      gap: var(--ces-space-3, 0.75rem);
    }
    .ces-choice-card {
      display: flex;
      flex-direction: column;
      gap: var(--ces-space-1, 0.25rem);
      padding: var(--ces-space-4, 1rem);
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-lg, 0.75rem);
      background: var(--ces-surface);
      color: var(--ces-foreground);
      text-align: left;
      cursor: pointer;
    }
    .ces-choice-card:hover,
    .ces-choice-card:focus-visible {
      border-color: var(--ces-primary, currentColor);
    }
    .ces-choice-card__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: var(--ces-space-2, 0.5rem);
      font-size: 1.25rem;
    }
    .ces-details-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
      gap: var(--ces-space-3, 0.75rem);
      margin: 0 0 var(--ces-space-4, 1rem);
    }
    .ces-details-grid dt {
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption, 0.75rem);
    }
    .ces-details-grid dd {
      display: flex;
      align-items: center;
      gap: var(--ces-space-1, 0.25rem);
      margin: 0;
    }
  `
})
export class MovimentacoesEstoqueComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly rh = inject(RhService);
  private readonly router = inject(Router);
  private readonly lookupStore = inject(LookupStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');
  private readonly saldo = viewChild.required<SaldoEstoqueDialogComponent>('saldo');

  protected readonly operacoes = OPERACOES;
  protected readonly tipos = TIPOS;
  protected readonly referencias: CesSelectOption<string>[] = generateReferenceOptions(24);
  protected readonly dataLocal = dataLocal;
  protected readonly novaOperacaoAberta = signal(false);
  protected readonly importacaoAberta = signal(false);

  protected readonly estoques = signal<EstoqueResponse[]>([]);
  protected readonly materiais = signal<MaterialResponse[]>([]);
  protected readonly estoqueOptions = computed<CesSelectOption<string>[]>(() => this.estoques().map((e) => ({ label: e.nomeIdentificador, value: e.id })));
  protected readonly bases = new RemoteLookup<BaseOperacionalResponse>(
    {
      identify: (b) => b.id,
      selectedKeys: () => [this.lista.filters.idBaseOrigem(), this.lista.filters.idBaseDestino()].filter((id): id is string => !!id),
      load: ({ query, page, size }) => this.rh.listarBasesOperacionais({ ativo: true, buscaGlobal: query, page, size, sort: 'nome,asc' })
    },
    this.destroyRef
  );
  protected readonly baseOptions = computed<CesSelectOption<string>[]>(() => this.bases.items().map((b) => ({ label: b.nome, value: b.id })));
  protected readonly materiaisFiltro: BuscaMateriais<string> = new BuscaMateriais<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchMaterials({ query, cursor }),
    selectedValues: () => [this.lista.filters.idMaterial()].filter((id): id is string => !!id),
    destroyRef: this.destroyRef
  });

  protected readonly lista = cesListState({
    storageKey: 'almox-movimentacoes',
    filters: {
      idEstoque: cesFilter.value<string | null>(null, {
        label: 'Estoque',
        param: 'estoque',
        format: (id): string | null => (id ? (this.estoques().find((e) => e.id === id)?.nomeIdentificador ?? 'selecionado') : null)
      }),
      idMaterial: cesFilter.value<string | null>(null, {
        label: 'Material',
        param: 'material',
        format: (id): string | null => (id ? (this.materiaisFiltro.items().find((m) => m.value === id)?.label ?? 'material selecionado') : null)
      }),
      idBaseOrigem: cesFilter.value<string | null>(null, { label: 'Base de origem', param: 'baseOrigem', format: (id): string | null => (id ? this.nomeBase(id) : null) }),
      idBaseDestino: cesFilter.value<string | null>(null, { label: 'Base de destino', param: 'baseDestino', format: (id): string | null => (id ? this.nomeBase(id) : null) }),
      tipo: cesFilter.value<TipoMovimentacao | null>(null, { label: 'Tipo', options: () => TIPOS }),
      referencia: cesFilter.value<string | null>(null, { label: 'Referência', param: 'ref', type: 'string' }),
      // Nº de série é dado sensível: fica fora da URL.
      numSerie: cesFilter.value<string>('', { label: 'Nº de série', url: false }),
      periodo: cesFilter.range({ from: null, to: null }, { label: 'Período' })
    },
    sort: [{ key: 'data', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<MovimentacaoOperacaoSummaryResponse>(
    (pedido) =>
      this.almoxarifado.listarMovimentacoes({
        ...this.filtro(),
        page: pedido.page,
        size: pedido.size,
        sort: pedido.sort.map((r) => `${r.key},${r.direction}`)[0] ?? 'dataHora,desc'
      } as never),
    { errorMessage: getErrorMessage }
  );
  protected readonly filtrosCopiloto = computed(() => {
    const f = this.lista.applied();
    return { estoque: f.idEstoque, tipo: f.tipo, inicio: dataLocal(f.periodo.from)?.toISOString(), fim: dataLocal(f.periodo.to)?.toISOString() };
  });

  protected readonly colunas: CesDataTableColumn<MovimentacaoOperacaoSummaryResponse>[] = [
    { key: 'operacao', header: 'Operação', type: 'custom', priority: 1, sortField: 'idReferenciaOperacao', value: (m) => m.idReferenciaOperacao },
    {
      key: 'data',
      header: 'Data',
      type: 'date',
      priority: 1,
      sortField: 'dataHora',
      value: (m) => m.dataHora,
      display: (m) => new Date(m.dataHora).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
    },
    { key: 'tipo', header: 'Tipo', type: 'custom', priority: 1, sortField: 'tipo', value: (m) => this.tipoLabel(m.tipo) },
    { key: 'origem', header: 'Origem', value: (m) => m.estoqueOrigem?.nomeIdentificador, display: (m) => m.estoqueOrigem?.nomeIdentificador ?? '—' },
    { key: 'destino', header: 'Destino', value: (m) => m.estoqueDestino?.nomeIdentificador, display: (m) => m.estoqueDestino?.nomeIdentificador ?? '—' },
    { key: 'operador', header: 'Operador', sortField: 'operadorNome', value: (m) => m.operadorNome, display: (m) => m.operadorNome || '—' }
  ];
  protected readonly rotulo = (m: MovimentacaoOperacaoSummaryResponse) => `Operação #${m.idReferenciaOperacao?.substring(0, 8)}`;

  // Prévia da operação
  protected readonly detalheVisible = signal(false);
  protected readonly loadingDetalhe = signal(false);
  protected readonly operacaoSelecionada = signal<MovimentacaoOperacaoResponse | null>(null);
  protected readonly totalSelecionado = computed(() => (this.operacaoSelecionada()?.itens ?? []).reduce((total, item) => total + (item.quantidade || 0), 0));
  protected readonly rotuloCondicao = rotuloCondicao;
  protected readonly tomCondicao = tomCondicao;
  /** Um material por linha; os seriados expandem com as séries (o backend manda uma linha por série). */
  protected readonly itensAgrupados = computed<(ItemMovimentacaoAgrupado & { grade: SerieGradeItem[] })[]>(() =>
    agruparItensPorMaterial(this.operacaoSelecionada()?.itens).map((g) => ({ ...g, grade: g.series.map((numSerie) => ({ numSerie })) }))
  );
  protected readonly chaveItem = (g: ItemMovimentacaoAgrupado) => g.chave;
  protected readonly temSeries = (g: ItemMovimentacaoAgrupado) => g.series.length > 0;
  protected readonly colunasItens: CesDataTableColumn<ItemMovimentacaoAgrupado>[] = [
    { key: 'codigo', header: 'Código', priority: 1, value: (g) => g.material?.codigo },
    {
      key: 'descricao',
      header: 'Material',
      type: 'custom',
      priority: 1,
      searchable: true,
      value: (g) => g.material?.descricao,
      display: (g) => [g.material?.descricao, ...g.series].join(' ')
    },
    { key: 'condicao', header: 'Condição', type: 'custom', value: (g) => rotuloCondicao(g.condicao) },
    { key: 'quantidade', header: 'Quantidade', type: 'number', value: (g) => g.quantidade }
  ];

  constructor() {
    this.almoxarifado.listarEstoquesAtivos().subscribe({ next: (r) => this.estoques.set(r?.content ?? []), error: () => undefined });
    this.almoxarifado.listarMateriais({ page: 0, size: 1000, ativo: true }).subscribe({ next: (r) => this.materiais.set(r?.content ?? []), error: () => undefined });
  }

  private filtro(): Record<string, unknown> {
    const { filters: f, query } = this.lista.request();
    const params: Record<string, unknown> = {};
    if (f.idEstoque) params['idEstoque'] = f.idEstoque;
    if (f.idMaterial) params['idMaterial'] = f.idMaterial;
    if (f.idBaseOrigem) params['idBaseOrigem'] = f.idBaseOrigem;
    if (f.idBaseDestino) params['idBaseDestino'] = f.idBaseDestino;
    if (f.tipo) params['tipo'] = f.tipo;
    if (f.numSerie.trim()) params['numSerie'] = f.numSerie.trim();
    if (f.referencia) params['referencia'] = f.referencia;
    if (f.periodo.from) params['dataInicio'] = dataLocal(f.periodo.from)!.toISOString();
    if (f.periodo.to) params['dataFim'] = dataLocal(f.periodo.to)!.toISOString();
    if (query) params['buscaGlobal'] = query;
    return params;
  }

  private nomeBase(id: string): string {
    return this.baseOptions().find((o) => o.value === id)?.label ?? 'selecionada';
  }

  protected periodo(ponta: 'from' | 'to', data: Date | null): void {
    this.lista.filters.periodo.update((atual) => ({ ...atual, [ponta]: dataIso(data) }));
  }

  protected tipoTone(tipo?: string | null): CesTone {
    return (tipo && TOM_TIPO[tipo]) || 'neutral';
  }

  protected tipoLabel(tipo?: string | null): string {
    return TIPOS.find((t) => t.value === tipo)?.label ?? (tipo ? String(tipo).replace('_', ' ') : '—');
  }

  protected verDetalhes(mov: MovimentacaoOperacaoSummaryResponse): void {
    this.operacaoSelecionada.set(null);
    this.loadingDetalhe.set(true);
    this.detalheVisible.set(true);
    this.almoxarifado.buscarOperacaoPorIdReferencia(mov.idReferenciaOperacao).subscribe({
      next: (res) => {
        this.operacaoSelecionada.set(res);
        this.loadingDetalhe.set(false);
      },
      error: () => {
        this.loadingDetalhe.set(false);
        this.detalheVisible.set(false);
      }
    });
  }

  protected navigateToOperation(rota: string): void {
    this.novaOperacaoAberta.set(false);
    if (rota === 'IMPORT_MODAL') {
      this.importacaoAberta.set(true);
      return;
    }
    void this.router.navigate([rota]);
  }

  protected imprimir(mov: MovimentacaoOperacaoSummaryResponse): void {
    const url = this.router.serializeUrl(this.router.createUrlTree(['/almoxarifado/movimentacoes', mov.idReferenciaOperacao], { queryParams: { mode: 'print' } }));
    window.open(url, '_blank', 'noopener');
  }

  protected abrirRelatorio(idReferencia: string): void {
    window.open(this.router.serializeUrl(this.router.createUrlTree(['/almoxarifado/movimentacoes', idReferencia])), '_blank', 'noopener');
  }

  protected abrirSaldoEstoque(estoqueId: string, estoqueNome: string): void {
    this.saldo().show(estoqueId, estoqueNome);
  }

  protected showLogs(mov: MovimentacaoOperacaoSummaryResponse): void {
    this.auditoria().show(mov.idReferenciaOperacao, 'movimentacoes_estoque');
  }
}
