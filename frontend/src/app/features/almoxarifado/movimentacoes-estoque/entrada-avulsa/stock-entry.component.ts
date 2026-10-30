import { CommonModule, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal, computed, ViewChild, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators, FormGroup } from '@angular/forms';
import { Router } from '@angular/router';
import { MenuItem, MessageService, ConfirmationService } from 'primeng/api';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { StepperModule } from 'primeng/stepper';

import { DateMaskDirective } from '../../../../shared/directives/date-mask.directive';
import { SaldoEstoqueDialogComponent } from '../../../../shared/components/saldo-estoque-dialog/saldo-estoque-dialog.component';
import { HelpDialogComponent } from '../../../../shared/components/help-dialog/help-dialog.component';
import { CesButtonComponent, CesStatusComponent } from '@ces/ui';
import { CesAutocompleteComponent } from '@ces/ui/forms';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import {
  EstoqueResponse,
  MaterialResponse,
  MovimentacaoBulkRequest,
} from '../../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RemoteLookup as CursorRemoteLookup } from '../../../../core/lookup/store/remote-lookup';
import { NavigationHistoryService, bindStepToUrl, createFormDraft } from '../../../../core/navigation';

export interface EntryItem {
  idMaterial: string;
  codigo: string;
  descricao: string;
  quantidade: number;
  unidadeMedida?: string;
  numSeries?: string[];
  detalhaSerie: boolean;
  condicao: string;
  temporaryQuantidade?: number;
}

