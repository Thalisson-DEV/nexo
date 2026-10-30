import { HttpResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CesAutocompleteComponent,
  CesButtonComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterDefs,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesNumberInputComponent,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  CesTone,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup as BuscaColaboradores } from '../../../core/lookup/store/remote-lookup';
import { ReferenciaOpcao, SolicitacaoFinanceira, SolicitacaoFinanceiraFilter, StatusSolicitacao, TipoSolicitacao } from '../../../core/models/financeiro.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AuthService } from '../../../core/services/auth.service';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { RhService } from '../../../core/services/rh.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { CAMPOS_DATA_HORA, CAMPOS_DATA_LOCAL, SolicitacaoFiltrosView, normalizarFiltrosSolicitacao } from './solicitacao-financeira-filtros';
import {
  ATIVO_OPTIONS,
  BOOLEANO_OPTIONS,
  FORMA_PAGAMENTO_OPTIONS,
  LOCAL_AQUISICAO_OPTIONS,
  MOTIVO_SEM_COMPRA_OPTIONS,
  MOTIVO_SERVICO_OPTIONS,
  NATUREZA_SERVICO_OPTIONS,
  PRIORIDADE_OPTIONS,
  REGIME_VEICULO_OPTIONS,
  STATUS_OPTIONS,
  TIPO_DESPESA_OPTIONS
} from './solicitacao-financeira-opcoes';
import { buscaGlobalParaConsulta, fornecedorEhNaoCadastrado, fornecedorLabel, fornecedorRazaoSocial, nomeSolicitante } from './solicitacao-financeira.utils';

type Modo = 'minhas' | 'aprovacao' | 'todas';
type LookupKey = 'formularios' | 'bases' | 'processos' | 'obras' | 'fornecedores' | 'veiculos' | 'usuarios' | 'compras';

interface Campo {
  key: string;
  label: string;
  avancado?: boolean;
  somentePagamento?: boolean;
}
interface CampoLookup extends Campo {
  lookup: LookupKey;
}
interface CampoEnum extends Campo {
  options: readonly CesSelectOption<string>[];
}
interface CampoNumero extends Campo {
  modo: 'currency' | 'decimal';
}

const comTodos = <T,>(opcoes: readonly CesSelectOption<T>[], todos = 'Todos'): CesSelectOption<T | null>[] => [{ label: todos, value: null }, ...opcoes];
const SIM_NAO = comTodos(BOOLEANO_OPTIONS);
const ATIVOS = comTodos(ATIVO_OPTIONS);
const STATUS = comTodos(STATUS_OPTIONS);
const TIPOS: CesSelectOption<TipoSolicitacao | null>[] = [
  { label: 'Compras e pagamentos', value: null },
  { label: 'Compras', value: 'COMPRA' },
  { label: 'Pagamentos', value: 'PAGAMENTO' }
];
const TOM_STATUS: Record<StatusSolicitacao, CesTone> = { RASCUNHO: 'neutral', SOLICITADO: 'warning', APROVADO: 'success', REJEITADO: 'danger', CANCELADO: 'neutral' };

