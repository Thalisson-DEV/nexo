import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { StepperModule } from 'primeng/stepper';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { catchError, of } from 'rxjs';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesPageHeaderComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTone,
  cesFilter,
  cesListState
} from '@ces/ui';
import { CesSelectComponent } from '@ces/ui/forms';
import { SyncReferenceSelectorComponent } from './sync-reference-selector.component';
import { SyncReferenceCatalogService, syncReferenceKind } from './sync-reference-catalog.service';
import { HelpDialogComponent } from '../../../shared/components/help-dialog/help-dialog.component';
import {
  CopilotoAreaComponent,
  CopilotoDashboardContext,
  CopilotoGatilhoComponent,
  CopilotoPainelService
} from '../../copiloto/painel';
import { getErrorMessage } from '../../../core/models/shared.models';
import { LIMITE_EXPORTACAO, dataIso, dataLocal, exportacaoPadrao } from '../../../shared/listagem/listagem';
import {
  SyncAcaoRecomendada,
  SyncCaseStatus,
  SyncDiagnostico,
  SyncErrorAttachment,
  SyncErrorCaseDetail,
  SyncErrorCaseSummary
} from '../../../core/models/sync-error.models';
import { SyncErrorCasesService } from '../../../core/services/sync-error-cases.service';
import { AuthService } from '../../../core/services/auth.service';
import { UsuarioLookupService } from '../../../core/services/usuario-lookup.service';
import { RhService } from '../../../core/services/rh.service';

interface FriendlyField {
  key: string;
  label: string;
  value: string;
  originalValue: string;
  type: 'text' | 'textarea' | 'number' | 'boolean' | 'date' | 'operation';
  isModified: boolean;
  dateValue?: Date | null;
  /** Tipo do valor recebido, para devolver número como número e série como texto. */
  jsonType?: 'string' | 'number' | 'boolean';
}

interface FriendlyMaterialItem {
  idMaterial?: string;
  codigo?: string;
  numSerie?: string;
  quantidade?: number | string;
  operacao?: string;
}

interface EditableMaterial {
  /** Item como veio do aparelho; campos não editáveis seguem intactos no reenvio. */
  base: Record<string, unknown>;
  fields: FriendlyField[];
}

interface SummaryEntry {
  label: string;
  value: string;
  href?: string;
}

interface VersionChange {
  label: string;
  before: string;
  after: string;
}

const NON_EDITABLE_KEYS = new Set([
  'tempId',
  'erroOrigem',
  'idOperacaoLocal',
  'idRegistroLocal',
  'idOperacao',
  'hash',
  'hashOriginal',
  'localUuid'
]);

/**
 * Dados que o servidor decide ou que o aparelho coletou sozinho: aparecem no
 * diagnóstico, mas não se corrigem à mão (o turno, por exemplo, é escolhido pelo
 * servidor no reenvio).
 */
const READ_ONLY_KEYS = new Set([
  'idTurno', 'latitude', 'longitude', 'latitudeInicio', 'longitudeInicio', 'latitudeFim', 'longitudeFim',
  'precisao', 'dataLancamento', 'dataInicio', 'dataFim', 'tipoNota', 'instanteOrigem'
]);

/** Campos de cada material que fazem sentido corrigir, na ordem da tela. */
const MATERIAL_KEYS = ['idMaterial', 'operacao', 'numSerie', 'quantidade'];

const OPERATION_LABELS: Record<string, string> = { APLICADO: 'Aplicado', RETIRADO: 'Retirado' };

const LOCATION_KEYS = new Set(['latitude', 'longitude', 'latitudeInicio', 'longitudeInicio', 'latitudeFim', 'longitudeFim']);

/** Ordem de leitura do resumo: identificação da nota primeiro, detalhes depois. */
const SUMMARY_ORDER = ['referenciaNota', 'idTipoServico', 'idEquipe', 'idTurno', 'dataLancamento', 'dataInicio', 'tipoNota'];

const FIELD_LABELS: Record<string, { label: string; type?: 'text' | 'textarea' | 'number' }> = {
  referenciaNota: { label: 'Número da Nota / OS' },
  idTipoServico: { label: 'Tipo de serviço' },
  tipoNota: { label: 'Tipo de nota' },
  dataLancamento: { label: 'Lançada em' },
  acoes: { label: 'Serviços executados' },
  operacao: { label: 'Operação' },
  idEquipe: { label: 'Equipe' },
  equipe: { label: 'Equipe' },
  idMunicipio: { label: 'Município' },
  municipio: { label: 'Município' },
  idTurno: { label: 'Turno de Trabalho' },
  turno: { label: 'Turno de Trabalho' },
  idVeiculo: { label: 'Veículo' },
  veiculo: { label: 'Veículo' },
  placa: { label: 'Placa do Veículo' },
  kmInicial: { label: 'Quilometragem Inicial (KM)', type: 'number' },
  kmFinal: { label: 'Quilometragem Final (KM)', type: 'number' },
  dataInicio: { label: 'Data e Hora de Início' },
  dataFim: { label: 'Data e Hora de Término' },
  dataHora: { label: 'Data e Horário' },
  dataRealizacao: { label: 'Data de Realização' },
  observacao: { label: 'Observações de Campo', type: 'textarea' },
  observacoes: { label: 'Observações de Campo', type: 'textarea' },
  idMaterial: { label: 'Material' },
  codigoMaterial: { label: 'Código do Material' },
  quantidade: { label: 'Quantidade', type: 'number' },
  numSerie: { label: 'Número de Série' },
  idAlmoxarifado: { label: 'Almoxarifado' },
  idOrigem: { label: 'Estoque de Origem' },
  idDestino: { label: 'Estoque de Destino' },
  idTipoInspecao: { label: 'Tipo de Inspeção' },
  idFormulario: { label: 'Formulário de Inspeção' },
  idProcesso: { label: 'Processo Operacional' },
  idBaseOperacional: { label: 'Base Operacional' },
  idInspecao: { label: 'Inspeção Vinculada' },
  idDesvio: { label: 'Apontamento / Desvio' },
  idColaboradorSaque: { label: 'Colaborador do Saque' },
  idRequisitante: { label: 'Solicitante' },
  idUsuario: { label: 'Usuário do Aplicativo' },
  saqueForaEquipe: { label: 'Saque Fora da Equipe' },
  contexto: { label: 'Contexto da Evidência' },
  motivo: { label: 'Motivo / Descrição' },
  simulacao: { label: 'Tipo de Registro' }
};

