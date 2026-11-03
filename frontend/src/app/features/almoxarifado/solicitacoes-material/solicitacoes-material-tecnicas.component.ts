import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
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
import { bindDrawerToUrl } from '../../../core/navigation';
import { AuthService } from '../../../core/services/auth.service';
import { DevolucaoTecnicaFormComponent } from './devolucao-tecnica-form/devolucao-tecnica-form.component';
import { SolicitacaoTecnicaFormComponent } from './solicitacao-tecnica-form/solicitacao-tecnica-form.component';
import { ConnectivityService } from '../../../core/services/connectivity.service';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../core/services/solicitacao-material-tecnica.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { dataIso, dataLocal } from '../../../shared/listagem/listagem';
import { ExportarSolicitacoesDialogComponent } from './exportar-solicitacoes-dialog.component';
import { separacaoLabel, separacaoTone, statusLabel, statusTone, tipoTecnicoEhAdicao, tipoTecnicoIcon, tipoTecnicoLabel } from './solicitacao-tecnica-presentation';
import { SolicitacoesTecnicasImportacaoComponent } from './solicitacoes-tecnicas-importacao/solicitacoes-tecnicas-importacao.component';
import { SolicitacoesTecnicasCalendarioComponent } from './solicitacoes-tecnicas-calendario/solicitacoes-tecnicas-calendario.component';
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
type VisaoTecnica = 'tabela' | 'kanban' | 'calendario';

const VISOES: { value: VisaoTecnica; label: string; icon: string; rota: string }[] = [
  { value: 'tabela', label: 'Tabela', icon: 'pi pi-table', rota: '/almoxarifado/solicitacoes-tecnicas' },
  { value: 'kanban', label: 'Quadro', icon: 'pi pi-objects-column', rota: '/almoxarifado/solicitacoes-tecnicas/kanban' },
  { value: 'calendario', label: 'Calendário', icon: 'pi pi-calendar', rota: '/almoxarifado/solicitacoes-tecnicas/calendario' }
];

/** Vocabulário dos atalhos, na ordem do atendimento. Os de exceção só aparecem quando há algo neles. */
const ATALHOS_STATUS: { value: StatusSolicitacaoMaterial; atalho: string; sempre: boolean }[] = [
  { value: 'PENDENTE', atalho: 'Aguardando aprovação', sempre: true },
  { value: 'APROVADO', atalho: 'Aguardando retirada', sempre: true },
  { value: 'ENTREGUE_PARCIAL', atalho: 'Entregue parcial', sempre: true },
  { value: 'ENTREGUE', atalho: 'Entregue', sempre: true },
  { value: 'REJEITADO', atalho: 'Rejeitado', sempre: true },
  { value: 'DEVOLVIDO_PARCIAL', atalho: 'Devolvido parcial', sempre: false },
  { value: 'DEVOLVIDO', atalho: 'Devolvido', sempre: false },
  { value: 'ESTORNADO', atalho: 'Estornado', sempre: false },
  { value: 'CANCELADO', atalho: 'Cancelado', sempre: false }
];
const ORDEM_STATUS = ATALHOS_STATUS.map((s) => s.value);
/** Status em que ainda há trabalho do almoxarifado: aprovar ou entregar. */
const STATUS_EM_ABERTO: readonly StatusSolicitacaoMaterial[] = ['PENDENTE', 'APROVADO', 'ENTREGUE_PARCIAL'];

