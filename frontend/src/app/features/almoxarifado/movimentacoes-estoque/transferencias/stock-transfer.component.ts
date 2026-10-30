import { CommonModule, DecimalPipe } from '@angular/common';
import { Component, inject, signal, computed, ViewChild, ElementRef, ChangeDetectionStrategy } from '@angular/core';
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
import { MultiSelectModule } from 'primeng/multiselect';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { StepperModule } from 'primeng/stepper';
import { CheckboxModule } from 'primeng/checkbox';

import { DateMaskDirective } from '../../../../shared/directives/date-mask.directive';
import { SaldoEstoqueDialogComponent } from '../../../../shared/components/saldo-estoque-dialog/saldo-estoque-dialog.component';
import { HelpDialogComponent } from '../../../../shared/components/help-dialog/help-dialog.component';
import { CesButtonComponent, CesStatusComponent } from '@ces/ui';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import {
  EstoqueResponse,
  SaldoResponse,
  MovimentacaoBulkRequest,
  PageResponseSaldoSerieResponse,
  SaldoSerieResponse,
} from '../../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { NavigationHistoryService, bindStepToUrl, createFormDraft } from '../../../../core/navigation';

export interface TransferItem {
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
  selector: 'app-stock-transfer',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    SelectModule,
    InputNumberModule,
    InputTextModule,
    TextareaModule,
    DatePickerModule,
    TableModule,
    ButtonModule,
    TooltipModule,
    DialogModule,
    MultiSelectModule,
    BreadcrumbModule,
    StepperModule,
    CheckboxModule,
    SaldoEstoqueDialogComponent,
    HelpDialogComponent,
    DateMaskDirective,
    CesButtonComponent,
    CesStatusComponent,
    DecimalPipe,
  ],
  template: `
    <app-saldo-estoque-dialog #saldoDialog></app-saldo-estoque-dialog>

    <!-- Diálogo de Gerenciamento de Séries -->
    <p-dialog
      header="Gerenciar Números de Série"
      [(visible)]="serialDialogVisible"
      [modal]="true"
      [style]="{ width: '95vw', maxWidth: '480px' }"
      [draggable]="false"
      [resizable]="false"
      styleClass="custom-dialog"
      appendTo="body"
    >
      @if (selectedItemForSerials()) {
        <div class="flex flex-col gap-4 py-1">
          <div class="bg-primary-50 dark:bg-primary-950/50 p-3 rounded-xl border border-primary-200 dark:border-primary-800 flex items-start gap-3">
            <i class="pi pi-info-circle text-primary-600 dark:text-primary-400 mt-0.5"></i>
            <div class="flex flex-col gap-0.5">
              <span class="text-[10px] font-bold text-primary-700 dark:text-primary-300 uppercase tracking-wider">Controle Serial</span>
              <p class="text-xs text-primary-800 dark:text-primary-200 leading-tight">
                Selecione os <b>{{ maxSerialsToSelect() }}</b> números de série que sairão da origem.
              </p>
            </div>
          </div>

          <div class="flex flex-col gap-1 p-3 bg-slate-50 dark:bg-slate-800/60 rounded-xl border border-slate-100 dark:border-slate-800">
            <span class="text-[9px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-widest">Material Selecionado</span>
            <span class="text-xs font-bold text-slate-700 dark:text-slate-200 font-mono">
              {{ selectedItemForSerials()?.codigo }} - {{ selectedItemForSerials()?.descricao }}
            </span>
          </div>

          <div class="flex flex-col gap-1.5">
            <label class="text-[11px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
              Séries Disponíveis ({{ serialsToEdit.length }}/{{ maxSerialsToSelect() }} selecionadas)
            </label>
            <p-multiSelect
              [(ngModel)]="serialsToEdit"
              [options]="availableSerials()"
              optionLabel="label"
              optionValue="value"
              dataKey="value"
              placeholder="Escolha os números de série..."
              [selectionLimit]="maxSerialsToSelect()"
              display="chip"
              appendTo="body"
              [loading]="loadingSerials()"
              size="small"
              class="w-full !rounded-xl"
              [showClear]="true"
            ></p-multiSelect>
            <span class="text-[10px] text-slate-400">
              Apenas séries ativas no estoque de origem são listadas.
            </span>
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

      <!-- Card do Formulário (Padrão SolicitacaoMaterialForm) -->
      <div class="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 overflow-hidden max-w-4xl w-full mx-auto shadow-sm">
        <!-- Header da Operação -->
        <div class="px-5 md:px-8 py-6 border-b border-slate-100 dark:border-slate-800 flex items-start justify-between gap-4">
          <div>
            <div class="flex items-center gap-2 mb-2">
              <div class="w-8 h-8 rounded-lg bg-primary-50 dark:bg-primary-950/60 border border-primary-200 dark:border-primary-800 flex items-center justify-center text-primary-600 dark:text-primary-400">
                <i class="pi pi-arrow-right-arrow-left text-sm"></i>
              </div>
              <span class="text-[10px] font-bold text-primary-600 dark:text-primary-400 uppercase tracking-widest">
                Operação de Estoque
              </span>
            </div>
            <h1 class="text-lg font-bold text-slate-800 dark:text-slate-100">
              Transferência entre Estoques
            </h1>
            <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
              Transfira saldo de materiais de um estoque físico de origem para um estoque de destino, com conferência de séries e revisão analítica.
            </p>
          </div>
          <p-button
            icon="pi pi-question-circle"
            [text]="true"
            [rounded]="true"
            (onClick)="onHelp()"
            pTooltip="Guia de Transferência"
            styleClass="!text-slate-400 hover:!text-primary-600 !w-8 !h-8 shrink-0"
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
                      [ngClass]="value <= activeStep() ? 'step-circle-active' : 'step-circle-inactive'"
                    >
                      <i class="pi pi-building text-xs"></i>
                    </span>
                    <span
                      class="step-label"
                      [ngClass]="value <= activeStep() ? 'text-primary-600 dark:text-primary-400' : 'text-slate-400'"
                    >
                      1. Origem e Destino
                    </span>
                  </button>
                </ng-template>
              </p-step>

              <p-step [value]="2" class="flex flex-row flex-auto gap-2">
                <ng-template #content let-value="value">
                  <button type="button" class="step-nav-btn" [disabled]="!isHeaderValid()" (click)="goToStep(2)">
                    <span
                      class="step-circle"
                      [ngClass]="value <= activeStep() ? 'step-circle-active' : 'step-circle-inactive'"
                    >
                      <i class="pi pi-box text-xs"></i>
                    </span>
                    <span
                      class="step-label"
                      [ngClass]="value <= activeStep() ? 'text-primary-600 dark:text-primary-400' : 'text-slate-400'"
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
                      [ngClass]="value <= activeStep() ? 'step-circle-active' : 'step-circle-inactive'"
                    >
                      <i class="pi pi-check text-xs"></i>
                    </span>
                    <span
                      class="step-label"
                      [ngClass]="value <= activeStep() ? 'text-primary-600 dark:text-primary-400' : 'text-slate-400'"
                    >
                      3. Revisão & Confirmação
                    </span>
                  </button>
                </ng-template>
              </p-step>
            </p-step-list>

            <p-step-panels>
              <!-- ETAPA 1: ORIGEM, DESTINO E IDENTIFICAÇÃO -->
              <p-step-panel [value]="1">
                <ng-template #content>
                  <div [formGroup]="headerForm" class="flex flex-col gap-6">
                    <div class="border-l-2 border-primary-500 pl-3">
                      <h3 class="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                        1. Identificação dos Estoques
                      </h3>
                      <p class="text-[11px] text-slate-400 mt-0.5">
                        Defina o estoque físico de saída e o de entrada para a transferência.
                      </p>
                    </div>

                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <!-- Origem -->
                      <div class="flex flex-col gap-1.5">
                        <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                          Estoque de Origem *
                        </label>
                        <div class="flex items-center gap-2">
                          <p-select
                            formControlName="idEstoqueOrigem"
                            [options]="estoques()"
                            optionLabel="nomeIdentificador"
                            optionValue="id"
                            placeholder="Selecione a origem..."
                            [filter]="true"
                            [showClear]="true"
                            filterBy="nomeIdentificador"
                            class="custom-select flex-1"
                            size="small"
                            appendTo="body"
                          ></p-select>
                          @if (headerForm.get('idEstoqueOrigem')?.value) {
                            <button
                              type="button"
                              pTooltip="Ver Saldo em Estoque"
                              (click)="abrirSaldo(headerForm.get('idEstoqueOrigem')?.value)"
                              class="w-9 h-9 rounded-xl flex items-center justify-center bg-slate-100 dark:bg-slate-800 text-primary-600 dark:text-primary-400 hover:bg-primary-50 dark:hover:bg-slate-700 transition-colors shrink-0"
                            >
                              <i class="pi pi-box"></i>
                            </button>
                          }
                        </div>
                        @if (fieldError('idEstoqueOrigem')) {
                          <span class="text-[10px] text-rose-500 font-semibold">{{ fieldError('idEstoqueOrigem') }}</span>
                        }
                      </div>

                      <!-- Destino -->
                      <div class="flex flex-col gap-1.5">
                        <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                          Estoque de Destino *
                        </label>
                        <div class="flex items-center gap-2">
                          <p-select
                            formControlName="idEstoqueDestino"
                            [options]="estoquesDestino()"
                            optionLabel="nomeIdentificador"
                            optionValue="id"
                            placeholder="Selecione o destino..."
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
                              class="w-9 h-9 rounded-xl flex items-center justify-center bg-slate-100 dark:bg-slate-800 text-primary-600 dark:text-primary-400 hover:bg-primary-50 dark:hover:bg-slate-700 transition-colors shrink-0"
                            >
                              <i class="pi pi-box"></i>
                            </button>
                          }
                        </div>
                        @if (fieldError('idEstoqueDestino')) {
                          <span class="text-[10px] text-rose-500 font-semibold">{{ fieldError('idEstoqueDestino') }}</span>
                        }
                      </div>

                      <!-- Data da Transferência -->
                      <div class="flex flex-col gap-1.5">
                        <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                          Data da Transferência *
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

                      <!-- Opção de Transferência Total -->
                      <div class="flex flex-col justify-center gap-1.5">
                        <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                          Atalho Operacional
                        </label>
                        <div class="flex items-center gap-2.5 p-2.5 bg-slate-50 dark:bg-slate-800/60 rounded-xl border border-slate-200/80 dark:border-slate-800">
                          <p-checkbox
                            [(ngModel)]="totalTransferFlag"
                            [ngModelOptions]="{ standalone: true }"
                            [binary]="true"
                            [disabled]="!selectedOrigemId()"
                            inputId="totalTransferCheck"
                            size="small"
                          ></p-checkbox>
                          <label for="totalTransferCheck" class="text-xs font-semibold text-slate-700 dark:text-slate-300 cursor-pointer">
                            Transferência Total do Estoque
                          </label>
                          <i
                            class="pi pi-info-circle text-slate-400 text-xs ml-auto"
                            pTooltip="Importa todos os materiais com saldo positivo da origem para esta transferência automaticamente."
                          ></i>
                        </div>
                      </div>

                      <!-- Observações -->
                      <div class="flex flex-col gap-1.5 md:col-span-2">
                        <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                          Observações / Justificativa
                        </label>
                        <textarea
                          pInputTextarea
                          formControlName="observacoes"
                          rows="2"
                          class="w-full text-xs p-3 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 text-slate-800 dark:text-slate-100"
                          placeholder="Informe detalhes, motivo da transferência ou referências operacionais..."
                        ></textarea>
                      </div>
                    </div>

                    <div class="flex justify-end gap-3 pt-4 border-t border-slate-100 dark:border-slate-800">
                      <ces-button
                        label="Avançar para Materiais"
                        icon="pi pi-arrow-right"
                        [disabled]="!isHeaderValid() || loading()"
                        [loading]="loading()"
                        (pressed)="saveHeader()"
                      ></ces-button>
                    </div>
                  </div>
                </ng-template>
              </p-step-panel>

              <!-- ETAPA 2: MATERIAIS E SÉRIES -->
              <p-step-panel [value]="2">
                <ng-template #content>
                  <div class="flex flex-col gap-6">
                    <div class="border-l-2 border-primary-500 pl-3">
                      <h3 class="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                        2. Composição dos Materiais
                      </h3>
                      <p class="text-[11px] text-slate-400 mt-0.5">
                        Adicione os materiais e informe os números de série requeridos.
                      </p>
                    </div>

                    <!-- Card de Inserção de Item -->
                    <div [formGroup]="itemForm" class="p-4 bg-slate-50/70 dark:bg-slate-800/40 rounded-2xl border border-slate-200/80 dark:border-slate-800 flex flex-col gap-4">
                      <div class="grid grid-cols-1 md:grid-cols-12 gap-3 items-end">
                        <div class="flex flex-col gap-1.5 md:col-span-7">
                          <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                            Material Disponível na Origem *
                          </label>
                          <p-select
                            formControlName="idMaterial"
                            [options]="saldosDisponiveis()"
                            optionLabel="label"
                            optionValue="id"
                            placeholder="Selecione um material..."
                            [filter]="true"
                            [showClear]="true"
                            filterBy="material.descricao,material.codigo,condicao"
                            class="custom-select w-full"
                            size="small"
                            appendTo="body"
                            (onChange)="onMaterialChange($event)"
                          >
                            <ng-template let-item pTemplate="item">
                              <div class="flex flex-col gap-0.5 py-1">
                                <div class="flex items-center gap-2">
                                  <span class="text-xs font-mono font-bold">{{ item.material.codigo }}</span>
                                  <span class="text-[9px] px-1.5 py-0.5 rounded font-bold uppercase tracking-wider"
                                    [ngClass]="getCondicaoBadgeClass(item.condicao)">
                                    {{ item.condicao || 'NOVO' }}
                                  </span>
                                  @if (item.material.detalhaSerie) {
                                    <span class="text-[9px] font-bold text-primary-600 dark:text-primary-400 bg-primary-50 dark:bg-primary-950/60 px-1.5 py-0.5 rounded border border-primary-200 dark:border-primary-800 uppercase">
                                      Serial
                                    </span>
                                  }
                                </div>
                                <span class="text-xs text-slate-700 dark:text-slate-200">{{ item.material.descricao }}</span>
                                <span class="text-[10px] text-slate-400">
                                  Saldo Atual: <b class="text-slate-700 dark:text-slate-300">{{ item.quantidade }} {{ item.material.unidadeMedida }}</b>
                                </span>
                              </div>
                            </ng-template>
                          </p-select>
                        </div>

                        <div class="flex flex-col gap-1.5 md:col-span-3">
                          <label class="text-[10px] font-bold uppercase tracking-wider text-slate-600 dark:text-slate-400">
                            Quantidade ({{ unidadeMedida() }}) *
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
                            [disabled]="itemForm.invalid"
                            (pressed)="addItem()"
                            className="w-full"
                          ></ces-button>
                        </div>
                      </div>

                      @if (selectedMaterial()) {
                        <div class="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
                          <i class="pi pi-info-circle text-xs text-primary-500"></i>
                          <span>
                            Saldo em estoque: <b>{{ maxQuantidade() }} {{ unidadeMedida() }}</b>.
                            @if (selectedMaterial()?.material?.detalhaSerie) {
                              <span class="text-primary-600 dark:text-primary-400 font-semibold ml-1">
                                Exige seleção unitária de séries após adicionar.
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
                          Itens da Transferência
                        </span>
                        <ces-status tone="brand">
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
                                      class="w-7 h-7 rounded-lg flex items-center justify-center text-indigo-600 dark:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-950/40"
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
                                @if (item.numSeries && item.numSeries.length === Math.floor(item.quantidade)) {
                                  <span class="inline-flex items-center gap-1 text-[9px] font-bold text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-200 dark:border-emerald-800">
                                    <i class="pi pi-check text-[9px]"></i>
                                    {{ item.numSeries.length }} séries vinculadas
                                  </span>
                                } @else {
                                  <button
                                    type="button"
                                    (click)="openSerialManager(rowIndex)"
                                    class="inline-flex items-center gap-1 text-[9px] font-bold text-rose-700 dark:text-rose-400 bg-rose-50 dark:bg-rose-950/60 px-2 py-0.5 rounded border border-rose-200 dark:border-rose-800 hover:scale-105 transition-transform"
                                  >
                                    <i class="pi pi-exclamation-triangle text-[9px]"></i>
                                    {{ item.numSeries?.length || 0 }}/{{ Math.floor(item.quantidade) }} pendente
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
                          <span class="text-slate-500 dark:text-slate-400 font-medium">Volume Total:</span>
                          <span class="font-bold text-slate-800 dark:text-slate-100 font-mono">
                            {{ totalQuantidade() | number: '1.3-3' }} unidades
                          </span>
                        </div>
                      }
                    </div>

                    <div class="flex justify-between items-center pt-4 border-t border-slate-100 dark:border-slate-800">
                      <ces-button
                        label="Voltar para Origem/Destino"
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

              <!-- ETAPA 3: REVISÃO E CONFIRMAÇÃO -->
              <p-step-panel [value]="3">
                <ng-template #content>
                  <div class="flex flex-col gap-6">
                    <div class="border-l-2 border-primary-500 pl-3">
                      <h3 class="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                        3. Revisão da Transferência
                      </h3>
                      <p class="text-[11px] text-slate-400 mt-0.5">
                        Confira todas as informações antes de oficializar a transferência.
                      </p>
                    </div>

                    <!-- Cards de Resumo da Operação -->
                    <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                      <div class="bg-slate-50 dark:bg-slate-800/60 p-4 rounded-xl border border-slate-100 dark:border-slate-800 flex flex-col gap-1">
                        <span class="text-[9px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">Origem</span>
                        <span class="text-xs font-bold text-slate-800 dark:text-slate-100 mt-0.5">
                          {{ nomeEstoqueOrigem() }}
                        </span>
                      </div>

                      <div class="bg-slate-50 dark:bg-slate-800/60 p-4 rounded-xl border border-slate-100 dark:border-slate-800 flex flex-col gap-1">
                        <span class="text-[9px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">Destino</span>
                        <span class="text-xs font-bold text-slate-800 dark:text-slate-100 mt-0.5">
                          {{ nomeEstoqueDestino() }}
                        </span>
                      </div>

                      <div class="bg-slate-50 dark:bg-slate-800/60 p-4 rounded-xl border border-slate-100 dark:border-slate-800 flex flex-col gap-1">
                        <span class="text-[9px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">Data</span>
                        <span class="text-xs font-bold text-slate-800 dark:text-slate-100 mt-0.5">
                          {{ headerForm.get('data')?.value | date: 'dd/MM/yyyy' }}
                        </span>
                      </div>
                    </div>

                    @if (headerForm.get('observacoes')?.value) {
                      <div class="p-3 bg-slate-50 dark:bg-slate-800/40 rounded-xl border border-slate-100 dark:border-slate-800 text-xs">
                        <span class="font-bold text-slate-500 uppercase text-[9px] block mb-1">Observações:</span>
                        <span class="text-slate-700 dark:text-slate-300">{{ headerForm.get('observacoes')?.value }}</span>
                      </div>
                    }

                    <!-- Alertas de Validação -->
                    @if (hasMissingSerials()) {
                      <div class="p-3.5 bg-rose-50 dark:bg-rose-950/60 rounded-xl border border-rose-200 dark:border-rose-800 flex items-start gap-3">
                        <i class="pi pi-times-circle text-rose-600 dark:text-rose-400 text-base mt-0.5"></i>
                        <div class="flex flex-col gap-0.5">
                          <span class="text-xs font-bold text-rose-800 dark:text-rose-300">Números de Série Pendentes</span>
                          <p class="text-xs text-rose-700 dark:text-rose-300/90 leading-tight">
                            Existem materiais com controle de série que ainda não possuem todas as séries informadas. Volte para a etapa de Materiais e selecione os seriais necessários.
                          </p>
                        </div>
                      </div>
                    }

                    <!-- Tabela de Itens Revisão -->
                    <div class="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200/80 dark:border-slate-800 overflow-hidden shadow-sm">
                      <div class="p-3.5 bg-slate-50 dark:bg-slate-800/80 border-b border-slate-200 dark:border-slate-800 flex justify-between items-center">
                        <span class="text-[10px] font-bold uppercase tracking-wider text-slate-700 dark:text-slate-200">
                          Resumo dos Materiais a Transferir
                        </span>
                        <span class="text-xs font-bold text-slate-600 dark:text-slate-300 font-mono">
                          {{ items().length }} {{ items().length === 1 ? 'item' : 'itens' }} ({{ totalQuantidade() | number: '1.3-3' }} un.)
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
                            <td class="!py-2.5 text-right font-mono font-bold text-xs text-primary-600 dark:text-primary-400">
                              {{ item.quantidade | number: '1.3-3' }} {{ item.unidadeMedida }}
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
                        label="Confirmar Transferência"
                        icon="pi pi-check-circle"
                        variant="success"
                        [disabled]="items().length === 0 || hasMissingSerials() || loading()"
                        [loading]="loading()"
                        (pressed)="confirmTransfer()"
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

    <!-- Modal de Seleção de Tipo de Assinatura -->
    <p-dialog
      [visible]="displaySignatureTypeModal()"
      (visibleChange)="displaySignatureTypeModal.set($event)"
      [modal]="true"
      header="Tipo de Assinatura"
      [style]="{ width: '95vw', maxWidth: '520px' }"
      [draggable]="false"
      [resizable]="false"
      styleClass="custom-dialog"
      appendTo="body"
    >
      <div class="flex flex-col gap-6 py-2">
        <div class="text-center">
          <p class="text-xs text-slate-600 dark:text-slate-400">
            Como deseja registrar as assinaturas do estoque entregador e do estoque recebedor?
          </p>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <!-- Opção Física -->
          <div
            class="border-2 border-slate-200 dark:border-slate-700 rounded-2xl p-6 flex flex-col items-center justify-center gap-3 cursor-pointer hover:border-primary-500 hover:bg-primary-50/50 dark:hover:bg-primary-950/30 transition-all group"
            (click)="selectSignatureType('FISICA')"
          >
            <div
              class="w-14 h-14 rounded-full bg-slate-100 dark:bg-slate-800 group-hover:bg-primary-100 dark:group-hover:bg-primary-900/60 flex items-center justify-center transition-colors"
            >
              <i
                class="pi pi-print text-xl text-slate-500 dark:text-slate-400 group-hover:text-primary-600 dark:group-hover:text-primary-300 transition-colors"
              ></i>
            </div>
            <div class="text-center">
              <span class="block text-xs font-bold text-slate-800 dark:text-slate-200">Assinatura Física</span>
              <span class="block text-[11px] text-slate-500 dark:text-slate-400 mt-1 leading-tight"
                >Será impresso e assinado<br />manualmente em papel</span
              >
            </div>
          </div>

          <!-- Opção Digital -->
          <div
            class="border-2 border-slate-200 dark:border-slate-700 rounded-2xl p-6 flex flex-col items-center justify-center gap-3 cursor-pointer hover:border-primary-500 hover:bg-primary-50/50 dark:hover:bg-primary-950/30 transition-all group"
            (click)="selectSignatureType('DIGITAL')"
          >
            <div
              class="w-14 h-14 rounded-full bg-slate-100 dark:bg-slate-800 group-hover:bg-primary-100 dark:group-hover:bg-primary-900/60 flex items-center justify-center transition-colors"
            >
              <i
                class="pi pi-tablet text-xl text-slate-500 dark:text-slate-400 group-hover:text-primary-600 dark:group-hover:text-primary-300 transition-colors"
              ></i>
            </div>
            <div class="text-center">
              <span class="block text-xs font-bold text-slate-800 dark:text-slate-200">Assinatura Digital</span>
              <span class="block text-[11px] text-slate-500 dark:text-slate-400 mt-1 leading-tight"
                >Assinatura eletrônica<br />direto no dispositivo</span
              >
            </div>
          </div>
        </div>
      </div>
    </p-dialog>

    <!-- Modal de Dashboard de Assinatura Digital -->
    <p-dialog
      [visible]="displayDigitalSignatureDashboard()"
      (visibleChange)="displayDigitalSignatureDashboard.set($event)"
      [modal]="true"
      header="Assinaturas da Transferência"
      [style]="{ width: '95vw', maxWidth: '640px' }"
      [draggable]="false"
      [resizable]="false"
      styleClass="custom-dialog"
      appendTo="body"
    >
      <div class="flex flex-col gap-6 p-2">
        <div class="text-center">
          <h3 class="text-base font-bold text-slate-900 dark:text-slate-100 uppercase tracking-tight">Painel de Assinaturas</h3>
          <p class="text-xs text-slate-600 dark:text-slate-400 mt-1">
            Ambas as partes precisam realizar a assinatura eletrônica para oficializar a transferência.
          </p>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <!-- Slot Estoque Entregador (Origem) -->
          <div class="flex flex-col gap-2">
            <label class="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wider text-center">
              Estoque Entregador
            </label>
            <span class="text-[11px] text-slate-500 dark:text-slate-400 text-center truncate">
              {{ nomeEstoqueOrigem() }}
            </span>

            @if (isBase64(transferSignatures.assinaturaEntregador)) {
              <div
                class="border-2 border-emerald-200 dark:border-emerald-800/80 rounded-2xl p-4 bg-emerald-50/40 dark:bg-emerald-950/30 flex flex-col items-center gap-3 relative"
              >
                <img [src]="transferSignatures.assinaturaEntregador" class="h-16 object-contain" alt="Assinatura Entregador" />
                <span
                  class="text-[10px] font-bold text-emerald-700 dark:text-emerald-300 uppercase tracking-wider bg-white dark:bg-slate-900 px-3 py-1 rounded-full shadow-2xs border border-emerald-200 dark:border-emerald-800"
                  >Assinado</span
                >
                <p-button
                  icon="pi pi-refresh"
                  [rounded]="true"
                  [text]="true"
                  severity="secondary"
                  class="absolute top-2 right-2"
                  (onClick)="openSignaturePad('entregador')"
                  pTooltip="Refazer Assinatura"
                ></p-button>
              </div>
            } @else {
              <div
                class="border-2 border-dashed border-slate-300 dark:border-slate-700 rounded-2xl p-6 flex flex-col items-center justify-center gap-2.5 cursor-pointer hover:border-primary-500 hover:bg-primary-50/50 dark:hover:bg-primary-950/30 transition-all group"
                (click)="openSignaturePad('entregador')"
              >
                <div
                  class="w-12 h-12 rounded-full bg-slate-100 dark:bg-slate-800 group-hover:bg-primary-100 dark:group-hover:bg-primary-900/60 flex items-center justify-center transition-colors"
                >
                  <i class="pi pi-user text-lg text-slate-400 group-hover:text-primary-600 dark:group-hover:text-primary-300"></i>
                </div>
                <span
                  class="text-xs font-semibold text-slate-600 dark:text-slate-300 group-hover:text-primary-600 dark:group-hover:text-primary-300"
                  >Assinar agora (Entregador)</span
                >
              </div>
            }
          </div>

          <!-- Slot Estoque Recebedor (Destino) -->
          <div class="flex flex-col gap-2">
            <label class="text-xs font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wider text-center">
              Estoque Recebedor
            </label>
            <span class="text-[11px] text-slate-500 dark:text-slate-400 text-center truncate">
              {{ nomeEstoqueDestino() }}
            </span>

            @if (isBase64(transferSignatures.assinaturaRecebedor)) {
              <div
                class="border-2 border-emerald-200 dark:border-emerald-800/80 rounded-2xl p-4 bg-emerald-50/40 dark:bg-emerald-950/30 flex flex-col items-center gap-3 relative"
              >
                <img [src]="transferSignatures.assinaturaRecebedor" class="h-16 object-contain" alt="Assinatura Recebedor" />
                <span
                  class="text-[10px] font-bold text-emerald-700 dark:text-emerald-300 uppercase tracking-wider bg-white dark:bg-slate-900 px-3 py-1 rounded-full shadow-2xs border border-emerald-200 dark:border-emerald-800"
                  >Assinado</span
                >
                <p-button
                  icon="pi pi-refresh"
                  [rounded]="true"
                  [text]="true"
                  severity="secondary"
                  class="absolute top-2 right-2"
                  (onClick)="openSignaturePad('recebedor')"
                  pTooltip="Refazer Assinatura"
                ></p-button>
              </div>
            } @else {
              <div
                class="border-2 border-dashed border-slate-300 dark:border-slate-700 rounded-2xl p-6 flex flex-col items-center justify-center gap-2.5 cursor-pointer hover:border-primary-500 hover:bg-primary-50/50 dark:hover:bg-primary-950/30 transition-all group"
                (click)="openSignaturePad('recebedor')"
              >
                <div
                  class="w-12 h-12 rounded-full bg-slate-100 dark:bg-slate-800 group-hover:bg-primary-100 dark:group-hover:bg-primary-900/60 flex items-center justify-center transition-colors"
                >
                  <i class="pi pi-users text-lg text-slate-400 group-hover:text-primary-600 dark:group-hover:text-primary-300"></i>
                </div>
                <span
                  class="text-xs font-semibold text-slate-600 dark:text-slate-300 group-hover:text-primary-600 dark:group-hover:text-primary-300"
                  >Assinar agora (Recebedor)</span
                >
              </div>
            }
          </div>
        </div>

        <div class="mt-4 border-t border-slate-200 dark:border-slate-700 pt-4 flex items-center justify-end gap-2.5">
          <ces-button
            label="Voltar"
            variant="ghost"
            size="sm"
            [disabled]="loading()"
            (pressed)="displayDigitalSignatureDashboard.set(false); displaySignatureTypeModal.set(true)"
          />
          <ces-button
            label="Finalizar Transferência Digital"
            icon="pi pi-check-circle"
            variant="primary"
            size="sm"
            [loading]="loading()"
            [disabled]="loading() || !isBase64(transferSignatures.assinaturaEntregador) || !isBase64(transferSignatures.assinaturaRecebedor)"
            (pressed)="executeTransfer()"
          />
        </div>
      </div>
    </p-dialog>

    <!-- Modal de Assinatura Digital (Canvas) -->
    <p-dialog
      [visible]="displaySignatureModal()"
      (visibleChange)="displaySignatureModal.set($event)"
      [modal]="true"
      [header]="currentSignatureField() === 'entregador' ? 'Assinatura do Estoque Entregador' : 'Assinatura do Estoque Recebedor'"
      [style]="{ width: '95vw', maxWidth: '600px' }"
      [draggable]="false"
      [resizable]="false"
      styleClass="custom-dialog"
      appendTo="body"
      (onHide)="clearSignaturePad()"
    >
      <div class="flex flex-col gap-4">
        <div
          class="bg-primary-50/60 dark:bg-primary-950/40 p-3.5 rounded-xl border border-primary-200 dark:border-primary-800 flex items-start gap-3"
        >
          <i class="pi pi-pencil text-primary-600 dark:text-primary-400 mt-0.5 text-base"></i>
          <div class="flex flex-col gap-0.5">
            <span class="text-xs font-bold text-primary-800 dark:text-primary-200 uppercase tracking-wider">Área de Assinatura</span>
            <p class="text-xs text-primary-700 dark:text-primary-300 leading-relaxed">
              Use o mouse ou o dedo para desenhar a sua assinatura na área abaixo.
            </p>
          </div>
        </div>

        <div
          class="border-2 border-dashed border-slate-300 dark:border-slate-700 rounded-xl bg-white dark:bg-slate-900 flex justify-center items-center relative overflow-hidden"
          style="height: 250px"
        >
          <canvas
            #signatureCanvas
            class="w-full h-full cursor-crosshair touch-none"
            (mousedown)="startDrawing($event)"
            (mousemove)="draw($event)"
            (mouseup)="stopDrawing()"
            (mouseout)="stopDrawing()"
            (touchstart)="startDrawing($event)"
            (touchmove)="draw($event)"
            (touchend)="stopDrawing()"
            (touchcancel)="stopDrawing()"
          ></canvas>
          <div class="absolute bottom-2 right-2 flex gap-2">
            <p-button
              icon="pi pi-trash"
              [rounded]="true"
              [text]="true"
              severity="danger"
              (onClick)="clearSignaturePad()"
              pTooltip="Limpar Assinatura"
            ></p-button>
          </div>
          <div class="absolute inset-0 pointer-events-none flex items-center justify-center opacity-10">
            <span class="text-3xl font-bold uppercase tracking-widest text-slate-400 dark:text-slate-600">Assine Aqui</span>
          </div>
        </div>

        <div class="flex justify-end gap-2.5 mt-4 border-t border-slate-200 dark:border-slate-700 pt-4">
          <ces-button label="Cancelar" variant="ghost" size="sm" (pressed)="displaySignatureModal.set(false)" />
          <ces-button label="Salvar Assinatura" icon="pi pi-check" variant="primary" size="sm" (pressed)="saveSignature()" />
        </div>
      </div>
    </p-dialog>

    <!-- Diálogo de Ajuda -->
    <app-help-dialog #helpDialog title="Guia da Tela: Transferência entre Estoques">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-arrow-right-arrow-left"></i> Como Funciona</h4>
        <div class="help-item">
          <div class="help-icon"><i class="pi pi-info-circle"></i></div>
          <div class="help-text">
            <span class="help-text-title">Passo a Passo</span>
            <span class="help-text-desc">Selecione o estoque de saída e o de entrada, adicione os materiais desejados com suas quantidades e confirme a transferência.</span>
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
    .step-circle-active {
      background: var(--p-primary-600);
      color: white;
      border-color: var(--p-primary-600);
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
export class StockTransferComponent {
  protected readonly Math = Math;
  private fb = inject(FormBuilder);
  private almoxService = inject(AlmoxarifadoService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);

  @ViewChild('saldoDialog') saldoDialog!: SaldoEstoqueDialogComponent;
  @ViewChild('helpDialog') helpDialog!: HelpDialogComponent;

  breadcrumb: MenuItem[] = [
    { label: 'Almoxarifado' },
    { label: 'Movimentações', routerLink: '/almoxarifado/movimentacoes' },
    { label: 'Transferência entre Estoques' },
  ];

  activeStep = signal<1 | 2 | 3>(1);
  loading = signal<boolean>(false);
  estoques = signal<EstoqueResponse[]>([]);
  saldos = signal<SaldoResponse[]>([]);
  items = signal<TransferItem[]>([]);
  totalTransferFlag = signal<boolean>(false);
  editingIndex = signal<number | null>(null);

  headerForm: FormGroup = this.fb.group({
    idEstoqueOrigem: [null, Validators.required],
    idEstoqueDestino: [null, Validators.required],
    data: [new Date(), Validators.required],
    observacoes: [null],
  });

  itemForm: FormGroup = this.fb.group({
    idMaterial: [null, Validators.required],
    quantidade: [null, [Validators.required, Validators.min(0.001)]],
  });

  selectedOrigemId = signal<string | null>(null);
  selectedMaterial = signal<SaldoResponse | null>(null);
  maxQuantidade = computed(() => this.selectedMaterial()?.quantidade || 0);
  unidadeMedida = computed(() => this.selectedMaterial()?.material?.unidadeMedida || '-');

  // Gestão de Séries
  serialDialogVisible = false;
  selectedItemForSerials = signal<TransferItem | null>(null);
  serialsToEdit: string[] = [];
  availableSerials = signal<{ label: string; value: string }[]>([]);
  loadingSerials = signal<boolean>(false);
  editingSerialIndex = signal<number | null>(null);

  maxSerialsToSelect = computed(() => {
    const qty = this.selectedItemForSerials()?.quantidade || 0;
    return Math.floor(qty);
  });

  // Gestão de Assinaturas
  displaySignatureTypeModal = signal(false);
  displayDigitalSignatureDashboard = signal(false);
  displaySignatureModal = signal(false);
  currentSignatureField = signal<'entregador' | 'recebedor'>('entregador');
  transferSignatures = {
    assinaturaEntregador: '',
    assinaturaRecebedor: '',
  };

  @ViewChild('signatureCanvas') signatureCanvas?: ElementRef<HTMLCanvasElement>;
  private signaturePadCtx: CanvasRenderingContext2D | null = null;
  private isDrawing = false;

  estoquesDestino = computed(() => {
    const origemId = this.selectedOrigemId();
    return this.estoques().filter((e) => e.id !== origemId);
  });

  saldosDisponiveis = computed(() => {
    return this.saldos().map((s) => ({
      ...s,
      label: `${s.material.codigo} - ${s.material.descricao} [${s.condicao || 'NOVO'}] (Saldo: ${s.quantidade})`,
    }));
  });

  totalQuantidade = computed(() => {
    return this.items().reduce((acc, item) => acc + (item.quantidade || 0), 0);
  });

  nomeEstoqueOrigem = computed(() => {
    const id = this.headerForm.get('idEstoqueOrigem')?.value;
    return this.estoques().find((e) => e.id === id)?.nomeIdentificador || '-';
  });

  nomeEstoqueDestino = computed(() => {
    const id = this.headerForm.get('idEstoqueDestino')?.value;
    return this.estoques().find((e) => e.id === id)?.nomeIdentificador || '-';
  });

  hasMissingSerials = computed(() => {
    return this.items().some(
      (i) => i.detalhaSerie && (!i.numSeries || i.numSeries.length !== Math.floor(i.quantidade)),
    );
  });

  private readonly passoUrl = bindStepToUrl(this.activeStep);
  /** Rascunho do wizard. Só em memória: os itens podem trazer números de série (dado sensível). */
  private readonly rascunho = createFormDraft<{ cabecalho: Record<string, unknown>; itens: TransferItem[] }>({
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

    this.headerForm.get('idEstoqueOrigem')?.valueChanges.subscribe((val) => {
      this.selectedOrigemId.set(val);
      this.totalTransferFlag.set(false);
      this.items.set([]);
      this.itemForm.reset();
      this.selectedMaterial.set(null);
      this.editingIndex.set(null);
      this.saldos.set([]);
    });
    this.rascunho.restore();
    // O passo 2 depende dos saldos carregados ao avançar: reabre no passo 1, com os dados.
    this.passoUrl.restore(() => false);
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
    const origem = this.headerForm.get('idEstoqueOrigem')?.value;
    const destino = this.headerForm.get('idEstoqueDestino')?.value;
    const data = this.headerForm.get('data')?.value;
    return !!origem && !!destino && origem !== destino && !!data;
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

  saveHeader() {
    if (!this.isHeaderValid()) {
      this.headerForm.markAllAsTouched();
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'Preencha os campos obrigatórios do cabeçalho',
      });
      return;
    }

    const origemId = this.headerForm.get('idEstoqueOrigem')?.value;
    this.loading.set(true);

    this.almoxService.listarSaldos({ idEstoque: origemId, size: 1000 }).subscribe({
      next: (res) => {
        const saldos = res.content;
        this.saldos.set(saldos);
        const hasStock = saldos.some((s) => s.quantidade > 0);

        if (this.totalTransferFlag()) {
          if (!hasStock) {
            this.loading.set(false);
            this.messageService.add({
              severity: 'error',
              summary: 'Estoque Zerado',
              detail: 'O estoque de origem selecionado não possui itens com saldo para transferência total.',
            });
            return;
          }

          const newItems: TransferItem[] = [];
          saldos
            .filter((s) => s.quantidade > 0)
            .forEach((s) => {
              newItems.push({
                idMaterial: s.material.id,
                codigo: s.material.codigo,
                descricao: s.material.descricao,
                quantidade: s.quantidade,
                unidadeMedida: s.material.unidadeMedida,
                numSeries: s.material.detalhaSerie ? [] : undefined,
                detalhaSerie: s.material.detalhaSerie,
                condicao: s.condicao || 'NOVO',
              });
            });
          this.items.set(newItems);

          if (saldos.some((s) => s.material.detalhaSerie && s.quantidade > 0)) {
            this.messageService.add({
              severity: 'info',
              summary: 'Aviso de Séries',
              detail: 'Materiais com número de série foram adicionados. Informe os números clicando no ícone de código de barras.',
            });
          }
        }

        this.loading.set(false);
        this.activeStep.set(2);
      },
      error: () => {
        this.loading.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Falha ao validar estoque de origem',
        });
      },
    });
  }

  onMaterialChange(event: any) {
    const saldo = this.saldos().find((s) => s.id === event.value);
    this.selectedMaterial.set(saldo || null);
    this.itemForm.get('quantidade')?.enable();
  }

  addItem() {
    const saldo = this.selectedMaterial();
    if (!saldo) return;

    const formVal = this.itemForm.getRawValue();
    if (this.itemForm.invalid) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'Preencha a quantidade corretamente',
      });
      return;
    }

    if (this.items().some((i) => i.idMaterial === saldo.material.id && i.condicao === (saldo.condicao || 'NOVO'))) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'Este material com esta condição já foi adicionado',
      });
      return;
    }

    const newItem: TransferItem = {
      idMaterial: saldo.material.id,
      codigo: saldo.material.codigo,
      descricao: saldo.material.descricao,
      quantidade: formVal.quantidade,
      unidadeMedida: saldo.material.unidadeMedida,
      numSeries: saldo.material.detalhaSerie ? [] : undefined,
      detalhaSerie: saldo.material.detalhaSerie,
      condicao: saldo.condicao || 'NOVO',
    };
    this.items.update((prev) => [...prev, newItem]);

    this.itemForm.reset();
    this.selectedMaterial.set(null);
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
      const newQtyLimit = Math.floor(tempQty);
      if (newQtyLimit < newNumSeries.length) {
        newNumSeries = newNumSeries.slice(0, newQtyLimit);
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
    this.serialsToEdit = [...(item.numSeries || [])];
    this.editingSerialIndex.set(index);
    this.serialDialogVisible = true;
    this.fetchAvailableSerials(item.idMaterial);
  }

  fetchAvailableSerials(idMaterial: string) {
    const idEstoque = this.selectedOrigemId();
    if (!idEstoque) return;

    this.loadingSerials.set(true);
    this.almoxService.listarSeriais({ idEstoque, idMaterial, ativo: true, size: 1000 }).subscribe({
      next: (res: PageResponseSaldoSerieResponse) => {
        this.availableSerials.set(
          res.content.map((s: SaldoSerieResponse) => ({ label: s.numSerie, value: s.numSerie })),
        );
        this.loadingSerials.set(false);
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Erro',
          detail: 'Falha ao carregar números de série',
        });
        this.loadingSerials.set(false);
      },
    });
  }

  saveSerials() {
    const index = this.editingSerialIndex();
    if (index !== null) {
      this.items.update((prev) => {
        const next = [...prev];
        next[index] = { ...next[index], numSeries: [...this.serialsToEdit] };
        return next;
      });
    }
    this.serialDialogVisible = false;
    this.messageService.add({
      severity: 'success',
      summary: 'Sucesso',
      detail: 'Números de série atualizados',
    });
  }

  confirmTransfer() {
    if (this.items().length === 0) {
      this.messageService.add({ severity: 'warn', summary: 'Atenção', detail: 'Adicione ao menos um item' });
      return;
    }

    if (this.hasMissingSerials()) {
      this.messageService.add({
        severity: 'error',
        summary: 'Atenção',
        detail: 'Preencha todos os números de série obrigatórios para os itens marcados.',
      });
      return;
    }

    this.displaySignatureTypeModal.set(true);
  }

  selectSignatureType(type: 'FISICA' | 'DIGITAL') {
    if (type === 'FISICA') {
      this.transferSignatures.assinaturaEntregador = 'FISICA';
      this.transferSignatures.assinaturaRecebedor = 'FISICA';
      this.displaySignatureTypeModal.set(false);
      this.executeTransfer();
    } else {
      this.displaySignatureTypeModal.set(false);
      this.displayDigitalSignatureDashboard.set(true);
    }
  }

  openSignaturePad(field: 'entregador' | 'recebedor') {
    this.currentSignatureField.set(field);
    this.displayDigitalSignatureDashboard.set(false);
    this.displaySignatureModal.set(true);
    setTimeout(() => this.initSignaturePad(), 100);
  }

  private initSignaturePad() {
    const canvas = this.signatureCanvas?.nativeElement;
    if (canvas) {
      const rect = canvas.getBoundingClientRect();
      canvas.width = rect.width;
      canvas.height = rect.height;
      this.signaturePadCtx = canvas.getContext('2d');
      if (this.signaturePadCtx) {
        this.signaturePadCtx.lineWidth = 3;
        this.signaturePadCtx.lineCap = 'round';
        this.signaturePadCtx.lineJoin = 'round';
        this.signaturePadCtx.strokeStyle = '#000';
        this.clearSignaturePad();
      }
    }
  }

  startDrawing(event: MouseEvent | TouchEvent) {
    this.isDrawing = true;
    this.draw(event);
  }

  draw(event: MouseEvent | TouchEvent) {
    if (!this.isDrawing || !this.signaturePadCtx || !this.signatureCanvas) return;
    event.preventDefault();

    const canvas = this.signatureCanvas.nativeElement;
    const rect = canvas.getBoundingClientRect();

    let clientX, clientY;
    if (event instanceof MouseEvent) {
      clientX = event.clientX;
      clientY = event.clientY;
    } else if (window.TouchEvent && event instanceof TouchEvent && event.touches.length > 0) {
      clientX = event.touches[0].clientX;
      clientY = event.touches[0].clientY;
    } else {
      return;
    }

    const x = clientX - rect.left;
    const y = clientY - rect.top;

    this.signaturePadCtx.lineTo(x, y);
    this.signaturePadCtx.stroke();
    this.signaturePadCtx.beginPath();
    this.signaturePadCtx.moveTo(x, y);
  }

  stopDrawing() {
    this.isDrawing = false;
    if (this.signaturePadCtx) {
      this.signaturePadCtx.beginPath();
    }
  }

  clearSignaturePad() {
    if (this.signaturePadCtx && this.signatureCanvas) {
      const canvas = this.signatureCanvas.nativeElement;
      this.signaturePadCtx.clearRect(0, 0, canvas.width, canvas.height);
      this.signaturePadCtx.beginPath();
    }
  }

  saveSignature() {
    const canvas = this.signatureCanvas?.nativeElement;
    if (canvas) {
      const base64 = canvas.toDataURL('image/png');
      const field = this.currentSignatureField();
      if (field === 'entregador') {
        this.transferSignatures.assinaturaEntregador = base64;
      } else {
        this.transferSignatures.assinaturaRecebedor = base64;
      }
      this.displaySignatureModal.set(false);
      this.displayDigitalSignatureDashboard.set(true);
    }
  }

  isBase64(str: string | undefined | null): boolean {
    if (!str) return false;
    return str.startsWith('data:image/');
  }

  executeTransfer() {
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
      idEstoqueOrigem: header.idEstoqueOrigem,
      idEstoqueDestino: header.idEstoqueDestino,
      itens: flatItens,
      assinaturaEntregador: this.transferSignatures.assinaturaEntregador || undefined,
      assinaturaRecebedor: this.transferSignatures.assinaturaRecebedor || undefined,
    };

    this.almoxService.registrarTransferenciaLote(request).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.displayDigitalSignatureDashboard.set(false);
        this.messageService.add({
          severity: 'success',
          summary: 'Sucesso',
          detail: 'Transferência entre estoques realizada com sucesso!',
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
          summary: 'Erro na Transferência',
          detail: getErrorMessage(err),
        });
      },
    });
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