const LOOKUPS: CampoLookup[] = [
  { key: 'formularioId', label: 'Formulário', lookup: 'formularios' },
  { key: 'fornecedorId', label: 'Fornecedor cadastrado', lookup: 'fornecedores' },
  { key: 'processoId', label: 'Processo / centro de custo', lookup: 'processos' },
  { key: 'obraId', label: 'Obra / contrato', lookup: 'obras' },
  { key: 'baseId', label: 'Base', lookup: 'bases' },
  { key: 'usuarioSolicitanteId', label: 'Usuário solicitante', lookup: 'usuarios', avancado: true },
  { key: 'usuarioDecisaoId', label: 'Usuário da decisão', lookup: 'usuarios', avancado: true },
  { key: 'usuarioCancelamentoId', label: 'Usuário do cancelamento', lookup: 'usuarios', avancado: true },
  { key: 'veiculoId', label: 'Veículo / placa', lookup: 'veiculos', avancado: true },
  { key: 'solicitacaoCompraId', label: 'Compra vinculada', lookup: 'compras', avancado: true, somentePagamento: true }
];
const ENUMS: CampoEnum[] = [
  { key: 'prioridade', label: 'Prioridade', options: PRIORIDADE_OPTIONS },
  { key: 'localAquisicao', label: 'Local de aquisição', options: LOCAL_AQUISICAO_OPTIONS, avancado: true },
  { key: 'formaPagamento', label: 'Forma de pagamento', options: FORMA_PAGAMENTO_OPTIONS, avancado: true },
  { key: 'regimeVeiculo', label: 'Regime do veículo', options: REGIME_VEICULO_OPTIONS, avancado: true },
  { key: 'naturezaServico', label: 'Natureza do serviço', options: NATUREZA_SERVICO_OPTIONS, avancado: true },
  { key: 'motivoServico', label: 'Motivo do serviço', options: MOTIVO_SERVICO_OPTIONS, avancado: true },
  { key: 'tipoDespesa', label: 'Tipo de despesa', options: TIPO_DESPESA_OPTIONS, avancado: true },
  { key: 'motivoSemCompra', label: 'Motivo de não haver compra', options: MOTIVO_SEM_COMPRA_OPTIONS, avancado: true, somentePagamento: true }
];
const NUMEROS: CampoNumero[] = [
  { key: 'valorMinimo', label: 'Valor mínimo', modo: 'currency' },
  { key: 'valorMaximo', label: 'Valor máximo', modo: 'currency' },
  { key: 'quantidadeMinima', label: 'Quantidade mínima', modo: 'decimal', avancado: true },
  { key: 'quantidadeMaxima', label: 'Quantidade máxima', modo: 'decimal', avancado: true },
  { key: 'parcelasMinimas', label: 'Parcelas mínimas', modo: 'decimal', avancado: true },
  { key: 'parcelasMaximas', label: 'Parcelas máximas', modo: 'decimal', avancado: true },
  { key: 'valorAprovadoMinimo', label: 'Valor aprovado mínimo', modo: 'currency', avancado: true },
  { key: 'valorAprovadoMaximo', label: 'Valor aprovado máximo', modo: 'currency', avancado: true }
];
const BOOLEANOS: Campo[] = [
  { key: 'temNf', label: 'Possui nota fiscal', avancado: true },
  { key: 'possuiDocumentos', label: 'Possui documentos ativos', avancado: true },
  { key: 'possuiCompraPrevia', label: 'Possui compra prévia', avancado: true, somentePagamento: true }
];
const DATAS: Campo[] = [
  { key: 'dataCriacaoInicio', label: 'Criada a partir de' },
  { key: 'dataCriacaoFim', label: 'Criada até' },
  { key: 'vencimentoInicio', label: 'Vencimento a partir de' },
  { key: 'vencimentoFim', label: 'Vencimento até' },
  { key: 'dataSubmissaoInicio', label: 'Submissão a partir de', avancado: true },
  { key: 'dataSubmissaoFim', label: 'Submissão até', avancado: true },
  { key: 'dataDecisaoInicio', label: 'Decisão a partir de', avancado: true },
  { key: 'dataDecisaoFim', label: 'Decisão até', avancado: true },
  { key: 'dataCancelamentoInicio', label: 'Cancelamento a partir de', avancado: true },
  { key: 'dataCancelamentoFim', label: 'Cancelamento até', avancado: true },
  { key: 'dataUltimaAlteracaoInicio', label: 'Alteração a partir de', avancado: true },
  { key: 'dataUltimaAlteracaoFim', label: 'Alteração até', avancado: true },
  { key: 'dataDesativacaoInicio', label: 'Desativação a partir de', avancado: true },
  { key: 'dataDesativacaoFim', label: 'Desativação até', avancado: true }
];
const ENDPOINTS: Record<LookupKey, { url: string; utilizaveis?: boolean }> = {
  formularios: { url: '/v1/financeiro/formularios' },
  bases: { url: '/v1/bases-operacionais' },
  processos: { url: '/v1/processos-negocio' },
  obras: { url: '/v1/contratos' },
  fornecedores: { url: '/v1/financeiro/fornecedores', utilizaveis: true },
  veiculos: { url: '/v1/veiculos' },
  usuarios: { url: '/v1/iam/usuarios' },
  compras: { url: '/v1/financeiro/solicitacoes-compra' }
};
const FORMATOS: CesSelectOption<'resumo' | 'detalhado'>[] = [
  { label: 'Resumido (dados essenciais)', value: 'resumo' },
  { label: 'Detalhado (campos do formulário, auditoria e documentos)', value: 'detalhado' }
];