function lerVisao(modo: unknown): VisaoTecnica {
  return modo === 'kanban' || modo === 'calendario' ? modo : 'tabela';
}

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
  styles: `
    .sol-toolbar { display: flex; align-items: center; justify-content: flex-end; gap: 0.75rem; flex-wrap: wrap; }
    .sol-atalhos { flex: 1 1 auto; min-width: 0; }
    .sol-contagem {
      display: inline-flex; align-items: center; justify-content: center; min-width: 1.375rem; height: 1.25rem; padding: 0 0.375rem;
      border-radius: 999px; background: var(--ces-surface-muted); color: var(--ces-foreground-muted);
      font-size: 0.7rem; font-weight: 700; font-variant-numeric: tabular-nums;
    }
    .ces-quick-filter[aria-pressed='true'] .sol-contagem { background: var(--ces-surface); color: var(--ces-foreground); }
    .sol-contagem--alerta { background: var(--ces-warning-soft); color: var(--ces-warning-text); }
    .sol-divisor { width: 1px; align-self: stretch; margin: 0.25rem 0.125rem; background: var(--ces-border); }
    .sol-tom { width: 0.5rem; height: 0.5rem; border-radius: 999px; background: var(--sol-tom); flex: none; }
    [data-tone='warning'] { --sol-tom: var(--ces-warning); }
    [data-tone='info'] { --sol-tom: var(--ces-info); }
    [data-tone='success'] { --sol-tom: var(--ces-success); }
    [data-tone='danger'] { --sol-tom: var(--ces-danger); }
    [data-tone='neutral'] { --sol-tom: var(--ces-foreground-subtle); }
    .sol-visoes { display: inline-flex; flex: none; padding: 2px; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface-muted); }
    .sol-visoes button {
      display: inline-flex; align-items: center; gap: 0.375rem; min-height: calc(var(--ces-control-height-sm) - 2px); padding: 0 0.75rem;
      border: 0; border-radius: calc(var(--ces-radius-md) - 2px); background: transparent; color: var(--ces-foreground-muted);
      font: inherit; font-size: var(--ces-font-control); font-weight: 600; cursor: pointer;
    }
    .sol-visoes button:hover { color: var(--ces-foreground); }
    .sol-visoes button[aria-current='page'] { background: var(--ces-surface); color: var(--ces-foreground); box-shadow: var(--ces-shadow-sm); }
    .sol-visoes button:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 1px; }
    .sol-visoes .pi { font-size: 0.8rem; }
    @media (max-width: 40rem) {
      .sol-visoes { width: 100%; }
      .sol-visoes button { flex: 1; justify-content: center; }
    }
  `,
  imports: [
    DevolucaoTecnicaFormComponent,
    SolicitacaoTecnicaFormComponent,
    SolicitacoesTecnicasCalendarioComponent,
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
        [title]="titulo"
        icon="pi pi-wrench"
        [breadcrumb]="[{ label: 'Almoxarifado' }, { label: 'Solicitações técnicas' }]"
        description="Pedidos e devoluções de material para obras: planejamento, separação, atendimento e reserva."
        helpLabel="Ajuda sobre solicitações técnicas"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-download" label="Exportar" (pressed)="exportacaoAberta.set(true)"></ces-button>
        @if (canImport) {
          <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacaoAberta.set(true)"></ces-button>
        }
        @if (podeDevolver) {
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

      <div class="sol-toolbar">
        @if (visao !== 'kanban') {
          <div class="ces-quick-filters sol-atalhos" role="group" aria-label="Mostrar só os status escolhidos">
            <button type="button" class="ces-quick-filter" [attr.aria-pressed]="!statusAplicados().length" (click)="definirStatus([])">
              Todas <span class="sol-contagem">{{ contagemTotal() ?? '–' }}</span>
            </button>
            <button type="button" class="ces-quick-filter" [attr.aria-pressed]="emAbertoAtivo()" (click)="definirStatus(emAberto)"
              title="Pendentes, aprovadas e entregues em parte: tudo o que ainda exige ação">
              <i class="pi pi-inbox" aria-hidden="true"></i> Em aberto <span class="sol-contagem" [class.sol-contagem--alerta]="(contagemAbertas() ?? 0) > 0">{{ contagemAbertas() ?? '–' }}</span>
            </button>
            <span class="sol-divisor" aria-hidden="true"></span>
            @for (s of atalhosVisiveis(); track s.value) {
              <button type="button" class="ces-quick-filter" [attr.aria-pressed]="statusAplicados().includes(s.value)" [attr.data-tone]="tomStatus(s.value)" (click)="alternarStatus(s.value)"
                [attr.aria-label]="s.atalho + ': ' + (contagens()[s.value] ?? 'contagem indisponível')">
                <span class="sol-tom" aria-hidden="true"></span> {{ s.atalho }} <span class="sol-contagem">{{ contagens()[s.value] ?? '–' }}</span>
              </button>
            }
          </div>
        }
        <nav class="sol-visoes" aria-label="Forma de visualizar">
          @for (v of visoes; track v.value) {
            <button type="button" [attr.aria-current]="visao === v.value ? 'page' : null" (click)="trocarVisao(v.value)">
              <i [class]="v.icon" aria-hidden="true"></i>{{ v.label }}
            </button>
          }
        </nav>
      </div>

      @if (visao === 'kanban') {
        <app-solicitacoes-tecnicas-kanban [filtros]="kanbanFiltros()" (navegarParaEtapa)="onKanbanNavegarParaEtapa()"></app-solicitacoes-tecnicas-kanban>
      } @else if (visao === 'calendario') {
        <app-solicitacoes-tecnicas-calendario [filtros]="calendarioFiltros()" [rotuloObra]="rotuloObra" (abrir)="abrirDetalhe($event)"></app-solicitacoes-tecnicas-calendario>
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

    @if (drawer.mode() === 'novo') {
      @if (drawerModo() === 'devolucao') {
        @if (podeDevolver) {
          <app-devolucao-tecnica-form [idObra]="drawerObra()" (fechado)="drawer.close()"></app-devolucao-tecnica-form>
        }
      } @else if (canCreate) {
        <app-solicitacao-tecnica-form [idObra]="drawerObra()" (criadas)="aoCriar()" (fechado)="drawer.close()"></app-solicitacao-tecnica-form>
      }
    }
  `
})
export class SolicitacoesMaterialTecnicasComponent {
  private readonly service = inject(SolicitacaoMaterialTecnicaService);
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly obrasService = inject(ObrasService);
  private readonly rh = inject(RhService);
  private readonly auth = inject(AuthService);
  private readonly parametros = toSignal(inject(ActivatedRoute).queryParamMap);
  protected readonly drawer = bindDrawerToUrl({ extraParams: ['modo', 'idObra'] });
  protected readonly drawerObra = computed(() => this.parametros()?.get('idObra') ?? null);
  protected readonly drawerModo = computed(() => (this.parametros()?.get('modo') === 'devolucao' ? 'devolucao' : 'adicao'));
  private readonly kanbanTela = viewChild(SolicitacoesTecnicasKanbanComponent);
  private readonly calendarioTela = viewChild(SolicitacoesTecnicasCalendarioComponent);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly visoes = VISOES;
  protected readonly visao: VisaoTecnica = lerVisao(inject(ActivatedRoute).snapshot.data['modo']);
  protected readonly titulo = this.visao === 'kanban' ? 'Quadro de solicitações técnicas' : this.visao === 'calendario' ? 'Calendário de solicitações técnicas' : 'Solicitações técnicas de material';
  protected readonly chave = this.visao === 'tabela' ? 'almox-solicitacoes-tecnicas' : `almox-solicitacoes-tecnicas-${this.visao}`;
  protected readonly isOnline = inject(ConnectivityService).isOnline;
  protected readonly canCreate = this.hasPermission('estoque:solicitacao:create');
  protected readonly canImport = this.hasPermission('estoque:solicitacao:import');
  protected readonly podeDevolver = this.hasPermission('estoque:movimentacao:write');
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
  protected readonly emAberto = STATUS_EM_ABERTO;
  protected readonly tomStatus = statusTone;
  protected readonly statusAplicados = computed(() => this.lista.applied().statuses);
  protected readonly emAbertoAtivo = computed(() => {
    const s = this.statusAplicados();
    return s.length === STATUS_EM_ABERTO.length && STATUS_EM_ABERTO.every((x) => s.includes(x));
  });
  /** Quantas solicitações há em cada status no recorte, ignorando os atalhos, para o número não sumir ao filtrar. */
  protected readonly contagens = signal<Partial<Record<StatusSolicitacaoMaterial, number>>>({});
  private readonly versaoContagens = signal(0);
  protected readonly contagemTotal = computed(() => this.somar(ORDEM_STATUS));
  protected readonly contagemAbertas = computed(() => this.somar(STATUS_EM_ABERTO));
  protected readonly atalhosVisiveis = computed(() =>
    ATALHOS_STATUS.filter((s) => s.sempre || (this.contagens()[s.value] ?? 0) > 0 || this.statusAplicados().includes(s.value))
  );
  private readonly carregaContagens = (() => {
    const consulta = computed(() => {
      this.lista.applied();
      const { statuses: _s, ...semStatus } = this.filtro();
      return { filtro: semStatus, versao: this.versaoContagens() };
    }, { equal: (a, b) => JSON.stringify(a) === JSON.stringify(b) });
    return toObservable(consulta)
      .pipe(
        switchMap(({ filtro }) => forkJoin(ORDEM_STATUS.map((status) =>
          this.service.listar({ ...filtro, statuses: [status], page: 0, size: 1 } as SolicitacaoMaterialFilter).pipe(
            map((r) => [status, r.page?.totalElements ?? 0] as const),
            catchError(() => of([status, undefined] as const))
          )
        ))),
        takeUntilDestroyed()
      )
      .subscribe((pares) => this.contagens.set(Object.fromEntries(pares.filter(([, n]) => n !== undefined))));
  })();

  private somar(status: readonly StatusSolicitacaoMaterial[]): number | null {
    const c = this.contagens();
    return status.every((s) => c[s] !== undefined) ? status.reduce((soma, s) => soma + (c[s] ?? 0), 0) : null;
  }

  protected definirStatus(status: readonly StatusSolicitacaoMaterial[]): void {
    this.lista.set('statuses', [...status]);
  }

  /** Atalho de status: liga/desliga o status, somando aos já escolhidos. */
  protected alternarStatus(status: StatusSolicitacaoMaterial): void {
    const atuais = this.statusAplicados();
    const proximos = atuais.includes(status) ? atuais.filter((s) => s !== status) : [...atuais, status];
    this.definirStatus(ORDEM_STATUS.filter((s) => proximos.includes(s)));
  }

  protected readonly calendarioFiltros = computed<Record<string, unknown>>(() => {
    this.lista.applied();
    const { page: _p, size: _s, ...resto } = this.filtro();
    return resto;
  });

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
    this.drawer.connect((modo) => {
      // A URL antiga do cadastro redireciona para cá sem guard: sem permissão, o drawer não abre.
      if (modo === 'novo' && !(this.drawerModo() === 'devolucao' ? this.podeDevolver : this.canCreate)) {
        this.notifications.warning('Você não tem permissão para criar solicitações.');
        this.drawer.close();
      }
    });
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
    if (this.canCreate) this.drawer.openNew();
  }

  protected createDevolucao(): void {
    if (this.podeDevolver) this.drawer.openNew({ modo: 'devolucao' });
  }

  protected aoCriar(): void {
    this.lista.reload();
    this.versaoContagens.update((v) => v + 1);
    this.kanbanTela()?.recarregar();
    this.calendarioTela()?.recarregar();
  }

  protected trocarVisao(visao: VisaoTecnica): void {
    if (visao === this.visao) return;
    void this.router.navigate([VISOES.find((v) => v.value === visao)!.rota], { queryParams: this.lista.queryParams() });
  }

  protected readonly rotuloObra = (idObra?: string | null): string => this.obraLabel(idObra);

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
