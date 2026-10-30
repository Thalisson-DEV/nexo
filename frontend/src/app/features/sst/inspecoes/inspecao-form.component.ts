import { DateMaskDirective } from '../../../shared/directives/date-mask.directive';
import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, ViewChild, signal, computed, inject, ChangeDetectorRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MenuItem, MessageService } from 'primeng/api';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { DatePickerModule } from 'primeng/datepicker';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { SkeletonModule } from 'primeng/skeleton';
import { DialogModule } from 'primeng/dialog';

import { MapDialogComponent } from '../../../shared/components/map-dialog/map-dialog.component';
import {
  LocationPickerDialogComponent,
  LocalSelecionado
} from '../../../shared/components/location-picker-dialog/location-picker-dialog.component';
import { HelpDialogComponent } from '../../../shared/components/help-dialog/help-dialog.component';
import { EvidenciasInspecaoUploadComponent } from './evidencias-inspecao-upload.component';
import { SstService } from '../../../core/services/sst.service';
import { RhService } from '../../../core/services/rh.service';
import { AuthService } from '../../../core/services/auth.service';
import { ThemeService } from '../../../core/services/theme.service';
import {
  AtualizarInspecaoRequest,
  DesvioInspecaoRequest,
  FormularioInspecaoResponse,
  FormularioInspecaoResumoResponse,
  GravidadeDesvio,
  GRAVIDADE_BADGE,
  GRAVIDADE_LABELS,
  PerguntaResponse,
  RegistrarInspecaoRequest,
  RespostaInspecaoRequest,
  TipoInspecaoResponse,
  isTipoObjetivo,
  ANEXO_ACCEPT,
  validarArquivoAnexo
} from '../../../core/models/sst.models';
import {
  BaseOperacionalResponse,
  EquipeResponse,
  ProcessoNegocioResponse,
  VeiculoResponse
} from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup as LookupRemoteLookup } from '../../../core/lookup/store/remote-lookup';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { CesAutocompleteComponent, CesBooleanControlComponent } from '@ces/ui/forms';

interface RespostaState {
  opcoesSelecionadas: string[];
  valorTexto: string | null;
  valorNumero: number | null;
  valorData: Date | null;
}
interface AnexoPreviewLocal {
  file: File;
  previewUrl?: string;
  isImage: boolean;
  nome: string;
  tamanho: string;
}
interface DesvioState {
  idPergunta: string;
  idOpcaoResposta: string;
  descricao: string;
  acaoCorretiva: string;
  prazo: Date | null;
  colaboradores: string[];
  tratadoInLoco?: boolean;
  arquivos?: File[];
  fotosPreview?: string[];
  anexosPreview?: AnexoPreviewLocal[];
}
interface Inspecionado {
  id: string;
  nome: string;
}