@Component({
  selector: 'app-sync-error-cases',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    InputTextModule,
    DialogModule,
    TooltipModule,
    TextareaModule,
    StepperModule,
    CesButtonComponent,
    CesCalloutComponent,
    CesDatepickerComponent,
    CesSelectComponent,
    SyncReferenceSelectorComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesPageHeaderComponent,
    CesStatusComponent,
    HelpDialogComponent,
    CopilotoAreaComponent,
    CopilotoGatilhoComponent
  ],
  styles: [`
    :host ::ng-deep {
      .p-stepper {
        --p-stepper-separator-background: var(--ces-border);
        --p-stepper-separator-active-background: var(--p-primary-600);
      }
      .p-stepper-separator {
        background-color: var(--ces-border) !important;
      }
      .custom-select, .custom-date {
        width: 100%;
      }
    }
  `],
  template: `
    <app-copiloto-area [contexto]="contextoCopiloto">
    <div class="ces-page">
      <ces-page-header
        title="Sincronismo"
        icon="pi pi-sync"
        [breadcrumb]="breadcrumbItems"
        description="Registros do aplicativo de campo que o servidor recusou: o que aconteceu, o que fazer e o reenvio."
        helpLabel="Ajuda sobre o sincronismo"
        (help)="helpDialog.show()"
      >
        @if (copilotoDisponivel) {
          <app-copiloto-gatilho actions></app-copiloto-gatilho>
        }
      </ces-page-header>

      <ces-filter-panel #painel storageKey="admin-sync-erros" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Situação" [options]="statusOptions" [(value)]="lista.filters.status"></ces-select>
          <ces-select label="Tipo de atividade" [options]="operationOptions" [(value)]="lista.filters.tipo"></ces-select>
          <ces-datepicker label="Enviados a partir de" [value]="dataLocal(lista.filters.envio().from)" [clearable]="true" (valueChange)="periodo('from', $event)"></ces-datepicker>
          <ces-datepicker label="Enviados até" [value]="dataLocal(lista.filters.envio().to)" [clearable]="true" (valueChange)="periodo('to', $event)"></ces-datepicker>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Pendências de sincronismo"
        mode="server"
        storageKey="admin-sync-erros"
        exportFileName="erros-sincronizacao"
        searchPlaceholder="Colaborador, atividade ou motivo"
        rowActionLabel="Tratar"
        rowActionIcon="pi pi-wrench"
        noResultsTitle="Nenhum erro de sincronização com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os erros de sincronização' : 'Tudo sincronizado'"
        [stateDescription]="resultado.error() ?? 'Nenhum registro do campo aguarda tratamento.'"
        [filtered]="lista.filtered()"
        [exportRows]="exportar"
        [exportLimit]="limiteExportacao"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (rowActivate)="open($event)"
      >
        <ng-template cesCell="atividade" let-item>
          <span class="ces-cell-strong"><i [class]="getOperationIcon(item.operationType)" aria-hidden="true"></i> {{ getOperationLabel(item.operationType) }}</span>
          @if (getRecordBusinessLabel(item); as recordRef) {
            <span class="ces-cell-muted">{{ recordRef }}</span>
          }
        </ng-template>
        <ng-template cesCell="problema" let-item>
          <span class="ces-cell-strong">{{ diagnostico(item).titulo }}</span>
          <span class="ces-cell-muted" [title]="diagnostico(item).explicacao">{{ diagnostico(item).explicacao }}</span>
        </ng-template>
        <ng-template cesCell="status" let-item>
          <ces-status [tone]="statusTone(item.status)" [icon]="statusIcon(item.status)">{{ statusLabel(item.status) }}</ces-status>
        </ng-template>
      </ces-data-table>
    </div>
    </app-copiloto-area>

    <!-- Modal de Tratamento com Stepper -->
    <p-dialog
      [visible]="displayDetail()"
      (visibleChange)="onDialogVisibleChange($event)"
      [header]="detail() ? 'Tratamento de Pendência: ' + getOperationLabel(detail()!.operationType) : 'Tratamento de Pendência'"
      [modal]="true"
      [style]="{ width: '95vw', maxWidth: '880px' }"
      [draggable]="false"
      [resizable]="false"
      styleClass="custom-dialog"
      appendTo="body"
    >
      @if (detail(); as current) {
        <!-- Top Overview Header -->
        <div class="mb-4 p-3.5 bg-slate-50 dark:bg-slate-900/60 rounded-xl border border-slate-200 dark:border-slate-800 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="font-bold text-slate-800 dark:text-slate-100 flex items-center gap-1.5">
              <i [class]="getOperationIcon(current.operationType) + ' text-primary-600 dark:text-primary-400 text-sm'"></i>
              {{ getOperationLabel(current.operationType) }}
            </span>
            @if (getRecordBusinessLabel(current); as recordRef) {
              <span class="text-slate-300 dark:text-slate-700">•</span>
              <span class="font-semibold text-primary-700 dark:text-primary-400">{{ recordRef }}</span>
            }
          </div>
          <div class="flex items-center gap-2">
            <ces-status [tone]="statusTone(current.status)" [icon]="statusIcon(current.status)">{{ statusLabel(current.status) }}</ces-status>
            @if (!isAssignedToCurrentUser(current) && canManage()) {
              <p-button
                label="Assumir Atendimento"
                icon="pi pi-user-plus"
                size="small"
                [text]="true"
                (onClick)="assignToMe(current)"
                [loading]="saving()"
                styleClass="!text-primary-700 dark:!text-primary-400 !font-bold !text-[11px]"
              ></p-button>
            } @else if (current.assignedToName) {
              <span class="text-[11px] text-slate-600 dark:text-slate-300">
                <i class="pi pi-user text-slate-400 dark:text-slate-500"></i> Atendido por: <strong>{{ getUserDisplayName(current.assignedToName, true) }}</strong>
              </span>
            }
          </div>
        </div>

        <!-- Stepper -->
        <p-stepper [value]="activeStep()">
          <p-step-list class="!justify-center mb-5">
            <!-- Step 1 Header -->
            <p-step [value]="1">
              <ng-template #content let-activateCallback="activateCallback" let-value="value">
                <button
                  type="button"
                  class="bg-transparent border-0 inline-flex flex-col items-center gap-1.5 cursor-pointer"
                  (click)="activeStep.set(1)"
                >
                  <span
                    class="rounded-full border-2 w-8 h-8 inline-flex items-center justify-center transition-all duration-300"
                    [ngClass]="{
                      'bg-primary-700 dark:bg-primary-600 text-white border-primary-700 dark:border-primary-600 shadow-sm': value <= activeStep(),
                      'border-slate-200 dark:border-slate-700 text-slate-400 dark:text-slate-500 bg-transparent': value > activeStep(),
                    }"
                  >
                    <i class="pi pi-search text-[10px]"></i>
                  </span>
                  <span
                    class="text-[10px] font-bold uppercase tracking-wider transition-colors duration-300"
                    [ngClass]="value <= activeStep() ? 'text-primary-700 dark:text-primary-400' : 'text-slate-400 dark:text-slate-500'"
                  >
                    1. Diagnóstico
                  </span>
                </button>
              </ng-template>
            </p-step>

            <!-- Step 2 Header -->
            <p-step [value]="2">
              <ng-template #content let-activateCallback="activateCallback" let-value="value">
                <button
                  type="button"
                  class="bg-transparent border-0 inline-flex flex-col items-center gap-1.5 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
                  (click)="activeStep.set(2)"
                  [disabled]="!canCorrect(current)"
                >
                  <span
                    class="rounded-full border-2 w-8 h-8 inline-flex items-center justify-center transition-all duration-300"
                    [ngClass]="{
                      'bg-primary-700 dark:bg-primary-600 text-white border-primary-700 dark:border-primary-600 shadow-sm': value <= activeStep(),
                      'border-slate-200 dark:border-slate-700 text-slate-400 dark:text-slate-500 bg-transparent': value > activeStep(),
                    }"
                  >
                    <i class="pi pi-pencil text-[10px]"></i>
                  </span>
                  <span
                    class="text-[10px] font-bold uppercase tracking-wider transition-colors duration-300"
                    [ngClass]="value <= activeStep() ? 'text-primary-700 dark:text-primary-400' : 'text-slate-400 dark:text-slate-500'"
                  >
                    2. Correção
                  </span>
                </button>
              </ng-template>
            </p-step>

            <!-- Step 3 Header -->
            <p-step [value]="3">
              <ng-template #content let-activateCallback="activateCallback" let-value="value">
                <button
                  type="button"
                  class="bg-transparent border-0 inline-flex flex-col items-center gap-1.5 cursor-pointer"
                  (click)="activeStep.set(3)"
                >
                  <span
                    class="rounded-full border-2 w-8 h-8 inline-flex items-center justify-center transition-all duration-300"
                    [ngClass]="{
                      'bg-primary-700 dark:bg-primary-600 text-white border-primary-700 dark:border-primary-600 shadow-sm': value <= activeStep(),
                      'border-slate-200 dark:border-slate-700 text-slate-400 dark:text-slate-500 bg-transparent': value > activeStep(),
                    }"
                  >
                    <i class="pi pi-check-circle text-[10px]"></i>
                  </span>
                  <span
                    class="text-[10px] font-bold uppercase tracking-wider transition-colors duration-300"
                    [ngClass]="value <= activeStep() ? 'text-primary-700 dark:text-primary-400' : 'text-slate-400 dark:text-slate-500'"
                  >
                    3. Revisão e Reenvio
                  </span>
                </button>
              </ng-template>
            </p-step>
          </p-step-list>

          <p-step-panels>
            <!-- Step 1: Diagnóstico -->
            <p-step-panel [value]="1">
              <ng-template #content let-activateCallback="activateCallback">
                <div class="flex flex-col gap-4 mt-1">
                  <!-- Diagnóstico: o que aconteceu e o que fazer (ou o desfecho, se encerrado) -->
                  @if (current.status === 'RESOLVIDO' || current.status === 'REJEITADO') {
                    <ces-callout [tone]="current.status === 'RESOLVIDO' ? 'success' : 'neutral'" [title]="current.status === 'RESOLVIDO' ? 'Registro sincronizado' : 'Registro descartado'" [dense]="true">
                      <p class="text-xs leading-relaxed text-[var(--ces-foreground)]">{{ desfecho(current) }}</p>
                      <p class="mt-1.5 text-[11px] leading-relaxed text-[var(--ces-foreground-muted)]">
                        <strong>O que tinha impedido:</strong> {{ diagnostico(current).titulo }}. {{ diagnostico(current).explicacao }}
                      </p>
                    </ces-callout>
                  } @else {
                    <ces-callout [tone]="toneDaAcao(diagnostico(current).acao)" [title]="diagnostico(current).titulo" [dense]="true">
                      <p class="mb-1.5 text-xs leading-relaxed text-[var(--ces-foreground-muted)]">{{ diagnostico(current).explicacao }}</p>
                      <p class="text-xs leading-relaxed text-[var(--ces-foreground)]"><strong>O que fazer:</strong> {{ diagnostico(current).orientacao }}</p>
                    </ces-callout>
                  }
                  @if (copilotoDisponivel) {
                    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 p-3 rounded-xl border border-slate-200 dark:border-slate-800">
                      <p class="text-xs text-[var(--ces-foreground-muted)]">
                        Quer uma segunda opinião? O Copiloto confere turnos e notas da equipe e aponta a causa provável e a resolução. Quem decide é você.
                      </p>
                      <ces-button label="Pré-análise do Copiloto" icon="pi pi-sparkles" variant="outline" size="sm" className="shrink-0" (pressed)="preAnalisar(current)"></ces-button>
                    </div>
                  }

                  <!-- Card: Identificação do Envio -->
                  <div class="p-4 bg-slate-50 dark:bg-slate-900/40 rounded-xl border border-slate-200 dark:border-slate-800">
                    <div class="font-bold text-xs text-slate-700 dark:text-slate-200 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                      <i class="pi pi-send text-primary-600 dark:text-primary-400"></i> Informações do Envio
                    </div>
                    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
                      <div>
                        <span class="text-slate-400 dark:text-slate-500 block text-[11px]">Enviado por</span>
                        <strong class="text-slate-800 dark:text-slate-100">{{ getUserDisplayName(current.sourceUserName, false) }}</strong>
                      </div>
                      <div>
                        <span class="text-slate-400 dark:text-slate-500 block text-[11px]">Recebido em</span>
                        <span class="text-slate-700 dark:text-slate-200">{{ current.receivedAt ? (current.receivedAt | date:'dd/MM/yyyy HH:mm') : '—' }}</span>
                      </div>
                      <div>
                        <span class="text-slate-400 dark:text-slate-500 block text-[11px]">Reenvios pelo tratamento</span>
                        <span class="text-slate-700 dark:text-slate-200">{{ current.attempts === 0 ? 'Nenhum' : current.attempts }}</span>
                      </div>
                      <div>
                        <span class="text-slate-400 dark:text-slate-500 block text-[11px]">Responsável</span>
                        <span class="text-slate-700 dark:text-slate-200">{{ getUserDisplayName(current.assignedToName, true) }}</span>
                      </div>
                    </div>
                  </div>

                  <!-- Card: Dados Informados pela Equipe -->
                  <div class="p-4 bg-slate-50 dark:bg-slate-900/40 rounded-xl border border-slate-200 dark:border-slate-800">
                    <div class="font-bold text-xs text-slate-700 dark:text-slate-200 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                      <i class="pi pi-list text-primary-600 dark:text-primary-400"></i> {{ current.correction ? 'Dados após a última correção' : 'Dados informados no aplicativo' }}
                    </div>
                    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 text-xs">
                      @for (entry of getFriendlySummaryEntries(payloadAtual(current)); track entry.label) {
                        <div class="p-2.5 bg-white dark:bg-slate-900/80 rounded-lg border border-slate-200 dark:border-slate-800">
                          <span class="text-slate-400 dark:text-slate-500 block text-[11px] font-medium">{{ entry.label }}</span>
                          @if (entry.href) {
                            <a [href]="entry.href" target="_blank" rel="noopener" class="font-semibold text-primary-700 dark:text-primary-400 break-words underline-offset-2 hover:underline">
                              {{ entry.value }} <i class="pi pi-external-link text-[9px]" aria-hidden="true"></i>
                            </a>
                          } @else {
                            <strong class="text-slate-800 dark:text-slate-100 break-words">{{ entry.value }}</strong>
                          }
                        </div>
                      }
                      @if (getFriendlySummaryEntries(payloadAtual(current)).length === 0) {
                        <span class="text-slate-400 dark:text-slate-500 text-xs italic col-span-3">Nenhum campo textual registrado.</span>
                      }
                    </div>

                    @if (getComplexItems(payloadAtual(current)).acoes.length > 0) {
                      <div class="mt-4 pt-3 border-t border-slate-200 dark:border-slate-800">
                        <span class="text-[11px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400 mb-2 block">
                          Serviços executados ({{ getComplexItems(payloadAtual(current)).acoes.length }})
                        </span>
                        <ul class="flex flex-col gap-1.5 text-xs">
                          @for (acao of getComplexItems(payloadAtual(current)).acoes; track $index) {
                            <li class="p-2.5 bg-white dark:bg-slate-900/80 rounded-lg border border-slate-200 dark:border-slate-800 font-semibold text-slate-800 dark:text-slate-200">{{ nomeDaReferencia(acao, 'Serviço') }}</li>
                          }
                        </ul>
                      </div>
                    }

                    @if (getComplexItems(payloadAtual(current)).materiais.length > 0) {
                      <div class="mt-4 pt-3 border-t border-slate-200 dark:border-slate-800">
                        <span class="text-[11px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400 mb-2 block">
                          Materiais informados ({{ getComplexItems(payloadAtual(current)).materiais.length }})
                        </span>
                        <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                          @for (mat of getComplexItems(payloadAtual(current)).materiais; track $index) {
                            <div class="p-2.5 bg-white dark:bg-slate-900/80 rounded-lg border flex justify-between items-start gap-3"
                              [ngClass]="serieRepetida(current, mat.numSerie) ? 'border-[var(--ces-danger)]' : 'border-slate-200 dark:border-slate-800'">
                              <div class="min-w-0">
                                <span class="font-semibold text-slate-800 dark:text-slate-200 block">{{ getMaterialLabel(mat) }}</span>
                                @if (mat.numSerie) {
                                  <span class="text-[11px] block" [ngClass]="serieRepetida(current, mat.numSerie) ? 'text-[var(--ces-danger-text)] font-semibold' : 'text-slate-500 dark:text-slate-400'">
                                    Série {{ mat.numSerie }}{{ serieRepetida(current, mat.numSerie) ? ' · repetida na nota' : '' }}
                                  </span>
                                }
                              </div>
                              <div class="text-right shrink-0">
                                <span class="font-bold text-primary-700 dark:text-primary-400">{{ mat.quantidade ?? 1 }}</span>
                                @if (mat.operacao) {
                                  <span class="text-[11px] text-slate-500 dark:text-slate-400 block">{{ operationLabel(mat.operacao) }}</span>
                                }
                              </div>
                            </div>
                          }
                        </div>
                      </div>
                    }
                  </div>

                  <!-- Card: Correções feitas no tratamento -->
                  @if (current.versoes?.length) {
                    <div class="p-4 bg-slate-50 dark:bg-slate-900/40 rounded-xl border border-slate-200 dark:border-slate-800">
                      <div class="font-bold text-xs text-slate-700 dark:text-slate-200 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                        <i class="pi pi-history text-primary-600 dark:text-primary-400"></i> Correções feitas
                      </div>
                      <ol class="flex flex-col gap-2 text-xs">
                        @for (versao of current.versoes; track versao.numero; let i = $index) {
                          <li class="p-3 bg-white dark:bg-slate-900/80 rounded-lg border border-slate-200 dark:border-slate-800">
                            <div class="flex flex-wrap justify-between gap-2">
                              <strong class="text-slate-800 dark:text-slate-100">Correção {{ versao.numero }} · {{ getUserDisplayName(versao.idUsuario, true) }}</strong>
                              <span class="text-[11px] text-slate-500 dark:text-slate-400">{{ versao.criadaEm ? (versao.criadaEm | date:'dd/MM/yyyy HH:mm') : '' }}</span>
                            </div>
                            <p class="mt-1 text-slate-600 dark:text-slate-300">{{ versao.justificativa }}</p>
                            @if (alteracoesDaVersao(current, i).length) {
                              <ul class="mt-2 flex flex-col gap-1">
                                @for (m of alteracoesDaVersao(current, i); track $index) {
                                  <li class="text-[11px] text-slate-600 dark:text-slate-300">
                                    {{ m.label }}: <span class="line-through text-slate-400 dark:text-slate-500">{{ m.before || '(vazio)' }}</span>
                                    <i class="pi pi-arrow-right text-[8px] mx-1 text-slate-400" aria-hidden="true"></i>
                                    <strong class="text-[var(--ces-success-text)]">{{ m.after || '(vazio)' }}</strong>
                                  </li>
                                }
                              </ul>
                            }
                          </li>
                        }
                      </ol>
                    </div>
                  }

                  <!-- Card: Fotos e Evidências Anexadas -->
                  @if (current.attachments.length > 0) {
                    <div class="p-4 bg-slate-50 dark:bg-slate-900/40 rounded-xl border border-slate-200 dark:border-slate-800">
                      <div class="font-bold text-xs text-slate-700 dark:text-slate-200 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                        <i class="pi pi-camera text-primary-600 dark:text-primary-400"></i> Fotos e Evidências Anexadas ({{ current.attachments.length }})
                      </div>
                      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        @for (att of current.attachments; track att.id) {
                          <div class="p-3 bg-white dark:bg-slate-900/80 rounded-lg border border-slate-200 dark:border-slate-800 flex items-center justify-between gap-3 text-xs">
                            <div class="flex items-center gap-2.5 min-w-0">
                              <div class="w-8 h-8 rounded-lg bg-primary-50 dark:bg-primary-950/60 text-primary-600 dark:text-primary-400 flex items-center justify-center shrink-0">
                                <i class="pi pi-image text-sm"></i>
                              </div>
                              <div class="min-w-0">
                                <span class="font-medium text-slate-800 dark:text-slate-200 block truncate" [pTooltip]="att.fileName">{{ att.fileName }}</span>
                                <span class="text-[10px] text-slate-400 dark:text-slate-500">{{ formatFileSize(att.size) }}</span>
                              </div>
                            </div>
                            <p-button
                              icon="pi pi-download"
                              label="Baixar"
                              size="small"
                              [text]="true"
                              (onClick)="downloadAttachment(current.id, att)"
                              styleClass="!text-primary-700 dark:!text-primary-400 !font-bold !text-[11px]"
                            ></p-button>
                          </div>
                        }
                      </div>
                    </div>
                  }
                </div>

                <!-- Footer Navigation Step 1 -->
                <div class="flex flex-wrap justify-between items-center gap-2 mt-6 pt-3 border-t border-slate-200 dark:border-slate-800">
                  <p-button
                    label="Fechar"
                    icon="pi pi-times"
                    [text]="true"
                    (onClick)="displayDetail.set(false)"
                    styleClass="!text-slate-500 dark:!text-slate-400 !text-xs !font-bold"
                  ></p-button>

                  @if (current.status === 'RESOLVIDO' || current.status === 'REJEITADO') {
                    <span class="text-xs text-[var(--ces-foreground-muted)]">Caso encerrado.</span>
                  } @else if (canCorrect(current)) {
                    <div class="flex flex-wrap justify-end gap-2">
                      @if (diagnostico(current).acao === 'DESCARTAR') {
                        <ces-button label="Descartar registro" icon="pi pi-trash" variant="destructive" size="sm" (pressed)="irParaDescarte()"></ces-button>
                      }
                      <ces-button label="Corrigir dados" icon="pi pi-pencil" [variant]="diagnostico(current).acao === 'CORRIGIR' ? 'primary' : 'outline'" size="sm" (pressed)="activeStep.set(2)"></ces-button>
                      @if (podeReenviarSemAlterar(current)) {
                        <ces-button label="Reenviar sem alterar" icon="pi pi-send" [variant]="diagnostico(current).acao === 'REENVIAR' ? 'primary' : 'outline'" size="sm"
                          [loading]="saving()" [disabled]="saving()" (pressed)="reenviarSemAlterar(current)"></ces-button>
                      }
                    </div>
                  } @else {
                    <span class="text-xs text-slate-400 dark:text-slate-500 italic">Você não possui permissão para tratar este registro.</span>
                  }
                </div>
              </ng-template>
            </p-step-panel>

            <!-- Step 2: Correção -->
            <p-step-panel [value]="2">
              <ng-template #content let-activateCallback="activateCallback">
                <div class="flex flex-col gap-4 mt-1">
                  <!-- Info Banner -->
                  <ces-callout tone="info" title="Ajuste só o que estiver errado" [dense]="true">
                    {{ diagnostico(current).orientacao }} O envio original fica guardado para consulta.
                  </ces-callout>

                  <!-- Campos Editáveis -->
                  <div class="space-y-3">
                    @for (field of editableFields(); track field.key) {
                      <div class="flex flex-col gap-1">
                        @if (referenceKind(field.key); as kind) {
                          <app-sync-reference-selector [kind]="kind" [label]="field.label" [value]="field.value" (valueChange)="setFieldValue(field, $event)" />
                        } @else {
                        @if (field.type !== 'boolean' && field.type !== 'date') {
                        <label class="filter-label !text-slate-700 dark:!text-slate-300 font-medium text-xs flex items-center justify-between">
                          <span>{{ field.label }}</span>
                          @if (field.isModified) {
                            <span class="text-[10px] font-bold text-amber-600 dark:text-amber-400 uppercase tracking-tight bg-amber-50 dark:bg-amber-950/50 px-1.5 py-0.5 rounded border border-amber-200 dark:border-amber-800">Modificado</span>
                          }
                        </label>
                        }
                        @if (field.type === 'boolean') {
                          <ces-select [label]="field.label" [options]="booleanOptions" [value]="field.value" (valueChange)="setFieldValue(field, $event)" />
                        } @else if (field.type === 'date') {
                          <ces-datepicker [label]="field.label" [value]="field.dateValue ?? null" (valueChange)="setDateValue(field, $event)" />
                        } @else if (field.type === 'textarea') {
                          <textarea
                            pTextarea
                            [(ngModel)]="field.value"
                            (ngModelChange)="checkFieldModified(field)"
                            rows="2"
                            class="custom-input !w-full"
                            placeholder="Informe o valor corrigido"
                          ></textarea>
                        } @else {
                          <input
                            pInputText
                            [(ngModel)]="field.value"
                            (ngModelChange)="checkFieldModified(field)"
                            class="custom-input !w-full"
                            placeholder="Informe o valor corrigido"
                          />
                        }
                        }
                      </div>
                    }

                    @if (acoesKey()) {
                      <div class="space-y-2 pt-3 border-t border-slate-200 dark:border-slate-800">
                        <h4 class="font-bold text-xs text-[var(--ces-foreground)]">Serviços executados ({{ editableAcoes().length }})</h4>
                        @for (acao of editableAcoes(); track acao; let index = $index) {
                          <div class="flex items-center justify-between gap-2 p-2.5 rounded-lg border border-slate-200 dark:border-slate-800 text-xs">
                            <span class="font-semibold text-[var(--ces-foreground)]">{{ nomeDaReferencia(acao, 'Serviço') }}</span>
                            <ces-button [label]="'Remover serviço ' + (index + 1)" icon="pi pi-trash" variant="ghost" size="sm" (pressed)="removerAcao(index)"></ces-button>
                          </div>
                        }
                        <app-sync-reference-selector kind="action" label="Adicionar serviço" [value]="null" (valueChange)="adicionarAcao($event)" />
                      </div>
                    }

                    @if (materialKey()) {
                      <div class="space-y-3 pt-3 border-t border-slate-200 dark:border-slate-800">
                        <div class="flex items-center justify-between gap-2">
                          <h4 class="font-bold text-xs text-[var(--ces-foreground)]">Materiais ({{ editableMaterials().length }})</h4>
                          <ces-button label="Adicionar material" icon="pi pi-plus" variant="secondary" size="sm" (pressed)="addMaterial()"></ces-button>
                        </div>
                        @for (material of editableMaterials(); track material; let index = $index) {
                          <div class="p-3 rounded-xl border border-slate-200 dark:border-slate-800 space-y-2">
                            <div class="flex items-center justify-between">
                              <span class="font-semibold text-xs text-[var(--ces-foreground)]">Material {{ index + 1 }}</span>
                              <ces-button [label]="'Remover material ' + (index + 1)" icon="pi pi-trash" variant="destructive" size="sm" (pressed)="removeMaterial(index)"></ces-button>
                            </div>
                            @for (field of material.fields; track field.key) {
                              @if (referenceKind(field.key); as kind) {
                                <app-sync-reference-selector [kind]="kind" [label]="field.label" [value]="field.value" (valueChange)="setFieldValue(field, $event)" />
                              } @else if (field.type === 'operation') {
                                <ces-select [label]="field.label" [options]="operacaoMaterialOptions" [value]="field.value" (valueChange)="setFieldValue(field, $event)" />
                              } @else {
                                <label class="flex flex-col gap-1 text-xs text-[var(--ces-foreground)]">
                                  <span>{{ field.label }}</span>
                                  <input pInputText [(ngModel)]="field.value" (ngModelChange)="checkFieldModified(field); revalidarSeries()" class="custom-input !w-full" [attr.inputmode]="field.type === 'number' ? 'decimal' : null"
                                    [attr.aria-invalid]="field.key === 'numSerie' && seriesRepetidasNaCorrecao().has(normalizarSerie(field.value)) ? true : null" />
                                  @if (field.key === 'numSerie' && seriesRepetidasNaCorrecao().has(normalizarSerie(field.value))) {
                                    <span class="text-[11px] font-semibold text-[var(--ces-danger-text)]">Esta série aparece em outro material da nota. Cada série entra uma vez só.</span>
                                  }
                                </label>
                              }
                            }
                          </div>
                        }
                      </div>
                    }

                    @for (field of complexFields(); track field.key) {
                      <label class="flex flex-col gap-1 text-xs text-[var(--ces-foreground)]">
                        <span>{{ field.label }} (dados avançados)</span>
                        <textarea pTextarea [(ngModel)]="field.value" (ngModelChange)="checkFieldModified(field)" rows="4" class="custom-input !w-full font-mono"></textarea>
                      </label>
                    }

                    @if (editableFields().length === 0 && !materialKey() && !acoesKey() && complexFields().length === 0) {
                      <div class="p-4 bg-slate-50 dark:bg-slate-900/40 rounded-xl border border-slate-200 dark:border-slate-800 text-xs text-slate-500 dark:text-slate-400 text-center">
                        Este registro não possui campos cadastrais diretos de edição (os dados principais e anexos estão preservados). Preencha a justificativa abaixo para prosseguir com a revisão e reprocessamento.
                      </div>
                    }
                  </div>

                  <!-- Campo de Justificativa (Obrigatório) -->
                  <div class="flex flex-col gap-1.5 p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900/40">
                    <label class="filter-label !text-slate-800 dark:!text-slate-200 !text-xs font-bold flex items-center gap-1">
                      <i class="pi pi-comment text-primary-600 dark:text-primary-400"></i> Por que esta correção está sendo feita? <span class="text-red-500">*</span>
                    </label>
                    <p class="text-[11px] text-slate-400 dark:text-slate-500">Descreva o motivo de forma simples para fins de auditoria e controle operacional.</p>
                    <textarea
                      id="sync-justificativa"
                      pTextarea
                      [(ngModel)]="justification"
                      rows="3"
                      class="custom-input !w-full !resize-none"
                      placeholder="Ex: Placa do veículo ajustada após conferência com o encarregado da base operacional."
                    ></textarea>
                    @if (showJustificationError()) {
                      <span class="text-[11px] text-red-500 dark:text-red-400 font-semibold flex items-center gap-1">
                        <i class="pi pi-exclamation-circle text-[10px]"></i> Por favor, informe a justificativa da alteração para continuar.
                      </span>
                    }
                  </div>
                </div>

                @if (canCorrect(current)) {
                  <div class="flex justify-end mt-3">
                    <ces-button label="Descartar registro" icon="pi pi-trash" variant="destructive" size="sm" [disabled]="saving()" (pressed)="onDiscard(current)"></ces-button>
                  </div>
                }

                <!-- Footer Navigation Step 2 -->
                <div class="flex justify-between items-center mt-6 pt-3 border-t border-slate-200 dark:border-slate-800">
                  <p-button
                    label="VOLTAR AO DIAGNÓSTICO"
                    icon="pi pi-arrow-left"
                    [text]="true"
                    (onClick)="activeStep.set(1)"
                    styleClass="!text-primary-700 dark:!text-primary-400 !text-xs !font-bold"
                  ></p-button>

                  <p-button
                    label="SALVAR AJUSTES E AVANÇAR"
                    icon="pi pi-save"
                    iconPos="right"
                    (onClick)="onSaveCorrection(current)"
                    [loading]="saving()"
                    styleClass="!rounded-xl !bg-primary-700 hover:!bg-primary-800 dark:!bg-primary-600 dark:hover:!bg-primary-500 !border-primary-700 dark:!border-primary-600 !text-white !py-2.5 !px-6 !font-bold !text-xs !shadow-sm"
                  ></p-button>
                </div>
              </ng-template>
            </p-step-panel>

            <!-- Step 3: Revisão e Reenvio -->
            <p-step-panel [value]="3">
              <ng-template #content let-activateCallback="activateCallback">
                <div class="flex flex-col gap-4 mt-1">
                  <!-- Card: Resumo das Modificações -->
                  <div class="p-4 bg-slate-50 dark:bg-slate-900/40 rounded-xl border border-slate-200 dark:border-slate-800">
                    <div class="font-bold text-xs text-slate-700 dark:text-slate-200 uppercase tracking-wider mb-3 flex items-center gap-1.5">
                      <i class="pi pi-check-square text-emerald-600 dark:text-emerald-400"></i> Resumo das Modificações Realizadas
                    </div>
                    @if (getModifiedFieldsList().length > 0) {
                      <div class="space-y-2 mb-3">
                        @for (item of getModifiedFieldsList(); track item.key) {
                          <div class="p-2.5 bg-white dark:bg-slate-900/80 rounded-lg border border-slate-200 dark:border-slate-800 text-xs flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                            <span class="font-medium text-slate-600 dark:text-slate-300">{{ item.label }}:</span>
                            <div class="flex items-center gap-2">
                              <span class="text-slate-400 dark:text-slate-500 line-through text-[11px]">{{ item.originalValue || '(vazio)' }}</span>
                              <i class="pi pi-arrow-right text-[9px] text-slate-400 dark:text-slate-500"></i>
                              <strong class="text-emerald-700 dark:text-emerald-400">{{ item.value }}</strong>
                            </div>
                          </div>
                        }
                      </div>
                    } @else {
                      <p class="text-xs text-slate-500 dark:text-slate-400 italic mb-3">Nenhum campo pontual modificado (registro mantido com justificativa).</p>
                    }

                    <div class="pt-2.5 border-t border-slate-200 dark:border-slate-800 text-xs">
                      <span class="text-slate-400 dark:text-slate-500 block text-[11px] mb-0.5">Justificativa informada:</span>
                      <strong class="text-slate-700 dark:text-slate-200 italic">"{{ justification || current.correction?.justification || 'Sem justificativa adicional' }}"</strong>
                    </div>
                  </div>

                  <!-- Card: Liberar para Reprocessamento -->
                  <div class="p-4 bg-[var(--ces-success-soft)] border border-[color-mix(in_srgb,var(--ces-success)_32%,transparent)] border-l-4 border-l-[var(--ces-success)] rounded-xl flex flex-col sm:flex-row items-center justify-between gap-4">
                    <div class="flex items-center gap-3">
                      <div class="w-10 h-10 rounded-full bg-[color-mix(in_srgb,var(--ces-success)_20%,transparent)] text-[var(--ces-success-text)] flex items-center justify-center shrink-0">
                        <i class="pi pi-sync text-lg"></i>
                      </div>
                      <div>
                        <h4 class="font-bold text-xs text-[var(--ces-foreground)] uppercase tracking-wider">Tudo pronto para reenviar</h4>
                        <p class="text-xs text-[var(--ces-foreground-muted)]">O servidor tenta gravar o registro com os dados corrigidos. Se recusar, o caso continua aqui com o novo motivo.</p>
                      </div>
                    </div>
                    <ces-button
                      label="Reenviar agora"
                      icon="pi pi-check"
                      variant="success"
                      size="md"
                      [loading]="saving()"
                      [disabled]="!canReprocess(current) || saving()"
                      (pressed)="onReprocess(current)"
                    ></ces-button>
                  </div>
                </div>

                <!-- Footer Navigation Step 3 -->
                <div class="flex justify-between items-center mt-6 pt-3 border-t border-slate-200 dark:border-slate-800">
                  <p-button
                    label="VOLTAR AOS AJUSTES"
                    icon="pi pi-arrow-left"
                    [text]="true"
                    (onClick)="activeStep.set(2)"
                    styleClass="!text-primary-700 dark:!text-primary-400 !text-xs !font-bold"
                  ></p-button>

                  <p-button
                    label="CONCLUIR E FECHAR"
                    icon="pi pi-check"
                    (onClick)="displayDetail.set(false)"
                    styleClass="!rounded-xl !bg-slate-700 hover:!bg-slate-800 dark:!bg-slate-800 dark:hover:!bg-slate-700 !border-slate-700 dark:!border-slate-600 !py-2.5 !px-6 !font-bold !text-xs !text-white"
                  ></p-button>
                </div>
              </ng-template>
            </p-step-panel>
          </p-step-panels>
        </p-stepper>
      }
    </p-dialog>

    <!-- Guia de Ajuda Amigável -->
    <app-help-dialog #helpDialog title="Guia da tela: sincronismo">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-info-circle"></i> 1. O que é esta tela?</h4>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-mobile"></i></div>
          <div class="help-text">
            <span class="help-text-title">Registros Recebidos do Campo</span>
            <span class="help-text-desc">
              Esta tela reúne os lançamentos realizados pelas equipes no aplicativo móvel (abertura/fechamento de turno, execuções de serviço, solicitações de material ou inspeções de segurança) que necessitam de conferência ou correção antes de entrarem no sistema da empresa.
            </span>
          </div>
        </div>
      </div>

      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-step-forward"></i> 2. Como Funciona o Tratamento em 3 Passos</h4>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-search"></i></div>
          <div class="help-text">
            <span class="help-text-title">Passo 1: Diagnóstico</span>
            <span class="help-text-desc">
              Veja em linguagem simples o que impediu o registro e o que fazer. Os cadastros aparecem pelo nome (equipe, turno, serviço, material) e as séries repetidas ficam destacadas.
            </span>
          </div>
        </div>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-pencil"></i></div>
          <div class="help-text">
            <span class="help-text-title">Passo 2: Correção</span>
            <span class="help-text-desc">
              Ajuste as informações necessárias nos campos editáveis e informe obrigatoriamente a Justificativa da alteração para registro de auditoria.
            </span>
          </div>
        </div>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-check-circle"></i></div>
          <div class="help-text">
            <span class="help-text-title">Passo 3: Revisão e Reenvio</span>
            <span class="help-text-desc">
              Confira o resumo das alterações e clique em <strong>"Reenviar agora"</strong>. Quando o diagnóstico indicar que nada precisa mudar, use <strong>"Reenviar sem alterar"</strong> direto no Passo 1.
            </span>
          </div>
        </div>
      </div>

      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-tag"></i> 3. Significado das Situações</h4>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-clock"></i></div>
          <div class="help-text">
            <span class="help-text-title">Não foi possível sincronizar</span>
            <span class="help-text-desc">Registro recém-chegado do aplicativo de campo que o servidor recusou; aguarda um atendente.</span>
          </div>
        </div>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-user"></i></div>
          <div class="help-text">
            <span class="help-text-title">Em tratamento</span>
            <span class="help-text-desc">Um atendente assumiu o caso ou já realizou alterações no registro.</span>
          </div>
        </div>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-sync"></i></div>
          <div class="help-text">
            <span class="help-text-title">Aguardando sincronização</span>
            <span class="help-text-desc">Correção salva e liberada; o registro está na fila para ser reenviado.</span>
          </div>
        </div>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-check"></i></div>
          <div class="help-text">
            <span class="help-text-title">Sincronizado</span>
            <span class="help-text-desc">O servidor confirmou o registro, que já está nos cadastros operacionais da empresa.</span>
          </div>
        </div>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-ban"></i></div>
          <div class="help-text">
            <span class="help-text-title">Descartado</span>
            <span class="help-text-desc">Encerrado sem integração, com a justificativa registrada.</span>
          </div>
        </div>
      </div>
    </app-help-dialog>
  `
})
export class SyncErrorCasesComponent {
  private readonly service = inject(SyncErrorCasesService);
  private readonly messages = inject(MessageService);
  private readonly confirmations = inject(CesConfirmationService);
  private readonly auth = inject(AuthService);
  private readonly usuarioLookup = inject(UsuarioLookupService, { optional: true });
  private readonly rhService = inject(RhService, { optional: true });
  private readonly catalogo = inject(SyncReferenceCatalogService);
  private readonly copiloto = inject(CopilotoPainelService);
  protected readonly copilotoDisponivel = this.copiloto.disponivelParaUsuario();