@Component({
  selector: 'app-solicitacoes-financeiras',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    CesAutocompleteComponent,
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesNumberInputComponent,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="podeCriar()" (newRequested)="novo()">
      <ces-page-header
        [title]="titulo()"
        icon="pi pi-wallet"
        [breadcrumb]="breadcrumb()"
        [description]="descricao"
        helpLabel="Ajuda sobre solicitações financeiras"
        (help)="ajuda.show()"
      >
        @if (podeExportar()) {
          <ces-button actions variant="outline" icon="pi pi-download" label="Exportar CSV" (pressed)="abrirExportacao()"></ces-button>
        }
        @if (podeCriar()) {
          <ces-button actions icon="pi pi-plus" [label]="tipoAtual() === 'PAGAMENTO' ? 'Novo pagamento' : 'Nova compra'" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel #painel [storageKey]="chave" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          @if (tipoDaRota === 'AMBOS') {
            <ces-select label="Tipo" [options]="tipos" [value]="filtro('tipo')" (valueChange)="definir('tipo', $event)"></ces-select>
          }
          @if (modo !== 'aprovacao') {
            <ces-select label="Status" [options]="statusOpcoes" [value]="filtro('status')" (valueChange)="definir('status', $event)"></ces-select>
          }
          <ces-number-input label="Número" [value]="$any(filtro('numero'))" (valueChange)="definir('numero', $event)" [min]="1"></ces-number-input>
          @if (modo !== 'minhas') {
            <ces-select label="Somente minhas" [options]="simNao" [value]="filtro('apenasMinhas')" (valueChange)="definir('apenasMinhas', $event)"></ces-select>
          }
          <ces-select label="Situação do registro" [options]="ativos" [value]="filtro('ativo')" (valueChange)="definir('ativo', $event)"></ces-select>
          <ces-autocomplete label="Colaborador solicitante" placeholder="Digite o nome" [options]="colaboradores.items()" [ngModel]="filtro('solicitanteColaboradorId')"
            (ngModelChange)="definir('solicitanteColaboradorId', $event)" [loading]="colaboradores.loading()" [status]="colaboradores.status()" [clearable]="true"
            (search)="colaboradores.search($event)" (loadMore)="colaboradores.loadNext()" (retry)="colaboradores.retry()"></ces-autocomplete>
          @for (campo of lookupsVisiveis(); track campo.key) {
            <ces-select [label]="campo.label" [options]="opcoesLookup(campo.lookup)" [value]="filtro(campo.key)" (valueChange)="definir(campo.key, $event)"
              [clearable]="true" [filter]="true" placeholder="Todos" [loading]="lookupCarregando(campo.lookup)"
              (opened)="lookups[campo.lookup].open()" (search)="lookups[campo.lookup].search($event)"></ces-select>
          }
          @for (campo of enumsVisiveis(); track campo.key) {
            <ces-select [label]="campo.label" [options]="comTodos(campo.options)" [value]="filtro(campo.key)" (valueChange)="definir(campo.key, $event)"></ces-select>
          }
          @for (campo of numerosVisiveis(); track campo.key) {
            <ces-number-input [label]="campo.label" [mode]="campo.modo" [min]="0" [value]="$any(filtro(campo.key))" (valueChange)="definir(campo.key, $event)"></ces-number-input>
          }
          @for (campo of booleanosVisiveis(); track campo.key) {
            <ces-select [label]="campo.label" [options]="simNao" [value]="filtro(campo.key)" (valueChange)="definir(campo.key, $event)"></ces-select>
          }
          @if (maisFiltros()) {
            <ces-text-input label="Número da NF" [value]="$any(filtro('numeroNf')) ?? ''" (valueChange)="definir('numeroNf', $event || null)"></ces-text-input>
          }
          @for (campo of datasVisiveis(); track campo.key) {
            <ces-datepicker [label]="campo.label" [value]="dataLocal($any(filtro(campo.key)))" [clearable]="true" (valueChange)="definir(campo.key, dataIso($event))"></ces-datepicker>
          }
        </div>
        <div class="ces-actions-row">
          <ces-button variant="link" size="sm" [icon]="maisFiltros() ? 'pi pi-angle-up' : 'pi pi-angle-down'" [label]="maisFiltros() ? 'Menos filtros' : 'Mais filtros'" (pressed)="maisFiltros.set(!maisFiltros())"></ces-button>
        </div>
      </ces-filter-panel>

      <ces-data-table
        [title]="titulo()"
        mode="server"
        [storageKey]="chave"
        searchPlaceholder="Fornecedor, descrição, PIX, linha digitável…"
        rowActionLabel="Detalhes"
        rowActionIcon="pi pi-arrow-right"
        [emptyActionLabel]="podeCriar() ? (tipoAtual() === 'PAGAMENTO' ? 'Novo pagamento' : 'Nova compra') : undefined"
        noResultsTitle="Nenhuma solicitação encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as solicitações' : modo === 'aprovacao' ? 'Nada aguardando aprovação' : 'Nenhuma solicitação por aqui'"
        [stateDescription]="resultado.error() ?? (modo === 'aprovacao' ? 'A fila está zerada.' : 'As solicitações de compra e pagamento aparecem aqui.')"
        [filtered]="lista.filtered()"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="novo()"
        (rowActivate)="abrir($event)"
      >
        <ng-template cesCell="numero" let-row><span class="ces-cell-strong">#{{ row.numero }}</span></ng-template>
        <ng-template cesCell="status" let-row>
          <ces-status [tone]="tom(row.status)">{{ rotuloStatus(row.status) }}</ces-status>
        </ng-template>
        <ng-template cesCell="solicitacao" let-row>
          <span>{{ valorExibicao(row, 'descricao') || row.formulario?.titulo }}</span>
          <span class="ces-cell-muted">{{ row.formulario?.tipoSolicitacao === 'COMPRA' ? 'Compra' : 'Pagamento' }}</span>
        </ng-template>
        <ng-template cesCell="fornecedor" let-row>
          <span>{{ fornecedorLabel(row) }}</span>
          @if (fornecedorRazaoSocial(row); as razao) {
            <span class="ces-cell-muted" [title]="razao">{{ razao }}</span>
          }
          @if (fornecedorEhNaoCadastrado(row)) {
            <ces-status tone="warning" icon="pi-exclamation-triangle" emphasis="outline">Não cadastrado</ces-status>
          }
        </ng-template>
        <ng-template cesRowActions let-row>
          @if (row.acoes?.podeEditar) {
            <ces-icon-button icon="pi pi-pencil" [label]="'Editar solicitação #' + row.numero" size="icon-sm" (pressed)="editar(row)"></ces-icon-button>
          }
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="exportacaoAberta" title="Exportar solicitações (CSV)" description="Exporta o recorte atual da lista, inclusive a busca." size="sm">
      @if (opcoesExportacao().length > 1) {
        <ces-select label="Tipo" [options]="opcoesExportacao()" [(value)]="exportTipo"></ces-select>
      }
      <ces-select label="Formato" [options]="formatos" [(value)]="exportFormato"></ces-select>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="exportacaoAberta.set(false)"></ces-button>
        <ces-button icon="pi pi-download" label="Exportar" [loading]="exportando()" (pressed)="exportar()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-ajuda-listagem #ajuda [titulo]="titulo()" artigoPlural="as" entidadePlural="solicitações" busca="fornecedor, descrição, chave PIX ou linha digitável" [podeCriar]="podeCriar()">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-sliders-h"></i> Filtros</h4>
        <p class="help-text-desc">Os filtros mais usados aparecem primeiro; "Mais filtros" mostra datas de decisão e cancelamento, usuários, veículo e campos do formulário. A situação abre em "Ativos".</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-download"></i> Exportar</h4>
        <p class="help-text-desc">A exportação leva o recorte atual (filtros e busca), no formato resumido ou detalhado.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class SolicitacoesFinanceirasComponent {
  private readonly service = inject(FinanceiroService);
  private readonly router = inject(Router);
  private readonly notificacoes = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly rota = inject(ActivatedRoute);

  protected readonly tipoDaRota = (this.rota.snapshot.data['tipo'] as TipoSolicitacao | 'AMBOS') ?? 'AMBOS';
  protected readonly modo = (this.rota.snapshot.data['modo'] as Modo) ?? 'todas';
  protected readonly chave = `fin-solicitacoes-${this.modo}-${this.tipoDaRota}`.toLowerCase();
  protected readonly tipos = TIPOS;
  protected readonly statusOpcoes = STATUS;
  protected readonly simNao = SIM_NAO;
  protected readonly ativos = ATIVOS;
  protected readonly formatos = FORMATOS;
  protected readonly comTodos = comTodos;
  protected readonly dataLocal = dataLocal;
  protected readonly dataIso = dataIso;
  protected readonly fornecedorLabel = fornecedorLabel;
  protected readonly fornecedorRazaoSocial = fornecedorRazaoSocial;
  protected readonly fornecedorEhNaoCadastrado = fornecedorEhNaoCadastrado;
  protected readonly maisFiltros = signal(false);

  private readonly permissoes = this.auth.currentUser()?.permissoes ?? [];
  protected readonly podeExportarCompra = this.permissoes.includes('financeiro:compra:read');
  protected readonly podeExportarPagamento = this.permissoes.includes('financeiro:pagamento:read');
  protected readonly podeExportar = () => this.podeExportarCompra || this.podeExportarPagamento;
  protected readonly podeCriar = computed(() => this.modo !== 'aprovacao');
  protected readonly descricao =
    this.modo === 'aprovacao'
      ? 'Solicitações aguardando a sua decisão, das mais antigas para as mais novas.'
      : this.modo === 'minhas'
        ? 'Solicitações de compra e pagamento que você abriu.'
        : 'Todas as solicitações de compra e pagamento, com filtros por fornecedor, obra, valores e datas.';

  // Busca paginada das referências (fornecedores, obras, usuários...).
  protected readonly lookups = Object.fromEntries(
    (Object.keys(ENDPOINTS) as LookupKey[]).map((chave) => [
      chave,
      new RemoteLookup<ReferenciaOpcao>(
        {
          identify: (item) => item.id,
          selectedKeys: () =>
            LOOKUPS.filter((c) => c.lookup === chave)
              .map((c) => this.lista.filters[c.key]() as string | null)
              .filter((v): v is string => !!v),
          load: ({ query, page, size }) => this.service.buscarOpcoesReferencia(ENDPOINTS[chave].url, query, !!ENDPOINTS[chave].utilizaveis, page, size)
        },
        this.destroyRef
      )
    ])
  ) as Record<LookupKey, RemoteLookup<ReferenciaOpcao>>;
  protected readonly colaboradores: BuscaColaboradores<string> = new BuscaColaboradores<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => {
      const id = this.lista.filters['solicitanteColaboradorId']() as string | null;
      return id ? [id] : [];
    },
    destroyRef: this.destroyRef
  });
  private readonly lookupStore = inject(LookupStore);

  protected readonly lista = cesListState({
    storageKey: this.chave,
    queryParam: 'buscaGlobal',
    filters: this.definicoes(),
    sort: [{ key: 'criada', direction: this.modo === 'aprovacao' ? 'asc' : 'desc' }]
  });
  protected readonly tipoAtual = computed<TipoSolicitacao | 'AMBOS'>(() =>
    this.tipoDaRota !== 'AMBOS' ? this.tipoDaRota : ((this.lista.applied()['tipo'] as TipoSolicitacao | null) ?? 'AMBOS')
  );
  protected readonly resultado = this.lista.results<SolicitacaoFinanceira>(
    (pedido) =>
      this.service.listarSolicitacoes(this.tipoAtual(), {
        ...this.consulta(this.tipoAtual()),
        page: pedido.page,
        size: pedido.size,
        sort: pedido.sort.map((r) => `${r.key},${r.direction}`)[0] ?? 'dataCriacao,desc'
      }),
    { errorMessage: getErrorMessage }
  );

  protected readonly titulo = computed(() => {
    const tipo = this.tipoAtual();
    const sufixo = tipo === 'COMPRA' ? 'compras' : tipo === 'PAGAMENTO' ? 'pagamentos' : 'geral';
    if (this.modo === 'aprovacao') return `Fila de aprovação – ${sufixo}`;
    if (this.modo === 'todas') return tipo === 'COMPRA' ? 'Gestão de compras' : tipo === 'PAGAMENTO' ? 'Gestão de pagamentos' : 'Gestão de solicitações';
    return `Minhas solicitações – ${sufixo}`;
  });
  protected readonly breadcrumb = computed(() => [
    { label: 'Financeiro' },
    { label: this.modo === 'aprovacao' ? 'Aprovações' : this.modo === 'todas' ? 'Gestão' : 'Minhas solicitações' },
    { label: this.tipoAtual() === 'COMPRA' ? 'Compras' : this.tipoAtual() === 'PAGAMENTO' ? 'Pagamentos' : 'Todas' }
  ]);

  protected readonly colunas: CesDataTableColumn<SolicitacaoFinanceira>[] = [
    { key: 'numero', header: 'Número', type: 'custom', priority: 1, sortField: 'numero', value: (s) => s.numero, display: (s) => `#${s.numero}` },
    { key: 'status', header: 'Status', type: 'custom', priority: 1, value: (s) => s.status, display: (s) => this.rotuloStatus(s.status) },
    { key: 'solicitacao', header: 'Solicitação', type: 'custom', sortField: 'formulario.tipoSolicitacao', value: (s) => this.valorExibicao(s, 'descricao') || s.formulario?.titulo },
    { key: 'fornecedor', header: 'Fornecedor', type: 'custom', priority: 1, sortField: 'fornecedor', value: (s) => fornecedorLabel(s) },
    { key: 'solicitante', header: 'Solicitante', value: (s) => nomeSolicitante(s), display: (s) => nomeSolicitante(s) },
    { key: 'valor', header: 'Valor', type: 'currency', value: (s) => s.valor },
    { key: 'criada', header: 'Criada em', type: 'date', sortField: 'dataCriacao', value: (s) => s.dataCriacao }
  ];
  protected readonly rotulo = (s: SolicitacaoFinanceira) => `#${s.numero}`;

  protected readonly lookupsVisiveis = computed(() => this.visiveis(LOOKUPS));
  protected readonly enumsVisiveis = computed(() => this.visiveis(ENUMS));
  protected readonly numerosVisiveis = computed(() => this.visiveis(NUMEROS));
  protected readonly booleanosVisiveis = computed(() => this.visiveis(BOOLEANOS));
  protected readonly datasVisiveis = computed(() => this.visiveis(DATAS));

  protected readonly exportacaoAberta = signal(false);
  protected readonly exportTipo = signal<TipoSolicitacao>('COMPRA');
  protected readonly exportFormato = signal<'resumo' | 'detalhado'>('resumo');
  protected readonly exportando = signal(false);
  protected readonly opcoesExportacao = computed<CesSelectOption<TipoSolicitacao>[]>(() => [
    ...(this.podeExportarCompra ? [{ label: 'Solicitações de compra', value: 'COMPRA' as const }] : []),
    ...(this.podeExportarPagamento ? [{ label: 'Solicitações de pagamento', value: 'PAGAMENTO' as const }] : [])
  ]);

  constructor() {
    // Nome do colaborador vindo da URL (chip e seleção).
    const id = this.lista.filters['solicitanteColaboradorId']() as string | null;
    if (id) {
      inject(RhService)
        .buscarColaboradorPorId(id)
        .subscribe({ next: (c) => this.colaboradores.seedSelected({ value: c.id, label: c.nomeCompleto || id }), error: () => undefined });
    }
  }

  /** Definições do estado da lista, com os nomes de parâmetro já usados pelo painel. */
  private definicoes(): CesFilterDefs {
    const defs: CesFilterDefs = {
      ativo: cesFilter.value<boolean | null>(true, { label: 'Situação', options: () => ATIVOS }),
      numero: cesFilter.value<number | null>(null, { label: 'Número', type: 'number' }),
      numeroNf: cesFilter.value<string | null>(null, { label: 'Número da NF' }),
      solicitanteColaboradorId: cesFilter.value<string | null>(null, {
        label: 'Colaborador',
        format: (v): string | null => (v ? (this.colaboradores.items().find((c) => c.value === v)?.label ?? 'colaborador selecionado') : null)
      })
    };
    // Na fila de aprovação o status é fixo (SOLICITADO); em rota de um tipo só, o tipo também.
    if (this.modo !== 'aprovacao') defs['status'] = cesFilter.value<StatusSolicitacao | null>(null, { label: 'Status', type: 'string', options: () => STATUS });
    if (this.tipoDaRota === 'AMBOS') defs['tipo'] = cesFilter.value<TipoSolicitacao | null>(null, { label: 'Tipo', type: 'string', options: () => TIPOS });
    if (this.modo !== 'minhas') defs['apenasMinhas'] = cesFilter.value<boolean | null>(null, { label: 'Somente minhas', options: () => SIM_NAO });
    for (const c of BOOLEANOS) defs[c.key] = cesFilter.value<boolean | null>(null, { label: c.label, options: () => SIM_NAO });
    for (const c of NUMEROS) defs[c.key] = cesFilter.value<number | null>(null, { label: c.label, type: 'number' });
    for (const c of ENUMS) defs[c.key] = cesFilter.value<string | null>(null, { label: c.label, type: 'string', options: () => comTodos(c.options) });
    for (const c of LOOKUPS) {
      defs[c.key] = cesFilter.value<string | null>(null, {
        label: c.label,
        format: (v): string | null => (v ? (this.lookups[c.lookup].items().find((o) => o.id === v)?.label ?? 'selecionado') : null)
      });
    }
    for (const c of DATAS) defs[c.key] = cesFilter.date(null, { label: c.label });
    return defs;
  }

  private visiveis<T extends Campo>(campos: readonly T[]): T[] {
    const pagamento = this.tipoAtual() === 'PAGAMENTO';
    return campos.filter((c) => (!c.avancado || this.maisFiltros()) && (!c.somentePagamento || pagamento));
  }

  protected filtro(chave: string): unknown {
    return this.lista.filters[chave]?.();
  }

  protected definir(chave: string, valor: unknown): void {
    this.lista.filters[chave]?.set(valor);
  }

  protected opcoesLookup(chave: LookupKey): CesSelectOption<string>[] {
    return this.lookups[chave].items().map((o) => ({ label: o.label, value: o.id }));
  }

  protected lookupCarregando(chave: LookupKey): boolean {
    return this.lookups[chave].loading();
  }

  /** Recorte aplicado no formato do backend (também usado na exportação). */
  private consulta(tipo: TipoSolicitacao | 'AMBOS'): SolicitacaoFinanceiraFilter {
    const { filters, query } = this.lista.request();
    const visao: Record<string, unknown> = { ...filters };
    for (const chave of [...CAMPOS_DATA_LOCAL, ...CAMPOS_DATA_HORA]) visao[chave] = dataLocal(filters[chave] as string | null);
    const params: SolicitacaoFinanceiraFilter = {
      ...normalizarFiltrosSolicitacao(visao as SolicitacaoFiltrosView),
      buscaGlobal: buscaGlobalParaConsulta(query),
      apenasMinhas: this.modo === 'minhas' ? true : ((filters['apenasMinhas'] as boolean | null) ?? undefined),
      status: this.modo === 'aprovacao' ? 'SOLICITADO' : ((filters['status'] as StatusSolicitacao | null) ?? undefined)
    };
    if (tipo !== 'PAGAMENTO') {
      delete params.solicitacaoCompraId;
      delete params.possuiCompraPrevia;
      delete params.motivoSemCompra;
    }
    return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null)) as SolicitacaoFinanceiraFilter;
  }

  protected valorExibicao(s: SolicitacaoFinanceira, chave: string): string {
    return s.valores?.[chave]?.exibicao ?? '';
  }

  protected rotuloStatus(s: StatusSolicitacao): string {
    return STATUS_OPTIONS.find((o) => o.value === s)?.label ?? s;
  }

  protected tom(s: StatusSolicitacao): CesTone {
    return TOM_STATUS[s] ?? 'neutral';
  }

  protected novo(): void {
    if (!this.podeCriar()) return;
    void this.router.navigate(['/financeiro', this.tipoAtual() === 'PAGAMENTO' ? 'pagamentos' : 'compras', 'nova']);
  }

  protected abrir(s: SolicitacaoFinanceira): void {
    void this.router.navigate(['/financeiro', s.formulario.tipoSolicitacao === 'COMPRA' ? 'compras' : 'pagamentos', s.id]);
  }

  protected editar(s: SolicitacaoFinanceira): void {
    void this.router.navigate(['/financeiro', s.formulario.tipoSolicitacao === 'COMPRA' ? 'compras' : 'pagamentos', s.id, 'editar']);
  }

  protected abrirExportacao(): void {
    const tipo = this.tipoAtual();
    this.exportTipo.set(tipo === 'PAGAMENTO' && this.podeExportarPagamento ? 'PAGAMENTO' : this.podeExportarCompra ? 'COMPRA' : 'PAGAMENTO');
    this.exportFormato.set('resumo');
    this.exportacaoAberta.set(true);
  }

  protected exportar(): void {
    if (this.exportando()) return;
    const tipo = this.exportTipo();
    const formato = this.exportFormato();
    const filtros = this.consulta(tipo);
    this.exportando.set(true);
    (formato === 'detalhado' ? this.service.exportarSolicitacoesDetalhado(tipo, filtros) : this.service.exportarSolicitacoesResumo(tipo, filtros)).subscribe({
      next: (resposta: HttpResponse<Blob>) => {
        this.exportando.set(false);
        this.exportacaoAberta.set(false);
        if (!resposta.body) return;
        this.baixar(resposta.body, this.nomeArquivo(resposta, `solicitacoes_${tipo.toLowerCase()}_${formato}.csv`));
        this.notificacoes.success(`CSV de ${tipo === 'COMPRA' ? 'compras' : 'pagamentos'} (${formato}) gerado.`);
      },
      error: async (erro: unknown) => {
        this.exportando.set(false);
        this.notificacoes.error(await this.mensagemDeErroBlob(erro));
      }
    });
  }

  private nomeArquivo(resposta: HttpResponse<Blob>, padrao: string): string {
    const disposicao = resposta.headers?.get('content-disposition') || resposta.headers?.get('Content-Disposition');
    return (disposicao && /filename="?([^";]+)"?/i.exec(disposicao)?.[1]?.trim()) || padrao;
  }

  private baixar(blob: Blob, nome: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = nome;
    link.click();
    URL.revokeObjectURL(url);
  }

  /** O erro da exportação chega como Blob: lê o Problem Details dentro dele. */
  private async mensagemDeErroBlob(erro: unknown): Promise<string> {
    const corpo = (erro as { error?: unknown })?.error;
    if (corpo instanceof Blob) {
      try {
        const json = JSON.parse(await corpo.text());
        return json.detail || json.title || getErrorMessage(erro);
      } catch {
        // não era JSON
      }
    }
    return getErrorMessage(erro);
  }
}