@Component({
  selector: 'app-sst-inspecao-form',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    BreadcrumbModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    TextareaModule,
    SelectModule,
    MultiSelectModule,
    DatePickerModule,
    ToastModule,
    TooltipModule,
    SkeletonModule,
    DialogModule,
    MapDialogComponent,
    LocationPickerDialogComponent,
    HelpDialogComponent,
    EvidenciasInspecaoUploadComponent,
    DateMaskDirective,
    CesAutocompleteComponent,
    CesBooleanControlComponent
  ],
  providers: [MessageService],
  template: `
    <p-toast></p-toast>
    <app-map-dialog #mapDialog></app-map-dialog>
    <app-location-picker-dialog #localPicker (localConfirmado)="onLocalConfirmado($event)"></app-location-picker-dialog>

    <div class="flex flex-col gap-3 md:gap-4 pb-28 max-w-5xl mx-auto w-full rise">
      <!-- Barra superior -->
      <div class="flex flex-wrap items-center justify-between gap-x-2 gap-y-1">
        <p-breadcrumb [model]="breadcrumbItems" styleClass="!bg-transparent !border-0 !p-0"></p-breadcrumb>
        <div class="flex items-center gap-1 shrink-0 ml-auto">
          <p-button icon="pi pi-question-circle" label="Guia" [text]="true" severity="secondary" (onClick)="helpDialog.show()" styleClass="!text-primary-700 dark:!text-primary-400 !text-[10px] !font-medium !px-2"></p-button>
          <p-button icon="pi pi-arrow-left" label="Voltar" [text]="true" severity="secondary" (onClick)="voltar()" styleClass="!text-slate-400 dark:!text-slate-400 hover:dark:!text-slate-200 !text-[10px] !font-medium !px-2"></p-button>
        </div>
      </div>

      <!-- ========================= CABEÇALHO ========================= -->
      <div class="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-sm">
        <div class="border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50 px-4 md:px-8 py-3.5 md:py-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div class="flex items-center gap-3 min-w-0">
            <div class="w-9 h-9 rounded-lg bg-primary-100 dark:bg-primary-950/60 text-primary-700 dark:text-primary-400 border border-transparent dark:border-primary-800/40 flex items-center justify-center shrink-0">
              <i class="pi pi-shield text-lg"></i>
            </div>
            <div class="flex flex-col min-w-0">
              <span class="text-[13px] md:text-sm font-medium text-slate-800 dark:text-slate-100 uppercase tracking-widest leading-tight">{{ isModoEdicao() ? 'Editar Inspeção de Segurança' : 'Nova Inspeção de Segurança' }}</span>
              <span class="text-[10px] text-slate-500 dark:text-slate-400 font-medium">{{ isModoEdicao() ? 'Altere os dados, checklist, desvios e evidências da inspeção' : 'Preencha os dados iniciais do formulário' }}</span>
            </div>
          </div>
          <div class="flex items-stretch gap-2 shrink-0">
            @if (temLocalizacao()) {
              <button type="button" (click)="abrirMapaGeo()" class="flex flex-1 md:flex-none items-center justify-center gap-1.5 text-[10px] font-medium text-primary-700 dark:text-primary-300 uppercase tracking-widest hover:underline bg-primary-50 dark:bg-primary-950/40 px-3 py-2 md:py-1.5 rounded-md border border-primary-100 dark:border-primary-800/50">
                <i class="pi pi-map-marker text-[11px]"></i> Localização Definida
              </button>
            }
            <button type="button" (click)="abrirSeletorLocal()" class="flex flex-1 md:flex-none items-center justify-center gap-1.5 text-[10px] font-medium text-slate-600 dark:text-slate-300 uppercase tracking-widest hover:underline bg-slate-50 dark:bg-slate-800 px-3 py-2 md:py-1.5 rounded-md border border-slate-200 dark:border-slate-700">
              <i class="pi pi-map text-[11px]"></i> {{ temLocalizacao() ? 'Editar' : 'Selecionar' }} Localização
            </button>
          </div>
        </div>

        <div class="px-4 md:px-8 py-5 md:py-6 flex flex-col gap-5 md:gap-6">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 md:gap-5">
            <div class="flex flex-col gap-2">
              <label class="dl flex items-center gap-2"><i class="pi pi-file-edit text-slate-400 dark:text-slate-500"></i> Modelo do Formulário <span class="text-red-500">*</span></label>
              <p-select [options]="formularios()" [(ngModel)]="idFormulario" [disabled]="isModoEdicao()" optionLabel="titulo" optionValue="id" placeholder="Selecione o molde da inspeção" class="doc-select w-full" size="small" [filter]="true" appendTo="body" (onChange)="onSelecionarFormulario()"></p-select>
            </div>

            <div class="flex flex-col gap-2">
              <label class="dl flex items-center gap-2"><i class="pi pi-tags text-slate-400 dark:text-slate-500"></i> Tipo de Inspeção <span class="text-red-500">*</span></label>
              <p-select [options]="tiposInspecao()" [(ngModel)]="idTipoInspecao" optionLabel="nome" optionValue="id" placeholder="Selecione o tipo de inspeção" class="doc-select w-full" size="small" [filter]="true" appendTo="body"></p-select>
            </div>
          </div>

          @if (carregandoForm()) {
            <p-skeleton height="3rem" styleClass="!rounded-xl"></p-skeleton>
          }

          @if (formulario()) {
            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 md:gap-5 p-4 md:p-5 bg-slate-50/50 dark:bg-slate-800/40 rounded-xl border border-slate-100 dark:border-slate-800">
              <div class="flex flex-col gap-2">
                <label class="dl flex items-center gap-2"><i class="pi pi-sitemap text-slate-400 dark:text-slate-500"></i> Processo <span class="text-red-500">*</span></label>
                <p-select [options]="processos()" [(ngModel)]="idProcesso" optionLabel="nome" optionValue="id" placeholder="Selecione" class="doc-select w-full" size="small" [filter]="true" appendTo="body"></p-select>
              </div>
              <div class="flex flex-col gap-2">
                <label class="dl flex items-center gap-2"><i class="pi pi-calendar-clock text-slate-400 dark:text-slate-500"></i> Data / hora <span class="text-red-500">*</span></label>
                <p-datepicker [(ngModel)]="dataRealizacao" [showTime]="true" hourFormat="24" dateFormat="dd/mm/yy" [showIcon]="true" iconDisplay="input" placeholder="dd/mm/aaaa hh:mm" appendTo="body" size="small" styleClass="doc-date w-full"></p-datepicker>
              </div>
              <div class="flex flex-col gap-2">
                <label class="dl flex items-center gap-2"><i class="pi pi-building text-slate-400 dark:text-slate-500"></i> Base operacional <span class="text-red-500">*</span></label>
                @if (podeLerBases()) {
                  <p-select [options]="basesOperacionais()" [(ngModel)]="idBaseOperacional" optionLabel="nome" optionValue="id" placeholder="Selecione a base" class="doc-select w-full" size="small" [filter]="true" appendTo="body" [loading]="carregandoBases()"></p-select>
                } @else {
                  <div class="rounded-lg border border-amber-200 dark:border-amber-900/50 bg-amber-50 dark:bg-amber-950/30 px-3 py-2 text-[10px] leading-relaxed text-amber-800 dark:text-amber-300">
                    <i class="pi pi-lock mr-1"></i>
                    Seu perfil pode lançar inspeções, mas não pode consultar bases. Solicite a permissão <strong>rh:base-operacional:read</strong>.
                  </div>
                }
              </div>
              <div class="flex flex-col gap-2">
                <label class="dl flex items-center gap-2"><i class="pi pi-car text-slate-400 dark:text-slate-500"></i> Veículo</label>
                <p-select [options]="veiculos()" [(ngModel)]="idVeiculo" optionLabel="placa" optionValue="id" placeholder="Opcional" class="doc-select w-full" size="small" [filter]="true" filterBy="placa,modelo" [showClear]="true" [loading]="veiculosLoading()" [virtualScroll]="true" [virtualScrollItemSize]="38" [lazy]="true" appendTo="body" (onShow)="openVeiculos()" (onFilter)="searchVeiculos($event)" (onLazyLoad)="loadMoreVeiculos()"></p-select>
              </div>
            </div>

            <div class="flex flex-col gap-2">
              <label class="dl flex items-center gap-2"><i class="pi pi-comment text-slate-400 dark:text-slate-500"></i> Observações</label>
              <textarea pTextarea [(ngModel)]="observacoes" rows="2" placeholder="Observações gerais da inspeção..." class="doc-textarea w-full"></textarea>
            </div>

            @if (isModoEdicao()) {
              <div class="flex flex-col gap-2">
                <label class="dl flex items-center gap-2"><i class="pi pi-history text-slate-400 dark:text-slate-500"></i> Justificativa da Edição</label>
                <textarea pTextarea [(ngModel)]="justificativaEdicao" rows="2" maxlength="500" placeholder="Motivo da alteração da inspeção (registrado no histórico de auditoria)..." class="doc-textarea w-full"></textarea>
              </div>
            }

            <!-- Colaboradores: adicionar (esq.) | inspecionados (dir.) -->
            <div class="grid grid-cols-1 md:grid-cols-2 rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden">
              <div class="p-4 md:p-5 flex flex-col gap-4 bg-slate-50/80 dark:bg-slate-900/60 border-b md:border-b-0 md:border-r border-slate-200 dark:border-slate-800">
                <div class="flex items-center gap-2 border-b border-slate-200 dark:border-slate-800 pb-2">
                  <i class="pi pi-user-plus text-primary-700 dark:text-primary-400"></i>
                  <span class="text-[11px] font-medium text-slate-700 dark:text-slate-200 uppercase tracking-widest">Adicionar Colaboradores</span>
                </div>
                <div class="flex flex-col gap-2">
                  <label class="dl">Por Equipe</label>
                  <p-select [options]="equipes()" [(ngModel)]="idEquipe" optionLabel="codigo" optionValue="id" placeholder="Individual (sem equipe)" class="doc-select w-full" size="small" [filter]="true" filterBy="codigo,nome" [showClear]="true" [loading]="equipesLoading()" [virtualScroll]="true" [virtualScrollItemSize]="38" [lazy]="true" appendTo="body" (onShow)="openEquipes()" (onFilter)="searchEquipes($event)" (onLazyLoad)="loadMoreEquipes()" (onChange)="onSelecionarEquipe()"></p-select>
                  <span class="text-[10px] text-slate-400 dark:text-slate-500 flex items-center gap-1"><i class="pi pi-info-circle"></i> Integrantes entram automaticamente.</span>
                </div>
                <div class="flex flex-col gap-2 mt-2">
                  <label class="dl">Manualmente</label>
                  <ces-autocomplete
                    label="Adicionar colaborador manualmente"
                    [labelVisible]="false"
                    inputId="inspecao-colaborador-manual"
                    [options]="colaboradoresDisponiveis()"
                    [ngModel]="colaboradorParaAdicionarId"
                    (ngModelChange)="onAdicionarColaborador($event)"
                    placeholder="Digite o nome do colaborador"
                    [loading]="colaboradoresLoading()"
                    [status]="colaboradoresStatus()"
                    [error]="colaboradoresError()"
                    [minLength]="2"
                    [forceSelection]="true"
                    [virtualScroll]="true"
                    [virtualScrollItemSize]="38"
                    [clearable]="true"
                    appendTo="body"
                    class="w-full"
                    (search)="searchColaboradores($event)"
                    (loadMore)="loadMoreColaboradores()"
                    (retry)="retryColaboradores()"
                  ></ces-autocomplete>
                </div>
              </div>
              <div class="p-4 md:p-5 flex flex-col gap-4 bg-white dark:bg-slate-900">
                <div class="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-2">
                  <div class="flex items-center gap-2">
                    <i class="pi pi-users text-primary-700 dark:text-primary-400"></i>
                    <span class="text-[11px] font-medium text-slate-700 dark:text-slate-200 uppercase tracking-widest">Lista de Inspecionados <span class="text-red-500">*</span></span>
                  </div>
                  <span class="inline-flex items-center justify-center min-w-6 h-6 px-2 rounded-full bg-primary-100 dark:bg-primary-950/60 text-primary-700 dark:text-primary-400 border border-transparent dark:border-primary-800/40 text-[11px] font-medium">{{ inspecionados().length }}</span>
                </div>
                @if (inspecionados().length) {
                  <div class="flex flex-col divide-y divide-slate-100 dark:divide-slate-800 max-h-[220px] overflow-y-auto pr-1">
                    @for (c of inspecionados(); track c.id) {
                      <div class="flex items-center justify-between gap-2 py-2.5 group">
                        <span class="flex items-center gap-2 text-[12px] font-medium text-slate-700 dark:text-slate-200 truncate"><i class="pi pi-user text-[11px] text-slate-400 dark:text-slate-500 group-hover:text-primary-700 dark:group-hover:text-primary-400 transition-colors"></i>{{ c.nome }}</span>
                        <button type="button" (click)="removerInspecionado(c.id)" class="w-6 h-6 rounded flex items-center justify-center text-slate-400 dark:text-slate-500 hover:text-red-500 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/40 transition-colors shrink-0" pTooltip="Remover"><i class="pi pi-times text-[10px]"></i></button>
                      </div>
                    }
                  </div>
                } @else {
                  <div class="flex flex-col items-center justify-center gap-2 py-10 text-center bg-slate-50/50 dark:bg-slate-800/30 rounded-lg border border-dashed border-slate-200 dark:border-slate-700">
                    <i class="pi pi-users text-2xl text-slate-300 dark:text-slate-600"></i>
                    <span class="text-[11px] font-medium text-slate-500 dark:text-slate-400">Nenhum colaborador na lista.</span>
                    <span class="text-[10px] text-slate-400 dark:text-slate-500">Utilize o painel ao lado para adicionar.</span>
                  </div>
                }
              </div>
            </div>

            <!-- Evidência fotográfica: obrigatória, sobe antes do lançamento -->
            <app-evidencias-inspecao-upload #evidencias></app-evidencias-inspecao-upload>
          }
        </div>
      </div>

      <!-- ========================= CORPO (CHECKLIST) ========================= -->
      @if (formulario()) {
        @if (!podePreencherChecklist()) {
          <div class="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-800 p-5 md:p-6 flex flex-col items-center justify-center text-center gap-3.5 shadow-sm">
            <div class="w-11 h-11 rounded-full bg-blue-50 dark:bg-blue-950/50 text-blue-500 dark:text-blue-400 flex items-center justify-center border border-blue-100 dark:border-blue-900/50">
              <i class="pi pi-lock text-lg"></i>
            </div>
            <div class="flex flex-col gap-0.5">
              <span class="text-[12px] font-medium text-slate-700 dark:text-slate-200">Complete os dados para liberar o checklist</span>
              <span class="text-[10px] text-slate-500 dark:text-slate-400">Faltam apenas estes passos:</span>
            </div>
            <div class="flex flex-col gap-1.5 w-full max-w-xs mt-0.5">
              @for (r of requisitos(); track r.label) {
                <div class="flex items-center gap-2 text-left rounded-lg border px-3 py-2 transition-colors"
                     [ngClass]="r.ok ? 'border-emerald-200 dark:border-emerald-900/60 bg-emerald-50/50 dark:bg-emerald-950/30' : 'border-slate-200 dark:border-slate-700/60 bg-slate-50 dark:bg-slate-800/40'">
                  <i class="pi text-[12px] shrink-0" [ngClass]="r.ok ? 'pi-check-circle text-emerald-500 dark:text-emerald-400' : 'pi-circle text-slate-300 dark:text-slate-600'"></i>
                  <span class="text-[11px] font-medium" [ngClass]="r.ok ? 'text-emerald-700 dark:text-emerald-400' : 'text-slate-600 dark:text-slate-400'">{{ r.label }}</span>
                </div>
              }
            </div>
          </div>
        } @else {
          <div class="flex flex-col gap-4 md:gap-6">
            @for (grupo of formulario()!.grupos; track grupo.id; let gi = $index) {
              <div class="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 overflow-hidden shadow-sm">
                <!-- Cabeçalho do grupo -->
                <div class="bg-slate-100 dark:bg-slate-800/70 border-b border-slate-200 dark:border-slate-800 px-4 md:px-5 py-3 md:py-3.5">
                  <span class="text-[13px] font-medium text-slate-800 dark:text-slate-100">{{ formatarNumero(gi + 1) }}. {{ grupo.titulo }}</span>
                </div>

                <!-- Perguntas -->
                <div class="flex flex-col divide-y divide-slate-100 dark:divide-slate-800">
                  @for (pergunta of grupo.perguntas; track pergunta.id; let pi = $index) {
                    <div class="px-4 md:px-5 py-4 md:py-5 bg-white dark:bg-slate-900 hover:bg-slate-50/50 dark:hover:bg-slate-800/30 transition-colors">
                      @if (isObjetivo(pergunta.tipoResposta)) {
                        <div class="flex flex-col gap-4">
                          <div class="flex flex-col gap-2.5">
                            <div class="flex items-start gap-2">
                              <span class="text-[12px] md:text-sm font-medium text-slate-800 dark:text-slate-200 leading-snug">
                                {{ pi + 1 }}. {{ pergunta.enunciado }}
                                @if (pergunta.obrigatoria) { <span class="text-red-500 ml-1" pTooltip="Obrigatório">*</span> }
                              </span>
                            </div>
                            <!-- Opções em caixas de seleção (Coluna única) -->
                            <div class="flex flex-col gap-0.5 ml-4">
                              @for (opcao of pergunta.opcoes; track opcao.id) {
                                <button type="button" (click)="toggleOpcao(pergunta, opcao.id, opcao.geraDesvio)"
                                  class="group flex items-start gap-2.5 text-left py-2 px-2 -mx-1.5 rounded-md transition-colors"
                                  [ngClass]="isOpcaoSelecionada(pergunta.id, opcao.id) ? (opcao.geraDesvio ? 'text-red-700 dark:text-red-400 font-medium bg-red-50 dark:bg-red-950/40' : 'text-primary-700 dark:text-primary-300 font-medium bg-primary-50 dark:bg-primary-950/40') : 'text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800/60'">
                                  <div class="w-3.5 h-3.5 mt-0.5 rounded-sm border shrink-0 flex-none"
                                      [ngClass]="boxClass(pergunta.id, opcao.id, opcao.geraDesvio)">
                                  </div>
                                  <span class="text-[11px] leading-snug">{{ opcao.texto }}</span>
                                </button>
                              }
                            </div>
                          </div>

                        <!-- Bloco(s) de desvio -->
                        @for (opcao of pergunta.opcoes; track opcao.id) {
                          @if (opcao.geraDesvio && isOpcaoSelecionada(pergunta.id, opcao.id) && desvioRef(pergunta.id, opcao.id); as dv) {
                            <div class="rounded-lg border border-l-4 px-3.5 md:px-5 py-3.5 md:py-4 flex flex-col gap-3.5 md:gap-4 mt-2 ml-1 md:ml-4 transition-all" [ngStyle]="desvioBoxStyle(opcao.gravidade)">
                              <div class="flex flex-col gap-2 pb-2.5 border-b sm:flex-row sm:items-center sm:justify-between" [ngStyle]="{'border-bottom-color': corGravidade(opcao.gravidade) + '40'}">
                                <div class="flex items-center gap-2 flex-wrap min-w-0">
                                  <i class="pi pi-exclamation-triangle text-sm shrink-0" [ngStyle]="{'color': corGravidade(opcao.gravidade)}"></i>
                                  <span class="text-[11px] font-semibold uppercase tracking-widest" [ngStyle]="{'color': corGravidadeTexto(opcao.gravidade)}">Tratamento de Desvio</span>
                                  <span class="text-[11px] font-medium px-2 py-0.5 rounded-md max-w-full truncate" [ngStyle]="{'background-color': corGravidade(opcao.gravidade) + '30', 'color': corGravidadeTexto(opcao.gravidade)}">Motivo: {{ opcao.texto }}</span>
                                  @if (dv.tratadoInLoco) {
                                    <span class="text-[10px] font-bold uppercase tracking-tight px-2.5 py-1 rounded-md bg-emerald-600 text-white shadow-sm shrink-0 flex items-center gap-1">
                                      <i class="pi pi-check text-[10px]"></i> Tratado in-loco
                                    </span>
                                  }
                                </div>
                                <div class="flex items-center gap-2 self-start sm:self-auto shrink-0 flex-wrap">
                                  @if (opcao.gravidade) {
                                    <span class="text-[10px] font-bold uppercase tracking-tight px-3.5 py-1.5 rounded-md shadow-sm shrink-0" [ngStyle]="{'background-color': corGravidade(opcao.gravidade), 'color': '#ffffff'}">Gravidade: {{ gravidadeLabel(opcao.gravidade) }}</span>
                                  }
                                  <input #fileInput type="file" [accept]="acceptAttr" multiple class="hidden" (change)="onAnexarArquivo($event, dv)" />
                                  <button type="button" (click)="fileInput.click()" pTooltip="Anexar evidência do desvio (Arquivos ou Galeria)" tooltipPosition="left" class="flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs md:text-sm font-semibold bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 text-slate-800 dark:text-slate-100 border border-slate-300 dark:border-slate-600 shadow-sm transition-all hover:border-slate-400 dark:hover:border-slate-500 active:scale-95">
                                    <i class="pi pi-paperclip text-blue-600 dark:text-blue-400 text-sm"></i>
                                    <span>Anexar Evidência</span>
                                  </button>
                                </div>
                              </div>

                              <!-- Banner de Tratamento in-loco -->
                              <div class="flex items-center justify-between p-2.5 rounded-lg border transition-colors"
                                   [ngClass]="dv.tratadoInLoco ? 'bg-emerald-50/80 border-emerald-300 dark:bg-emerald-950/30 dark:border-emerald-700/50' : 'bg-slate-50/80 border-slate-200 dark:bg-slate-800/40 dark:border-slate-700/60'">
                                <div class="flex items-center gap-2.5">
                                  <i class="text-base" [ngClass]="dv.tratadoInLoco ? 'pi pi-check-circle text-emerald-600 dark:text-emerald-400' : 'pi pi-clock text-slate-400'"></i>
                                  <div class="flex flex-col">
                                    <span class="text-xs font-semibold" [ngClass]="dv.tratadoInLoco ? 'text-emerald-900 dark:text-emerald-200' : 'text-slate-700 dark:text-slate-200'">
                                      Tratamento in-loco (tratado na hora)
                                    </span>
                                    <span class="text-[11px]" [ngClass]="dv.tratadoInLoco ? 'text-emerald-700 dark:text-emerald-400' : 'text-slate-500 dark:text-slate-400'">
                                      {{ dv.tratadoInLoco ? 'O desvio foi sanado imediatamente durante a inspeção. Será registrado como Resolvido.' : 'Marque se o desvio já foi sanado no próprio local de execução.' }}
                                    </span>
                                  </div>
                                </div>
                                <ces-boolean-control
                                  kind="toggle"
                                  [inline]="true"
                                  [value]="!!dv.tratadoInLoco"
                                  (valueChange)="onToggleTratadoInLoco(dv, $event)"
                                ></ces-boolean-control>
                              </div>

                              <div class="grid grid-cols-1 md:grid-cols-2 gap-5 md:gap-6 pt-1">
                                <!-- Lado Esquerdo (2 opções) -->
                                <div class="flex flex-col gap-4">
                                  <div class="flex flex-col gap-1.5">
                                    <label class="dl flex items-center gap-1.5 font-semibold" [ngStyle]="{'color': corGravidadeTexto(opcao.gravidade)}"><i class="pi pi-file-edit"></i> Descrição do problema</label>
                                    <input pInputText [(ngModel)]="dv.descricao" placeholder="Descreva o que foi constatado" class="doc-input w-full" [ngStyle]="{'border-color': corGravidade(opcao.gravidade) + '50'}" />
                                  </div>
                                  <div class="flex flex-col gap-1.5">
                                    <label class="dl flex items-center gap-1.5 font-semibold" [ngStyle]="{'color': corGravidadeTexto(opcao.gravidade)}">
                                      <i class="pi pi-wrench"></i>
                                      <span>{{ dv.tratadoInLoco ? 'Ação corretiva realizada no local' : 'Ação corretiva recomendada' }}</span>
                                      @if (dv.tratadoInLoco) { <span class="text-red-500 ml-0.5" pTooltip="Obrigatório para desvios tratados in-loco">*</span> }
                                    </label>
                                    <input pInputText [(ngModel)]="dv.acaoCorretiva" [placeholder]="dv.tratadoInLoco ? 'Descreva o que foi feito na hora para sanar o desvio' : 'O que deve ser feito para corrigir'" class="doc-input w-full" [ngStyle]="{'border-color': corGravidade(opcao.gravidade) + '50'}" />
                                  </div>
                                </div>
                                <!-- Lado Direito (2 opções) -->
                                <div class="flex flex-col gap-4">
                                  <div class="flex flex-col gap-1.5">
                                    <label class="dl flex items-center gap-1.5 font-semibold" [ngStyle]="{'color': corGravidadeTexto(opcao.gravidade)}">
                                      <i class="pi pi-calendar"></i>
                                      <span>{{ dv.tratadoInLoco ? 'Data da resolução' : 'Prazo' }}</span>
                                      @if (dv.tratadoInLoco) { <span class="text-[10px] font-normal opacity-75">(in-loco)</span> }
                                    </label>
                                    <p-datepicker [(ngModel)]="dv.prazo" dateFormat="dd/mm/yy" dateMask [showIcon]="true" iconDisplay="input" [showButtonBar]="true" placeholder="dd/mm/aaaa" appendTo="body" size="small" styleClass="doc-date w-full"></p-datepicker>
                                  </div>
                                  <div class="flex flex-col gap-1.5">
                                    <label class="dl flex items-center gap-1.5 font-semibold" [ngStyle]="{'color': corGravidadeTexto(opcao.gravidade)}"><i class="pi pi-users"></i> Responsáveis <span class="normal-case font-normal opacity-75">(inspecionados)</span></label>
                                    <p-multiSelect [options]="inspecionados()" size="small" [(ngModel)]="dv.colaboradores" optionLabel="nome" optionValue="id" placeholder="Atribuir a..." appendTo="body" styleClass="doc-multiselect w-full"></p-multiSelect>
                                  </div>
                                </div>
                              </div>
                              @if (dv.anexosPreview && dv.anexosPreview.length > 0) {
                                <div class="flex flex-col gap-2.5 pt-3 border-t" [ngStyle]="{'border-top-color': corGravidade(opcao.gravidade) + '40'}">
                                  <span class="text-xs font-semibold flex items-center gap-1.5" [ngStyle]="{'color': corGravidadeTexto(opcao.gravidade)}">
                                    <i class="pi pi-paperclip"></i> Evidências selecionadas ({{ dv.anexosPreview.length }}):
                                  </span>
                                  <div class="flex items-center gap-2.5 flex-wrap">
                                    @for (anexo of dv.anexosPreview; track $index) {
                                      @if (anexo.isImage) {
                                        <div (click)="abrirPreviewFoto(anexo.previewUrl || '')" pTooltip="Clique para visualizar em tela cheia" tooltipPosition="top" class="relative group w-16 h-16 rounded-lg border border-slate-300 dark:border-slate-700 overflow-hidden shadow-sm bg-white dark:bg-slate-800 cursor-pointer hover:border-blue-500 transition-all">
                                          @if (anexo.previewUrl) {
                                            <img [src]="anexo.previewUrl" class="w-full h-full object-cover" alt="Evidência do desvio" />
                                          } @else {
                                            <div class="w-full h-full flex items-center justify-center">
                                              <i class="pi pi-spin pi-spinner text-slate-300 dark:text-slate-600 text-sm"></i>
                                            </div>
                                          }
                                          <button type="button" (click)="$event.stopPropagation(); removerAnexoPreview(dv, $index)" pTooltip="Remover evidência" tooltipPosition="top" class="absolute top-1 right-1 w-6 h-6 rounded-full bg-red-600 text-white flex items-center justify-center ring-1 ring-white/70 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity shadow">
                                            <i class="pi pi-trash text-[10px]"></i>
                                          </button>
                                        </div>
                                      } @else {
                                        <div class="relative group flex items-center gap-2 bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-lg px-3 py-2 shadow-sm hover:border-blue-500 transition-all max-w-[240px]">
                                          <i [class]="getIconArquivo(anexo.nome)" class="text-base shrink-0"></i>
                                          <div class="flex flex-col min-w-0 pr-5">
                                            <span class="text-xs font-medium text-slate-800 dark:text-slate-200 truncate" [title]="anexo.nome">{{ anexo.nome }}</span>
                                            <span class="text-[10px] text-slate-400 dark:text-slate-500">{{ anexo.tamanho }}</span>
                                          </div>
                                          <button type="button" (click)="removerAnexoPreview(dv, $index)" pTooltip="Remover evidência" tooltipPosition="top" class="absolute top-1.5 right-1.5 w-6 h-6 rounded-full bg-red-600 text-white flex items-center justify-center ring-1 ring-white/70 opacity-100 md:opacity-0 md:group-hover:opacity-100 transition-opacity shadow">
                                            <i class="pi pi-trash text-[10px]"></i>
                                          </button>
                                        </div>
                                      }
                                    }
                                  </div>
                                </div>
                              }
                            </div>
                          }
                        }
                      </div>
                    } @else {
                      <div class="flex flex-col gap-3">
                        <div class="flex items-start gap-2">
                          <span class="text-xs font-medium text-slate-800 dark:text-slate-200 leading-snug">
                            {{ pi + 1 }}. {{ pergunta.enunciado }}
                            @if (pergunta.obrigatoria) { <span class="text-red-500 ml-1" pTooltip="Obrigatório">*</span> }
                          </span>
                        </div>
                        <div class="ml-4 md:ml-6 mt-1">
                          @if (pergunta.tipoResposta === 'TEXTO') {
                            <textarea pTextarea [(ngModel)]="respostas[pergunta.id].valorTexto" rows="2" placeholder="Digite sua resposta..." class="doc-textarea w-full md:max-w-2xl bg-white dark:bg-slate-900 focus:bg-slate-50 dark:focus:bg-slate-800/80 transition-colors"></textarea>
                          }
                          @if (pergunta.tipoResposta === 'NUMERO') {
                            <p-inputnumber [(ngModel)]="respostas[pergunta.id].valorNumero" [showButtons]="false" placeholder="0" styleClass="doc-number w-full md:w-48"></p-inputnumber>
                          }
                          @if (pergunta.tipoResposta === 'DATA') {
                            <p-datepicker [(ngModel)]="respostas[pergunta.id].valorData" dateFormat="dd/mm/yy" dateMask [showIcon]="true" iconDisplay="input" [showButtonBar]="true" placeholder="dd/mm/aaaa" appendTo="body" size="small" styleClass="doc-date w-full md:w-48"></p-datepicker>
                          }
                        </div>
                      </div>
                    }
                  </div>
                }
              </div>
            </div>
          }
        </div>
        }
      }
    </div>

    @if (formulario() && podePreencherChecklist()) {
      <div class="fixed inset-x-0 bottom-0 z-30 px-4 pb-4 pt-6 pointer-events-none bg-gradient-to-t from-white via-white to-transparent dark:from-slate-950 dark:via-slate-950/80 dark:to-transparent md:inset-x-auto md:right-12 md:bottom-6 md:p-0 md:bg-none">
        <!-- O tooltip vai no wrapper: botão desabilitado não dispara eventos de mouse. -->
        <span class="pointer-events-auto block md:inline-block" [pTooltip]="motivoBloqueioLancamento()" tooltipPosition="top">
          <p-button [label]="isModoEdicao() ? 'Salvar Alterações' : 'Registrar Inspeção'" icon="pi pi-check-circle" [loading]="saving()" [disabled]="!podeLancar()" (onClick)="lancar()" styleClass="!w-full md:!w-auto !justify-center !rounded-xl !bg-primary-700 hover:!bg-primary-800 dark:!bg-primary-600 dark:hover:!bg-primary-500 !border-primary-700 dark:!border-primary-600 !text-white !font-medium !text-[11px] !tracking-widest !uppercase !px-6 !py-3 transition-all"></p-button>
        </span>
      </div>
    }

    <p-dialog [(visible)]="fotoExpandidaVisible" [modal]="true" appendTo="body" [dismissableMask]="true" [showHeader]="true" header="Visualizar Evidência" [style]="{width: '90vw', maxWidth: '750px'}" styleClass="overflow-hidden rounded-2xl">
      @if (fotoExpandida) {
        <div class="flex items-center justify-center p-3 bg-slate-950 rounded-xl">
          <img [src]="fotoExpandida" class="max-h-[75vh] w-auto object-contain rounded-lg shadow-2xl" alt="Foto expandida do desvio" />
        </div>
      }
    </p-dialog>

    <app-help-dialog #helpDialog title="Guia da Tela: Lançamento de Inspeção">
      <!-- 1. Passo inicial -->
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-file-edit"></i> 1. Seleção do Modelo e Contexto</h4>

        <div class="help-item">
          <div class="help-icon"><i class="pi pi-list"></i></div>
          <div class="help-text">
            <span class="help-text-title">Escolher o Modelo</span>
            <span class="help-text-desc">No campo <strong>Modelo do Formulário</strong>, selecione o checklist de inspeção que será aplicado. O sistema carregará automaticamente todas as perguntas e grupos do modelo escolhido.</span>
          </div>
        </div>

        <div class="help-item">
          <div class="help-icon"><i class="pi pi-sitemap"></i></div>
          <div class="help-text">
            <span class="help-text-title">Processo, Veículo e Data</span>
            <span class="help-text-desc">Preencha obrigatoriamente o <strong>Processo</strong> operacional avaliado e a <strong>Data/Hora</strong> da inspeção. Caso a inspeção seja em um carro da frota, selecione a placa no campo <strong>Veículo</strong>.</span>
          </div>
        </div>
      </div>

      <div class="h-px bg-slate-100 dark:bg-slate-800 my-4"></div>

      <!-- 2. Equipe e Inspecionados -->
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-users"></i> 2. Equipe e Colaboradores</h4>

        <div class="help-item">
          <div class="help-icon"><i class="pi pi-users"></i></div>
          <div class="help-text">
            <span class="help-text-title">Equipes em Campo</span>
            <span class="help-text-desc">Selecione uma ou mais equipes que estão sendo inspecionadas. Ao escolher a equipe, os colaboradores vinculados a ela serão adicionados automaticamente à lista de avaliados.</span>
          </div>
        </div>

        <div class="help-item">
          <div class="help-icon"><i class="pi pi-user-plus"></i></div>
          <div class="help-text">
            <span class="help-text-title">Adicionar Colaborador Avulso</span>
            <span class="help-text-desc">Caso precise incluir algum funcionário específico que não esteja na equipe selecionada, selecione-o no campo de colaboradores avulsos.</span>
          </div>
        </div>
      </div>

      <div class="h-px bg-slate-100 dark:bg-slate-800 my-4"></div>

      <!-- 3. Preenchimento do Checklist -->
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-check-square"></i> 3. Respondendo ao Questionário</h4>

        <div class="help-item">
          <div class="help-icon"><i class="pi pi-check text-green-600"></i></div>
          <div class="help-text">
            <span class="help-text-title">Opções de Conformidade</span>
            <span class="help-text-desc">Para cada pergunta do questionário, clique na opção correspondente (ex: Conforme, Não Conforme, Não se Aplica).</span>
          </div>
        </div>

        <div class="help-item">
          <div class="help-icon"><i class="pi pi-exclamation-triangle text-red-500"></i></div>
          <div class="help-text">
            <span class="help-text-title">Lançando Desvios</span>
            <span class="help-text-desc">Sempre que responder uma opção configurada como irregularidade/desvio, abrirá um quadro vermelho para informar o <strong>Prazo</strong>, a descrição do problema e a ação corretiva imediata.</span>
          </div>
        </div>
      </div>

      <div class="h-px bg-slate-100 dark:bg-slate-800 my-4"></div>

      <!-- 4. Localização e Envio -->
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-send"></i> 4. Finalização</h4>

        <div class="help-item">
          <div class="help-icon"><i class="pi pi-map-marker text-primary-700 dark:text-primary-400"></i></div>
          <div class="help-text">
            <span class="help-text-title">Geolocalização Automática</span>
            <span class="help-text-desc">O aplicativo captura automaticamente suas coordenadas de GPS no momento da abertura do questionário para comprovação de presença em campo.</span>
          </div>
        </div>

        <div class="help-item">
          <div class="help-icon !bg-amber-50 !text-amber-600 dark:!bg-amber-950/50 dark:!text-amber-400"><i class="pi pi-camera"></i></div>
          <div class="help-text">
            <span class="help-text-title">Evidência Fotográfica (Obrigatória)</span>
            <span class="help-text-desc">Toda inspeção exige <strong>ao menos uma fotografia</strong> comprovando que ela aconteceu de fato em campo. Arraste as fotos para a área indicada ou clique para selecionar — são aceitos apenas arquivos de imagem (JPEG, PNG, WebP ou HEIC) de até 15 MB. Enquanto não houver nenhuma foto, o botão de registrar permanece bloqueado.</span>
          </div>
        </div>

        <div class="help-item">
          <div class="help-icon !bg-primary-50 !text-primary-700 dark:!bg-primary-950/50 dark:!text-primary-400"><i class="pi pi-check-circle"></i></div>
          <div class="help-text">
            <span class="help-text-title">Registrar Inspeção</span>
            <span class="help-text-desc">Após responder a todas as perguntas obrigatórias, clique no botão azul no canto inferior direito <strong>"REGISTRAR INSPEÇÃO"</strong> para salvar e transmitir o relatório.</span>
          </div>
        </div>
      </div>
    </app-help-dialog>
  `,
  styles: [`
    :host ::ng-deep {
      .doc-input.p-inputtext, .doc-textarea.p-inputtext,
      .doc-select .p-select-label, .doc-select .p-select-placeholder,
      .doc-multiselect .p-multiselect-label,
      .doc-number .p-inputnumber-input, .doc-date .p-inputtext {
        font-size: 11px !important;
        font-weight: 500 !important;
        color: #374151;
        border-radius: 6px !important;
      }
      .doc-input.p-inputtext, .doc-select .p-select-label, .doc-multiselect .p-multiselect-label,
      .doc-number .p-inputnumber-input, .doc-date .p-inputtext { height: 34px !important; display: flex; align-items: center; }
      .doc-textarea.p-inputtext { height: auto !important; padding: 0.5rem 0.75rem; }
      .doc-input.p-inputtext, .doc-textarea.p-inputtext { width: 100%; border-color: #e5e7eb !important; }
      .doc-select .p-select, .doc-multiselect .p-multiselect, .doc-date .p-datepicker, .doc-number.p-inputnumber { width: 100%; }
      .doc-select .p-select, .doc-multiselect .p-multiselect { height: 34px; border-color: #e5e7eb !important; border-radius: 6px !important; }
      .doc-date .p-inputtext { border-color: #e5e7eb !important; }
      .p-breadcrumb .p-menuitem-text { font-size: 0.7rem; font-weight: 500; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.08em; }
    }
    .dl { font-size: 10.5px; font-weight: 500; color: #64748b; text-transform: capitalize; letter-spacing: 0.08em; }

    :host-context(.app-dark),
    :host-context(.dark) {
      .dl {
        color: #94a3b8;
      }
      ::ng-deep {
        .doc-input.p-inputtext,
        .doc-textarea.p-inputtext,
        .doc-select .p-select-label,
        .doc-multiselect .p-multiselect-label,
        .doc-number .p-inputnumber-input,
        .doc-date .p-inputtext {
          color: #f1f5f9 !important;
          background-color: #0f172a !important;
        }
        .doc-input.p-inputtext:not([style*="border-color"]),
        .doc-textarea.p-inputtext:not([style*="border-color"]) {
          border-color: #334155 !important;
        }
        .doc-select .p-select-placeholder,
        .doc-multiselect .p-multiselect-label:empty,
        .doc-input::placeholder,
        .doc-textarea::placeholder,
        .doc-date .p-inputtext::placeholder {
          color: #64748b !important;
        }
        .doc-select .p-select,
        .doc-multiselect .p-multiselect,
        .doc-date .p-datepicker {
          background-color: #0f172a !important;
          border-color: #334155 !important;
        }
        .doc-date .p-inputtext {
          border-color: #334155 !important;
        }
        .doc-select .p-select:not(.p-disabled):hover,
        .doc-multiselect .p-multiselect:not(.p-disabled):hover,
        .doc-input.p-inputtext:not(:disabled):hover,
        .doc-textarea.p-inputtext:not(:disabled):hover {
          border-color: #475569 !important;
        }
        .doc-select .p-select:not(.p-disabled).p-focus,
        .doc-multiselect .p-multiselect:not(.p-disabled).p-focus,
        .doc-input.p-inputtext:not(:disabled):focus,
        .doc-textarea.p-inputtext:not(:disabled):focus {
          border-color: #3b82f6 !important;
        }
        .p-breadcrumb .p-menuitem-text {
          color: #94a3b8 !important;
        }
        .p-breadcrumb .p-menuitem-icon,
        .p-breadcrumb .p-breadcrumb-separator {
          color: #64748b !important;
        }
      }
    }

    /* Entrada sutil ao abrir a tela — dinamismo discreto, mantendo a estética. */
    @keyframes riseIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
    .rise { animation: riseIn .4s cubic-bezier(.22,.61,.36,1) both; }
    @media (prefers-reduced-motion: reduce) { .rise { animation: none; } }
  `]
})
export class InspecaoFormComponent implements OnInit {
  @ViewChild('mapDialog') mapDialog!: MapDialogComponent;
  @ViewChild('localPicker') localPicker!: LocationPickerDialogComponent;
  @ViewChild('helpDialog') helpDialog!: HelpDialogComponent;
  @ViewChild('evidencias') evidenciasUpload?: EvidenciasInspecaoUploadComponent;