  /** O Copiloto consulta os casos, os turnos e as notas da equipe; a pessoa decide o tratamento. */
  protected readonly contextoCopiloto: CopilotoDashboardContext = {
    id: 'admin.sincronismo',
    nome: 'Sincronismo',
    modulo: 'Administração',
    descricao: 'Registros do aplicativo de campo recusados pelo servidor (turnos, notas, solicitações, transferências, inspeções) e o tratamento de cada um: reenviar, corrigir ou descartar.',
    rota: '/admin/cadastros/tratamento-erros',
    filtros: [],
    metricas: [],
    graficos: [],
    datasets: ['sincronizacao_casos', 'sincronizacao_operacoes', 'turnos', 'execucoes_comerciais', 'execucoes_materiais'],
    sugestoes: [
      { rotulo: 'Erros mais comuns', icone: 'pi pi-chart-bar', requer: ['dados'], pergunta: 'Quais os motivos de recusa mais comuns nas pendências de sincronismo dos últimos 30 dias, e quais equipes mais aparecem?' },
      { rotulo: 'Pendências paradas', icone: 'pi pi-clock', requer: ['dados'], pergunta: 'Quais pendências de sincronismo estão abertas há mais de 2 dias, de quais equipes, e por quê?' }
    ]
  };

  protected readonly equipeMap = signal<Map<string, string>>(new Map());
  protected readonly veiculoMap = signal<Map<string, string>>(new Map());
  protected readonly materialMap = signal<Map<string, string>>(new Map());