@Component({
  selector: 'app-stock-entry',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    SelectModule,
    CesAutocompleteComponent,
    InputNumberModule,
    InputTextModule,
    TextareaModule,
    DatePickerModule,
    TableModule,
    ButtonModule,
    TooltipModule,
    DialogModule,
    BreadcrumbModule,
    StepperModule,
    SaldoEstoqueDialogComponent,
    HelpDialogComponent,
    DateMaskDirective,
    CesButtonComponent,
    CesStatusComponent,
    DecimalPipe,
  ],
  template: `
    <app-saldo-estoque-dialog #saldoDialog></app-saldo-estoque-dialog>

    <!-- Diálogo de Gerenciamento e Entrada de Séries -->
    <p-dialog
      header="Entrada de Números de Série"
      [(visible)]="serialDialogVisible"
      [modal]="true"
      [style]="{ width: '95vw', maxWidth: '520px' }"
      [draggable]="false"
      [resizable]="false"
      styleClass="custom-dialog"
      appendTo="body"
    >
      @if (selectedItemForSerials()) {
        <div class="flex flex-col gap-4 py-1">
          <div class="bg-emerald-50 dark:bg-emerald-950/50 p-3 rounded-xl border border-emerald-200 dark:border-emerald-800 flex items-start gap-3">
            <i class="pi pi-info-circle text-emerald-600 dark:text-emerald-400 mt-0.5"></i>
            <div class="flex flex-col gap-0.5">
              <span class="text-[10px] font-bold text-emerald-800 dark:text-emerald-300 uppercase tracking-wider">
                Controle Unitário de Séries
              </span>
              <p class="text-xs text-emerald-800 dark:text-emerald-200 leading-tight">
                Informe <b>{{ expectedSerialsCount() }}</b> número(s) de série correspondentes à quantidade deste item.
              </p>
            </div>
          </div>

          <div class="flex flex-col gap-1 p-3 bg-slate-50 dark:bg-slate-800/60 rounded-xl border border-slate-100 dark:border-slate-800">
            <span class="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-widest">Material Selecionado</span>
            <span class="text-xs font-bold text-slate-700 dark:text-slate-200 font-mono">
              {{ selectedItemForSerials()?.codigo }} - {{ selectedItemForSerials()?.descricao }}
            </span>
          </div>

          <!-- Opção Rápida de Colar Múltiplas Séries -->
          <div class="flex flex-col gap-1.5">
            <div class="flex justify-between items-center">
              <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                Colar Séries em Lote (Opcional)
              </label>
              <button
                type="button"
                (click)="applyBatchSerials()"
                class="text-[10px] font-bold text-primary-600 dark:text-primary-400 hover:underline"
              >
                Distribuir Séries
              </button>
            </div>
            <textarea
              pInputTextarea
              [(ngModel)]="batchSerialsText"
              rows="2"
              class="w-full text-xs p-2.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-800 dark:text-slate-100 font-mono"
              placeholder="Cole as séries separadas por quebra de linha ou vírgula..."
            ></textarea>
          </div>

          <!-- Campos Individuais de Série -->
          <div class="max-h-[260px] overflow-y-auto pr-1 flex flex-col gap-2 custom-scrollbar">
            @for (sn of serialsToEdit; track $index) {
              <div class="flex items-center gap-2">
                <span class="w-7 text-[10px] font-mono text-slate-400 font-bold text-right shrink-0">
                  #{{ $index + 1 }}
                </span>
                <input
                  pInputText
                  [(ngModel)]="serialsToEdit[$index]"
                  class="w-full !text-xs !py-2 !rounded-xl !font-mono uppercase"
                  placeholder="Número de série da unidade..."
                  maxlength="40"
                />
              </div>
            }
          </div>

          <div class="flex justify-end gap-2 mt-4 pt-3 border-t border-slate-100 dark:border-slate-800">
            <p-button
              label="Cancelar"
              [text]="true"
              severity="secondary"
              (onClick)="serialDialogVisible = false"
              styleClass="!text-xs !font-bold"
            ></p-button>
            <ces-button
              label="Salvar Séries"
              icon="pi pi-check"
              size="sm"
              (pressed)="saveSerials()"
            ></ces-button>
          </div>
        </div>
      }
    </p-dialog>

    <!-- Estrutura Principal -->
    <div class="flex flex-col gap-4 pb-28">
      <!-- Barra Superior: Breadcrumb e Voltar -->
      <div class="flex justify-between items-center">
        <p-breadcrumb [model]="breadcrumb" styleClass="!bg-transparent !border-0 !p-0" />
        <p-button
          icon="pi pi-arrow-left"
          label="Voltar para Movimentações"
          [text]="true"
          severity="secondary"
          (onClick)="cancel()"
          styleClass="!text-slate-500 dark:!text-slate-400 !text-xs !font-semibold"
        />
      </div>

      <!-- Card do Formulário -->
      <div class="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 overflow-hidden max-w-4xl w-full mx-auto shadow-sm">
        <!-- Header da Operação -->
        <div class="px-5 md:px-8 py-6 border-b border-slate-100 dark:border-slate-800 flex items-start justify-between gap-4">
          <div>
            <div class="flex items-center gap-2 mb-2">
              <div class="w-8 h-8 rounded-lg bg-emerald-50 dark:bg-emerald-950/60 border border-emerald-200 dark:border-emerald-800 flex items-center justify-center text-emerald-600 dark:text-emerald-400">
                <i class="pi pi-plus-circle text-sm"></i>
              </div>
              <span class="text-[10px] font-bold text-emerald-600 dark:text-emerald-400 uppercase tracking-widest">
                Operação de Estoque
              </span>
            </div>
            <h1 class="text-lg font-bold text-slate-800 dark:text-slate-100">
              Entrada Avulsa de Estoque
            </h1>
            <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
              Registre ajustes positivos, entradas avulsas e suprimentos de materiais no estoque de destino com conferência unitária de séries.
            </p>
          </div>
          <p-button
            icon="pi pi-question-circle"
            [text]="true"
            [rounded]="true"
            (onClick)="onHelp()"
            pTooltip="Guia de Entrada Avulsa"
            styleClass="!text-slate-400 hover:!text-emerald-600 !w-8 !h-8 shrink-0"
          />
        </div>

        <div class="px-5 md:px-8 py-6">
          <p-stepper [(value)]="activeStep">
            <p-step-list class="!justify-center mb-6">
              <p-step [value]="1" class="flex flex-row flex-auto gap-2">
                <ng-template #content let-value="value">
                  <button type="button" class="step-nav-btn" (click)="goToStep(1)">
                    <span
                      class="step-circle"
                      [ngClass]="value <= activeStep() ? 'step-circle-active-emerald' : 'step-circle-inactive'"
                    >
                      <i class="pi pi-building text-xs"></i>
                    </span>
                    <span
                      class="step-label"
                      [ngClass]="value <= activeStep() ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400'"
                    >
                      1. Destino & Dados
                    </span>
                  </button>
                </ng-template>
              </p-step>

              <p-step [value]="2" class="flex flex-row flex-auto gap-2">
                <ng-template #content let-value="value">
                  <button type="button" class="step-nav-btn" [disabled]="!isHeaderValid()" (click)="goToStep(2)">
                    <span
                      class="step-circle"
                      [ngClass]="value <= activeStep() ? 'step-circle-active-emerald' : 'step-circle-inactive'"
                    >
                      <i class="pi pi-box text-xs"></i>
                    </span>
                    <span
                      class="step-label"
                      [ngClass]="value <= activeStep() ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400'"
                    >
                      2. Materiais & Séries
                    </span>
                  </button>
                </ng-template>
              </p-step>

              <p-step [value]="3" class="flex flex-row flex-auto gap-2">
                <ng-template #content let-value="value">
                  <button type="button" class="step-nav-btn" [disabled]="items().length === 0" (click)="goToStep(3)">
                    <span
                      class="step-circle"
                      [ngClass]="value <= activeStep() ? 'step-circle-active-emerald' : 'step-circle-inactive'"
                    >
                      <i class="pi pi-check text-xs"></i>
                    </span>
                    <span
                      class="step-label"
                      [ngClass]="value <= activeStep() ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400'"
                    >
                      3. Revisão & Efetivação
                    </span>
                  </button>
                </ng-template>
              </p-step>
            </p-step-list>

            <p-step-panels>
              <!-- ETAPA 1: DESTINO E MOTIVO -->
              <p-step-panel [value]="1">
                <ng-template #content>
                  <div [formGroup]="headerForm" class="flex flex-col gap-6">
                    <div class="border-l-2 border-emerald-500 pl-3">
                      <h3 class="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                        1. Estoque de Destino e Justificativa
                      </h3>
                      <p class="text-[11px] text-slate-400 mt-0.5">
                        Defina o estoque onde os materiais entrarão e registre o motivo da entrada.
                      </p>
                    </div>

                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <!-- Estoque de Destino -->
                      <div class="flex flex-col gap-1.5 md:col-span-2">
                        <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                          Estoque de Destino *
                        </label>
                        <div class="flex items-center gap-2">
                          <p-select
                            formControlName="idEstoqueDestino"
                            [options]="estoques()"
                            optionLabel="nomeIdentificador"
                            optionValue="id"
                            placeholder="Selecione o estoque onde os itens entrarão..."
                            [filter]="true"
                            [showClear]="true"
                            filterBy="nomeIdentificador"
                            class="custom-select flex-1"
                            size="small"
                            appendTo="body"
                          ></p-select>
                          @if (headerForm.get('idEstoqueDestino')?.value) {
                            <button
                              type="button"
                              pTooltip="Ver Saldo em Estoque"
                              (click)="abrirSaldo(headerForm.get('idEstoqueDestino')?.value)"
                              class="w-9 h-9 rounded-xl flex items-center justify-center bg-slate-100 dark:bg-slate-800 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-50 dark:hover:bg-slate-700 transition-colors shrink-0"
                            >
                              <i class="pi pi-box"></i>
                            </button>
                          }
                        </div>
                        @if (fieldError('idEstoqueDestino')) {
                          <span class="text-[10px] text-rose-500 font-semibold">{{ fieldError('idEstoqueDestino') }}</span>
                        }
                      </div>

                      <!-- Data da Entrada -->
                      <div class="flex flex-col gap-1.5">
                        <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                          Data da Entrada *
                        </label>
                        <p-datepicker
                          dateMask
                          formControlName="data"
                          dateFormat="dd/mm/yy"
                          [showIcon]="true"
                          iconDisplay="input"
                          [showOnFocus]="true"
                          placeholder="dd/mm/aaaa"
                          class="custom-datepicker w-full"
                          size="small"
                          appendTo="body"
                        ></p-datepicker>
                      </div>

                      <!-- Motivo da Entrada -->
                      <div class="flex flex-col gap-1.5">
                        <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                          Motivo da Entrada *
                        </label>
                        <p-select
                          formControlName="motivo"
                          [options]="motivoOptions"
                          optionLabel="label"
                          optionValue="value"
                          placeholder="Selecione o motivo..."
                          class="custom-select w-full"
                          size="small"
                          appendTo="body"
                        ></p-select>
                      </div>

                      <!-- Observações -->
                      <div class="flex flex-col gap-1.5 md:col-span-2">
                        <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                          Observações / Detalhes Operacionais
                        </label>
                        <textarea
                          pInputTextarea
                          formControlName="observacoes"
                          rows="2"
                          class="w-full text-xs p-3 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-800 dark:text-slate-100"
                          placeholder="Informe número de nota, processo, justificativa ou contexto desta entrada..."
                        ></textarea>
                      </div>
                    </div>

                    <div class="flex justify-end gap-3 pt-4 border-t border-slate-100 dark:border-slate-800">
                      <ces-button
                        label="Avançar para Materiais"
                        icon="pi pi-arrow-right"
                        [disabled]="!isHeaderValid()"
                        (pressed)="activeStep.set(2)"
                      ></ces-button>
                    </div>
                  </div>
                </ng-template>
              </p-step-panel>

              <!-- ETAPA 2: MATERIAIS E SÉRIES -->
              <p-step-panel [value]="2">
                <ng-template #content>
                  <div class="flex flex-col gap-6">
                    <div class="border-l-2 border-emerald-500 pl-3">
                      <h3 class="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                        2. Materiais a Ingressar
                      </h3>
                      <p class="text-[11px] text-slate-400 mt-0.5">
                        Localize o material no catálogo, informe a condição física, quantidade e números de série.
                      </p>
                    </div>

                    <!-- Card de Inserção de Item -->
                    <div [formGroup]="itemForm" class="p-4 bg-slate-50/70 dark:bg-slate-800/40 rounded-2xl border border-slate-200/80 dark:border-slate-800 flex flex-col gap-4">
                      <div class="grid grid-cols-1 md:grid-cols-12 gap-3 items-end">
                        <div class="flex flex-col gap-1.5 md:col-span-5">
                          <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                            Material (Busca no Catálogo) *
                          </label>
                          <ces-autocomplete
                            inputId="materialInput"
                            [label]="'Material'"
                            [labelVisible]="false"
                            [options]="materials()"
                            [ngModel]="selectedMaterialId()"
                            [ngModelOptions]="{ standalone: true }"
                            (ngModelChange)="onMaterialChange($event)"
                            [loading]="materialsLoading() || materialFetching()"
                            [status]="materialsStatus()"
                            [error]="materialsError()"
                            [minLength]="2"
                            [virtualScroll]="true"
                            [virtualScrollItemSize]="38"
                            [clearable]="true"
                            placeholder="Código ou descrição do material..."
                            class="w-full"
                            appendTo="body"
                            (search)="searchMaterials($event)"
                            (loadMore)="loadMoreMaterials()"
                            (retry)="retryMaterials()"
                          ></ces-autocomplete>
                        </div>

                        <div class="flex flex-col gap-1.5 md:col-span-3">
                          <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                            Condição *
                          </label>
                          <p-select
                            formControlName="condicao"
                            [options]="condicaoOptions"
                            optionLabel="label"
                            optionValue="value"
                            placeholder="Condição..."
                            class="custom-select w-full"
                            size="small"
                            appendTo="body"
                          ></p-select>
                        </div>

                        <div class="flex flex-col gap-1.5 md:col-span-2">
                          <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                            Qtd ({{ unidadeMedida() }}) *
                          </label>
                          <p-inputNumber
                            formControlName="quantidade"
                            [min]="0.001"
                            [minFractionDigits]="3"
                            [maxFractionDigits]="3"
                            size="small"
                            class="w-full"
                            inputStyleClass="!w-full !text-xs !font-bold"
                            placeholder="0,000"
                          ></p-inputNumber>
                        </div>

                        <div class="md:col-span-2">
                          <ces-button
                            label="Adicionar"
                            icon="pi pi-plus"
                            size="sm"
                            [loading]="materialFetching()"
                            [disabled]="itemForm.invalid || !selectedMaterial() || materialFetching()"
                            (pressed)="addItem()"
                            className="w-full"
                          ></ces-button>
                        </div>
                      </div>

                      @if (selectedMaterial()) {
                        <div class="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
                          <i class="pi pi-info-circle text-xs text-emerald-500"></i>
                          <span>
                            Item selecionado: <b class="text-slate-700 dark:text-slate-200">{{ selectedMaterial()?.codigo }} - {{ selectedMaterial()?.descricao }}</b>.
                            @if (selectedMaterial()?.detalhaSerie) {
                              <span class="text-emerald-700 dark:text-emerald-400 font-bold ml-1">
                                Exige o registro de números de série para cada unidade.
                              </span>
                            }
                          </span>
                        </div>
                      }
                    </div>

                    <!-- Tabela de Itens Adicionados -->
                    <div class="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200/80 dark:border-slate-800 overflow-hidden shadow-sm">
                      <div class="p-3.5 bg-slate-50 dark:bg-slate-800/80 border-b border-slate-200 dark:border-slate-800 flex justify-between items-center">
                        <span class="text-[10px] font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                          Materiais a Creditar no Estoque
                        </span>
                        <ces-status tone="success">
                          {{ items().length }} {{ items().length === 1 ? 'item' : 'itens' }}
                        </ces-status>
                      </div>

                      <p-table [value]="items()" class="p-datatable-sm" [responsiveLayout]="'scroll'">
                        <ng-template pTemplate="header">
                          <tr class="bg-slate-50/50 dark:bg-slate-800/40 border-b border-slate-200 dark:border-slate-800">
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5 text-center" style="width: 100px;">Ações</th>
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5" style="width: 110px">Código</th>
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5">Descrição</th>
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5 text-center" style="width: 90px">Condição</th>
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5 text-right" style="width: 110px">Qtd.</th>
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5 text-center" style="width: 60px">Un.</th>
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5 text-center" style="width: 180px">Séries</th>
                          </tr>
                        </ng-template>
                        <ng-template pTemplate="body" let-item let-rowIndex="rowIndex">
                          <tr class="border-b border-slate-100 dark:border-slate-800/70 hover:bg-slate-50/30 dark:hover:bg-slate-800/20">
                            <td class="!py-2.5 text-center">
                              <div class="flex items-center justify-center gap-1">
                                @if (editingIndex() === rowIndex) {
                                  <button
                                    type="button"
                                    (click)="saveEdit(rowIndex)"
                                    pTooltip="Salvar Edição"
                                    class="w-7 h-7 rounded-lg flex items-center justify-center text-emerald-600 hover:bg-emerald-50 dark:hover:bg-emerald-950/40"
                                  >
                                    <i class="pi pi-check text-xs"></i>
                                  </button>
                                  <button
                                    type="button"
                                    (click)="cancelEdit()"
                                    pTooltip="Cancelar"
                                    class="w-7 h-7 rounded-lg flex items-center justify-center text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800"
                                  >
                                    <i class="pi pi-times text-xs"></i>
                                  </button>
                                } @else {
                                  <button
                                    type="button"
                                    (click)="startEdit(rowIndex)"
                                    pTooltip="Editar Quantidade"
                                    class="w-7 h-7 rounded-lg flex items-center justify-center text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-950/40"
                                  >
                                    <i class="pi pi-pencil text-xs"></i>
                                  </button>
                                  <button
                                    type="button"
                                    (click)="removeItem(rowIndex)"
                                    pTooltip="Remover Item"
                                    class="w-7 h-7 rounded-lg flex items-center justify-center text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950/40"
                                  >
                                    <i class="pi pi-trash text-xs"></i>
                                  </button>
                                  @if (item.detalhaSerie) {
                                    <button
                                      type="button"
                                      (click)="openSerialManager(rowIndex)"
                                      pTooltip="Gerenciar Números de Série"
                                      class="w-7 h-7 rounded-lg flex items-center justify-center text-emerald-600 dark:text-emerald-400 hover:bg-emerald-50 dark:hover:bg-emerald-950/40"
                                    >
                                      <i class="pi pi-barcode text-xs"></i>
                                    </button>
                                  }
                                }
                              </div>
                            </td>
                            <td class="!py-2.5 text-xs font-mono font-bold text-slate-600 dark:text-slate-300">
                              {{ item.codigo }}
                            </td>
                            <td class="!py-2.5 text-xs font-medium text-slate-800 dark:text-slate-200">
                              {{ item.descricao }}
                            </td>
                            <td class="!py-2.5 text-center">
                              <span
                                class="text-[9px] font-bold uppercase tracking-wider px-2 py-0.5 rounded"
                                [ngClass]="getCondicaoBadgeClass(item.condicao)"
                              >
                                {{ item.condicao }}
                              </span>
                            </td>
                            <td class="!py-2.5 text-right font-mono font-bold text-xs">
                              @if (editingIndex() === rowIndex) {
                                <p-inputNumber
                                  [(ngModel)]="item.temporaryQuantidade"
                                  [min]="0.001"
                                  [minFractionDigits]="3"
                                  [maxFractionDigits]="3"
                                  size="small"
                                  inputStyleClass="!w-[75px] !text-right !text-xs !py-1 !font-bold"
                                ></p-inputNumber>
                              } @else {
                                <span class="text-slate-800 dark:text-slate-100">
                                  {{ item.quantidade | number: '1.3-3' }}
                                </span>
                              }
                            </td>
                            <td class="!py-2.5 text-center text-xs text-slate-500">
                              {{ item.unidadeMedida }}
                            </td>
                            <td class="!py-2.5 text-center">
                              @if (item.detalhaSerie) {
                                @if (isItemSerialsComplete(item)) {
                                  <span class="inline-flex items-center gap-1 text-[9px] font-bold text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-200 dark:border-emerald-800">
                                    <i class="pi pi-check text-[9px]"></i>
                                    {{ item.numSeries?.length }} séries cadastradas
                                  </span>
                                } @else {
                                  <button
                                    type="button"
                                    (click)="openSerialManager(rowIndex)"
                                    class="inline-flex items-center gap-1 text-[9px] font-bold text-rose-700 dark:text-rose-400 bg-rose-50 dark:bg-rose-950/60 px-2 py-0.5 rounded border border-rose-200 dark:border-rose-800 hover:scale-105 transition-transform"
                                  >
                                    <i class="pi pi-exclamation-triangle text-[9px]"></i>
                                    {{ countFilledSerials(item) }}/{{ Math.floor(item.quantidade) }} pendente
                                  </button>
                                }
                              } @else {
                                <span class="text-slate-400 text-xs">-</span>
                              }
                            </td>
                          </tr>
                        </ng-template>
                        <ng-template pTemplate="emptymessage">
                          <tr>
                            <td colspan="7" class="text-center py-8 text-slate-400 text-xs">
                              Nenhum material adicionado até o momento.
                            </td>
                          </tr>
                        </ng-template>
                      </p-table>

                      @if (items().length > 0) {
                        <div class="p-3 bg-slate-50 dark:bg-slate-800/50 border-t border-slate-200 dark:border-slate-800 flex justify-between items-center text-xs">
                          <span class="text-slate-500 dark:text-slate-400 font-medium">Volume Total a Creditar:</span>
                          <span class="font-bold text-emerald-600 dark:text-emerald-400 font-mono">
                            +{{ totalQuantidade() | number: '1.3-3' }} unidades
                          </span>
                        </div>
                      }
                    </div>

                    <div class="flex justify-between items-center pt-4 border-t border-slate-100 dark:border-slate-800">
                      <ces-button
                        label="Voltar para Destino"
                        icon="pi pi-arrow-left"
                        variant="secondary"
                        size="sm"
                        (pressed)="goToStep(1)"
                      ></ces-button>

                      <ces-button
                        label="Avançar para Revisão"
                        icon="pi pi-arrow-right"
                        size="sm"
                        [disabled]="items().length === 0"
                        (pressed)="goToStep(3)"
                      ></ces-button>
                    </div>
                  </div>
                </ng-template>
              </p-step-panel>

              <!-- ETAPA 3: REVISÃO E EFETIVAÇÃO -->
              <p-step-panel [value]="3">
                <ng-template #content>
                  <div class="flex flex-col gap-6">
                    <div class="border-l-2 border-emerald-500 pl-3">
                      <h3 class="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                        3. Revisão e Efetivação
                      </h3>
                      <p class="text-[11px] text-slate-400 mt-0.5">
                        Confira o estoque de crédito e os materiais antes de efetivar a entrada avulsa.
                      </p>
                    </div>

                    <!-- Cards de Resumo -->
                    <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
                      <div class="bg-slate-50 dark:bg-slate-800/60 p-4 rounded-xl border border-slate-100 dark:border-slate-800 flex flex-col gap-1">
                        <span class="text-[9px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">Estoque de Destino</span>
                        <span class="text-xs font-bold text-slate-800 dark:text-slate-100 mt-0.5">
                          {{ nomeEstoqueDestino() }}
                        </span>
                      </div>

                      <div class="bg-slate-50 dark:bg-slate-800/60 p-4 rounded-xl border border-slate-100 dark:border-slate-800 flex flex-col gap-1">
                        <span class="text-[9px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">Data de Lançamento</span>
                        <span class="text-xs font-bold text-slate-800 dark:text-slate-100 mt-0.5">
                          {{ headerForm.get('data')?.value | date: 'dd/MM/yyyy' }}
                        </span>
                      </div>

                      <div class="bg-slate-50 dark:bg-slate-800/60 p-4 rounded-xl border border-slate-100 dark:border-slate-800 flex flex-col gap-1">
                        <span class="text-[9px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">Motivo Informado</span>
                        <span class="text-xs font-bold text-emerald-600 dark:text-emerald-400 mt-0.5">
                          {{ getMotivoLabel(headerForm.get('motivo')?.value) }}
                        </span>
                      </div>
                    </div>

                    @if (headerForm.get('observacoes')?.value) {
                      <div class="p-3 bg-slate-50 dark:bg-slate-800/40 rounded-xl border border-slate-100 dark:border-slate-800 text-xs">
                        <span class="font-bold text-slate-500 uppercase text-[9px] block mb-1">Observações:</span>
                        <span class="text-slate-700 dark:text-slate-300">{{ headerForm.get('observacoes')?.value }}</span>
                      </div>
                    }

                    <!-- Alertas de Validação de Séries -->
                    @if (hasMissingSerials()) {
                      <div class="p-3.5 bg-rose-50 dark:bg-rose-950/60 rounded-xl border border-rose-200 dark:border-rose-800 flex items-start gap-3">
                        <i class="pi pi-times-circle text-rose-600 dark:text-rose-400 text-base mt-0.5"></i>
                        <div class="flex flex-col gap-0.5">
                          <span class="text-xs font-bold text-rose-800 dark:text-rose-300">Números de Série Pendentes</span>
                          <p class="text-xs text-rose-700 dark:text-rose-300/90 leading-tight">
                            Existem materiais com controle de série que ainda não possuem todas as séries cadastradas. Volte para a etapa de Materiais e preencha os números antes de confirmar.
                          </p>
                        </div>
                      </div>
                    }

                    <!-- Tabela de Resumo -->
                    <div class="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200/80 dark:border-slate-800 overflow-hidden shadow-sm">
                      <div class="p-3.5 bg-slate-50 dark:bg-slate-800/80 border-b border-slate-200 dark:border-slate-800 flex justify-between items-center">
                        <span class="text-[10px] font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                          Resumo dos Materiais a Ingressar
                        </span>
                        <span class="text-xs font-bold text-emerald-600 dark:text-emerald-400 font-mono">
                          +{{ totalQuantidade() | number: '1.3-3' }} unidades adicionadas
                        </span>
                      </div>

                      <p-table [value]="items()" class="p-datatable-sm" [responsiveLayout]="'scroll'">
                        <ng-template pTemplate="header">
                          <tr class="bg-slate-50/50 dark:bg-slate-800/40 border-b border-slate-200 dark:border-slate-800">
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5 !pl-4" style="width: 120px">Código</th>
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5">Descrição</th>
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5 text-center" style="width: 100px">Condição</th>
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5 text-right" style="width: 120px">Quantidade</th>
                            <th class="!text-[9px] !font-bold !uppercase !py-2.5 text-center !pr-4" style="width: 200px">Séries</th>
                          </tr>
                        </ng-template>
                        <ng-template pTemplate="body" let-item>
                          <tr class="border-b border-slate-100 dark:border-slate-800/70">
                            <td class="!py-2.5 !pl-4 text-xs font-mono font-bold text-slate-600 dark:text-slate-300">
                              {{ item.codigo }}
                            </td>
                            <td class="!py-2.5 text-xs font-medium text-slate-800 dark:text-slate-200">
                              {{ item.descricao }}
                            </td>
                            <td class="!py-2.5 text-center">
                              <span
                                class="text-[9px] font-bold uppercase tracking-wider px-2 py-0.5 rounded"
                                [ngClass]="getCondicaoBadgeClass(item.condicao)"
                              >
                                {{ item.condicao }}
                              </span>
                            </td>
                            <td class="!py-2.5 text-right font-mono font-bold text-xs text-emerald-600 dark:text-emerald-400">
                              +{{ item.quantidade | number: '1.3-3' }} {{ item.unidadeMedida }}
                            </td>
                            <td class="!py-2.5 text-center !pr-4">
                              @if (item.detalhaSerie) {
                                <div class="flex flex-wrap gap-1 justify-center max-w-[200px] mx-auto">
                                  @for (sn of item.numSeries; track sn) {
                                    <span class="text-[9px] font-mono bg-slate-100 dark:bg-slate-800 px-1.5 py-0.5 rounded text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700">
                                      {{ sn }}
                                    </span>
                                  }
                                </div>
                              } @else {
                                <span class="text-slate-400 text-xs">-</span>
                              }
                            </td>
                          </tr>
                        </ng-template>
                      </p-table>
                    </div>

                    <div class="flex justify-between items-center pt-4 border-t border-slate-100 dark:border-slate-800">
                      <ces-button
                        label="Voltar para Materiais"
                        icon="pi pi-arrow-left"
                        variant="secondary"
                        size="sm"
                        (pressed)="goToStep(2)"
                      ></ces-button>

                      <ces-button
                        label="Efetivar Entrada Avulsa"
                        icon="pi pi-check-circle"
                        variant="success"
                        [disabled]="items().length === 0 || hasMissingSerials() || loading()"
                        [loading]="loading()"
                        (pressed)="confirmAdjustment()"
                      ></ces-button>
                    </div>
                  </div>
                </ng-template>
              </p-step-panel>
            </p-step-panels>
          </p-stepper>
        </div>
      </div>
    </div>

    <!-- Diálogo de Ajuda -->
    <app-help-dialog #helpDialog title="Guia da Tela: Entrada Avulsa">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-plus-circle"></i> Entrada Avulsa de Estoque</h4>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-info-circle"></i></div>
          <div class="help-text">
            <span class="help-text-title">Ajustes Positivos</span>
            <span class="help-text-desc">Utilize esta rotina para registrar sobras de campo, doações, ajustes positivos de contagem física ou aquisições extraordinárias.</span>
          </div>
        </div>
      </div>
    </app-help-dialog>
  `,
  styles: [`
    .step-nav-btn {
      background: transparent;
      border: 0;
      display: inline-flex;
      flex-direction: column;
      align-items: center;
      gap: 0.375rem;
      cursor: pointer;
    }
    .step-nav-btn:disabled {
      cursor: default;
      opacity: 0.5;
    }
    .step-circle {
      border-radius: 9999px;
      border-width: 2px;
      width: 2.25rem;
      height: 2.25rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      transition: all 0.25s ease-in-out;
    }
    .step-circle-active-emerald {
      background: #10b981;
      color: white;
      border-color: #10b981;
    }
    .step-circle-inactive {
      border-color: #cbd5e1;
      color: #94a3b8;
      background: transparent;
    }
    :host-context(.app-dark) .step-circle-inactive,
    :host-context(.dark) .step-circle-inactive {
      border-color: #334155;
      color: #64748b;
    }
    .step-label {
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockEntryComponent {
  protected readonly Math = Math;
  private fb = inject(FormBuilder);
  private almoxService = inject(AlmoxarifadoService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);
  private lookupStore = inject(LookupStore);
  private destroyRef = inject(DestroyRef);

  @ViewChild('saldoDialog') saldoDialog!: SaldoEstoqueDialogComponent;
  @ViewChild('helpDialog') helpDialog!: HelpDialogComponent;

  breadcrumb: MenuItem[] = [
    { label: 'Almoxarifado' },
    { label: 'Movimentações', routerLink: '/almoxarifado/movimentacoes' },
    { label: 'Entrada Avulsa' },
  ];

  activeStep = signal<1 | 2 | 3>(1);
  loading = signal<boolean>(false);
  estoques = signal<EstoqueResponse[]>([]);
  items = signal<EntryItem[]>([]);
  editingIndex = signal<number | null>(null);

  headerForm: FormGroup = this.fb.group({
    idEstoqueDestino: [null, Validators.required],
    data: [new Date(), Validators.required],
    motivo: ['AJUSTE_INVENTARIO', Validators.required],
    observacoes: [null],
  });

  itemForm: FormGroup = this.fb.group({
    condicao: ['NOVO', Validators.required],
    quantidade: [null, [Validators.required, Validators.min(0.001)]],
  });

  motivoOptions = [
    { label: 'Ajuste de Inventário (Positivo)', value: 'AJUSTE_INVENTARIO' },
    { label: 'Devolução de Equipe / Obra', value: 'DEVOLUCAO_CAMPO' },
    { label: 'Aquisição / Suprimento Extraordinário', value: 'AQUISICAO_EXTRA' },
    { label: 'Doação ou Bonificação', value: 'DOACAO_BONIFICACAO' },
    { label: 'Outros', value: 'OUTROS' },
  ];

  condicaoOptions = [
    { label: 'Novo', value: 'NOVO' },
    { label: 'Recuperado', value: 'RECUPERADO' },
    { label: 'Usado', value: 'USADO' },
    { label: 'Sucata', value: 'SUCATA' },
  ];

  selectedMaterialId = signal<string | null>(null);
  selectedMaterial = signal<MaterialResponse | null>(null);
  materialFetching = signal<boolean>(false);
  unidadeMedida = computed(() => this.selectedMaterial()?.unidadeMedida || '-');

  // Lookup de Materiais
  private readonly materialLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchMaterials({ query, cursor }),
    selectedValues: () => {
      const id = this.selectedMaterialId();
      return id ? [id] : [];
    },
    destroyRef: this.destroyRef,
  });

  protected readonly materials = this.materialLookup.items;
  protected readonly materialsLoading = this.materialLookup.loading;
  protected readonly materialsStatus = this.materialLookup.status;
  protected readonly materialsError = computed(() => {
    const error = this.materialLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });

  // Gestão de Séries
  serialDialogVisible = false;
  selectedItemForSerials = signal<EntryItem | null>(null);
  serialsToEdit: string[] = [];
  batchSerialsText = '';
  editingSerialIndex = signal<number | null>(null);

  expectedSerialsCount = computed(() => {
    const qty = this.selectedItemForSerials()?.quantidade || 0;
    return Math.floor(qty);
  });

  totalQuantidade = computed(() => {
    return this.items().reduce((acc, item) => acc + (item.quantidade || 0), 0);
  });

  nomeEstoqueDestino = computed(() => {
    const id = this.headerForm.get('idEstoqueDestino')?.value;
    return this.estoques().find((e) => e.id === id)?.nomeIdentificador || '-';
  });

  hasMissingSerials = computed(() => {
    return this.items().some(
      (i) => i.detalhaSerie && (!i.numSeries || i.numSeries.some((sn) => !sn || sn.trim() === '')),
    );
  });

  private readonly passoUrl = bindStepToUrl(this.activeStep);
  /** Rascunho do wizard. Só em memória: os itens podem trazer números de série (dado sensível). */
  private readonly rascunho = createFormDraft<{ cabecalho: Record<string, unknown>; itens: EntryItem[] }>({
    persist: false,
    read: () => ({ cabecalho: this.headerForm.getRawValue(), itens: this.items() }),
    write: (valor) => {
      // Cabeçalho primeiro: trocar o estoque/equipe limpa os itens (valueChanges).
      this.headerForm.patchValue(valor.cabecalho);
      this.items.set(valor.itens ?? []);
    },
  });

  constructor() {
    this.loadInitialData();
    const restaurado = this.rascunho.restore();
    this.passoUrl.restore(() => restaurado);
  }

  loadInitialData() {
    this.almoxService.listarEstoquesAtivos().subscribe({
      next: (res) => this.estoques.set(res.content),
      error: () =>
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Falha ao carregar estoques',
        }),
    });
  }

  isHeaderValid(): boolean {
    return this.headerForm.valid;
  }

  fieldError(field: string): string | null {
    const ctrl = this.headerForm.get(field);
    if (!ctrl || !ctrl.touched) return null;
    if (ctrl.hasError('required')) return 'Campo obrigatório';
    return null;
  }

  goToStep(step: 1 | 2 | 3) {
    if (step === 2 && !this.isHeaderValid()) {
      this.headerForm.markAllAsTouched();
      return;
    }
    if (step === 3 && this.items().length === 0) return;
    this.activeStep.set(step);
  }

  abrirSaldo(estoqueId: string) {
    const nome = this.estoques().find((s) => s.id === estoqueId)?.nomeIdentificador || estoqueId;
    this.saldoDialog.show(estoqueId, nome);
  }

  onHelp() {
    this.helpDialog.show();
  }

  cancel() {
    this.rascunho.clear();
    this.navegacao.back(['/almoxarifado/movimentacoes']);
  }

  searchMaterials(query: string): void {
    this.materialLookup.search(query);
  }

  loadMoreMaterials(): void {
    this.materialLookup.loadNext();
  }

  retryMaterials(): void {
    this.materialLookup.retry();
  }

  onMaterialChange(id: string | null): void {
    this.selectedMaterialId.set(id);
    if (!id) {
      this.selectedMaterial.set(null);
      return;
    }

    this.materialFetching.set(true);
    this.almoxService.buscarMaterialPorId(id).subscribe({
      next: (material) => {
        if (this.selectedMaterialId() === id) {
          this.selectedMaterial.set(material);
        }
        this.materialFetching.set(false);
      },
      error: () => {
        if (this.selectedMaterialId() === id) {
          this.selectedMaterial.set(null);
        }
        this.materialFetching.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Falha ao carregar detalhes do material',
        });
      },
    });
  }

  onMaterialSelected(option: any): void {
    if (typeof option === 'string') {
      this.onMaterialChange(option);
    } else if (option?.value) {
      this.onMaterialChange(option.value);
    } else if (option?.id) {
      this.selectedMaterial.set(option);
      this.selectedMaterialId.set(option.id);
    } else {
      this.onMaterialChange(null);
    }
  }

  addItem() {
    const material = this.selectedMaterial();
    if (!material) return;

    const formVal = this.itemForm.getRawValue();
    if (this.itemForm.invalid) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'Preencha a quantidade corretamente',
      });
      return;
    }

    if (this.items().some((i) => i.idMaterial === material.id && i.condicao === formVal.condicao)) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'Este item com esta condição já foi adicionado',
      });
      return;
    }

    const newItem: EntryItem = {
      idMaterial: material.id,
      codigo: material.codigo,
      descricao: material.descricao,
      quantidade: formVal.quantidade,
      unidadeMedida: material.unidadeMedida,
      numSeries: material.detalhaSerie ? Array(Math.floor(formVal.quantidade)).fill('') : undefined,
      detalhaSerie: material.detalhaSerie,
      condicao: formVal.condicao,
    };

    this.items.update((prev) => [...prev, newItem]);
    this.itemForm.reset({ condicao: 'NOVO' });
    this.selectedMaterial.set(null);
    this.selectedMaterialId.set(null);
  }

  removeItem(index: number) {
    this.items.update((prev) => prev.filter((_, i) => i !== index));
    if (this.editingIndex() === index) {
      this.editingIndex.set(null);
    }
  }

  startEdit(index: number) {
    const item = this.items()[index];
    item.temporaryQuantidade = item.quantidade;
    this.editingIndex.set(index);
  }

  saveEdit(index: number) {
    const item = this.items()[index];
    const tempQty = item.temporaryQuantidade;

    if (tempQty === undefined || tempQty === null || tempQty <= 0) {
      this.editingIndex.set(null);
      return;
    }

    let newNumSeries = [...(item.numSeries || [])];
    if (item.detalhaSerie) {
      const newQty = Math.floor(tempQty);
      if (newQty > newNumSeries.length) {
        newNumSeries = [...newNumSeries, ...Array(newQty - newNumSeries.length).fill('')];
      } else if (newQty < newNumSeries.length) {
        newNumSeries = newNumSeries.slice(0, newQty);
      }
    }

    this.items.update((prev) => {
      const next = [...prev];
      next[index] = {
        ...item,
        quantidade: tempQty,
        numSeries: item.detalhaSerie ? newNumSeries : undefined,
        temporaryQuantidade: undefined,
      };
      return next;
    });

    this.editingIndex.set(null);
    this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Quantidade atualizada' });
  }

  cancelEdit() {
    this.editingIndex.set(null);
  }

  openSerialManager(index: number) {
    const item = this.items()[index];
    this.selectedItemForSerials.set(item);
    const qty = Math.floor(item.quantidade);
    const existing = item.numSeries || [];
    this.serialsToEdit = Array.from({ length: qty }, (_, i) => existing[i] || '');
    this.batchSerialsText = '';
    this.editingSerialIndex.set(index);
    this.serialDialogVisible = true;
  }

  applyBatchSerials() {
    if (!this.batchSerialsText.trim()) return;
    const tokens = this.batchSerialsText
      .split(/[\n,;\t]+/)
      .map((s) => s.trim().toUpperCase())
      .filter((s) => s.length > 0);

    for (let i = 0; i < Math.min(tokens.length, this.serialsToEdit.length); i++) {
      this.serialsToEdit[i] = tokens[i];
    }
    this.messageService.add({
      severity: 'info',
      summary: 'Séries Aplicadas',
      detail: `${Math.min(tokens.length, this.serialsToEdit.length)} série(s) distribuída(s)`,
    });
  }

  saveSerials() {
    const index = this.editingSerialIndex();
    if (index !== null) {
      this.items.update((prev) => {
        const next = [...prev];
        next[index] = {
          ...next[index],
          numSeries: this.serialsToEdit.map((s) => s.trim().toUpperCase()),
        };
        return next;
      });
    }
    this.serialDialogVisible = false;
    this.messageService.add({
      severity: 'success',
      summary: 'Sucesso',
      detail: 'Números de série registrados',
    });
  }

  isItemSerialsComplete(item: EntryItem): boolean {
    if (!item.detalhaSerie) return true;
    const expected = Math.floor(item.quantidade);
    return !!item.numSeries && item.numSeries.length === expected && item.numSeries.every((s) => !!s && s.trim() !== '');
  }

  countFilledSerials(item: EntryItem): number {
    return (item.numSeries || []).filter((s) => !!s && s.trim() !== '').length;
  }

  confirmAdjustment() {
    if (this.items().length === 0) {
      this.messageService.add({ severity: 'warn', summary: 'Atenção', detail: 'Adicione ao menos um item' });
      return;
    }

    if (this.hasMissingSerials()) {
      this.messageService.add({
        severity: 'error',
        summary: 'Atenção',
        detail: 'Preencha todos os números de série obrigatórios na lista de materiais.',
      });
      return;
    }

    this.confirmationService.confirm({
      message: `Confirma a entrada avulsa de ${this.items().length} material(is) no estoque ${this.nomeEstoqueDestino()}?`,
      header: 'Confirmar Entrada Avulsa',
      icon: 'pi pi-plus-circle',
      acceptLabel: 'Efetivar Entrada',
      rejectLabel: 'Revisar',
      acceptButtonStyleClass: '!bg-emerald-600 !border-emerald-600',
      accept: () => this.executeAdjustment(),
    });
  }

  private executeAdjustment() {
    this.loading.set(true);
    const header = this.headerForm.value;

    const flatItens: any[] = [];
    this.items().forEach((i) => {
      if (i.detalhaSerie && i.numSeries) {
        i.numSeries.forEach((sn) => {
          flatItens.push({
            idMaterial: i.idMaterial,
            quantidade: 1.0,
            numSerie: sn,
            condicao: i.condicao,
          });
        });
      } else {
        flatItens.push({
          idMaterial: i.idMaterial,
          quantidade: i.quantidade,
          numSerie: undefined,
          condicao: i.condicao,
        });
      }
    });

    const request: MovimentacaoBulkRequest = {
      idEstoqueDestino: header.idEstoqueDestino,
      itens: flatItens,
    };

    this.almoxService.registrarEntradaLote(request).subscribe({
      next: () => {
        this.loading.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Entrada avulsa realizada com sucesso!',
        });
        this.rascunho.clear();
        setTimeout(() => {
          void this.router.navigate(['/almoxarifado/movimentacoes']);
        }, 1200);
      },
      error: (err) => {
        this.loading.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro na Entrada',
          detail: getErrorMessage(err),
        });
      },
    });
  }

  getMotivoLabel(motivo?: string): string {
    const found = this.motivoOptions.find((m) => m.value === motivo);
    return found ? found.label : motivo || '-';
  }

  protected getCondicaoBadgeClass(condicao?: string | null): string {
    switch (condicao) {
      case 'NOVO':
        return 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800';
      case 'SUCATA':
        return 'bg-rose-50 dark:bg-rose-950/60 text-rose-700 dark:text-rose-400 border border-rose-200 dark:border-rose-800';
      case 'RECUPERADO':
        return 'bg-blue-50 dark:bg-blue-950/60 text-blue-700 dark:text-blue-400 border border-blue-200 dark:border-blue-800';
      case 'USADO':
        return 'bg-amber-50 dark:bg-amber-950/60 text-amber-700 dark:text-amber-400 border border-amber-200 dark:border-amber-800';
      default:
        return 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-700';
    }
  }
}
