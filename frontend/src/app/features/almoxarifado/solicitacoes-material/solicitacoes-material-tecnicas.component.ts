import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import { EstoqueResponse, MaterialResponse } from '../../../core/models/almoxarifado.models';
import { AgenteResponsavelResponse, ObraResumoResponse } from '../../../core/models/obras.models';
import { EquipeResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import {
  SolicitacaoMaterialFilter,
  SolicitacaoMaterialSummaryResponse,
  StatusSeparacao,
  StatusSolicitacaoMaterial,
  TipoObraSolicitacao,
  TipoSolicitacaoTecnica
} from '../../../core/models/solicitacao-material-tecnica.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { AuthService } from '../../../core/services/auth.service';
import { ConnectivityService } from '../../../core/services/connectivity.service';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../core/services/solicitacao-material-tecnica.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { ExportarSolicitacoesDialogComponent } from './exportar-solicitacoes-dialog.component';
import { separacaoLabel, separacaoTone, statusLabel, statusTone, tipoTecnicoEhAdicao, tipoTecnicoIcon, tipoTecnicoLabel } from './solicitacao-tecnica-presentation';
import { SolicitacoesTecnicasImportacaoComponent } from './solicitacoes-tecnicas-importacao/solicitacoes-tecnicas-importacao.component';
import { SolicitacoesTecnicasKanbanComponent } from './solicitacoes-tecnicas-kanban/solicitacoes-tecnicas-kanban.component';

const comTodos = <T,>(opcoes: CesSelectOption<T>[], todos = 'Todos'): CesSelectOption<T | null>[] => [{ label: todos, value: null }, ...opcoes];

const TIPOS_OBRA = comTodos<TipoObraSolicitacao>([
  { label: 'Obra', value: 'OBRA' },
  { label: 'Corretiva', value: 'CORRETIVA' },
  { label: 'Preventiva', value: 'PREVENTIVA' },
  { label: 'LPT', value: 'LPT' }
]);
const TIPOS_TECNICOS = comTodos<TipoSolicitacaoTecnica>(
  (['ADICAO_COMUM', 'ADICAO_CONCRETO', 'DEVOLUCAO_COMUM', 'DEVOLUCAO_CONCRETO'] as const).map((value) => ({ label: tipoTecnicoLabel(value), value }))
);
const STATUS: CesSelectOption<StatusSolicitacaoMaterial>[] = (
  ['PENDENTE', 'APROVADO', 'REJEITADO', 'ENTREGUE_PARCIAL', 'ENTREGUE', 'DEVOLVIDO_PARCIAL', 'DEVOLVIDO', 'ESTORNADO', 'CANCELADO'] as const
).map((value) => ({ label: statusLabel(value), value }));
const SEPARACAO = comTodos<StatusSeparacao>(
  (['AGUARDANDO_AGENDAMENTO', 'PRONTO_PARA_SEPARACAO', 'EM_SEPARACAO', 'SEPARADO'] as const).map((value) => ({ label: separacaoLabel(value), value })),
  'Todas'
);

@Component({
  selector: 'app-solicitacoes-material-tecnicas',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AjudaListagemComponent,
    ExportarSolicitacoesDialogComponent,
    SolicitacoesTecnicasImportacaoComponent,
    SolicitacoesTecnicasKanbanComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="canCreate" (newRequested)="createRequest()">
      <ces-page-header
        [title]="kanban ? 'Quadro de solicitações técnicas' : 'Solicitações técnicas de material'"
        icon="pi pi-wrench"
        [breadcrumb]="[{ label: 'Almoxarifado' }, { label: 'Solicitações técnicas' }]"
        description="Pedidos e devoluções de material para obras: planejamento, separação, atendimento e reserva."
        helpLabel="Ajuda sobre solicitações técnicas"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" [icon]="kanban ? 'pi pi-table' : 'pi pi-objects-column'" [label]="kanban ? 'Ver tabela' : 'Quadro kanban'" (pressed)="alternarModoExibicao()"></ces-button>
        <ces-button actions variant="outline" icon="pi pi-download" label="Exportar" (pressed)="exportacaoAberta.set(true)"></ces-button>
        @if (canImport) {
          <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacaoAberta.set(true)"></ces-button>
        }
        @if (canCreate) {
          <ces-button actions variant="outline" icon="pi pi-undo" label="Nova devolução" (pressed)="createDevolucao()"></ces-button>
        }
        @if (canCreate) {
          <ces-button actions icon="pi pi-plus" label="Nova solicitação" [keyShortcut]="atalhos.newShortcut()" (pressed)="createRequest()"></ces-button>
        }
      </ces-page-header>

      @if (!isOnline()) {
        <ces-callout tone="warning" icon="pi pi-wifi" title="Sem conexão">Os dados podem estar desatualizados. Criar solicitações exige reconexão.</ces-callout>
      }

      <ces-filter-panel #painel [storageKey]="chave" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Obra" [options]="obraOptions()" [(value)]="lista.filters.idObra" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Almoxarifado de atendimento" [options]="almoxarifadoOptions()" [(value)]="lista.filters.idAlmoxarifado" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Equipe" [options]="equipeOptions()" [(value)]="lista.filters.idEquipe" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Tipo de obra" [options]="tiposObra" [(value)]="lista.filters.tipoObra"></ces-select>
          <ces-select label="Tipo de solicitação" [options]="tiposTecnicos" [(value)]="lista.filters.tipoTecnico"></ces-select>
          <ces-multiselect label="Status do atendimento" [options]="statusOpcoes" [(value)]="lista.filters.statuses" [filter]="false" placeholder="Todos"></ces-multiselect>
          <ces-select label="Etapa de separação" [options]="separacaoOpcoes" [(value)]="lista.filters.statusSeparacao"></ces-select>
          <ces-text-input label="Ocorrência (OC/OS)" [(value)]="lista.filters.ocorrencia"></ces-text-input>
          <ces-text-input label="Número da reserva" [(value)]="lista.filters.numeroReserva"></ces-text-input>
          <ces-select label="Agente da reserva" [options]="agenteOptions()" [(value)]="lista.filters.idAgenteReserva" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-text-input label="Referência" [(value)]="lista.filters.referencia"></ces-text-input>
          <ces-text-input label="Número de série" [(value)]="lista.filters.numSerie" [maxlength]="120" hint="Não fica salvo no endereço da página."></ces-text-input>
          <ces-datepicker label="Solicitadas a partir de" [value]="dataLocal(lista.filters.periodo().from)" [clearable]="true" (valueChange)="periodo('from', $event)"></ces-datepicker>
          <ces-datepicker label="Solicitadas até" [value]="dataLocal(lista.filters.periodo().to)" [clearable]="true" (valueChange)="periodo('to', $event)"></ces-datepicker>
        </div>
      </ces-filter-panel>

      @if (kanban) {
        <app-solicitacoes-tecnicas-kanban [filtros]="kanbanFiltros()" (navegarParaEtapa)="onKanbanNavegarParaEtapa()"></app-solicitacoes-tecnicas-kanban>
      } @else {
        <ces-data-table
          title="Solicitações técnicas"
          mode="server"
          [storageKey]="chave"
          searchPlaceholder="Obra, ocorrência, reserva ou nº de série"
          rowActionLabel="Detalhes"
          rowActionIcon="pi pi-arrow-right"
          [emptyActionLabel]="canCreate ? 'Nova solicitação' : undefined"
          noResultsTitle="Nenhuma solicitação encontrada com esse recorte."
          [columns]="colunas"
          [rows]="resultado.rows()"
          [totalRecords]="resultado.total()"
          [rowLabel]="rotulo"
          [state]="resultado.state()"
          [stateTitle]="resultado.error() ? 'Não foi possível carregar as solicitações técnicas' : 'Nenhuma solicitação técnica'"
          [stateDescription]="resultado.error() ?? 'As solicitações de material para obras aparecem aqui.'"
          [filtered]="lista.filtered()"
          [(page)]="lista.page"
          [(pageSize)]="lista.pageSize"
          [(sort)]="lista.sort"
          [(query)]="lista.query"
          (lazyLoad)="lista.load($event)"
          (adjustFilters)="painel.expandAndFocus()"
          (clearFilters)="lista.reset()"
          (emptyAction)="createRequest()"
          (rowActivate)="abrirDetalhe($event)"
        >
          <ng-template cesCell="solicitacao" let-row>
            <span class="ces-cell-strong">#{{ row.id.slice(0, 8) }}</span>
            <span>{{ row.dataSolicitacao | date: 'dd/MM/yyyy HH:mm' }}</span>
            @if (row.referencia || row.numSerie) { <span class="ces-cell-muted">{{ row.referencia || row.numSerie }}</span> }
          </ng-template>
          <ng-template cesCell="obra" let-row>
            <span [title]="obraLabel(row.idObra)">{{ obraLabel(row.idObra) }}</span>
            <span class="ces-cell-muted">{{ row.tipoObra || 'Tipo não informado' }}</span>
          </ng-template>
          <ng-template cesCell="tipo" let-row>
            @if (row.tipo) {
              <ces-status [tone]="tipoTecnicoEhAdicao(row.tipo) ? 'info' : 'neutral'" [icon]="tipoTecnicoIcon(row.tipo)">{{ tipoTecnicoLabel(row.tipo) }}</ces-status>
            } @else {
              <span class="ces-cell-muted">—</span>
            }
          </ng-template>
          <ng-template cesCell="equipe" let-row>
            <span>{{ equipeLabel(row.idEquipe) }}</span>
            @if (row.saqueForaEquipe) {
              <span class="ces-cell-muted">Saque fora da equipe</span>
            } @else if (row.idColaboradorSaque) {
              <span class="ces-cell-muted">Responsável definido</span>
            }
          </ng-template>
          <ng-template cesCell="planejamento" let-row>
            <ces-status [tone]="planejamentoTone(row)" [icon]="row.dataPrevista ? 'pi-calendar' : 'pi-calendar-minus'">{{ planejamentoLabel(row) }}</ces-status>
            <span class="ces-cell-muted">Prevista: {{ row.dataPrevista ? (row.dataPrevista | date: 'dd/MM/yyyy' : 'UTC') : 'não definida' }}</span>
            @if (row.atrasada) { <ces-status tone="danger" icon="pi-exclamation-triangle">Atrasada</ces-status> }
          </ng-template>
          <ng-template cesCell="separacao" let-row>
            @if (tipoTecnicoEhAdicao(row.tipo)) {
              <ces-status [tone]="separacaoTone(row.statusSeparacao)">{{ separacaoLabel(row.statusSeparacao) }}</ces-status>
            } @else {
              <span class="ces-cell-muted">Não se aplica</span>
            }
          </ng-template>
          <ng-template cesCell="status" let-row>
            <ces-status [tone]="statusTone(row.status)">{{ statusLabel(row.status) }}</ces-status>
            @if (row.possuiPendencia) { <ces-status tone="warning" icon="pi-exclamation-triangle">Com pendência</ces-status> }
          </ng-template>
          <ng-template cesCell="reserva" let-row>
            @if (row.numeroReserva) {
              <span class="ces-cell-strong">{{ row.numeroReserva }}</span>
              <span class="ces-cell-muted">{{ row.nomeAgenteReserva || 'Agente não informado' }}</span>
              @if (row.dataCriacaoReserva) { <span class="ces-cell-muted">Criada em {{ row.dataCriacaoReserva | date: 'dd/MM/yyyy' : 'UTC' }}</span> }
            } @else {
              <span class="ces-cell-muted">Sem reserva</span>
            }
          </ng-template>
          <ng-template cesRowActions let-row>
            <ces-icon-button icon="pi pi-print" [label]="'Imprimir relatório da solicitação #' + row.id.slice(0, 8)" size="icon-sm" (pressed)="imprimirRelatorio(row)"></ces-icon-button>
          </ng-template>
        </ces-data-table>
      }
    </div>

    <app-solicitacoes-tecnicas-importacao [(visible)]="importacaoAberta" [obras]="obras()" [equipes]="equipes()" [almoxarifados]="almoxarifados()"
      [materiaisList]="materiais()" [agentesList]="agentes()" (processado)="lista.reload()"></app-solicitacoes-tecnicas-importacao>

    <app-exportar-solicitacoes-dialog [visible]="exportacaoAberta()" (visibleChange)="exportacaoAberta.set($event)" contextoInicial="TECNICO"
      [filtrosAtivos]="filtrosExportacao()" [obraOptions]="obraOptions()" [almoxarifadoOptions]="almoxarifadoOptions()" [equipeOptions]="equipeOptions()"></app-exportar-solicitacoes-dialog>

    <app-ajuda-listagem #ajuda titulo="Solicitações técnicas" artigoPlural="as" entidadePlural="solicitações" busca="obra, ocorrência, reserva ou número de série"
      rotuloNovo="Nova solicitação" [podeCriar]="canCreate" [importacao]="canImport">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-box"></i> Fila de separação</h4>
        <p class="help-text-desc">Pedidos de adição passam pela separação física; devoluções seguem direto para o recebimento.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-info-circle"></i> Estados independentes</h4>
        <p class="help-text-desc">Atendimento e separação física são acompanhados separadamente, como na operação real.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-objects-column"></i> Quadro kanban</h4>
        <p class="help-text-desc">O quadro usa os mesmos filtros da tabela, organizados pelas etapas da operação.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class SolicitacoesMaterialTecnicasComponent {
  private readonly service = inject(SolicitacaoMaterialTecnicaService);
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly obrasService = inject(ObrasService);
  private readonly rh = inject(RhService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly kanban = inject(ActivatedRoute).snapshot.data['modo'] === 'kanban';
  protected readonly chave = this.kanban ? 'almox-solicitacoes-tecnicas-kanban' : 'almox-solicitacoes-tecnicas';
  protected readonly isOnline = inject(ConnectivityService).isOnline;
  protected readonly canCreate = this.hasPermission('estoque:solicitacao:create');
  protected readonly canImport = this.hasPermission('estoque:solicitacao:import');
  protected readonly importacaoAberta = signal(false);
  protected readonly exportacaoAberta = signal(false);

  protected readonly statusLabel = statusLabel;
  protected readonly statusTone = statusTone;
  protected readonly separacaoLabel = separacaoLabel;
  protected readonly separacaoTone = separacaoTone;
  protected readonly tipoTecnicoLabel = tipoTecnicoLabel;
  protected readonly tipoTecnicoIcon = tipoTecnicoIcon;
  protected readonly tipoTecnicoEhAdicao = tipoTecnicoEhAdicao;
  protected readonly dataLocal = dataLocal;
  protected readonly tiposObra = TIPOS_OBRA;
  protected readonly tiposTecnicos = TIPOS_TECNICOS;
  protected readonly statusOpcoes = STATUS;
  protected readonly separacaoOpcoes = SEPARACAO;

  // Cadastros dos filtros e da correção da importação.
  protected readonly obras = signal<ObraResumoResponse[]>([]);
  protected readonly equipes = signal<EquipeResponse[]>([]);
  protected readonly almoxarifados = signal<EstoqueResponse[]>([]);
  protected readonly agentes = signal<AgenteResponsavelResponse[]>([]);
  protected readonly materiais = signal<MaterialResponse[]>([]);
  protected readonly obraOptions = computed(() => this.obras().map((o) => ({ label: `${o.numero} — ${o.titulo}`, value: o.id })));
  protected readonly equipeOptions = computed(() => this.equipes().map((e) => ({ label: e.codigo, value: e.id })));
  protected readonly almoxarifadoOptions = computed(() => this.almoxarifados().map((e) => ({ label: e.nomeIdentificador, value: e.id })));
  protected readonly agenteOptions = computed(() => this.agentes().map((a) => ({ label: a.nome, value: a.id })));

  protected readonly lista = cesListState({
    storageKey: this.chave,
    filters: {
      idObra: cesFilter.value<string | null>(null, { label: 'Obra', param: 'obra', format: (id): string | null => (id ? this.obraLabel(id) : null) }),
      idAlmoxarifado: cesFilter.value<string | null>(null, {
        label: 'Almoxarifado',
        param: 'almoxarifado',
        format: (id): string | null => (id ? (this.almoxarifados().find((e) => e.id === id)?.nomeIdentificador ?? 'selecionado') : null)
      }),
      idEquipe: cesFilter.value<string | null>(null, { label: 'Equipe', param: 'equipe', format: (id): string | null => (id ? this.equipeLabel(id) : null) }),
      tipoObra: cesFilter.value<TipoObraSolicitacao | null>(null, { label: 'Tipo de obra', options: () => TIPOS_OBRA }),
      tipoTecnico: cesFilter.value<TipoSolicitacaoTecnica | null>(null, { label: 'Tipo', options: () => TIPOS_TECNICOS }),
      statuses: cesFilter.list<StatusSolicitacaoMaterial>([], { label: 'Status', param: 'status', itemLabel: (s) => statusLabel(s) }),
      statusSeparacao: cesFilter.value<StatusSeparacao | null>(null, { label: 'Separação', param: 'separacao', options: () => SEPARACAO }),
      ocorrencia: cesFilter.value<string>('', { label: 'Ocorrência' }),
      numeroReserva: cesFilter.value<string>('', { label: 'Reserva', param: 'reserva' }),
      idAgenteReserva: cesFilter.value<string | null>(null, {
        label: 'Agente',
        param: 'agenteReserva',
        format: (id): string | null => (id ? (this.agentes().find((a) => a.id === id)?.nome ?? 'selecionado') : null)
      }),
      referencia: cesFilter.value<string>('', { label: 'Referência', param: 'ref' }),
      // Nº de série é dado sensível: fica fora da URL.
      numSerie: cesFilter.value<string>('', { label: 'Nº de série', url: false }),
      periodo: cesFilter.range({ from: null, to: null }, { label: 'Solicitação' })
    },
    sort: [{ key: 'solicitacao', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<SolicitacaoMaterialSummaryResponse>(
    (pedido) =>
      this.service.listar({
        ...this.filtro(),
        page: pedido.page,
        size: pedido.size,
        sort: pedido.sort.map((r) => `${r.key},${r.direction}`)[0] ?? 'dataSolicitacao,desc'
      } as SolicitacaoMaterialFilter),
    { errorMessage: getErrorMessage }
  );
  protected readonly kanbanFiltros = computed<Partial<SolicitacaoMaterialFilter>>(() => {
    this.lista.applied();
    const { page: _p, size: _s, statuses: _st, referencia: _r, numSerie: _n, ...resto } = this.filtro();
    return resto;
  });
  /** O diálogo de exportação parte do recorte aplicado. */
  protected readonly filtrosExportacao = computed(() => {
    this.lista.applied();
    const f = this.filtro();
    return { ...f, dataInicio: dataLocal(f.dataInicio ?? null), dataFim: dataLocal(f.dataFim ?? null) };
  });

  protected readonly colunas: CesDataTableColumn<SolicitacaoMaterialSummaryResponse>[] = [
    { key: 'solicitacao', header: 'Solicitação', type: 'custom', priority: 1, sortField: 'dataSolicitacao', value: (s) => s.dataSolicitacao },
    { key: 'obra', header: 'Obra', type: 'custom', priority: 1, value: (s) => this.obraLabel(s.idObra) },
    { key: 'tipo', header: 'Tipo', type: 'custom', value: (s) => (s.tipo ? tipoTecnicoLabel(s.tipo) : '') },
    { key: 'ocorrencia', header: 'Ocorrência', value: (s) => s.ocorrencia?.codigo, display: (s) => s.ocorrencia?.codigo || '—' },
    { key: 'equipe', header: 'Equipe', type: 'custom', value: (s) => this.equipeLabel(s.idEquipe) },
    { key: 'planejamento', header: 'Planejamento', type: 'custom', sortField: 'dataPrevistaRetirada', value: (s) => s.dataPrevista },
    { key: 'separacao', header: 'Separação', type: 'custom', sortField: 'statusSeparacao', value: (s) => s.statusSeparacao },
    {
      key: 'entrega',
      header: 'Atendimento',
      type: 'date',
      sortField: 'dataEntrega',
      value: (s) => s.dataEntrega,
      display: (s) => (s.dataEntrega ? new Date(s.dataEntrega).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : 'Não realizado')
    },
    { key: 'status', header: 'Status', type: 'custom', priority: 1, sortField: 'status', value: (s) => statusLabel(s.status) },
    { key: 'reserva', header: 'Reserva', type: 'custom', value: (s) => s.numeroReserva },
    { key: 'itens', header: 'Itens', type: 'integer', value: (s) => s.quantidadeItens }
  ];
  protected readonly rotulo = (s: SolicitacaoMaterialSummaryResponse) => `Solicitação #${s.id.slice(0, 8)}`;

  constructor() {
    forkJoin({
      obras: this.obrasService.listarObras({ ativo: true, apenasPrincipais: true, size: 500, sort: 'numero,asc' }),
      equipes: this.rh.listarEquipes({ ativo: true, size: 1000, sort: 'codigo,asc' }),
      estoques: this.almoxarifado.listarEstoquesAtivos(),
      agentes: this.obrasService.listarAgentesAtivos(),
      materiais: this.almoxarifado.listarMateriais({ ativo: true, size: 1000, sort: 'codigo,asc' })
    }).subscribe({
      next: ({ obras, equipes, estoques, agentes, materiais }) => {
        // Só obras técnicas (processo OBRAS) entram nas solicitações técnicas.
        this.obras.set((obras.content || []).filter((o) => o.ativo && (!o.processoNome || o.processoNome.trim().toUpperCase() === 'OBRAS')));
        this.equipes.set(equipes.content);
        this.almoxarifados.set(estoques.content);
        this.agentes.set(agentes);
        this.materiais.set(materiais?.content || []);
      },
      error: () => this.notifications.warning('Alguns filtros de apoio não puderam ser carregados.')
    });
  }

  private filtro(): SolicitacaoMaterialFilter {
    const { filters: f, query } = this.lista.request();
    return {
      contexto: 'TECNICO',
      idObra: f.idObra,
      ocorrencia: f.ocorrencia.trim() || null,
      tipoObra: f.tipoObra,
      tipoTecnico: f.tipoTecnico,
      idEquipe: f.idEquipe,
      idAlmoxarifado: f.idAlmoxarifado,
      numeroReserva: f.numeroReserva.trim() || null,
      idAgenteReserva: f.idAgenteReserva,
      statusSeparacao: f.statusSeparacao,
      statuses: f.statuses,
      dataInicio: f.periodo.from,
      dataFim: f.periodo.to,
      referencia: f.referencia.trim() || null,
      numSerie: f.numSerie.trim() || null,
      buscaGlobal: query || null
    } as SolicitacaoMaterialFilter;
  }

  protected periodo(ponta: 'from' | 'to', data: Date | null): void {
    const atual = this.lista.filters.periodo();
    const valor = dataIso(data);
    if (atual[ponta] !== valor) this.lista.filters.periodo.set({ ...atual, [ponta]: valor });
  }

  protected createRequest(): void {
    if (this.canCreate) void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas/nova']);
  }

  protected createDevolucao(): void {
    void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas/devolucao']);
  }

  protected alternarModoExibicao(): void {
    void this.router.navigate([this.kanban ? '/almoxarifado/solicitacoes-tecnicas' : '/almoxarifado/solicitacoes-tecnicas/kanban']);
  }

  protected onKanbanNavegarParaEtapa(): void {
    void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas']);
  }

  protected abrirDetalhe(item: SolicitacaoMaterialSummaryResponse): void {
    void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas', item.id]);
  }

  protected imprimirRelatorio(item: SolicitacaoMaterialSummaryResponse): void {
    const url = this.router.serializeUrl(this.router.createUrlTree(['/almoxarifado/solicitacoes-tecnicas', item.id], { queryParams: { mode: 'print' } }));
    window.open(url, '_blank', 'noopener');
  }

  protected obraLabel(idObra?: string | null): string {
    if (!idObra) return 'Obra não informada';
    const obra = this.obras().find((item) => item.id === idObra);
    return obra ? `${obra.numero} · ${obra.titulo}` : `Obra ${idObra.slice(0, 8)}`;
  }

  protected equipeLabel(idEquipe?: string | null): string {
    if (!idEquipe) return '—';
    return this.equipes().find((item) => item.id === idEquipe)?.codigo ?? `Eq. ${idEquipe.slice(0, 8)}`;
  }

  protected planejamentoLabel(item: SolicitacaoMaterialSummaryResponse): string {
    if (item.etapaOperacional) return item.etapaOperacional === 'AGUARDANDO_PLANEJAMENTO' ? 'Não planejada' : 'Planejada';
    if (!item.dataPrevista || item.statusSeparacao === 'AGUARDANDO_AGENDAMENTO') return 'Não planejada';
    return 'Planejada';
  }

  protected planejamentoTone(item: SolicitacaoMaterialSummaryResponse): 'warning' | 'success' {
    return this.planejamentoLabel(item) === 'Planejada' ? 'success' : 'warning';
  }

  private hasPermission(permission: string): boolean {
    return this.auth.currentUser()?.permissoes?.includes(permission) ?? false;
  }
}