  constructor() {
    this.carregarCatalogos();
  }

  private carregarCatalogos(): void {
    if (this.rhService) {
      this.rhService.listarEquipes({ size: 1000 }).pipe(catchError(() => of(null))).subscribe(res => {
        if (res?.content) {
          const map = new Map<string, string>();
          for (const eq of res.content) {
            const label = eq.codigo ? `Equipe ${eq.codigo}` : `Equipe #${this.formatShortRef(eq.id)}`;
            map.set(eq.id, label);
            if (eq.codigo) map.set(eq.codigo, label);
          }
          this.equipeMap.set(map);
        }
      });

      this.rhService.listarVeiculosAtivos().pipe(catchError(() => of(null))).subscribe(res => {
        if (res?.content) {
          const map = new Map<string, string>();
          for (const v of res.content) {
            const label = v.placa ? `${v.placa}${v.modelo ? ' (' + v.modelo + ')' : ''}` : `Veículo #${this.formatShortRef(v.id)}`;
            map.set(v.id, label);
            if (v.placa) map.set(v.placa, label);
          }
          this.veiculoMap.set(map);
        }
      });
    }
  }

  protected readonly breadcrumbItems = [
    { label: 'Administração' },
    { label: 'Sincronismo' }
  ];

  protected readonly detail = signal<SyncErrorCaseDetail | null>(null);
  protected readonly displayDetail = signal(false);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly activeStep = signal(1);
  protected readonly showJustificationError = signal(false);
  protected readonly editableFields = signal<FriendlyField[]>([]);
  protected readonly referenceKind = syncReferenceKind;
  protected readonly booleanOptions = [{ label: 'Sim', value: 'true' }, { label: 'Não', value: 'false' }];
  protected readonly editableMaterials = signal<EditableMaterial[]>([]);
  protected readonly complexFields = signal<FriendlyField[]>([]);
  protected readonly materialKey = signal<'itens' | 'materiais' | null>(null);
  protected readonly acoesKey = signal<'acoes' | null>(null);
  protected readonly editableAcoes = signal<string[]>([]);
  /** Nomes dos serviços escolhidos na correção, que ainda não vieram do servidor. */
  private readonly nomesAdicionados = signal<Record<string, string>>({});
  protected readonly seriesRepetidasNaCorrecao = signal<Set<string>>(new Set());
  protected readonly operacaoMaterialOptions = [{ label: 'Aplicado', value: 'APLICADO' }, { label: 'Retirado', value: 'RETIRADO' }];