  private sst = inject(SstService);
  private rh = inject(RhService);
  private lookupStore = inject(LookupStore);
  private auth = inject(AuthService);
  private theme = inject(ThemeService, { optional: true });
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private messageService = inject(MessageService);
  private cdr = inject(ChangeDetectorRef);
  private destroyRef = inject(DestroyRef);
  private readonly equipeLookup = new RemoteLookup<EquipeResponse>(
    {
      identify: (equipe) => equipe.id,
      selectedKeys: () => (this.idEquipe ? [this.idEquipe] : []),
      load: ({ query, page, size }) =>
        this.rh.listarEquipes({ ativo: true, buscaGlobal: query, page, size, sort: 'codigo,asc' })
    },
    this.destroyRef
  );
  private readonly veiculoLookup = new RemoteLookup<VeiculoResponse>(
    {
      identify: (veiculo) => veiculo.id,
      selectedKeys: () => (this.idVeiculo ? [this.idVeiculo] : []),
      load: ({ query, page, size }) =>
        this.rh.listarVeiculos({ ativo: true, buscaGlobal: query, page, size, sort: 'placa,asc' })
    },
    this.destroyRef
  );
  private readonly colaboradorLookup = new LookupRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () => this.inspecionadosIds(),
    destroyRef: this.destroyRef
  });

  protected readonly idInspecaoEdicao = signal<string | null>(null);
  protected readonly isModoEdicao = computed(() => !!this.idInspecaoEdicao());
  protected justificativaEdicao = '';

  protected fotoExpandida: string | null = null;
  protected fotoExpandidaVisible = false;
  protected readonly acceptAttr = ANEXO_ACCEPT;

  protected breadcrumbItems: MenuItem[] = [
    { icon: 'pi pi-home', routerLink: '/home' },
    { label: 'SST' },
    { label: 'Inspeções', routerLink: '/sst/inspecoes' },
    { label: 'Nova' }
  ];

  protected saving = signal(false);
  protected carregandoForm = signal(false);
  protected geoOk = signal(false);

  protected formularios = signal<FormularioInspecaoResumoResponse[]>([]);
  protected tiposInspecao = signal<TipoInspecaoResponse[]>([]);
  protected processos = signal<ProcessoNegocioResponse[]>([]);
  protected readonly equipes = this.equipeLookup.items;
  protected readonly equipesLoading = this.equipeLookup.loading;
  protected basesOperacionais = signal<BaseOperacionalResponse[]>([]);
  protected carregandoBases = signal(false);
  protected podeLerBases = computed(() =>
    !!this.auth.currentUser()?.permissoes?.includes('rh:base-operacional:read')
  );
  protected readonly veiculos = this.veiculoLookup.items;
  protected readonly veiculosLoading = this.veiculoLookup.loading;
  protected readonly colaboradores = this.colaboradorLookup.items;
  protected readonly colaboradoresDisponiveis = computed(() => {
    const selecionados = new Set(this.inspecionadosIds());
    return this.colaboradores().filter(item => !selecionados.has(item.value));
  });
  protected readonly colaboradoresLoading = this.colaboradorLookup.loading;
  protected readonly colaboradoresStatus = this.colaboradorLookup.status;
  protected readonly colaboradoresError = computed(() => {
    const error = this.colaboradorLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });

  protected formulario = signal<FormularioInspecaoResponse | null>(null);

  protected idFormulario: string | null = null;
  protected idTipoInspecao: string | null = null;
  protected idProcesso: string | null = null;
  protected idEquipe: string | null = null;
  protected idBaseOperacional: string | null = null;
  protected idVeiculo: string | null = null;
  protected dataRealizacao: Date = new Date();
  protected observacoes = '';
  protected latitude = signal<number | null>(null);
  protected longitude = signal<number | null>(null);
  protected temLocalizacao = computed(() => this.latitude() != null && this.longitude() != null);

  protected inspecionados = signal<Inspecionado[]>([]);
  protected inspecionadosIds = computed(() => this.inspecionados().map((c) => c.id));
  protected colaboradorParaAdicionarId: string | null = null;

  protected respostas: Record<string, RespostaState> = {};
  protected desvios: Record<string, DesvioState> = {};

  protected isObjetivo = isTipoObjetivo;

  protected isDark(): boolean {
    return this.theme?.isDarkMode() ?? (typeof document !== 'undefined' && document.documentElement.classList.contains('app-dark'));
  }

  protected gravidadeLabel(g?: GravidadeDesvio | null): string {
    return g ? GRAVIDADE_LABELS[g] || g : '';
  }

  protected corGravidade(g?: GravidadeDesvio | null): string {
    switch (g) {
      case 'BAIXA': return '#FFC107';
      case 'MEDIA': return '#fd6214';
      case 'ALTA': return '#E63946';
      case 'CRITICA': return '#d40202';
      default: return '#fd6214';
    }
  }

  protected corGravidadeTexto(g?: GravidadeDesvio | null): string {
    if (this.isDark()) {
      switch (g) {
        case 'BAIXA': return '#fbbf24';
        case 'MEDIA': return '#fb923c';
        case 'ALTA': return '#f87171';
        case 'CRITICA': return '#fca5a5';
        default: return '#fb923c';
      }
    }
    switch (g) {
      case 'BAIXA': return '#785A00';
      case 'MEDIA': return '#B84500';
      case 'ALTA': return '#B81D2A';
      case 'CRITICA': return '#8a0101';
      default: return '#B84500';
    }
  }

  protected desvioBoxStyle(g?: GravidadeDesvio | null): Record<string, string> {
    const cor = this.corGravidade(g);
    if (this.isDark()) {
      return {
        'border-left-color': cor,
        'border-color': `${cor}50`,
        'background-color': `${cor}15`
      };
    }
    return {
      'border-left-color': cor,
      'border-color': `${cor}60`,
      'background-color': `${cor}24`
    };
  }

  ngOnInit() {
    this.carregarLookups();
    this.openEquipes();
    this.openVeiculos();

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.idInspecaoEdicao.set(idParam);
      this.breadcrumbItems = [
        { icon: 'pi pi-home', routerLink: '/home' },
        { label: 'SST' },
        { label: 'Inspeções', routerLink: '/sst/inspecoes' },
        { label: 'Detalhes', routerLink: `/sst/inspecoes/${idParam}` },
        { label: 'Editar' }
      ];
      this.carregarInspecaoParaEdicao(idParam);
    } else {
      this.capturarGeolocalizacao();
    }
  }

  private carregarInspecaoParaEdicao(id: string) {
    this.carregandoForm.set(true);
    this.sst.buscarInspecaoPorId(id).subscribe({
      next: (insp) => {
        const currentUser = this.auth.currentUser();
        if (currentUser && insp.idInspetor !== currentUser.id) {
          this.carregandoForm.set(false);
          this.messageService.add({
            severity: 'error',
            summary: 'Acesso negado',
            detail: 'Apenas o inspetor que registrou a inspeção pode editá-la.'
          });
          this.router.navigate(['/sst/inspecoes', id]);
          return;
        }

        this.idFormulario = insp.formulario?.id || null;
        this.idTipoInspecao = insp.tipoInspecao?.id || null;
        this.idProcesso = insp.processo?.id || null;
        this.idBaseOperacional = insp.baseOperacional?.id || null;
        this.idVeiculo = insp.veiculo?.id || null;
        if (insp.veiculo) {
          this.veiculoLookup.setSeed([insp.veiculo]);
        }
        this.idEquipe = insp.equipe?.id || null;
        if (insp.equipe) {
          this.equipeLookup.setSeed([insp.equipe]);
        }
        this.dataRealizacao = insp.dataRealizacao ? new Date(insp.dataRealizacao) : new Date();
        this.observacoes = insp.observacoes || '';
        this.latitude.set(insp.latitude ?? null);
        this.longitude.set(insp.longitude ?? null);
        if (insp.latitude != null && insp.longitude != null) {
          this.geoOk.set(true);
        }

        const colaboradores = (insp.colaboradoresInspecionados || []).map((c) => ({
          id: c.id,
          nome: c.nomeCompleto || c.id
        }));
        this.inspecionados.set(colaboradores);
        this.colaboradorLookup.seedSelected(colaboradores.map((c) => ({ value: c.id, label: c.nome })));

        if (!this.idFormulario) {
          this.carregandoForm.set(false);
          return;
        }

        this.sst.buscarFormularioPorId(this.idFormulario).subscribe({
          next: (form) => {
            const grupos = (form.grupos ?? []).slice().sort((a, b) => a.ordem - b.ordem);
            grupos.forEach((g) => (g.perguntas = (g.perguntas ?? []).slice().sort((a, b) => a.ordem - b.ordem)));
            form.grupos = grupos;
            this.formulario.set(form);

            this.respostas = {};
            grupos.forEach((g) => {
              g.perguntas.forEach((p) => {
                const rSalva = insp.respostas?.find((r) => r.idPergunta === p.id);
                if (rSalva) {
                  this.respostas[p.id] = {
                    opcoesSelecionadas: rSalva.opcoesSelecionadas ? [...rSalva.opcoesSelecionadas] : [],
                    valorTexto: rSalva.valorTexto ?? null,
                    valorNumero: rSalva.valorNumero ?? null,
                    valorData: rSalva.valorData ? new Date(rSalva.valorData + 'T12:00:00') : null
                  };
                } else {
                  const opcoesPadrao = isTipoObjetivo(p.tipoResposta)
                    ? (p.opcoes ?? []).filter((o) => o.padrao).map((o) => o.id)
                    : [];
                  this.respostas[p.id] = {
                    opcoesSelecionadas: opcoesPadrao,
                    valorTexto: null,
                    valorNumero: null,
                    valorData: null
                  };
                }
              });
            });

            this.desvios = {};
            (insp.desvios || []).forEach((d) => {
              const key = this.desvioKey(d.idPergunta, d.idOpcaoResposta);
              this.desvios[key] = {
                idPergunta: d.idPergunta,
                idOpcaoResposta: d.idOpcaoResposta,
                descricao: d.descricao || '',
                acaoCorretiva: d.acaoCorretiva || '',
                prazo: d.prazo ? new Date(d.prazo + 'T12:00:00') : null,
                colaboradores: (d.colaboradores || []).map((c) => c.id),
                tratadoInLoco: d.status === 'RESOLVIDO',
                arquivos: [],
                fotosPreview: [],
                anexosPreview: []
              };
            });

            this.carregandoForm.set(false);
            this.cdr.detectChanges();

            if (insp.evidencias && insp.evidencias.length > 0) {
              setTimeout(() => {
                this.evidenciasUpload?.carregarExistentes(insp.evidencias, insp.id);
              });
            }
          },
          error: (err) => {
            this.carregandoForm.set(false);
            this.messageService.add({
              severity: 'error',
              summary: 'Erro ao carregar formulário',
              detail: getErrorMessage(err)
            });
          }
        });
      },
      error: (err) => {
        this.carregandoForm.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro ao carregar inspeção',
          detail: getErrorMessage(err)
        });
        this.router.navigate(['/sst/inspecoes']);
      }
    });
  }

  protected formatarNumero(n: number): string {
    return n < 10 ? `0${n}` : String(n);
  }

  protected abrirMapaGeo() {
    const lat = this.latitude();
    const lng = this.longitude();
    if (lat != null && lng != null) {
      this.mapDialog.open(lat, lng, 'Local da inspeção');
    }
  }

  protected abrirSeletorLocal() {
    this.localPicker.open(this.latitude(), this.longitude());
  }

  protected onLocalConfirmado(local: LocalSelecionado) {
    this.latitude.set(local.latitude);
    this.longitude.set(local.longitude);
    this.geoOk.set(true);
    this.messageService.add({
      severity: 'success',
      summary: 'Localização definida',
      detail: local.endereco ?? `Lat: ${local.latitude.toFixed(5)}, Lon: ${local.longitude.toFixed(5)}`
    });
  }

  protected boxClass(idPergunta: string, idOpcao: string, geraDesvio: boolean): string {
    if (!this.isOpcaoSelecionada(idPergunta, idOpcao)) {
      return 'border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 group-hover:border-primary-400 group-hover:bg-slate-50 dark:group-hover:bg-slate-700';
    }
    return geraDesvio ? 'border-red-500 bg-red-500' : 'border-primary-700 dark:border-primary-500 bg-primary-700 dark:bg-primary-500';
  }

  private carregarLookups() {
    forkJoin({
      formularios: this.sst.listarFormulariosAtivos(),
      tiposInspecao: this.sst.listarOpcoesTiposInspecao(),
      processos: this.rh.listarProcessosAtivos()
    }).subscribe({
      next: (res) => {
        this.formularios.set(res.formularios.content);
        this.tiposInspecao.set(res.tiposInspecao);
        if (!this.isModoEdicao() && !this.idTipoInspecao && res.tiposInspecao.length > 0) {
          const campo = res.tiposInspecao.find((t) => t.nome.toLowerCase() === 'campo');
          this.idTipoInspecao = campo ? campo.id : res.tiposInspecao[0].id;
        }
        this.processos.set(res.processos.content);
      },
      error: (err) => this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) })
    });
    this.carregarBases();
  }

  protected openEquipes(): void { this.equipeLookup.open(); }
  protected searchEquipes(event: { filter?: string }): void {
    this.equipeLookup.search(event.filter ?? '');
  }
  protected loadMoreEquipes(): void { this.equipeLookup.loadNext(); }

  protected openVeiculos(): void { this.veiculoLookup.open(); }
  protected searchVeiculos(event: { filter?: string }): void {
    this.veiculoLookup.search(event.filter ?? '');
  }
  protected loadMoreVeiculos(): void { this.veiculoLookup.loadNext(); }

  protected searchColaboradores(query: string): void {
    this.colaboradorLookup.search(query);
  }
  protected loadMoreColaboradores(): void { this.colaboradorLookup.loadNext(); }
  protected retryColaboradores(): void { this.colaboradorLookup.retry(); }

  private carregarBases() {
    if (!this.podeLerBases()) return;
    this.carregandoBases.set(true);
    this.rh.listarBasesOperacionais({
      ativo: true,
      page: 0,
      size: 100,
      sort: 'nome,asc'
    }).subscribe({
      next: (res) => {
        this.basesOperacionais.set(res.content.filter((base) => base.ativo));
        this.carregandoBases.set(false);
      },
      error: (err) => {
        this.carregandoBases.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Bases indisponíveis',
          detail: getErrorMessage(err)
        });
      }
    });
  }

  protected podePreencherChecklist(): boolean {
    return !!this.idProcesso && !!this.idBaseOperacional && !!this.dataRealizacao && this.inspecionados().length > 0;
  }

  /** Passos obrigatórios para liberar o checklist — usado no card de bloqueio. */
  protected requisitos(): { ok: boolean; label: string }[] {
    return [
      { ok: !!this.idProcesso, label: 'Selecionar o processo' },
      { ok: !!this.idBaseOperacional, label: 'Selecionar a base operacional' },
      { ok: !!this.dataRealizacao, label: 'Informar data e hora' },
      { ok: this.inspecionados().length > 0, label: 'Adicionar ao menos 1 colaborador' }
    ];
  }

  private capturarGeolocalizacao() {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        // Só usa o GPS automático se o usuário ainda não escolheu manualmente.
        if (!this.temLocalizacao()) {
          this.latitude.set(pos.coords.latitude);
          this.longitude.set(pos.coords.longitude);
        }
        this.geoOk.set(true);
      },
      () => this.geoOk.set(false),
      { enableHighAccuracy: true, timeout: 8000 }
    );
  }

  protected onSelecionarFormulario() {
    if (!this.idFormulario) return;
    this.carregandoForm.set(true);
    this.formulario.set(null);
    this.respostas = {};
    this.desvios = {};
    this.sst.buscarFormularioPorId(this.idFormulario).subscribe({
      next: (form) => {
        const grupos = (form.grupos ?? []).slice().sort((a, b) => a.ordem - b.ordem);
        grupos.forEach((g) => (g.perguntas = (g.perguntas ?? []).slice().sort((a, b) => a.ordem - b.ordem)));
        form.grupos = grupos;
        this.formulario.set(form);
        grupos.forEach((g) =>
          g.perguntas.forEach((p) => {
            const opcoesPadrao = isTipoObjetivo(p.tipoResposta)
              ? (p.opcoes ?? []).filter((o) => o.padrao).map((o) => o.id)
              : [];
            this.respostas[p.id] = { opcoesSelecionadas: opcoesPadrao, valorTexto: null, valorNumero: null, valorData: null };
          })
        );
        this.carregandoForm.set(false);
      },
      error: (err) => {
        this.carregandoForm.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erro ao carregar formulário', detail: getErrorMessage(err) });
      }
    });
  }

  protected onSelecionarEquipe() {
    if (!this.idEquipe) return;
    const equipe = this.equipes().find((e) => e.id === this.idEquipe);
    if (equipe?.baseOperacional?.id) {
      this.idBaseOperacional = equipe.baseOperacional.id;
    }
    if (!equipe?.membros?.length) return;
    const atuais = new Map(this.inspecionados().map((c) => [c.id, c]));
    const membros = equipe.membros
      .filter((m) => m.vinculoAtivo)
      .filter((m) => Boolean(m.idColaborador))
      .map((m) => ({
        value: m.idColaborador,
        label: this.nomeMembroEquipe(m.nomeColaborador, m.idColaborador)
      }));
    membros.forEach(membro =>
      atuais.set(membro.value, { id: membro.value, nome: membro.label })
    );
    this.colaboradorLookup.seedSelected(membros);
    this.inspecionados.set([...atuais.values()]);
  }

  protected onAdicionarColaborador(id: string | null): void {
    this.colaboradorParaAdicionarId = id;
    if (!id) return;

    const colaborador = this.colaboradores().find(item => item.value === id);
    if (!colaborador) {
      this.colaboradorParaAdicionarId = null;
      return;
    }

    if (!this.inspecionados().some(item => item.id === id)) {
      this.inspecionados.update(items => [...items, { id, nome: colaborador.label }]);
    }
    this.colaboradorLookup.seedSelected(colaborador);
    this.colaboradorParaAdicionarId = null;
    this.limparColaboradoresInvalidos();
  }

  private nomeMembroEquipe(nome: string | null | undefined, id: string): string {
    const normalizado = nome?.trim();
    if (normalizado && !/^(?:(?:null|undefined)\s*,?\s*)+$/i.test(normalizado)) {
      return normalizado;
    }
    return `Colaborador sem nome (${id.slice(0, 8)})`;
  }

  protected removerInspecionado(id: string) {
    this.inspecionados.set(this.inspecionados().filter((c) => c.id !== id));
    this.limparColaboradoresInvalidos();
  }

  private limparColaboradoresInvalidos() {
    const validos = new Set(this.inspecionadosIds());
    Object.values(this.desvios).forEach((dv) => {
      dv.colaboradores = dv.colaboradores.filter((id) => validos.has(id));
    });
  }

  private desvioKey(idPergunta: string, idOpcao: string) {
    return `${idPergunta}:${idOpcao}`;
  }

  protected isOpcaoSelecionada(idPergunta: string, idOpcao: string): boolean {
    return this.respostas[idPergunta]?.opcoesSelecionadas.includes(idOpcao) ?? false;
  }

  protected desvioRef(idPergunta: string, idOpcao: string): DesvioState | undefined {
    return this.desvios[this.desvioKey(idPergunta, idOpcao)];
  }

  protected toggleOpcao(pergunta: PerguntaResponse, idOpcao: string, geraDesvio: boolean) {
    const estado = this.respostas[pergunta.id];
    if (!estado) return;

    if (pergunta.tipoResposta === 'MULTIPLA_ESCOLHA') {
      const jaSelecionada = estado.opcoesSelecionadas.includes(idOpcao);
      pergunta.opcoes.forEach((o) => delete this.desvios[this.desvioKey(pergunta.id, o.id)]);
      estado.opcoesSelecionadas = jaSelecionada ? [] : [idOpcao];
      if (!jaSelecionada && geraDesvio) this.criarDesvio(pergunta.id, idOpcao);
    } else {
      const idx = estado.opcoesSelecionadas.indexOf(idOpcao);
      if (idx >= 0) {
        estado.opcoesSelecionadas.splice(idx, 1);
        delete this.desvios[this.desvioKey(pergunta.id, idOpcao)];
      } else {
        estado.opcoesSelecionadas.push(idOpcao);
        if (geraDesvio) this.criarDesvio(pergunta.id, idOpcao);
      }
    }
  }

  private criarDesvio(idPergunta: string, idOpcao: string) {
    const key = this.desvioKey(idPergunta, idOpcao);
    if (this.desvios[key]) return;
    this.desvios[key] = {
      idPergunta,
      idOpcaoResposta: idOpcao,
      descricao: '',
      acaoCorretiva: '',
      prazo: null,
      colaboradores: [],
      tratadoInLoco: false,
      arquivos: [],
      fotosPreview: [],
      anexosPreview: []
    };
  }

  protected onToggleTratadoInLoco(dv: DesvioState, value: boolean) {
    dv.tratadoInLoco = value;
    if (value && !dv.prazo) {
      dv.prazo = new Date(this.dataRealizacao);
    }
  }

  protected onAnexarArquivo(event: Event, dv: DesvioState) {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;
    if (!dv.arquivos) dv.arquivos = [];
    if (!dv.fotosPreview) dv.fotosPreview = [];
    if (!dv.anexosPreview) dv.anexosPreview = [];

    Array.from(input.files).forEach(file => {
      const erro = validarArquivoAnexo(file);
      if (erro) {
        this.messageService.add({ severity: 'warn', summary: 'Arquivo inválido', detail: `${file.name}: ${erro}` });
        return;
      }

      dv.arquivos!.push(file);

      const isImage = file.type.startsWith('image/') || /\.(jpg|jpeg|png|webp|heic|heif)$/i.test(file.name);
      const itemPreview: AnexoPreviewLocal = {
        file,
        isImage,
        nome: file.name,
        tamanho: this.formatarTamanho(file.size)
      };
      dv.anexosPreview!.push(itemPreview);

      if (isImage) {
        const reader = new FileReader();
        reader.onload = (e) => {
          if (e.target?.result) {
            const dataUrl = e.target.result as string;
            itemPreview.previewUrl = dataUrl;
            dv.fotosPreview = [...(dv.fotosPreview || []), dataUrl];
            this.cdr.detectChanges();
          }
        };
        reader.readAsDataURL(file);
      } else {
        this.cdr.detectChanges();
      }
    });
    input.value = '';
  }

  protected onAnexarFoto(event: Event, dv: DesvioState) {
    this.onAnexarArquivo(event, dv);
  }

  protected abrirPreviewFoto(foto: string) {
    this.fotoExpandida = foto;
    this.fotoExpandidaVisible = true;
  }

  protected removerAnexoPreview(dv: DesvioState, index: number) {
    if (dv.arquivos && dv.arquivos.length > index) {
      dv.arquivos.splice(index, 1);
    }
    if (dv.anexosPreview && dv.anexosPreview.length > index) {
      dv.anexosPreview.splice(index, 1);
      dv.anexosPreview = [...dv.anexosPreview];
    }
    if (dv.fotosPreview && dv.fotosPreview.length > index) {
      dv.fotosPreview.splice(index, 1);
      dv.fotosPreview = [...dv.fotosPreview];
    }
    this.cdr.detectChanges();
  }

  protected removerFotoPreview(dv: DesvioState, index: number) {
    this.removerAnexoPreview(dv, index);
  }

  protected formatarTamanho(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  protected getIconArquivo(nome: string): string {
    const lower = nome.toLowerCase();
    if (lower.endsWith('.pdf')) return 'pi pi-file-pdf text-red-500';
    if (lower.endsWith('.doc') || lower.endsWith('.docx')) return 'pi pi-file-word text-blue-600';
    return 'pi pi-file text-slate-600';
  }

  /**
   * Motivo pelo qual o lançamento está bloqueado — exibido no tooltip do botão.
   * Descobrir a obrigatoriedade só no `400` do backend, depois de todo o
   * preenchimento, seria o pior momento possível.
   */
  protected motivoBloqueioLancamento(): string {
    if (this.evidenciasUpload?.enviando()) return 'Aguarde o envio das fotos terminar.';
    if (!this.evidenciasUpload?.temEvidencia()) {
      return 'Anexe ao menos uma evidência fotográfica: toda inspeção precisa comprovar que aconteceu em campo.';
    }
    return '';
  }

  protected podeLancar(): boolean {
    return !!this.evidenciasUpload?.temEvidencia() && !this.evidenciasUpload?.enviando();
  }

  private validar(): string | null {
    if (!this.idFormulario) return 'Selecione um formulário.';
    if (!this.idTipoInspecao) return 'Selecione o tipo de inspeção.';
    if (!this.idProcesso) return 'Selecione o processo.';
    if (!this.idBaseOperacional) return 'Selecione a base operacional.';
    if (!this.dataRealizacao) return 'Informe a data/hora da inspeção.';
    if (this.inspecionados().length === 0) return 'Adicione ao menos um colaborador inspecionado.';

    const form = this.formulario();
    if (!form) return 'Formulário não carregado.';

    for (const grupo of form.grupos) {
      for (const pergunta of grupo.perguntas) {
        const estado = this.respostas[pergunta.id];
        if (!pergunta.obrigatoria) continue;
        if (isTipoObjetivo(pergunta.tipoResposta)) {
          if (estado.opcoesSelecionadas.length === 0) return `Responda: "${pergunta.enunciado}".`;
        } else if (pergunta.tipoResposta === 'TEXTO') {
          if (!estado.valorTexto?.trim()) return `Responda: "${pergunta.enunciado}".`;
        } else if (pergunta.tipoResposta === 'NUMERO') {
          if (estado.valorNumero === null || estado.valorNumero === undefined) return `Responda: "${pergunta.enunciado}".`;
        } else if (pergunta.tipoResposta === 'DATA') {
          if (!estado.valorData) return `Responda: "${pergunta.enunciado}".`;
        }
      }
    }

    for (const dv of Object.values(this.desvios)) {
      if (dv.colaboradores.length === 0) return 'Atribua ao menos um colaborador a cada desvio.';
      if (dv.tratadoInLoco && !dv.acaoCorretiva?.trim()) {
        return 'Informe a ação corretiva realizada no local para o desvio marcado como tratado in-loco.';
      }
    }

    const bloqueio = this.motivoBloqueioLancamento();
    if (bloqueio) return bloqueio;
    return null;
  }

  protected lancar() {
    const erro = this.validar();
    if (erro) {
      this.messageService.add({ severity: 'warn', summary: 'Revise a inspeção', detail: erro });
      return;
    }

    if (this.isModoEdicao()) {
      this.salvarEdicao();
      return;
    }

    const respostas = this.extrairRespostas();
    const desvios = this.extrairDesvios();

    const request: RegistrarInspecaoRequest = {
      idFormulario: this.idFormulario!,
      idTipoInspecao: this.idTipoInspecao!,
      idProcesso: this.idProcesso!,
      idVeiculo: this.idVeiculo || null,
      idEquipe: this.idEquipe || null,
      idBaseOperacional: this.idBaseOperacional!,
      dataRealizacao: this.dataRealizacao.toISOString(),
      observacoes: this.observacoes?.trim() || undefined,
      latitude: this.latitude(),
      longitude: this.longitude(),
      colaboradoresInspecionados: this.inspecionadosIds(),
      respostas,
      desvios,
      // As fotos já subiram em requisições próprias; aqui vão só os IDs.
      evidencias: this.evidenciasUpload?.ids() ?? []
    };

    this.enviarLancamento(request, true);
  }

  private extrairRespostas(): RespostaInspecaoRequest[] {
    const form = this.formulario()!;
    const respostas: RespostaInspecaoRequest[] = [];
    form.grupos.forEach((g) =>
      g.perguntas.forEach((p) => {
        const estado = this.respostas[p.id];
        const objetiva = isTipoObjetivo(p.tipoResposta);
        const temResposta =
          (objetiva && estado.opcoesSelecionadas.length > 0) ||
          (p.tipoResposta === 'TEXTO' && !!estado.valorTexto?.trim()) ||
          (p.tipoResposta === 'NUMERO' && estado.valorNumero !== null && estado.valorNumero !== undefined) ||
          (p.tipoResposta === 'DATA' && !!estado.valorData);
        if (!temResposta) return;
        respostas.push({
          idPergunta: p.id,
          opcoesSelecionadas: objetiva ? estado.opcoesSelecionadas : [],
          valorTexto: p.tipoResposta === 'TEXTO' ? estado.valorTexto : null,
          valorNumero: p.tipoResposta === 'NUMERO' ? estado.valorNumero : null,
          valorData: p.tipoResposta === 'DATA' ? this.formatDateOnly(estado.valorData) : null
        });
      })
    );
    return respostas;
  }

  private extrairDesvios(): DesvioInspecaoRequest[] {
    return Object.values(this.desvios).map((dv) => ({
      idPergunta: dv.idPergunta,
      idOpcaoResposta: dv.idOpcaoResposta,
      descricao: dv.descricao?.trim() || undefined,
      acaoCorretiva: dv.acaoCorretiva?.trim() || undefined,
      prazo: dv.prazo ? this.formatDateOnly(dv.prazo) : (dv.tratadoInLoco ? this.formatDateOnly(this.dataRealizacao) : null),
      colaboradores: dv.colaboradores,
      status: dv.tratadoInLoco ? 'RESOLVIDO' : 'ABERTO'
    }));
  }

  private salvarEdicao() {
    const id = this.idInspecaoEdicao();
    if (!id) return;

    const respostas = this.extrairRespostas();
    const desvios = this.extrairDesvios();

    const request: AtualizarInspecaoRequest = {
      idFormulario: this.idFormulario!,
      idTipoInspecao: this.idTipoInspecao!,
      idProcesso: this.idProcesso!,
      idBaseOperacional: this.idBaseOperacional!,
      idVeiculo: this.idVeiculo || null,
      idEquipe: this.idEquipe || null,
      dataRealizacao: this.dataRealizacao.toISOString(),
      observacoes: this.observacoes?.trim() || null,
      latitude: this.latitude(),
      longitude: this.longitude(),
      colaboradoresInspecionados: this.inspecionadosIds(),
      respostas,
      desvios,
      evidencias: this.evidenciasUpload?.ids() ?? [],
      justificativa: this.justificativaEdicao?.trim() || null
    };

    this.saving.set(true);
    this.sst.atualizarInspecao(id, request).subscribe({
      next: (res) => {
        this.saving.set(false);
        if (!res) {
          this.messageService.add({
            severity: 'info',
            summary: 'Salvo offline',
            detail: 'Sem conexão: as alterações foram enfileiradas.'
          });
          this.router.navigate(['/sst/inspecoes', id]);
          return;
        }

        const uploads: any[] = [];
        if (res.desvios && res.desvios.length > 0) {
          res.desvios.forEach((dAtualizado) => {
            const key = this.desvioKey(dAtualizado.idPergunta, dAtualizado.idOpcaoResposta);
            const dvLocal = this.desvios[key];
            if (dvLocal && dvLocal.arquivos && dvLocal.arquivos.length > 0) {
              dvLocal.arquivos.forEach((file) => {
                uploads.push(this.sst.enviarAnexo(res.id, dAtualizado.id, 'DESVIO', file));
              });
            }
          });
        }

        if (uploads.length > 0) {
          forkJoin(uploads).subscribe({
            next: () => {
              this.messageService.add({
                severity: 'success',
                summary: 'Sucesso',
                detail: 'Inspeção atualizada e novas evidências enviadas com sucesso.'
              });
              this.router.navigate(['/sst/inspecoes', res.id]);
            },
            error: (err) => {
              this.messageService.add({
                severity: 'warn',
                summary: 'Atenção',
                detail: 'Inspeção atualizada, mas houve erro no envio de algumas evidências: ' + getErrorMessage(err)
              });
              this.router.navigate(['/sst/inspecoes', res.id]);
            }
          });
        } else {
          this.messageService.add({
            severity: 'success',
            summary: 'Sucesso',
            detail: 'Inspeção atualizada com sucesso.'
          });
          this.router.navigate(['/sst/inspecoes', res.id]);
        }
      },
      error: (err) => {
        this.saving.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro ao atualizar',
          detail: getErrorMessage(err)
        });
      }
    });
  }

  /**
   * `podeReenviarEvidencias` permite uma única recuperação automática: um `404`
   * no lançamento significa que alguma foto foi expurgada (mais de 72 h pendente)
   * ou descartada. Os arquivos continuam em memória, então reenviamos e tentamos
   * de novo antes de tratar o 404 como base operacional inexistente.
   */
  private enviarLancamento(request: RegistrarInspecaoRequest, podeReenviarEvidencias: boolean) {
    this.saving.set(true);
    this.sst.registrarInspecao(request).subscribe({
      next: (res) => {
        this.saving.set(false);
        if (res === null) {
          this.messageService.add({
            severity: 'info',
            summary: 'Salvo offline',
            detail: 'Sem conexão: a inspeção foi enfileirada e será enviada automaticamente.'
          });
          this.router.navigate(['/sst/inspecoes']);
          return;
        }

        const uploads: any[] = [];
        if (res.desvios && res.desvios.length > 0) {
          res.desvios.forEach(dCriado => {
            const key = this.desvioKey(dCriado.idPergunta, dCriado.idOpcaoResposta);
            const dvLocal = this.desvios[key];
            if (dvLocal && dvLocal.arquivos && dvLocal.arquivos.length > 0) {
              dvLocal.arquivos.forEach(file => {
                uploads.push(this.sst.enviarAnexo(res.id, dCriado.id, 'DESVIO', file));
              });
            }
          });
        }

        if (uploads.length > 0) {
          forkJoin(uploads).subscribe({
            next: () => {
              this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Inspeção e evidências dos desvios registradas com sucesso.' });
              this.router.navigate(['/sst/inspecoes', res.id]);
            },
            error: (err) => {
              this.messageService.add({ severity: 'warn', summary: 'Atenção', detail: 'Inspeção salva, mas houve erro no envio de algumas evidências: ' + getErrorMessage(err) });
              this.router.navigate(['/sst/inspecoes', res.id]);
            }
          });
        } else {
          this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Inspeção lançada.' });
          this.router.navigate(['/sst/inspecoes', res.id]);
        }
      },
      error: (err) => {
        if (err?.status === 404 && podeReenviarEvidencias && this.evidenciasUpload) {
          this.messageService.add({
            severity: 'info',
            summary: 'Reenviando evidências',
            detail: 'As fotos expiraram no servidor; estão sendo reenviadas automaticamente.'
          });
          this.evidenciasUpload.reenviarTodas().then((ids) => {
            if (!ids.length) {
              this.saving.set(false);
              this.messageService.add({
                severity: 'error',
                summary: 'Erro ao lançar',
                detail: 'Não foi possível reenviar as evidências. Tente novamente.'
              });
              return;
            }
            this.enviarLancamento({ ...request, evidencias: ids }, false);
          });
          return;
        }

        this.saving.set(false);
        if (err?.status === 404) {
          this.idBaseOperacional = null;
          this.carregarBases();
        }
        this.messageService.add({ severity: 'error', summary: 'Erro ao lançar', detail: getErrorMessage(err) });
      }
    });
  }

  private formatDateOnly(date: Date | null): string | null {
    if (!date) return null;
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  protected voltar() {
    if (this.isModoEdicao()) {
      this.router.navigate(['/sst/inspecoes', this.idInspecaoEdicao()]);
    } else {
      this.router.navigate(['/sst/inspecoes']);
    }
  }
}