  protected justification = '';
  protected readonly dataLocal = dataLocal;
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;

  protected readonly statusOptions: CesSelectOption<SyncCaseStatus | null>[] = [
    { label: 'Todas', value: null },
    { label: 'Não foi possível sincronizar', value: 'ABERTO' },
    { label: 'Em tratamento', value: 'EM_ANALISE' },
    { label: 'Aguardando sincronização', value: 'AGUARDANDO_REPROCESSAMENTO' },
    { label: 'Sincronizado', value: 'RESOLVIDO' },
    { label: 'Descartado', value: 'REJEITADO' }
  ];

  protected readonly operationOptions: CesSelectOption<string | null>[] = [
    { label: 'Todas', value: null },
    { label: 'Turno de Trabalho', value: 'TURNO' },
    { label: 'Execução de Serviço', value: 'EXECUCAO' },
    { label: 'Solicitação de Material', value: 'SOLICITACAO' },
    { label: 'Transferência de Estoque', value: 'TRANSFERENCIA' },
    { label: 'Inspeção de Segurança', value: 'INSPECAO' },
    { label: 'Evidência Fotográfica', value: 'ANEXO' }
  ];

  protected readonly lista = cesListState({
    storageKey: 'admin-sync-erros',
    filters: {
      status: cesFilter.value<SyncCaseStatus | null>(null, { label: 'Situação', options: () => this.statusOptions }),
      tipo: cesFilter.value<string | null>(null, { label: 'Atividade', options: () => this.operationOptions }),
      envio: cesFilter.range({ from: null, to: null }, { label: 'Envio' })
    },
    // Fila de tratamento: os mais antigos primeiro.
    sort: [{ key: 'envio', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<SyncErrorCaseSummary>(
    (pedido) => this.service.list({ ...this.filtro(), page: pedido.page, size: pedido.size, sort: pedido.sort.map((r) => `${r.key},${r.direction}`)[0] }),
    { errorMessage: getErrorMessage }
  );
  protected readonly exportar = exportacaoPadrao(
    (pagina) => this.service.list({ ...this.filtro(), ...pagina, sort: Array.isArray(pagina.sort) ? pagina.sort[0] : pagina.sort }),
    () => this.lista.request()
  );

  protected readonly colunas: CesDataTableColumn<SyncErrorCaseSummary>[] = [
    { key: 'envio', header: 'Enviado em', type: 'date', priority: 1, sortField: 'receivedAt', value: (c) => c.receivedAt,
      display: (c) => (c.receivedAt ? new Date(c.receivedAt).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—') },
    { key: 'atividade', header: 'Atividade', type: 'custom', priority: 1, sortField: 'tipo', value: (c) => this.getOperationLabel(c.operationType) },
    { key: 'origem', header: 'Equipe / colaborador', value: (c) => this.getUserDisplayName(c.sourceUserName, false) },
    { key: 'problema', header: 'Problema detectado', type: 'custom', value: (c) => this.diagnostico(c).titulo },
    { key: 'responsavel', header: 'Responsável', value: (c) => c.assignedToName, display: (c) => (c.assignedToName ? this.getUserDisplayName(c.assignedToName, true) : 'Não atribuído') },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, sortField: 'status', value: (c) => this.statusLabel(c.status) }
  ];
  protected readonly rotulo = (c: SyncErrorCaseSummary) =>
    [this.getOperationLabel(c.operationType), this.getRecordBusinessLabel(c)].filter(Boolean).join(' · ');

  private filtro() {
    const { filters: f, query } = this.lista.request();
    return {
      query: query || undefined,
      status: f.status ?? undefined,
      operationType: f.tipo ?? undefined,
      receivedFrom: f.envio.from ?? undefined,
      receivedTo: f.envio.to ?? undefined
    };
  }

  protected periodo(ponta: 'from' | 'to', data: Date | null): void {
    this.lista.filters.envio.update((atual) => ({ ...atual, [ponta]: dataIso(data) }));
  }

  protected open(item: SyncErrorCaseSummary): void {
    this.activeStep.set(1);
    this.showJustificationError.set(false);
    this.service.get(item.id).subscribe({
      next: detail => {
        this.detail.set(detail);
        this.populateFields(detail);
        this.displayDetail.set(true);
      },
      error: error => this.fail(error)
    });
  }

  protected onDialogVisibleChange(visible: boolean): void {
    this.displayDetail.set(visible);
    if (!visible) {
      this.detail.set(null);
      this.editableFields.set([]);
      this.editableMaterials.set([]);
      this.editableAcoes.set([]);
      this.complexFields.set([]);
      this.justification = '';
      this.activeStep.set(1);
    }
  }

  private populateFields(detail: SyncErrorCaseDetail): void {
    const original = this.unwrapPayload(detail.originalPayload);
    const latest = this.unwrapPayload(detail.correction?.payload ?? detail.originalPayload);
    const fields: FriendlyField[] = [];
    const complex: FriendlyField[] = [];
    const itemsKey = Array.isArray(latest['itens']) ? 'itens' : Array.isArray(latest['materiais']) ? 'materiais' : null;
    this.materialKey.set(itemsKey);
    const originalItems = itemsKey && Array.isArray(original[itemsKey]) ? original[itemsKey] as Record<string, unknown>[] : [];
    const currentItems = itemsKey ? latest[itemsKey] as Record<string, unknown>[] : [];
    this.editableMaterials.set(currentItems.map((item, index) => this.materialEditavel(item, originalItems[index] ?? {}, itemsKey === 'materiais')));

    const acoes = latest['acoes'];
    const acoesKey = Array.isArray(acoes) && acoes.every(a => typeof a === 'string') ? 'acoes' : null;
    this.acoesKey.set(acoesKey);
    this.editableAcoes.set(acoesKey ? [...(acoes as string[])] : []);
    this.nomesAdicionados.set({});

    for (const [key, val] of Object.entries(latest)) {
      if (NON_EDITABLE_KEYS.has(key) || READ_ONLY_KEYS.has(key)) continue;
      if (key === itemsKey || key === acoesKey) continue;
      if (typeof val === 'object' && val !== null) {
        complex.push({ key, label: FIELD_LABELS[key]?.label ?? this.formatKeyToLabel(key), value: JSON.stringify(val, null, 2), originalValue: JSON.stringify(original[key] ?? val, null, 2), type: 'textarea', isModified: JSON.stringify(val) !== JSON.stringify(original[key] ?? val) });
        continue;
      }

      const originalVal = original[key] !== undefined && original[key] !== null ? String(original[key]) : '';
      const currentVal = val !== undefined && val !== null ? String(val) : '';
      const config = FIELD_LABELS[key];
      const label = config?.label || this.formatKeyToLabel(key);
      const type = typeof val === 'boolean' ? 'boolean' : key === 'dataPrevista' ? 'date'
        : config?.type || (currentVal.length > 50 ? 'textarea' : 'text');

      fields.push({
        key,
        label,
        value: currentVal,
        originalValue: originalVal,
        type,
        isModified: currentVal !== originalVal,
        jsonType: this.jsonType(val),
        dateValue: key === 'dataPrevista' && /^\d{4}-\d{2}-\d{2}$/.test(currentVal)
          ? new Date(`${currentVal}T12:00:00`)
          : undefined
      });
    }

    this.editableFields.set(fields);
    this.complexFields.set(complex);
    this.justification = '';
    this.revalidarSeries();
  }

  /** Só os campos que fazem sentido corrigir; o resto do item segue como veio do aparelho. */
  private materialEditavel(item: Record<string, unknown>, original: Record<string, unknown>, execucao: boolean): EditableMaterial {
    const keys = MATERIAL_KEYS.filter(key => key in item || (execucao && (key === 'operacao' || key === 'numSerie')));
    return { base: { ...item }, fields: keys.map(key => this.campoMaterial(key, item[key], original[key])) };
  }

  private campoMaterial(key: string, val: unknown, originalVal: unknown): FriendlyField {
    const value = val == null ? '' : String(val);
    const originalValue = originalVal == null ? '' : String(originalVal);
    return {
      key,
      label: key === 'idMaterial' ? 'Material' : FIELD_LABELS[key]?.label ?? this.formatKeyToLabel(key),
      value,
      originalValue,
      type: key === 'quantidade' || key === 'qtd' ? 'number' : key === 'operacao' ? 'operation' : 'text',
      isModified: value !== originalValue,
      jsonType: this.jsonType(val)
    };
  }

  private jsonType(val: unknown): FriendlyField['jsonType'] {
    return typeof val === 'number' ? 'number' : typeof val === 'boolean' ? 'boolean' : 'string';
  }

  /** Devolve o valor no tipo recebido: série com zero à esquerda continua texto. */
  private converter(field: FriendlyField): unknown {
    const valor = field.value.trim();
    if (this.referenceKind(field.key)) return valor || null;
    if (field.jsonType === 'number') {
      const numero = Number(valor.replace(',', '.'));
      return valor && Number.isFinite(numero) ? numero : valor || null;
    }
    if (field.jsonType === 'boolean') return valor === 'true';
    return field.value;
  }

  protected checkFieldModified(field: FriendlyField): void {
    field.isModified = field.value !== field.originalValue;
  }

  protected setFieldValue(field: FriendlyField, value: string | null): void {
    field.value = value ?? '';
    this.checkFieldModified(field);
  }

  protected setDateValue(field: FriendlyField, value: Date | null): void {
    field.dateValue = value;
    field.value = value
      ? `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
      : '';
    this.checkFieldModified(field);
  }

  protected getModifiedFieldsList(): FriendlyField[] {
    const originalPayload = this.detail()?.originalPayload;
    const key = this.materialKey();
    const originalItems = key ? this.unwrapPayload(originalPayload)[key] : null;
    const originalCount = Array.isArray(originalItems) ? originalItems.length : 0;
    const currentCount = this.editableMaterials().length;
    return [
      ...this.editableFields().filter(f => f.isModified),
      ...this.complexFields().filter(f => f.isModified),
      ...(key && originalCount !== currentCount ? [{ key: 'materialCount', label: 'Quantidade de itens', value: String(currentCount), originalValue: String(originalCount), type: 'number' as const, isModified: true }] : []),
      ...this.editableMaterials().flatMap((item, index) => item.fields.filter(f => f.isModified).map(f => ({ ...f, label: `Material ${index + 1}: ${f.label}` })))
    ];
  }

  protected addMaterial(): void {
    const execucao = this.materialKey() === 'materiais';
    const novo: Record<string, unknown> = execucao
      ? { idMaterial: null, operacao: 'APLICADO', numSerie: null, quantidade: 1 }
      : { idMaterial: null, quantidade: 1 };
    this.editableMaterials.update(items => [...items, {
      base: {},
      fields: Object.entries(novo).map(([key, val]) => ({ ...this.campoMaterial(key, val, null), isModified: true, jsonType: key === 'quantidade' ? 'number' as const : 'string' as const }))
    }]);
  }

  protected removeMaterial(index: number): void {
    this.editableMaterials.update(items => items.filter((_, i) => i !== index));
    this.revalidarSeries();
  }

  protected adicionarAcao(id: string | null): void {
    if (!id || this.editableAcoes().includes(id)) return;
    this.editableAcoes.update(acoes => [...acoes, id]);
    this.catalogo.resolve('action', id).pipe(catchError(() => of(null))).subscribe(opcao => {
      if (opcao) this.nomesAdicionados.update(nomes => ({ ...nomes, [id]: opcao.label }));
    });
  }

  protected removerAcao(index: number): void {
    this.editableAcoes.update(acoes => acoes.filter((_, i) => i !== index));
  }

  protected normalizarSerie(valor: string | null | undefined): string {
    return (valor ?? '').trim().toUpperCase();
  }

  /** O servidor recusa a mesma série duas vezes na nota, em qualquer material ou operação. */
  protected revalidarSeries(): void {
    const contagem = new Map<string, number>();
    for (const material of this.editableMaterials()) {
      const serie = this.normalizarSerie(material.fields.find(f => f.key === 'numSerie')?.value);
      if (serie) contagem.set(serie, (contagem.get(serie) ?? 0) + 1);
    }
    this.seriesRepetidasNaCorrecao.set(new Set([...contagem].filter(([, n]) => n > 1).map(([serie]) => serie)));
  }

  protected onSaveCorrection(item: SyncErrorCaseDetail): void {
    if (!this.justification.trim()) {
      this.showJustificationError.set(true);
      this.messages.add({
        severity: 'warn',
        summary: 'Justificativa obrigatória',
        detail: 'Por favor, informe o motivo da alteração para salvar os ajustes.'
      });
      return;
    }
    this.showJustificationError.set(false);
    this.saving.set(true);

    const source = item.correction?.payload ?? item.originalPayload;
    const currentPayload = this.unwrapPayload(source);
    const updatedPayload: Record<string, unknown> = { ...currentPayload };
    if (this.seriesRepetidasNaCorrecao().size) {
      this.saving.set(false);
      this.messages.add({ severity: 'warn', summary: 'Série repetida', detail: 'Cada número de série entra uma vez só na nota. Corrija a série repetida antes de salvar.' });
      return;
    }
    for (const field of this.editableFields()) {
      updatedPayload[field.key] = this.converter(field);
    }
    if (this.acoesKey()) {
      updatedPayload['acoes'] = [...this.editableAcoes()];
    }
    for (const field of this.complexFields()) {
      try {
        updatedPayload[field.key] = JSON.parse(field.value);
      } catch {
        this.saving.set(false);
        this.messages.add({ severity: 'warn', summary: 'Dados inválidos', detail: `${field.label} precisa conter um JSON válido.` });
        return;
      }
    }
    const materialKey = this.materialKey();
    if (materialKey) {
      const materials: Record<string, unknown>[] = [];
      for (const [index, itemFields] of this.editableMaterials().entries()) {
        const material: Record<string, unknown> = { ...itemFields.base };
        for (const field of itemFields.fields) {
          if (field.key === 'idMaterial' && !field.value.trim()) {
            this.saving.set(false);
            this.messages.add({ severity: 'warn', summary: 'Material obrigatório', detail: `Informe o material ${index + 1}.` });
            return;
          }
          if (field.key === 'quantidade' || field.key === 'qtd') {
            const value = field.value.trim();
            if (!/^\d+(?:\.\d{1,3})?$/.test(value) || Number(value) <= 0 || !Number.isFinite(Number(value))) {
              this.saving.set(false);
              this.messages.add({ severity: 'warn', summary: 'Quantidade inválida', detail: `Informe uma quantidade positiva com até 3 casas decimais no material ${index + 1}.` });
              return;
            }
            material[field.key] = Number(value);
          } else {
            material[field.key] = field.key === 'numSerie' && !field.value.trim() ? null : this.converter(field);
          }
        }
        materials.push(material);
      }
      if (materials.length === 0) {
        this.saving.set(false);
        this.messages.add({ severity: 'warn', summary: 'Materiais obrigatórios', detail: 'Informe ao menos um material antes de salvar.' });
        return;
      }
      updatedPayload[materialKey] = materials;
    }
    const envelope = source && typeof source === 'object' && !Array.isArray(source) ? source as Record<string, unknown> : {};
    const payload = envelope['body'] && typeof envelope['body'] === 'object'
      ? { ...envelope, body: updatedPayload }
      : envelope['request'] && typeof envelope['request'] === 'object'
        ? { ...envelope, request: updatedPayload }
        : updatedPayload;

    this.service
      .saveCorrection(item.id, {
        payload,
        justification: this.justification.trim()
      })
      .subscribe({
        next: updatedDetail => {
          this.detail.set(updatedDetail);
          this.populateFields(updatedDetail);
          this.saving.set(false);
          this.messages.add({
            severity: 'success',
            summary: 'Ajustes salvos com sucesso',
            detail: 'As alterações foram registradas. Você pode agora revisar e liberar o reprocessamento.'
          });
          this.lista.reload();
          this.activeStep.set(3);
        },
        error: error => this.fail(error)
      });
  }

  protected onReprocess(item: SyncErrorCaseDetail): void {
    const just = this.justification.trim() || item.correction?.justification || 'Reenviado após correção pelo backoffice.';
    this.reenviar(item, just);
  }

  protected podeReenviarSemAlterar(item: SyncErrorCaseDetail): boolean {
    const acao = this.diagnostico(item).acao;
    return acao === 'REENVIAR' || acao === 'CONFERIR';
  }

  protected async reenviarSemAlterar(item: SyncErrorCaseDetail): Promise<void> {
    const diagnostico = this.diagnostico(item);
    const confirmed = await this.confirmations.confirm({
      title: 'Reenviar sem alterar?',
      message: 'O registro será enviado de novo exatamente como está. Se o servidor recusar, o caso continua aqui com o novo motivo.',
      confirmLabel: 'Reenviar',
      cancelLabel: 'Voltar'
    });
    if (!confirmed) return;
    this.reenviar(item, `Reenviado sem alteração. ${diagnostico.titulo}: ${diagnostico.orientacao}`);
  }

  private reenviar(item: SyncErrorCaseDetail, justificativa: string): void {
    this.saving.set(true);
    this.service.reprocess(item.id, justificativa.substring(0, 4000)).subscribe({
      next: updatedDetail => {
        this.detail.set(updatedDetail);
        this.populateFields(updatedDetail);
        this.saving.set(false);
        this.activeStep.set(1);
        this.messages.add({
          severity: 'success',
          summary: 'Registro sincronizado',
          detail: 'O servidor gravou o registro e o caso foi encerrado.'
        });
        this.lista.reload();
      },
      error: error => {
        this.fail(error);
        // A recusa vira o novo motivo do caso: recarrega para mostrar o diagnóstico atualizado.
        this.service.get(item.id).subscribe({ next: atual => { this.detail.set(atual); this.populateFields(atual); this.activeStep.set(1); } });
        this.lista.reload();
      }
    });
  }

  /** Leva o retrato do caso, com nomes no lugar das referências, para o Copiloto analisar. */
  protected preAnalisar(item: SyncErrorCaseDetail): void {
    const d = this.diagnostico(item);
    const payload = this.payloadAtual(item);
    const dados = this.getFriendlySummaryEntries(payload).filter(e => !e.href).map(e => `${e.label}: ${e.value}`);
    const itens = this.getComplexItems(payload);
    const servicos = itens.acoes.map(a => this.nomeDaReferencia(a, 'Serviço'));
    const materiais = itens.materiais.map(m =>
      `${this.getMaterialLabel(m)} (${[this.operationLabel(m.operacao), m.numSerie ? 'série ' + m.numSerie : '', `qtd ${m.quantidade ?? 1}`].filter(Boolean).join(', ')})`);
    const registro = [this.getOperationLabel(item.operationType), this.getRecordBusinessLabel(item)].filter(Boolean).join(' · ');
    const recebido = item.receivedAt ? new Date(item.receivedAt).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '';
    const pergunta = [
      'Faça uma pré-análise desta pendência de sincronismo para quem vai tratá-la.',
      `Registro: ${registro}, enviado por ${this.getUserDisplayName(item.sourceUserName, false)}${recebido ? ' em ' + recebido : ''}.`,
      `Leitura do sistema: ${d.titulo}. ${d.explicacao} Recomendação do sistema: ${d.orientacao}`,
      dados.length ? `Dados: ${dados.join('; ')}.` : '',
      servicos.length ? `Serviços: ${servicos.join('; ')}.` : '',
      materiais.length ? `Materiais: ${materiais.join('; ')}.` : '',
      'Responda: 1) a causa provável; 2) se os dados são coerentes, conferindo os turnos e as notas da equipe nesse dia (por exemplo, se a nota já foi lançada em outro registro); '
        + '3) a resolução recomendada: reenviar sem alterar, corrigir o quê ou descartar. Não execute nenhuma ação: quem decide é a pessoa que trata o caso.'
    ].filter(Boolean).join('\n');
    this.onDialogVisibleChange(false);
    // Abaixo de 1280px o painel sobrepõe a tela inteira; o caso é reaberto pela lista.
    this.copiloto.perguntarSobre({ tipo: 'secao', id: `sincronismo.${item.id}`, rotulo: registro }, pergunta);
  }

  protected irParaDescarte(): void {
    this.activeStep.set(2);
    setTimeout(() => document.getElementById('sync-justificativa')?.focus());
  }

  protected async onDiscard(item: SyncErrorCaseDetail): Promise<void> {
    const reason = this.justification.trim();
    if (!reason) {
      this.showJustificationError.set(true);
      this.messages.add({ severity: 'warn', summary: 'Justificativa obrigatória', detail: 'Informe o motivo do descarte no Passo 2.' });
      this.activeStep.set(2);
      return;
    }
    const confirmed = await this.confirmations.confirm({
      title: 'Descartar registro?',
      message: 'O registro será encerrado sem entrar no sistema. O envio original e a justificativa permanecerão para consulta.',
      confirmLabel: 'Descartar registro',
      cancelLabel: 'Voltar',
      variant: 'destructive'
    });
    if (!confirmed) return;
    this.saving.set(true);
    this.service.discard(item.id, reason).subscribe({
      next: updated => {
        this.detail.set(updated);
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Registro descartado', detail: 'O caso foi encerrado sem integração.' });
        this.lista.reload();
        this.activeStep.set(1);
      },
      error: error => this.fail(error)
    });
  }

  protected assignToMe(item: SyncErrorCaseDetail): void {
    const userId = this.auth.currentUser()?.id;
    if (!userId) {
      this.fail(new Error('Usuário atual não identificado.'));
      return;
    }
    this.saving.set(true);
    this.service.assign(item.id, userId).subscribe({
      next: detail => {
        this.detail.set(detail);
        this.saving.set(false);
        this.messages.add({
          severity: 'success',
          summary: 'Atendimento assumido',
          detail: 'Esta pendência foi atribuída a você para tratamento.'
        });
        this.lista.reload();
      },
      error: error => this.fail(error)
    });
  }

  protected downloadAttachment(caseId: string, att: SyncErrorAttachment): void {
    this.service.downloadAttachment(caseId, att.id).subscribe({
      next: blob => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = att.fileName || 'anexo';
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: error => this.fail(error)
    });
  }

  protected isAssignedToCurrentUser(item: SyncErrorCaseDetail): boolean {
    const currentUserId = this.auth.currentUser()?.id;
    return !!currentUserId && item.assignedToName === currentUserId;
  }

  protected canCorrect(item: SyncErrorCaseDetail): boolean {
    return (
      this.canManage() &&
      (item.status === 'ABERTO' ||
        item.status === 'EM_ANALISE' ||
        item.status === 'AGUARDANDO_REPROCESSAMENTO')
    );
  }

  protected canReprocess(item: SyncErrorCaseDetail): boolean {
    return this.canCorrect(item);
  }

  protected canManage(): boolean {
    return this.auth.currentUser()?.permissoes?.includes('sincronizacao:manage') ?? false;
  }

  protected getOperationLabel(type?: string): string {
    if (!type) return 'Operação de Campo';
    const found = this.operationOptions.find(o => o.value === type);
    return found ? found.label : type;
  }

  protected getOperationIcon(type?: string): string {
    switch (type) {
      case 'TURNO':
        return 'pi pi-clock';
      case 'EXECUCAO':
        return 'pi pi-wrench';
      case 'SOLICITACAO':
        return 'pi pi-box';
      case 'TRANSFERENCIA':
        return 'pi pi-arrow-right-arrow-left';
      case 'INSPECAO':
        return 'pi pi-shield';
      case 'ANEXO':
        return 'pi pi-camera';
      default:
        return 'pi pi-file';
    }
  }

  protected statusLabel(status: SyncCaseStatus): string {
    return this.statusOptions.find(option => option.value === status)?.label ?? status;
  }

  protected statusTone(status: SyncCaseStatus): CesTone {
    return ({ ABERTO: 'danger', EM_ANALISE: 'info', AGUARDANDO_REPROCESSAMENTO: 'warning', RESOLVIDO: 'success', REJEITADO: 'neutral' } as Record<string, CesTone>)[status] ?? 'neutral';
  }

  protected statusIcon(status: SyncCaseStatus): string {
    return ({ ABERTO: 'pi-exclamation-circle', EM_ANALISE: 'pi-user', AGUARDANDO_REPROCESSAMENTO: 'pi-sync', RESOLVIDO: 'pi-check', REJEITADO: 'pi-ban' } as Record<string, string>)[status] ?? 'pi-circle';
  }

  /** Diagnóstico do servidor; sem ele (registro antigo), a tradução local do erro. */
  protected diagnostico(item: SyncErrorCaseSummary): SyncDiagnostico {
    if (item.diagnostico) return item.diagnostico;
    const info = this.getFriendlyErrorInfo(item.errorDetail);
    return { codigo: 'OUTRO', titulo: info.title, explicacao: info.explanation, orientacao: info.suggestion, acao: 'CONFERIR' };
  }

  protected toneDaAcao(acao: SyncAcaoRecomendada): CesTone {
    return ({ REENVIAR: 'success', AGUARDAR: 'info', CORRIGIR: 'warning', CONFERIR: 'warning', DESCARTAR: 'neutral' } as Record<SyncAcaoRecomendada, CesTone>)[acao];
  }

  protected desfecho(item: SyncErrorCaseDetail): string {
    const quando = item.resolvedAt || item.updatedAt;
    const data = quando ? new Date(quando).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '';
    const motivo = item.justificativaUltima ? ` ${item.justificativaUltima}` : '';
    return item.status === 'RESOLVIDO'
      ? `Entrou no sistema${data ? ' em ' + data : ''}.${motivo}`
      : `Encerrado sem entrar no sistema${data ? ' em ' + data : ''}.${motivo ? ' Motivo:' + motivo : ''}`;
  }

  /** Dados que o próximo reenvio usa: a última correção ou o envio original. */
  protected payloadAtual(item: SyncErrorCaseDetail): unknown {
    return item.correction?.payload ?? item.originalPayload;
  }

  protected nomeDaReferencia(valor: unknown, tipo: string): string {
    const chave = String(valor ?? '');
    return this.detail()?.referencias?.[chave] ?? this.nomesAdicionados()[chave] ?? `${tipo} não localizado`;
  }

  protected operationLabel(operacao: string | undefined): string {
    return operacao ? OPERATION_LABELS[operacao] ?? operacao : '';
  }

  protected serieRepetida(item: SyncErrorCaseDetail, serie: string | undefined): boolean {
    const alvo = this.normalizarSerie(serie);
    if (!alvo) return false;
    return this.getComplexItems(this.payloadAtual(item)).materiais.filter(m => this.normalizarSerie(m.numSerie) === alvo).length > 1;
  }

  /** O que a correção mudou em relação à versão anterior, com nomes no lugar das referências. */
  protected alteracoesDaVersao(item: SyncErrorCaseDetail, indice: number): VersionChange[] {
    const versoes = item.versoes ?? [];
    const antes = this.unwrapPayload(indice === 0 ? item.originalPayload : versoes[indice - 1]?.payload);
    const depois = this.unwrapPayload(versoes[indice]?.payload);
    const mudancas: VersionChange[] = [];
    const chaves = new Set([...Object.keys(antes), ...Object.keys(depois)]);
    for (const key of chaves) {
      if (NON_EDITABLE_KEYS.has(key)) continue;
      const a = antes[key];
      const d = depois[key];
      if (key === 'acoes' && (Array.isArray(a) || Array.isArray(d))) {
        const nomes = (lista: unknown) => (Array.isArray(lista) ? lista : []).map(v => this.nomeDaReferencia(v, 'Serviço')).join(', ');
        if (nomes(a) !== nomes(d)) mudancas.push({ label: 'Serviços', before: nomes(a), after: nomes(d) });
        continue;
      }
      if (key === 'materiais' || key === 'itens') {
        const listaA = Array.isArray(a) ? a as Record<string, unknown>[] : [];
        const listaD = Array.isArray(d) ? d as Record<string, unknown>[] : [];
        if (listaA.length !== listaD.length) mudancas.push({ label: 'Quantidade de materiais', before: String(listaA.length), after: String(listaD.length) });
        for (let i = 0; i < Math.min(listaA.length, listaD.length); i++) {
          for (const campo of MATERIAL_KEYS) {
            const va = this.formatSummaryValue(campo, listaA[i]?.[campo]);
            const vd = this.formatSummaryValue(campo, listaD[i]?.[campo]);
            if (va !== vd) mudancas.push({ label: `Material ${i + 1} · ${campo === 'idMaterial' ? 'Material' : FIELD_LABELS[campo]?.label ?? campo}`, before: va, after: vd });
          }
        }
        continue;
      }
      if (typeof a === 'object' || typeof d === 'object') continue;
      const va = this.formatSummaryValue(key, a);
      const vd = this.formatSummaryValue(key, d);
      if (va !== vd) mudancas.push({ label: FIELD_LABELS[key]?.label ?? this.formatKeyToLabel(key), before: va, after: vd });
    }
    return mudancas.map(m => ({ ...m, before: m.before === '—' ? '' : m.before, after: m.after === '—' ? '' : m.after }));
  }

  protected getFriendlyErrorInfo(rawError: string | undefined): { title: string; explanation: string; suggestion: string } {
    if (!rawError || rawError.trim() === '') {
      return {
        title: 'Validação cadastral necessária',
        explanation: 'O lançamento recebido do aplicativo necessita de conferência cadastral antes de ser confirmado.',
        suggestion: 'Revise os dados informados e avance para a etapa de correção caso necessário.'
      };
    }
    const lower = rawError.toLowerCase();
    if (lower.includes('sem foto de evidência') || lower.includes('não tem foto') || lower.includes('foto')) {
      return {
        title: 'Inspeção sem foto obrigatória',
        explanation: 'Esta inspeção de segurança foi enviada sem foto comprobatória exigida pelas normas de SST.',
        suggestion: 'Verifique se há fotos anexadas ou registre a justificativa da liberação no Passo 2.'
      };
    }
    if (lower.includes('não encontrada') || lower.includes('não encontrado')) {
      return {
        title: 'Cadastro relacionado não localizado',
        explanation: 'Um dos códigos informados (equipe, veículo, município ou material) não está cadastrado ou está inativo.',
        suggestion: 'No Passo 2 (Correção), ajuste o código para um cadastro válido e salve a justificativa.'
      };
    }
    if (lower.includes('saldo') || lower.includes('insuficiente')) {
      return {
        title: 'Saldo de estoque insuficiente',
        explanation: 'O estoque de origem não possui a quantidade informada para movimentar este material.',
        suggestion: 'Confira as quantidades informadas ou acerte o saldo no almoxarifado antes de reprocessar.'
      };
    }
    if (lower.includes('uuid') && lower.includes('recebido')) {
      return {
        title: 'Lançamento duplicado',
        explanation: 'Este mesmo envio já havia sido gravado anteriormente pelo aplicativo móvel.',
        suggestion: 'Verifique no histórico se o atendimento correspondente já consta realizado.'
      };
    }
    if (lower.includes('aguardando tratamento')) {
      return {
        title: 'Aguardando validação cadastral',
        explanation: 'O registro foi recebido com segurança do campo e foi direcionado para conferência de rotina do backoffice.',
        suggestion: 'Consulte os dados no Passo 1, efetue os ajustes no Passo 2 e libere no Passo 3.'
      };
    }

    let clean = rawError
      .replace(/^Erro informado pelo aplicativo:\s*/i, '')
      .replace(/^InvarianteVioladaException:\s*/i, '')
      .replace(/^IllegalArgumentException:\s*/i, '');
    if (clean.length > 200) {
      clean = clean.substring(0, 197) + '...';
    }
    return {
      title: 'Inconsistência no processamento',
      explanation: clean,
      suggestion: 'Consulte os dados no Passo 1, efetue os ajustes necessários no Passo 2 e reenvie no Passo 3.'
    };
  }

  protected getUserDisplayName(userIdOrName?: string | null, isAssignee = false): string {
    if (!userIdOrName || !userIdOrName.trim()) {
      return isAssignee ? 'Não atribuído' : 'Equipe de Campo';
    }
    const trimmed = userIdOrName.trim();
    const currentUser = this.auth.currentUser();
    if (currentUser && (currentUser.id === trimmed || currentUser.login === trimmed)) {
      return currentUser.nome || 'Você';
    }
    if (this.usuarioLookup) {
      const resolved = this.usuarioLookup.resolverNome(trimmed);
      if (resolved && !resolved.startsWith('USR-') && !this.isUuid(resolved)) {
        return resolved;
      }
    }
    if (this.isUuid(trimmed)) {
      return isAssignee ? 'Atendente de Suporte' : 'Técnico de Campo';
    }
    return trimmed;
  }

  protected getRecordBusinessLabel(item: SyncErrorCaseSummary | SyncErrorCaseDetail | null | undefined): string | null {
    if (!item) return null;
    const p = this.unwrapPayload((item as SyncErrorCaseDetail).originalPayload);
    if (!p || Object.keys(p).length === 0) return null;

    if (p['referenciaNota']) {
      return `OS / Nota: ${p['referenciaNota']}`;
    }
    if (p['placa']) {
      return `Veículo: ${p['placa']}`;
    }
    if (p['nomeArquivo']) {
      return `Arquivo: ${p['nomeArquivo']}`;
    }
    if (p['numero']) {
      return `Nº ${p['numero']}`;
    }
    if (p['idEquipe']) {
      const eq = this.equipeMap().get(String(p['idEquipe']));
      if (eq) return eq;
    }
    return null;
  }

  protected getMaterialLabel(mat: FriendlyMaterialItem): string {
    if (mat.idMaterial) {
      const nome = this.detail()?.referencias?.[mat.idMaterial] ?? this.materialMap().get(mat.idMaterial);
      if (nome) return nome;
    }
    if (mat.codigo) return `Código: ${mat.codigo}`;
    if (mat.idMaterial) return this.isUuid(mat.idMaterial) ? 'Material não localizado' : mat.idMaterial;
    return 'Material de Campo';
  }

  protected isUuid(value: string | undefined | null): boolean {
    if (!value || typeof value !== 'string') return false;
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.trim());
  }

  protected formatShortRef(value: string | undefined | null): string {
    if (!value) return '';
    const clean = value.replace(/[^a-zA-Z0-9]/g, '');
    return clean.substring(0, 8).toUpperCase();
  }

  protected formatSummaryValue(key: string, val: unknown): string {
    if (val === null || val === undefined) return '—';
    if (typeof val === 'boolean') {
      if (key === 'simulacao') return val ? 'Simulação de Teste' : 'Registro de Produção';
      if (key === 'saqueForaEquipe') return val ? 'Sim (Fora da equipe)' : 'Não';
      return val ? 'Sim' : 'Não';
    }

    const strVal = String(val).trim();
    if (!strVal) return '—';

    // 0. Nome resolvido pelo servidor para qualquer cadastro citado no registro
    const nome = this.detail()?.referencias?.[strVal];
    if (nome) return nome;
    if (key === 'operacao') return this.operationLabel(strVal);
    if (key === 'tipoNota') return strVal.charAt(0) + strVal.slice(1).toLowerCase();

    // 1. Resolver Usuários / Colaboradores
    if (key === 'idColaboradorSaque' || key === 'idRequisitante' || key === 'idUsuario' || key === 'idColaborador') {
      return this.getUserDisplayName(strVal, false);
    }

    // 2. Resolver Equipes
    if (key === 'idEquipe' || key === 'equipe') {
      const equipeLabel = this.equipeMap().get(strVal);
      if (equipeLabel) return equipeLabel;
      if (this.isUuid(strVal)) return 'Equipe não localizada';
      return strVal;
    }

    // 3. Resolver Veículos
    if (key === 'idVeiculo' || key === 'veiculo') {
      const veiculoLabel = this.veiculoMap().get(strVal);
      if (veiculoLabel) return veiculoLabel;
      if (this.isUuid(strVal)) return 'Veículo não localizado';
      return strVal;
    }

    // 4. Resolver Materiais
    if (key === 'idMaterial') {
      const matLabel = this.materialMap().get(strVal);
      if (matLabel) return matLabel;
      if (this.isUuid(strVal)) return 'Material não localizado';
      return strVal;
    }

    // 5. Formatar Vínculos e Chaves de Inspeção / Desvio / Turno
    if (key === 'idInspecao') {
      return `Inspeção #${this.formatShortRef(strVal)}`;
    }
    if (key === 'idDesvio') {
      return `Desvio #${this.formatShortRef(strVal)}`;
    }
    if (key === 'idTurno') {
      return 'Turno não localizado';
    }
    if (key === 'idFormulario' || key === 'idProcesso' || key === 'idTipoInspecao') {
      return `Cadastro #${this.formatShortRef(strVal)}`;
    }

    // 6. Se for qualquer outro UUID puro
    if (this.isUuid(strVal)) {
      return 'Cadastro não localizado';
    }

    // 7. Formatar datas se for formato ISO
    if (key.toLowerCase().includes('data') && strVal.includes('T')) {
      try {
        const d = new Date(strVal);
        if (!isNaN(d.getTime())) {
          return d.toLocaleString('pt-BR');
        }
      } catch {
        // Fallback
      }
    }

    return strVal;
  }

  protected getFriendlySummaryEntries(payload: unknown): SummaryEntry[] {
    if (!payload || typeof payload !== 'object') return [];
    const p = this.unwrapPayload(payload);
    const entries: Array<SummaryEntry & { ordem: number }> = [];
    const ordem = (key: string) => {
      const i = SUMMARY_ORDER.indexOf(key);
      return i < 0 ? SUMMARY_ORDER.length : i;
    };

    for (const [key, val] of Object.entries(p)) {
      if (val === null || val === undefined || NON_EDITABLE_KEYS.has(key) || key === 'precisao') continue;
      if (typeof val === 'object') continue;
      if (LOCATION_KEYS.has(key)) continue;
      if (key === 'idInspecao' && this.isUuid(String(val))) {
        entries.push({ label: 'Inspeção vinculada', value: 'Abrir inspeção', href: `/sst/inspecoes/${val}`, ordem: ordem(key) });
        continue;
      }
      const label = FIELD_LABELS[key]?.label || this.formatKeyToLabel(key);
      entries.push({ label, value: this.formatSummaryValue(key, val), ordem: ordem(key) });
    }

    const lat = p['latitude'] ?? p['latitudeInicio'];
    const lng = p['longitude'] ?? p['longitudeInicio'];
    if (lat != null && lng != null) {
      entries.push({ label: 'Localização', value: 'Ver no mapa', href: `https://www.google.com/maps?q=${encodeURIComponent(`${lat},${lng}`)}`, ordem: SUMMARY_ORDER.length + 1 });
    }
    return entries.sort((a, b) => a.ordem - b.ordem).map(({ ordem: _ordem, ...entry }) => entry);
  }

  protected getComplexItems(payload: unknown): { materiais: FriendlyMaterialItem[]; acoes: Array<unknown> } {
    const p = this.unwrapPayload(payload);
    const rawMateriais = Array.isArray(p['materiais'])
      ? (p['materiais'] as Array<Record<string, unknown>>)
      : Array.isArray(p['itens'])
        ? (p['itens'] as Array<Record<string, unknown>>)
        : [];
    const materiais: FriendlyMaterialItem[] = rawMateriais.map(m => ({
      idMaterial: typeof m['idMaterial'] === 'string' ? m['idMaterial'] : undefined,
      codigo: typeof m['codigo'] === 'string' ? m['codigo'] : undefined,
      numSerie: typeof m['numSerie'] === 'string' ? m['numSerie'] : undefined,
      quantidade: (m['quantidade'] ?? m['qtd']) as number | string | undefined,
      operacao: typeof m['operacao'] === 'string' ? m['operacao'] : undefined
    }));
    const acoes = Array.isArray(p['acoes']) ? (p['acoes'] as Array<unknown>) : [];
    return { materiais, acoes };
  }

  protected formatFileSize(bytes: number): string {
    if (!bytes || bytes <= 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
  }

  private unwrapPayload(payload: unknown): Record<string, unknown> {
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return {};
    const rec = payload as Record<string, unknown>;
    if (rec['body'] && typeof rec['body'] === 'object' && !Array.isArray(rec['body'])) {
      return rec['body'] as Record<string, unknown>;
    }
    if (rec['request'] && typeof rec['request'] === 'object' && !Array.isArray(rec['request'])) {
      return rec['request'] as Record<string, unknown>;
    }
    return rec;
  }

  private formatKeyToLabel(key: string): string {
    const result = key.replace(/([A-Z])/g, ' $1');
    return result.charAt(0).toUpperCase() + result.slice(1);
  }

  private fail(error: unknown): void {
    this.loading.set(false);
    this.saving.set(false);
    this.messages.add({
      severity: 'error',
      summary: 'Não foi possível concluir',
      detail: getErrorMessage(error)
    });
  }
}
