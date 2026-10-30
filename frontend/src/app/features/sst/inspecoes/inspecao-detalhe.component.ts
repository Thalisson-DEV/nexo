import { DateMaskDirective } from '../../../shared/directives/date-mask.directive';
import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnInit, OnDestroy, ViewChild, signal, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { TextareaModule } from 'primeng/textarea';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { AccordionModule } from 'primeng/accordion';
import { DesvioAnexosComponent } from './desvio-anexos.component';
import { AnexoDesvioGaleria, EvidenciasInspecaoComponent } from './evidencias-inspecao.component';
import { InspecaoSegurancaHistoricoComponent } from './inspecao-seguranca-historico/inspecao-seguranca-historico.component';

import { SstService } from '../../../core/services/sst.service';
import { RhService } from '../../../core/services/rh.service';
import { AuthService } from '../../../core/services/auth.service';
import {
  AnexoDesvioResponse,
  AtualizarCabecalhoInspecaoRequest,
  DesvioSegurancaResponse,
  EvidenciaInspecaoResponse,
  FormularioInspecaoResponse,
  GRAVIDADE_BADGE,
  GRAVIDADE_LABELS,
  GravidadeDesvio,
  InspecaoSegurancaResponse,
  OpcaoRespostaResponse,
  PerguntaResponse,
  STATUS_DESVIO_BADGE,
  STATUS_DESVIO_LABELS,
  STATUS_INSPECAO_BADGE,
  STATUS_INSPECAO_LABELS,
  StatusDesvio,
  StatusInspecao,
  TipoInspecaoResponse,
  TratarDesvioRequest,
  isTipoObjetivo
} from '../../../core/models/sst.models';
import {
  BaseOperacionalResponse,
  ColaboradorResponse,
  EquipeResponse,
  ProcessoNegocioResponse,
  VeiculoResponse
} from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';

declare var L: any;

@Component({
  selector: 'app-sst-inspecao-detalhe',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DatePipe,
    ButtonModule,
    DialogModule,
    SelectModule,
    InputTextModule,
    DatePickerModule,
    TextareaModule,
    ToastModule,
    TooltipModule,
    ProgressSpinnerModule,
    AccordionModule,
    DesvioAnexosComponent,
    EvidenciasInspecaoComponent,
    InspecaoSegurancaHistoricoComponent,
    DateMaskDirective
  ],
  providers: [MessageService],
  template: `
    <div class="min-h-screen bg-white dark:bg-[var(--ces-canvas)] text-slate-900 dark:text-[var(--ces-foreground)] p-4 md:p-8 relative print:p-0 print:bg-white print:text-black">
      <p-toast></p-toast>

      @if (loading()) {
        <div class="flex flex-col items-center justify-center min-h-[600px] gap-4">
          <p-progressSpinner strokeWidth="4" fill="transparent" animationDuration=".5s"></p-progressSpinner>
          <span class="text-sm font-medium text-slate-500 dark:text-slate-400 animate-pulse">Carregando relatório...</span>
        </div>
      } @else if (inspecao(); as insp) {
        <div class="max-w-5xl mx-auto pb-24 print:max-w-full rise print:animate-none">
          <!-- Cabeçalho da empresa -->
          <div class="grid grid-cols-3 items-center border-b border-gray-200 dark:border-slate-800 pb-6 mb-6 print:pb-2 print:mb-2">
            <div class="flex justify-start">
              <img src="logo_vertical.svg" alt="Logo" class="h-16 print:h-14" onerror="this.style.display='none'">
            </div>
            <div class="text-center flex flex-col gap-1">
              <h1 class="text-xs font-medium text-slate-900 dark:text-white uppercase">NOME DA EMPRESA</h1>
              <p class="text-[9px] text-slate-600 dark:text-slate-400 font-medium">Saúde e Segurança do Trabalho (SST)</p>
              <p class="text-[9px] text-slate-600 dark:text-slate-400 font-medium">Serviços prestados à Concessionária</p>
            </div>
            <div class="flex justify-end">
              <img src="logo_vertical.svg" alt="Logo Cliente" class="h-16 print:h-14 grayscale opacity-30 dark:invert dark:opacity-40" onerror="this.style.display='none'">
            </div>
          </div>

          <!-- Título -->
          <div class="py-4 text-center border-y border-gray-100 dark:border-slate-800 my-4 print:py-2 print:my-2">
            <h2 class="text-base font-medium text-gray-800 dark:text-white tracking-tight uppercase">Relatório de Inspeção de Segurança</h2>
            <p class="text-[10px] text-gray-500 dark:text-slate-400 font-medium mt-1">Nº {{ insp.id.substring(0, 8) | uppercase }} · {{ insp.formulario.titulo }} · {{ insp.dataRealizacao | date: 'dd/MM/yyyy HH:mm' }}</p>
          </div>

          <!-- Dados gerais agrupados -->
          <div class="grid grid-cols-1 md:grid-cols-3 gap-5 mb-4 bg-slate-50/40 dark:bg-slate-800/40 p-4 rounded-xl border border-slate-200 dark:border-slate-800 print:grid-cols-3 print:gap-3 print:mb-2 print:p-2">
            <div class="flex flex-col gap-3">
              <div class="flex items-center gap-2 border-b border-slate-200 dark:border-slate-700 pb-2">
                <i class="pi pi-tag text-primary-700 dark:text-primary-400 text-xs"></i>
                <span class="text-[10px] font-medium text-gray-700 dark:text-slate-200 uppercase tracking-widest">Identificação</span>
              </div>
              <div class="kv"><span class="kv-l">Tipo</span><span class="kv-v">{{ insp.tipoInspecao?.nome || '-' }}</span></div>
              <div class="kv"><span class="kv-l">Processo</span><span class="kv-v">{{ insp.processo.nome || '-' }}</span></div>
              <div class="kv"><span class="kv-l">Inspetor</span><span class="kv-v">{{ insp.nomeInspetor || '-' }}</span></div>
              <div class="kv">
                <span class="kv-l">Status</span>
                <span class="text-[10px] font-medium px-2 py-1 rounded-md uppercase tracking-tight" [ngClass]="statusInspBadge(insp.status)">{{ statusInspLabel(insp.status) }}</span>
              </div>
            </div>
            <div class="flex flex-col gap-3 md:border-x md:border-slate-200 dark:md:border-slate-700 md:px-6">
              <div class="flex items-center gap-2 border-b border-slate-200 dark:border-slate-700 pb-2">
                <i class="pi pi-sitemap text-primary-700 dark:text-primary-400 text-xs"></i>
                <span class="text-[10px] font-medium text-gray-700 dark:text-slate-200 uppercase tracking-widest">Contexto</span>
              </div>
              <div class="kv"><span class="kv-l">Equipe</span><span class="kv-v">{{ insp.equipe?.codigo || 'Individual' }}</span></div>
              <div class="kv"><span class="kv-l">Base</span><span class="kv-v">{{ insp.baseOperacional?.nome || 'Não identificada' }}</span></div>
              <div class="kv"><span class="kv-l">Veículo</span><span class="kv-v">{{ insp.veiculo?.placa || '-' }}</span></div>
              <div class="kv"><span class="kv-l">Realizada</span><span class="kv-v">{{ insp.dataRealizacao | date: 'dd/MM/yy HH:mm' }}</span></div>
            </div>
            <div class="flex flex-col gap-3">
              <div class="flex items-center gap-2 border-b border-slate-200 dark:border-slate-700 pb-2">
                <i class="pi pi-chart-bar text-primary-700 dark:text-primary-400 text-xs"></i>
                <span class="text-[10px] font-medium text-gray-700 dark:text-slate-200 uppercase tracking-widest">Resumo</span>
              </div>
              <div class="kv"><span class="kv-l">Inspecionados</span><span class="kv-v">{{ insp.colaboradoresInspecionados.length }}</span></div>
              <div class="kv">
                <span class="kv-l">Desvios</span>
                <span class="kv-v" [ngClass]="{ 'text-red-600 dark:text-red-400 font-bold': resumo().desvios > 0 }">{{ insp.desvios.length }}</span>
              </div>
              <div class="kv"><span class="kv-l">Respostas</span><span class="kv-v">{{ insp.respostas.length }}</span></div>
              <div class="flex flex-col gap-1.5 pt-2 mt-0.5 border-t border-slate-200 dark:border-slate-700">
                <div class="flex items-center justify-between">
                  <span class="kv-l">Conformidade</span>
                  <span class="text-[11px] font-medium" [ngClass]="conformidadeTom().texto">{{ resumo().conformidade }}%</span>
                </div>
                <div class="h-1.5 w-full rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden" style="-webkit-print-color-adjust: exact; print-color-adjust: exact;">
                  <div class="h-full rounded-full transition-[width] duration-700 ease-out" [ngClass]="conformidadeTom().barra" [style.width.%]="resumo().conformidade"></div>
                </div>
              </div>
            </div>
          </div>

          @if (insp.observacoes) {
            <div class="mb-4 flex flex-col gap-1 px-1">
              <span class="text-[9px] font-medium text-gray-400 dark:text-slate-400 uppercase tracking-widest">Observações</span>
              <p class="text-[11px] text-gray-600 dark:text-slate-300 leading-relaxed">{{ insp.observacoes }}</p>
            </div>
          }

          <!-- Colaboradores -->
          <div class="mb-6 px-1 print:mb-2">
            <div class="flex items-center gap-2 mb-3 border-l-2 border-primary-700 dark:border-primary-500 pl-3 print:mb-1.5">
              <span class="text-[11px] font-medium uppercase tracking-tight text-gray-800 dark:text-slate-100">Colaboradores Inspecionados</span>
            </div>
            <div class="flex flex-wrap gap-2">
              @for (c of insp.colaboradoresInspecionados; track c.id) {
                <span class="inline-flex items-center gap-1.5 bg-slate-50 dark:bg-slate-800/80 border border-slate-100 dark:border-slate-700 text-gray-600 dark:text-slate-200 text-[11px] font-medium px-2.5 py-1 rounded-md"><i class="pi pi-user text-[9px] text-primary-400"></i>{{ c.nomeCompleto }}</span>
              }
            </div>
          </div>

          <!-- Respostas -->
          <div class="mb-8 print:mb-3">
            <div class="flex items-center gap-2 mb-4 pl-1 border-l-4 border-primary-700 dark:border-primary-500 pl-3 print:mb-2">
              <span class="text-sm font-medium uppercase tracking-tight text-slate-800 dark:text-slate-100 print:text-[11px]">Checklist Preenchido</span>
            </div>

            @if (formulario()) {
              <div class="flex flex-col gap-4 print:gap-3">
                @for (grupo of formulario()!.grupos; track grupo.id; let gi = $index) {
                  <div class="bg-white dark:bg-slate-900/60 rounded-xl border border-slate-200 dark:border-slate-800 overflow-hidden break-inside-avoid mb-6 print:mb-3 print:overflow-visible print:[break-inside:auto]">
                    <div class="bg-slate-100 dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700/80 px-5 py-3.5 print:py-2">
                      <span class="text-sm font-medium text-slate-800 dark:text-white print:text-[11px]">{{ gi + 1 < 10 ? '0' + (gi + 1) : (gi + 1) }}. {{ grupo.titulo }}</span>
                    </div>
                    <div class="flex flex-col divide-y divide-slate-100 dark:divide-slate-800">
                      @for (pergunta of grupo.perguntas; track pergunta.id; let pi = $index) {
                        <div class="px-5 py-5 flex flex-col gap-2.5 md:flex-row md:items-start md:gap-6 print:flex-row print:items-start print:px-4 print:py-2.5 print:gap-6 print:break-inside-avoid" [ngClass]="{'bg-white dark:bg-slate-900/40': !respostaDesvio(pergunta), 'bg-red-50/40 dark:bg-red-950/25 dark:border-l-2 dark:border-l-red-500/70': respostaDesvio(pergunta)}">
                          <!-- Enunciado: 70% à esquerda -->
                          <div class="flex items-start gap-2 md:basis-[70%] md:shrink-0 print:basis-[70%] print:shrink-0">
                            <i class="pi mt-0.5 text-xs shrink-0" [ngClass]="respostaDesvio(pergunta) ? 'pi-exclamation-circle text-red-500 dark:text-red-400' : 'pi-check-circle text-slate-400 dark:text-slate-500'"></i>
                            <span class="text-xs font-medium text-slate-800 dark:text-slate-200 leading-snug print:text-[10.5px] print:leading-snug" [ngClass]="{'text-red-800 dark:text-red-300': respostaDesvio(pergunta)}">
                              {{ pi + 1 }}. {{ pergunta.enunciado }}
                            </span>
                          </div>
                          <!-- Resposta/opções: 30% à direita -->
                          <div class="md:basis-[30%] md:shrink-0 print:basis-[30%] print:shrink-0">
                            @if (isObjetivo(pergunta.tipoResposta)) {
                              <div class="flex flex-col gap-2 ml-6 mt-1 md:ml-0 md:mt-0 print:ml-0 print:mt-0 print:gap-1.5">
                                @for (opcao of pergunta.opcoes; track opcao.id) {
                                  <div class="flex items-center gap-2.5 text-left print:gap-1.5"
                                       [ngClass]="isOpcaoSelecionada(pergunta.id, opcao.id) ? (opcao.geraDesvio ? 'text-red-700 dark:text-red-300 font-medium' : 'text-primary-700 dark:text-primary-300 font-medium') : 'text-slate-600 dark:text-slate-400'">
                                    <div class="w-3.5 h-3.5 rounded-sm border shrink-0 flex-none print:w-3 print:h-3"
                                         style="-webkit-print-color-adjust: exact; print-color-adjust: exact;"
                                         [ngClass]="isOpcaoSelecionada(pergunta.id, opcao.id) ? (opcao.geraDesvio ? 'border-red-500 bg-red-500 dark:border-red-400 dark:bg-red-500' : 'border-primary-700 bg-primary-700 dark:border-primary-400 dark:bg-primary-500') : 'border-slate-300 bg-white dark:border-slate-600 dark:bg-slate-800'">
                                    </div>
                                    <span class="text-xs leading-snug print:text-[10.5px]">{{ opcao.texto }}</span>
                                  </div>
                                }
                              </div>
                            } @else {
                              <div class="ml-7 mt-1 md:ml-0 md:mt-0 print:ml-0 print:mt-0">
                                <span class="text-xs px-3 py-1.5 rounded-md border inline-block print:text-[10.5px] print:px-2.5 print:py-1"
                                      [ngClass]="respostaDesvio(pergunta) ? 'bg-red-50 text-red-700 border-red-200 dark:bg-red-950/50 dark:text-red-300 dark:border-red-800/80' : 'bg-slate-50 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-200 dark:border-slate-700'">
                                  {{ respostaTexto(pergunta) }}
                                </span>
                              </div>
                            }
                          </div>
                        </div>
                      }
                    </div>
                  </div>
                }
              </div>
            } @else {
              <div class="p-8 text-center border border-dashed border-slate-300 dark:border-slate-700 rounded-xl bg-slate-50 dark:bg-slate-800/40">
                <i class="pi pi-file-excel text-3xl text-slate-300 dark:text-slate-600 mb-2 block"></i>
                <span class="text-[11px] text-slate-500 dark:text-slate-400 font-medium">Não foi possível carregar as respostas detalhadas.</span>
              </div>
            }
          </div>

          <!-- Desvios -->
          <div class="mb-6 px-1">
            <div class="flex items-center gap-2 mb-3 border-l-2 border-red-400 dark:border-red-500 pl-3">
              <span class="text-[11px] font-medium uppercase tracking-tight text-gray-800 dark:text-slate-100">Desvios</span>
              <span class="text-[9px] text-slate-500 dark:text-slate-400 font-medium">({{ insp.desvios.length }})</span>
              @if (resumo().pendentes > 0) {
                <span class="ml-auto inline-flex items-center gap-1 text-[9px] font-medium uppercase tracking-tight text-amber-700 bg-amber-50 border border-amber-200 dark:bg-amber-950/50 dark:text-amber-300 dark:border-amber-800/60 px-2 py-0.5 rounded-full print:border-amber-300">
                  <i class="pi pi-clock text-[8px]"></i> {{ resumo().pendentes }} pendente{{ resumo().pendentes === 1 ? '' : 's' }}
                </span>
              }
            </div>
            @if (insp.desvios.length === 0) {
              <div class="flex items-center gap-2 text-[11px] text-slate-500 dark:text-slate-400"><i class="pi pi-check-circle text-emerald-500 dark:text-emerald-400"></i> Inspeção sem não-conformidades.</div>
            } @else {
              <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                @for (desvio of insp.desvios; track desvio.id) {
                  <div class="rounded-lg border border-l-[3px] px-4 py-3 flex flex-col gap-2.5 transition-colors"
                       [ngClass]="prazoVencido(desvio) ? 'border-red-200 bg-red-50/30 dark:border-red-900/60 dark:bg-red-950/25' : 'border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900/60 hover:border-slate-300 dark:hover:border-slate-700'"
                       [ngStyle]="{ 'border-left-color': gravidadeCor(desvio.gravidade) }"
                       style="-webkit-print-color-adjust: exact; print-color-adjust: exact;">
                    <div class="flex items-center justify-between gap-2 flex-wrap">
                      <div class="flex items-center gap-2">
                        <span class="text-[10px] font-medium uppercase tracking-tight px-2 py-1 rounded-md" [ngClass]="gravidadeBadge(desvio.gravidade)">{{ gravidadeLabel(desvio.gravidade) }}</span>
                        <span class="text-[10px] font-medium uppercase tracking-tight px-2 py-1 rounded-md" [ngClass]="statusDesvioBadge(desvio.status)">{{ statusDesvioLabel(desvio.status) }}</span>
                      </div>
                      @if (podeTratar()) {
                        <button type="button" (click)="abrirTratamento(desvio)" class="flex items-center gap-1 text-[10px] font-medium text-primary-700 dark:text-primary-400 uppercase tracking-widest cursor-pointer print:hidden hover:text-primary-600 dark:hover:text-primary-300"><i class="pi pi-pencil text-[9px]"></i> Tratar</button>
                      }
                    </div>
                    <span class="text-[10px] text-slate-500 dark:text-slate-400">{{ perguntaEnunciado(desvio.idPergunta) }} · <span class="text-slate-700 dark:text-slate-200 font-medium">{{ opcaoTexto(desvio.idOpcaoResposta) }}</span></span>
                    @if (desvio.descricao) { <p class="text-[11px] text-gray-700 dark:text-slate-200 leading-relaxed">{{ desvio.descricao }}</p> }
                    @if (desvio.acaoCorretiva) {
                      <div class="text-[11px] text-gray-500 dark:text-slate-300"><span class="text-[9px] font-medium text-gray-400 dark:text-slate-400 uppercase tracking-widest">Ação corretiva</span><br>{{ desvio.acaoCorretiva }}</div>
                    }
                    <div class="text-[11px]">
                      @if (desvio.prazo) {
                        <span [ngClass]="prazoVencido(desvio) ? 'text-red-500 dark:text-red-400 font-medium' : 'text-gray-500 dark:text-slate-400'"><i class="pi pi-calendar text-[10px]"></i> Prazo {{ desvio.prazo | date: 'dd/MM/yyyy' }}@if (prazoVencido(desvio)) { <span class="text-[9px] uppercase tracking-widest"> · vencido</span> }</span>
                      } @else { <span class="text-gray-400 dark:text-slate-500"><i class="pi pi-calendar text-[10px]"></i> Sem prazo</span> }
                      @if (desvio.dataResolucao) {
                        <span class="ml-3 text-emerald-700 dark:text-emerald-400"><i class="pi pi-check-circle text-[10px]"></i> Resolvido em {{ desvio.dataResolucao | date: 'dd/MM/yyyy HH:mm' }}</span>
                      }
                    </div>
                    @if (desvio.colaboradores.length) {
                      <div class="flex flex-wrap gap-1.5">
                        @for (c of desvio.colaboradores; track c.id) {
                          <span class="inline-flex items-center gap-1 border border-slate-200 dark:border-slate-700 bg-slate-50/50 dark:bg-slate-800/60 text-gray-500 dark:text-slate-300 text-[10px] font-medium px-2 py-0.5 rounded"><i class="pi pi-user text-[8px]"></i>{{ c.nomeCompleto }}</span>
                        }
                      </div>
                    }
                    <div class="border-t border-slate-100 dark:border-slate-800 pt-2.5 mt-0.5 print:hidden">
                      <app-desvio-anexos
                        [idInspecao]="insp.id"
                        [idDesvio]="desvio.id"
                        [anexos]="desvio.anexos || []"
                        [canWrite]="podeTratar()"
                        (anexosChange)="onAnexoMudado(desvio, $event)"
                      ></app-desvio-anexos>
                    </div>
                  </div>
                }
              </div>
            }
          </div>

          <!--
            Galeria única: fotos que comprovam a realização da inspeção + anexos
            dos desvios, cada card marcado com a sua origem. Entra na impressão —
            a foto é o que dá valor probatório ao relatório; só os controles de
            edição saem, pelas classes print:hidden.
          -->
          <app-evidencias-inspecao
            [idInspecao]="insp.id"
            [evidencias]="insp.evidencias || []"
            [anexosDesvios]="anexosDesviosGaleria()"
            [canWrite]="podeTratar()"
            (evidenciasChange)="onEvidenciasMudadas($event)"
          ></app-evidencias-inspecao>

          <!-- Geolocalização (oculta por completo na impressão) -->
          @if (insp.latitude != null && insp.longitude != null) {
            <div class="mb-6 px-1 print:hidden">
              <div class="flex items-center gap-2 mb-3 border-l-2 border-slate-400 dark:border-slate-600 pl-3">
                <span class="text-[11px] font-medium uppercase tracking-tight text-gray-800 dark:text-slate-100">Geolocalização</span>
              </div>
              <div class="text-[11px] text-gray-600 dark:text-slate-300 font-medium mb-3 flex items-center gap-2"><i class="pi pi-map-marker text-primary-700 dark:text-primary-400"></i> Lat: {{ insp.latitude }} · Lon: {{ insp.longitude }}</div>
              <div class="w-full h-[400px] rounded-2xl overflow-hidden border border-slate-200 dark:border-slate-800 bg-slate-100 dark:bg-slate-900 isolate">
                <div id="map-inspecao" class="w-full h-full"></div>
              </div>
            </div>
          }
          <!-- HISTÓRICO & AUDITORIA — OCULTO POR PADRÃO (SANFONA - OCULTO NA IMPRESSÃO) -->
          <div class="mb-6 px-1 print:hidden" id="secao-historico">
            <p-accordion [value]="timelineValue()" (valueChange)="timelineValue.set($event)">
              <p-accordion-panel value="timeline">
                <p-accordion-header>
                  <div class="flex items-center justify-between w-full gap-3 min-w-0 pr-2">
                    <div class="flex items-center gap-2.5 min-w-0">
                      <div class="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-800 flex items-center justify-center shrink-0">
                        <i class="pi pi-history text-slate-600 dark:text-slate-300 text-sm"></i>
                      </div>
                      <div class="flex flex-col gap-0.5 min-w-0 text-left">
                        <span class="text-xs md:text-sm font-bold text-slate-900 dark:text-slate-100">Linha do tempo de alterações</span>
                        <span class="text-xs text-slate-600 dark:text-slate-300 font-normal"
                          >Veja todas as ações realizadas nesta inspeção em ordem cronológica.</span
                        >
                      </div>
                    </div>
                  </div>
                </p-accordion-header>
                <p-accordion-content>
                  @if (isTimelineOpen()) {
                    <div class="pt-3">
                      <app-inspecao-seguranca-historico [idInspecao]="insp.id" [ocultarCabecalho]="true" #historicoComp></app-inspecao-seguranca-historico>
                    </div>
                  }
                </p-accordion-content>
              </p-accordion-panel>
            </p-accordion>
          </div>
        </div>

        <!-- Ações Finais (Flutuantes) -->
        <div class="fixed bottom-6 right-6 md:bottom-8 md:right-8 flex flex-col-reverse sm:flex-row items-end sm:items-center gap-3 z-30 print:hidden">
          <div class="flex items-center gap-3 flex-wrap justify-end">
            @if (podeEditarCabecalho()) {
              <p-button
                label="Editar Inspeção"
                icon="pi pi-pencil"
                (onClick)="navegarParaEdicao()"
                styleClass="!rounded-xl !bg-amber-600 hover:!bg-amber-500 !border-amber-600 !text-white !font-medium !text-[11px] !px-5 !py-3 shadow-sm md:flex hidden"
              ></p-button>
            }
            <p-button
              label="Histórico"
              icon="pi pi-history"
              (onClick)="rolarParaHistorico()"
              styleClass="!rounded-xl !bg-slate-700 dark:!bg-slate-800 !border-slate-700 dark:!border-slate-700 hover:!bg-slate-600 !text-white !font-medium !text-[11px] !px-5 !py-3 shadow-sm md:flex hidden"
            ></p-button>
            <p-button
              label="Imprimir"
              icon="pi pi-print"
              (onClick)="printReport()"
              styleClass="!rounded-xl !bg-slate-700 dark:!bg-slate-800 !border-slate-700 dark:!border-slate-700 hover:!bg-slate-600 hover:!border-slate-600 !text-white !font-medium !text-[11px] !px-6 !py-3 shadow-sm md:flex hidden"
            ></p-button>
            <p-button
              label="Voltar"
              icon="pi pi-arrow-left"
              [text]="true"
              severity="secondary"
              (onClick)="voltar()"
              styleClass="!rounded-xl !bg-white dark:!bg-slate-800 !border-slate-200 dark:!border-slate-700 !text-slate-600 dark:!text-slate-300 !font-medium !text-[11px] !px-6 !py-3 shadow-sm md:flex hidden"
            ></p-button>
          </div>
        </div>
      } @else {
        <div class="flex flex-col items-center justify-center gap-3 py-24">
          <i class="pi pi-exclamation-circle text-3xl text-slate-300 dark:text-slate-600"></i>
          <span class="text-[12px] font-medium text-gray-600 dark:text-slate-300">Inspeção não encontrada</span>
          <p-button label="Voltar à lista" icon="pi pi-arrow-left" [text]="true" (onClick)="voltar()" styleClass="!text-[10px] !font-medium"></p-button>
        </div>
      }

    <!-- Dialog de edição de cabeçalho -->
    <p-dialog
      [(visible)]="dialogEdicaoCabecalho"
      [modal]="true"
      [style]="{ width: '95vw', maxWidth: '780px' }"
      [draggable]="false"
      [resizable]="false"
      appendTo="body"
      header="Editar Cabeçalho da Inspeção"
    >
      <div class="flex flex-col gap-4 mt-2 max-h-[75vh] overflow-y-auto pr-1">
        <div class="bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900/50 rounded-lg p-3 text-[11px] text-amber-800 dark:text-amber-300">
          <i class="pi pi-info-circle mr-1"></i>
          Apenas o cabeçalho e colaboradores podem ser editados pelo criador do registro. O checklist de perguntas, respostas e desvios permanecem inalterados. Toda edição é registrada no histórico auditado.
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div class="flex flex-col gap-1.5">
            <label class="dl">Tipo de Inspeção <span class="text-red-500">*</span></label>
            <p-select [options]="tiposInspecao()" [(ngModel)]="edicaoIdTipoInspecao" optionLabel="nome" optionValue="id" placeholder="Selecione" class="doc-select" size="small" appendTo="body" [filter]="true"></p-select>
          </div>
          <div class="flex flex-col gap-1.5">
            <label class="dl">Processo <span class="text-red-500">*</span></label>
            <p-select [options]="processos()" [(ngModel)]="edicaoIdProcesso" optionLabel="nome" optionValue="id" placeholder="Selecione" class="doc-select" size="small" appendTo="body" [filter]="true"></p-select>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div class="flex flex-col gap-1.5">
            <label class="dl">Base Operacional <span class="text-red-500">*</span></label>
            <p-select [options]="basesOperacionais()" [(ngModel)]="edicaoIdBaseOperacional" optionLabel="nome" optionValue="id" placeholder="Selecione" class="doc-select" size="small" appendTo="body" [filter]="true"></p-select>
          </div>
          <div class="flex flex-col gap-1.5">
            <label class="dl">Data / Hora de Realização <span class="text-red-500">*</span></label>
            <p-datepicker [(ngModel)]="edicaoDataRealizacao" [showTime]="true" hourFormat="24" dateFormat="dd/mm/yy" dateMask [showIcon]="true" iconDisplay="input" placeholder="dd/mm/aaaa hh:mm" appendTo="body" size="small" styleClass="doc-date w-full"></p-datepicker>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div class="flex flex-col gap-1.5">
            <label class="dl">Veículo</label>
            <p-select [options]="veiculos()" [(ngModel)]="edicaoIdVeiculo" optionLabel="placa" optionValue="id" placeholder="Selecione ou deixe em branco" class="doc-select" size="small" appendTo="body" [filter]="true" [showClear]="true"></p-select>
          </div>
          <div class="flex flex-col gap-1.5">
            <label class="dl">Equipe</label>
            <p-select [options]="equipes()" [(ngModel)]="edicaoIdEquipe" optionLabel="codigo" optionValue="id" placeholder="Individual ou selecione equipe" class="doc-select" size="small" appendTo="body" [filter]="true" [showClear]="true" (onChange)="onSelecionarEquipeEdicao()"></p-select>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div class="flex flex-col gap-1.5">
            <label class="dl">Latitude (opcional)</label>
            <input pInputText type="number" step="any" [(ngModel)]="edicaoLatitude" placeholder="Ex: -12.9714" class="doc-input" />
          </div>
          <div class="flex flex-col gap-1.5">
            <label class="dl">Longitude (opcional)</label>
            <input pInputText type="number" step="any" [(ngModel)]="edicaoLongitude" placeholder="Ex: -38.5014" class="doc-input" />
          </div>
        </div>

        <div class="flex flex-col gap-1.5">
          <label class="dl">Observações</label>
          <textarea pTextarea [(ngModel)]="edicaoObservacoes" rows="2" placeholder="Observações gerais da inspeção..." class="w-full text-xs p-2 rounded-md border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900"></textarea>
        </div>

        <!-- Colaboradores Inspecionados -->
        <div class="rounded-xl border border-slate-200 dark:border-slate-800 p-4 flex flex-col gap-3 bg-slate-50/50 dark:bg-slate-800/30">
          <div class="flex items-center justify-between">
            <label class="dl flex items-center gap-1.5 text-slate-700 dark:text-slate-200 font-semibold">
              <i class="pi pi-users text-primary-700 dark:text-primary-400"></i>
              Colaboradores Inspecionados <span class="text-red-500">*</span>
            </label>
            <span class="text-[10px] text-slate-500">Total: {{ edicaoColaboradores().length }}</span>
          </div>

          <div class="flex gap-2 items-center">
            <div class="flex-1">
              <p-select [options]="colaboradoresDisponiveis()" [(ngModel)]="colaboradorSelecionadoParaAdicionar" optionLabel="nome" placeholder="Selecione um colaborador para adicionar..." class="doc-select w-full" size="small" [filter]="true" appendTo="body"></p-select>
            </div>
            <p-button label="Adicionar" icon="pi pi-plus" size="small" (onClick)="adicionarColaboradorEdicao()" [disabled]="!colaboradorSelecionadoParaAdicionar" styleClass="!text-[10px] !bg-primary-700 !border-primary-700 !text-white"></p-button>
          </div>

          <div class="flex flex-wrap gap-2 mt-1">
            @for (colab of edicaoColaboradores(); track colab.id) {
              <div class="inline-flex items-center gap-1.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 text-[11px] font-medium px-2.5 py-1 rounded-md">
                <i class="pi pi-user text-[10px] text-primary-500"></i>
                <span>{{ colab.nome }}</span>
                @if (colaboradorPossuiDesvio(colab.id)) {
                  <span class="text-amber-500 ml-1" pTooltip="Possui desvio vinculado e não pode ser removido" tooltipPosition="top"><i class="pi pi-lock text-[10px]"></i></span>
                } @else {
                  <button type="button" (click)="removerColaboradorEdicao(colab.id)" class="ml-1 text-slate-400 hover:text-red-500 cursor-pointer" pTooltip="Remover"><i class="pi pi-times text-[10px]"></i></button>
                }
              </div>
            }
            @if (edicaoColaboradores().length === 0) {
              <span class="text-[11px] text-red-500 italic">É obrigatório selecionar ao menos um colaborador inspecionado.</span>
            }
          </div>
        </div>

        <!-- Justificativa da Alteração -->
        <div class="flex flex-col gap-1.5">
          <label class="dl font-semibold text-slate-800 dark:text-slate-200">
            Justificativa da Alteração <span class="text-red-500">*</span>
          </label>
          <textarea pTextarea [(ngModel)]="justificativaEdicao" rows="3" placeholder="Descreva o motivo desta alteração no cabeçalho (mínimo 5 caracteres)..." class="w-full text-xs p-2.5 rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900"></textarea>
          <span class="text-[10px] text-slate-400">Esta justificativa ficará permanentemente registrada no histórico auditado da inspeção.</span>
        </div>
      </div>

      <ng-template pTemplate="footer">
        <div class="flex justify-end gap-2">
          <p-button label="Cancelar" [text]="true" severity="secondary" (onClick)="dialogEdicaoCabecalho = false" styleClass="!text-[10px] !font-medium"></p-button>
          <p-button label="Salvar Alterações" icon="pi pi-check" [loading]="salvandoCabecalho()" [disabled]="!formularioEdicaoValido()" (onClick)="salvarEdicaoCabecalho()" styleClass="!rounded-lg !bg-amber-600 !border-amber-600 hover:!bg-amber-700 !text-white !font-medium !text-[10px] !px-6 !py-2.5"></p-button>
        </div>
      </ng-template>
    </p-dialog>
    <!-- Dialog de tratamento -->
    <p-dialog [(visible)]="dialogTratamento" [modal]="true" [style]="{ width: '95vw', maxWidth: '460px' }" [draggable]="false" [resizable]="false" appendTo="body" [styleClass]="'custom-dialog dialog-tratamento-' + (tratamento.status || 'ABERTO').toLowerCase()">
      <ng-template #header>
        <div class="flex items-center gap-2.5 font-bold select-none">
          @if (tratamento.status === 'RESOLVIDO') {
            <i class="pi pi-check text-xl"></i>
          } @else if (tratamento.status === 'EM_TRATAMENTO') {
            <i class="pi pi-clock text-xl"></i>
          } @else {
            <i class="pi pi-times text-xl"></i>
          }
          <span class="text-base tracking-wide">Tratar desvio</span>
        </div>
      </ng-template>

      <div class="flex flex-col gap-4 mt-1">
        <div class="flex flex-col gap-1.5">
          <label class="dl">Status</label>
          <p-select [options]="statusDesvioOptions" [(ngModel)]="tratamento.status" optionLabel="label" optionValue="value" placeholder="Selecione" class="doc-select" size="small" appendTo="body" [disabled]="desvioInicialmenteResolvido">
            <ng-template #selectedItem let-selectedOption>
              @if (selectedOption) {
                <div class="flex items-center gap-2">
                  <i [class]="selectedOption.icon" [ngClass]="{
                    'text-emerald-600': selectedOption.value === 'RESOLVIDO',
                    'text-amber-500': selectedOption.value === 'EM_TRATAMENTO',
                    'text-red-600': selectedOption.value === 'ABERTO'
                  }"></i>
                  <span>{{ selectedOption.label }}</span>
                </div>
              }
            </ng-template>
            <ng-template #item let-option>
              <div class="flex items-center gap-2">
                <i [class]="option.icon" [ngClass]="{
                  'text-emerald-600': option.value === 'RESOLVIDO',
                  'text-amber-500': option.value === 'EM_TRATAMENTO',
                  'text-red-600': option.value === 'ABERTO'
                }"></i>
                <span>{{ option.label }}</span>
              </div>
            </ng-template>
          </p-select>
        </div>
        <div class="flex flex-col gap-1.5">
          <label class="dl">Ação corretiva</label>
          <input pInputText [(ngModel)]="tratamento.acaoCorretiva" placeholder="Atualize a ação (opcional)" class="doc-input" />
        </div>
        <div class="flex flex-col gap-1.5">
          <label class="dl">Prazo</label>
          <p-datepicker [(ngModel)]="tratamentoPrazo" dateFormat="dd/mm/yy" dateMask [showIcon]="true" iconDisplay="input" [showButtonBar]="true" placeholder="dd/mm/aaaa" appendTo="body" size="small" styleClass="doc-date"></p-datepicker>
        </div>
        <div class="flex justify-end gap-2 mt-2">
          <p-button label="Cancelar" [text]="true" severity="secondary" (onClick)="dialogTratamento = false" styleClass="!text-[10px] !font-medium"></p-button>
          <p-button label="Salvar" icon="pi pi-check" [loading]="salvandoTratamento()" [disabled]="!tratamento.status" (onClick)="salvarTratamento()" styleClass="!rounded-lg !bg-primary-700 !border-primary-700 !text-white !font-medium !text-[10px] !px-6 !py-2.5"></p-button>
        </div>
      </div>
    </p-dialog>
  </div>
  `,
  styles: [`
    :host ::ng-deep {
      .doc-input.p-inputtext, .doc-select .p-select-label, .doc-select .p-select-placeholder, .doc-date .p-inputtext {
        font-size: 11px !important; font-weight: 500 !important; color: inherit; height: 34px !important;
        display: flex; align-items: center; border-radius: 6px !important;
      }
      .doc-input.p-inputtext { width: 100%; }
      .doc-select .p-select, .doc-date .p-datepicker { width: 100%; }
      .doc-select .p-select { height: 34px; border-radius: 6px !important; }
    }
    .dl { font-size: 10px; font-weight: 500; color: #475569; text-transform: capitalize; letter-spacing: 0.08em; }
    .kv { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
    .kv-l { font-size: 10px; font-weight: 500; color: #475569; }
    .kv-v { font-size: 11px; font-weight: 500; color: #1e293b; text-align: right; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

    :host-context(.app-dark),
    :host-context(.dark) {
      .kv-l { color: #94a3b8; }
      .kv-v { color: #f8fafc; }
      .dl { color: #94a3b8; }

      #map-inspecao .leaflet-tile {
        filter: brightness(0.6) invert(1) contrast(3) hue-rotate(200deg) saturate(0.3) brightness(0.7);
      }
      #map-inspecao .leaflet-container {
        background: #0f172a;
      }
    }

    /* ACCORDION (HISTÓRICO & AUDITORIA) */
    p-accordion {
      display: block;
      width: 100%;
      --p-accordion-header-background: var(--ces-surface-subtle);
      --p-accordion-header-hover-background: var(--ces-surface-muted);
      --p-accordion-header-active-background: var(--ces-surface-subtle);
      --p-accordion-header-active-hover-background: var(--ces-surface-muted);
      --p-accordion-header-color: var(--ces-foreground);
      --p-accordion-header-active-color: var(--ces-foreground);
      --p-accordion-header-border-color: var(--ces-border);
      --p-accordion-content-background: var(--ces-surface);
      --p-accordion-content-color: var(--ces-foreground);
      --p-accordion-content-border-color: var(--ces-border);
      --p-accordion-panel-border-color: var(--ces-border);
    }

    p-accordion-panel {
      display: block;
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-xl, 0.75rem);
      background-color: var(--ces-surface);
      overflow: hidden;
      box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.04);
    }

    p-accordion-header {
      display: block;
      width: 100%;
      background-color: var(--ces-surface-subtle);
      color: var(--ces-foreground);
      border: none;
    }

    p-accordion-content {
      display: block;
      background-color: var(--ces-surface);
      color: var(--ces-foreground);
      border-top: 1px solid var(--ces-border);
    }

    :host ::ng-deep .p-accordioncontent-content {
      padding: 1rem 1.25rem;
    }

    /* Entrada sutil ao carregar o relatório (dinamismo discreto, sem alterar a estética). */
    @keyframes riseIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
    .rise { animation: riseIn .45s cubic-bezier(.22,.61,.36,1) both; }
    @media (prefers-reduced-motion: reduce) { .rise { animation: none; } }

    @media print {
      .fixed { display: none !important; }
      .rise { animation: none !important; }
      @page { margin: 10mm; }
      .kv-l { font-size: 9px; }
      .kv-v { font-size: 9px; }
      * { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }
    }
  `]
})
export class InspecaoDetalheComponent implements OnInit, OnDestroy {
  private sst = inject(SstService);
  private rh = inject(RhService);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private messageService = inject(MessageService);

  @ViewChild('historicoComp') protected historicoComp?: InspecaoSegurancaHistoricoComponent;

  protected readonly timelineValue = signal<string | number | string[] | number[] | null | undefined>(undefined);
  protected readonly isTimelineOpen = computed(() => {
    const val = this.timelineValue();
    if (!val) return false;
    return val === 'timeline' || (Array.isArray(val) && (val as any[]).includes('timeline'));
  });

  protected loading = signal(true);
  protected inspecao = signal<InspecaoSegurancaResponse | null>(null);
  protected formulario = signal<FormularioInspecaoResponse | null>(null);

  /** Apenas o criador da inspeção pode editar o cabeçalho */
  protected podeEditarCabecalho = computed(() => {
    const insp = this.inspecao();
    const user = this.auth.currentUser();
    return Boolean(insp && user && insp.idInspetor === user.id);
  });

  // Lookups para edição do cabeçalho
  protected lookupsCarregados = false;
  protected tiposInspecao = signal<TipoInspecaoResponse[]>([]);
  protected processos = signal<ProcessoNegocioResponse[]>([]);
  protected basesOperacionais = signal<BaseOperacionalResponse[]>([]);
  protected veiculos = signal<VeiculoResponse[]>([]);
  protected equipes = signal<EquipeResponse[]>([]);
  protected colaboradoresDisponiveis = signal<{ id: string; nome: string }[]>([]);
  protected colaboradorSelecionadoParaAdicionar: { id: string; nome: string } | null = null;

  // Estado do modal de edição
  protected dialogEdicaoCabecalho = false;
  protected salvandoCabecalho = signal(false);
  protected edicaoIdTipoInspecao = '';
  protected edicaoIdProcesso = '';
  protected edicaoIdBaseOperacional = '';
  protected edicaoIdVeiculo: string | null = null;
  protected edicaoIdEquipe: string | null = null;
  protected edicaoDataRealizacao: Date | null = null;
  protected edicaoObservacoes = '';
  protected edicaoLatitude: number | null = null;
  protected edicaoLongitude: number | null = null;
  protected edicaoColaboradores = signal<{ id: string; nome: string }[]>([]);
  protected justificativaEdicao = '';

  private perguntasById = new Map<string, PerguntaResponse>();
  private opcoesById = new Map<string, OpcaoRespostaResponse>();
  /** Opções marcadas como geradoras de desvio, para realçar respostas no relatório. */
  private opcoesDesvio = new Set<string>();
  private map: any;

  protected dialogTratamento = false;
  protected salvandoTratamento = signal(false);
  protected tratamento: TratarDesvioRequest = { status: 'EM_TRATAMENTO' };
  protected tratamentoPrazo: Date | null = null;
  private desvioEmTratamento: DesvioSegurancaResponse | null = null;
  protected desvioInicialmenteResolvido = false;

  protected podeTratar = computed(() => this.auth.currentUser()?.permissoes?.includes('sst:inspecao:write') ?? false);

  protected isObjetivo = isTipoObjetivo;

  protected statusDesvioOptions = (Object.keys(STATUS_DESVIO_LABELS) as StatusDesvio[]).map((value) => ({
    label: STATUS_DESVIO_LABELS[value],
    value,
    icon: value === 'RESOLVIDO' ? 'pi pi-check' : value === 'EM_TRATAMENTO' ? 'pi pi-clock' : 'pi pi-times'
  }));

  /**
   * Anexos dos desvios achatados e resolvidos contra o formulário (enunciado da
   * pergunta e opção marcada) — vão para a galeria única junto das evidências da
   * inspeção. Esta tela é quem tem o formulário carregado, por isso a resolução
   * do contexto acontece aqui.
   */
  protected anexosDesviosGaleria = computed<AnexoDesvioGaleria[]>(() => {
    const insp = this.inspecao();
    this.formulario(); // Reage a atualizações no formulário
    if (!insp?.desvios) return [];
    const itens: AnexoDesvioGaleria[] = [];
    for (const d of insp.desvios) {
      if (!d.anexos?.length) continue;
      const perguntaEnunciado = this.perguntaEnunciado(d.idPergunta);
      const opcaoTexto = this.opcaoTexto(d.idOpcaoResposta);
      for (const a of d.anexos) {
        // Itens ainda na fila offline não existem no servidor: sem binário para baixar.
        if (a.id.startsWith('local')) continue;
        itens.push({
          idDesvio: d.id,
          idAnexo: a.id,
          tipo: a.tipo,
          contexto: a.contexto,
          nomeArquivo: a.nomeArquivo,
          contentType: a.contentType,
          tamanhoBytes: a.tamanhoBytes,
          dataCriacao: a.dataCriacao,
          perguntaEnunciado,
          opcaoTexto
        });
      }
    }
    return itens;
  });

  /** Resumo executivo (números grandes e legíveis para leigos no topo do relatório). */
  protected resumo = computed(() => {
    const insp = this.inspecao();
    if (!insp) {
      return { colaboradores: 0, respostas: 0, desvios: 0, resolvidos: 0, pendentes: 0, conformidade: 100 };
    }
    const desvios = insp.desvios ?? [];
    const resolvidos = desvios.filter((d) => d.status === 'RESOLVIDO').length;
    const pendentes = desvios.length - resolvidos;
    const respostas = insp.respostas?.length ?? 0;
    const conformidade =
      respostas > 0 ? Math.max(0, Math.min(100, Math.round((1 - desvios.length / respostas) * 100))) : 100;
    return {
      colaboradores: insp.colaboradoresInspecionados?.length ?? 0,
      respostas,
      desvios: desvios.length,
      resolvidos,
      pendentes,
      conformidade
    };
  });

  /** Classe de cor do medidor de conformidade conforme a faixa. */
  protected conformidadeTom = computed(() => {
    const c = this.resumo().conformidade;
    if (c >= 90) return { texto: 'text-emerald-700 dark:text-emerald-400', barra: 'bg-emerald-500', fundo: 'bg-emerald-50 dark:bg-emerald-950/50', borda: 'border-emerald-200 dark:border-emerald-800', anel: 'text-emerald-500' };
    if (c >= 70) return { texto: 'text-amber-700 dark:text-amber-400', barra: 'bg-amber-500', fundo: 'bg-amber-50 dark:bg-amber-950/50', borda: 'border-amber-200 dark:border-amber-800', anel: 'text-amber-500' };
    return { texto: 'text-red-700 dark:text-red-400', barra: 'bg-red-500', fundo: 'bg-red-50 dark:bg-red-950/50', borda: 'border-red-200 dark:border-red-800', anel: 'text-red-500' };
  });

  ngOnInit() {
    const id = this.route.snapshot?.paramMap?.get('id');
    if (id) {
      this.carregar(id);
    } else {
      this.loading.set(false);
    }
    this.route.paramMap?.subscribe(params => {
      const pId = params.get('id');
      if (pId && pId !== id) {
        this.carregar(pId);
      }
    });
  }

  ngOnDestroy() {
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }

  private carregar(id: string) {
    this.loading.set(true);
    this.sst.buscarInspecaoPorId(id).subscribe({
      next: (insp) => {
        this.inspecao.set(insp);
        this.loading.set(false);
        if (insp.formulario?.id) this.carregarFormulario(insp.formulario.id);
        if (insp.latitude != null && insp.longitude != null) {
          setTimeout(() => this.initMap(insp.latitude!, insp.longitude!), 200);
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erro ao carregar', detail: getErrorMessage(err) });
      }
    });
  }

  private carregarFormulario(idFormulario: string) {
    this.sst.buscarFormularioPorId(idFormulario).subscribe({
      next: (form) => {
        this.formulario.set(form);
        this.perguntasById.clear();
        this.opcoesById.clear();
        this.opcoesDesvio.clear();
        (form.grupos ?? [])
          .slice()
          .sort((a, b) => a.ordem - b.ordem)
          .forEach((g) => {
            g.perguntas = (g.perguntas ?? []).slice().sort((a, b) => a.ordem - b.ordem);
            g.perguntas.forEach((p) => {
              this.perguntasById.set(p.id, p);
              (p.opcoes ?? []).forEach((o) => {
                this.opcoesById.set(o.id, o);
                if (o.geraDesvio) this.opcoesDesvio.add(o.id);
              });
            });
          });
      },
      error: () => this.formulario.set(null)
    });
  }

  private initMap(lat: number, lng: number) {
    const el = document.getElementById('map-inspecao');
    if (!el || typeof L === 'undefined') return;
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
    this.map = L.map(el, { zoomControl: true, attributionControl: true });
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap contributors'
    }).addTo(this.map);
    L.circleMarker([lat, lng], {
      radius: 9,
      fillColor: '#2563eb',
      color: '#fff',
      weight: 2,
      opacity: 1,
      fillOpacity: 0.85
    })
      .addTo(this.map)
      .bindPopup('Local da inspeção');
    this.map.setView([lat, lng], 16);
    setTimeout(() => this.map.invalidateSize(), 250);
  }

  protected respostaTexto(pergunta: PerguntaResponse): string {
    const resposta = this.inspecao()?.respostas.find((r) => r.idPergunta === pergunta.id);
    if (!resposta) return '—';
    if (isTipoObjetivo(pergunta.tipoResposta)) {
      const textos = (resposta.opcoesSelecionadas ?? []).map((id) => this.opcoesById.get(id)?.texto ?? id);
      return textos.length ? textos.join(', ') : '—';
    }
    if (pergunta.tipoResposta === 'TEXTO') return resposta.valorTexto || '—';
    if (pergunta.tipoResposta === 'NUMERO') return resposta.valorNumero != null ? String(resposta.valorNumero) : '—';
    if (pergunta.tipoResposta === 'DATA') {
      return resposta.valorData ? new Date(resposta.valorData + 'T00:00:00').toLocaleDateString('pt-BR') : '—';
    }
    return '—';
  }

  /** True se a resposta marcada gera desvio (para realçar em vermelho). */
  protected respostaDesvio(pergunta: PerguntaResponse): boolean {
    const resposta = this.inspecao()?.respostas.find((r) => r.idPergunta === pergunta.id);
    if (!resposta) return false;
    return (resposta.opcoesSelecionadas ?? []).some((id) => this.opcoesDesvio.has(id));
  }

  protected isOpcaoSelecionada(idPergunta: string, idOpcao: string): boolean {
    const resposta = this.inspecao()?.respostas.find((r) => r.idPergunta === idPergunta);
    return resposta?.opcoesSelecionadas?.includes(idOpcao) ?? false;
  }

  protected perguntaEnunciado(idPergunta: string): string {
    return this.perguntasById.get(idPergunta)?.enunciado ?? 'Pergunta';
  }
  protected opcaoTexto(idOpcao: string): string {
    return this.opcoesById.get(idOpcao)?.texto ?? '';
  }

  protected prazoVencido(desvio: DesvioSegurancaResponse): boolean {
    if (!desvio.prazo || desvio.status === 'RESOLVIDO') return false;
    const prazo = new Date(desvio.prazo + 'T23:59:59');
    return prazo.getTime() < Date.now();
  }

  protected  abrirTratamento(desvio: DesvioSegurancaResponse) {
    if (!desvio) return;
    this.desvioEmTratamento = desvio;
    this.desvioInicialmenteResolvido = desvio.status === 'RESOLVIDO';
    this.tratamento = {
      status: desvio.status || 'EM_TRATAMENTO',
      acaoCorretiva: desvio.acaoCorretiva || ''
    };
    this.tratamentoPrazo = desvio.prazo ? new Date(desvio.prazo + 'T00:00:00') : null;
    this.dialogTratamento = true;
  }

  protected salvarTratamento() {
    const insp = this.inspecao();
    const desvio = this.desvioEmTratamento;
    if (!insp || !desvio || !this.tratamento?.status) return;

    const request: TratarDesvioRequest = {
      status: this.tratamento.status,
      acaoCorretiva: this.tratamento.acaoCorretiva?.trim() || undefined,
      prazo: this.formatDateOnly(this.tratamentoPrazo)
    };

    this.salvandoTratamento.set(true);
    this.sst.tratarDesvio(insp.id, desvio.id, request).subscribe({
      next: (res) => {
        this.salvandoTratamento.set(false);
        this.dialogTratamento = false;
        if (res) this.inspecao.set(res);
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Desvio atualizado.' });
      },
      error: (err) => {
        this.salvandoTratamento.set(false);
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) });
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

  protected gravidadeLabel(g: GravidadeDesvio) {
    return GRAVIDADE_LABELS[g] ?? g;
  }
  protected gravidadeBadge(g: GravidadeDesvio) {
    return GRAVIDADE_BADGE[g] ?? 'bg-slate-50 text-slate-600';
  }
  /** Cor sólida por gravidade — usada no filete lateral do card de desvio. */
  protected gravidadeCor(g: GravidadeDesvio): string {
    const cores: Record<GravidadeDesvio, string> = {
      BAIXA: '#94a3b8',
      MEDIA: '#f59e0b',
      ALTA: '#f97316',
      CRITICA: '#ef4444'
    };
    return cores[g] ?? '#94a3b8';
  }
  protected statusDesvioLabel(s: StatusDesvio) {
    return STATUS_DESVIO_LABELS[s] ?? s;
  }
  protected statusDesvioBadge(s: StatusDesvio) {
    return STATUS_DESVIO_BADGE[s] ?? 'bg-slate-50 text-slate-600';
  }
  protected statusInspLabel(s: StatusInspecao) {
    return STATUS_INSPECAO_LABELS[s] ?? s;
  }
  protected statusInspBadge(s: StatusInspecao) {
    return STATUS_INSPECAO_BADGE[s] ?? 'bg-slate-50 text-slate-600';
  }

  protected onEvidenciasMudadas(evidencias: EvidenciaInspecaoResponse[]) {
    this.inspecao.update((insp) => (insp ? { ...insp, evidencias } : null));
  }

  protected onAnexoMudado(desvio: DesvioSegurancaResponse, novosAnexos: AnexoDesvioResponse[]) {
    desvio.anexos = novosAnexos;
    this.inspecao.update((insp) => (insp ? { ...insp, desvios: [...insp.desvios] } : null));
  }

  protected printReport() {
    window.print();
  }

  protected voltar() {
    this.router.navigate(['/sst/inspecoes']);
  }

  protected rolarParaHistorico() {
    this.timelineValue.set('timeline');
    setTimeout(() => {
      const el = document.getElementById('secao-historico');
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' });
      }
    }, 150);
  }

  protected carregarLookupsEdicao() {
    if (this.lookupsCarregados) return;
    this.lookupsCarregados = true;

    this.sst.listarOpcoesTiposInspecao().subscribe({
      next: (res) => this.tiposInspecao.set(res),
      error: () => {}
    });

    this.rh.listarProcessosAtivos().subscribe({
      next: (res) => this.processos.set(res.content ?? []),
      error: () => {}
    });

    this.rh.listarBasesOperacionais({ ativo: true, page: 0, size: 100, sort: 'nome,asc' }).subscribe({
      next: (res) => this.basesOperacionais.set(res.content ?? []),
      error: () => {}
    });

    this.rh.listarVeiculos({ ativo: true, size: 500 }).subscribe({
      next: (res) => this.veiculos.set(res.content ?? []),
      error: () => {}
    });

    this.rh.listarEquipes({ ativo: true, size: 500 }).subscribe({
      next: (res) => this.equipes.set(res.content ?? []),
      error: () => {}
    });

    this.rh.listarColaboradores({ ativo: true, size: 1000 }).subscribe({
      next: (res) => {
        const list = (res.content ?? []).map((c) => ({
          id: c.id,
          nome: c.nomeCompleto || c.id
        }));
        this.colaboradoresDisponiveis.set(list);
      },
      error: () => {}
    });
  }

  protected navegarParaEdicao() {
    const insp = this.inspecao();
    if (!insp) return;
    this.router.navigate(['/sst/inspecoes', insp.id, 'editar']);
  }

  protected abrirEdicaoCabecalho() {
    const insp = this.inspecao();
    if (!insp) return;

    this.carregarLookupsEdicao();

    this.edicaoIdTipoInspecao = insp.tipoInspecao?.id || '';
    this.edicaoIdProcesso = insp.processo?.id || '';
    this.edicaoIdBaseOperacional = insp.baseOperacional?.id || '';
    this.edicaoIdVeiculo = insp.veiculo?.id || null;
    this.edicaoIdEquipe = insp.equipe?.id || null;
    this.edicaoDataRealizacao = insp.dataRealizacao ? new Date(insp.dataRealizacao) : new Date();
    this.edicaoObservacoes = insp.observacoes || '';
    this.edicaoLatitude = insp.latitude ?? null;
    this.edicaoLongitude = insp.longitude ?? null;
    this.edicaoColaboradores.set(
      (insp.colaboradoresInspecionados ?? []).map((c) => ({ id: c.id, nome: c.nomeCompleto }))
    );
    this.colaboradorSelecionadoParaAdicionar = null;
    this.justificativaEdicao = '';

    this.dialogEdicaoCabecalho = true;
  }

  protected onSelecionarEquipeEdicao() {
    if (!this.edicaoIdEquipe) return;
    this.rh.buscarEquipePorId(this.edicaoIdEquipe).subscribe({
      next: (equipe) => {
        if (!equipe?.membros?.length) return;
        const atuais = [...this.edicaoColaboradores()];
        equipe.membros.forEach((m) => {
          if (!atuais.some((c) => c.id === m.idColaborador)) {
            atuais.push({
              id: m.idColaborador,
              nome: m.nomeColaborador || 'Colaborador'
            });
          }
        });
        this.edicaoColaboradores.set(atuais);
      },
      error: () => {}
    });
  }

  protected colaboradorPossuiDesvio(idColaborador: string): boolean {
    const insp = this.inspecao();
    if (!insp?.desvios) return false;
    return insp.desvios.some((d) => d.colaboradores?.some((c) => c.id === idColaborador));
  }

  protected removerColaboradorEdicao(idColaborador: string) {
    if (this.colaboradorPossuiDesvio(idColaborador)) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Não permitido',
        detail: 'Este colaborador possui desvios atribuídos nesta inspeção e não pode ser removido.'
      });
      return;
    }
    this.edicaoColaboradores.update((lista) => lista.filter((c) => c.id !== idColaborador));
  }

  protected adicionarColaboradorEdicao() {
    const selecionado = this.colaboradorSelecionadoParaAdicionar;
    if (!selecionado) return;
    const atuais = this.edicaoColaboradores();
    if (atuais.some((c) => c.id === selecionado.id)) {
      this.messageService.add({
        severity: 'info',
        summary: 'Já incluído',
        detail: 'Este colaborador já está na lista de inspecionados.'
      });
      return;
    }
    this.edicaoColaboradores.update((lista) => [...lista, selecionado]);
    this.colaboradorSelecionadoParaAdicionar = null;
  }

  protected formularioEdicaoValido(): boolean {
    return Boolean(
      this.edicaoIdTipoInspecao &&
      this.edicaoIdProcesso &&
      this.edicaoIdBaseOperacional &&
      this.edicaoDataRealizacao &&
      this.edicaoColaboradores().length > 0 &&
      this.justificativaEdicao?.trim().length >= 5
    );
  }

  protected salvarEdicaoCabecalho() {
    const insp = this.inspecao();
    if (!insp || !this.formularioEdicaoValido()) return;

    const request: AtualizarCabecalhoInspecaoRequest = {
      idTipoInspecao: this.edicaoIdTipoInspecao,
      idProcesso: this.edicaoIdProcesso,
      idBaseOperacional: this.edicaoIdBaseOperacional,
      idVeiculo: this.edicaoIdVeiculo || null,
      idEquipe: this.edicaoIdEquipe || null,
      dataRealizacao: this.edicaoDataRealizacao?.toISOString() || new Date().toISOString(),
      observacoes: this.edicaoObservacoes?.trim() || null,
      latitude: this.edicaoLatitude,
      longitude: this.edicaoLongitude,
      colaboradoresInspecionados: this.edicaoColaboradores().map((c) => c.id),
      justificativa: this.justificativaEdicao.trim()
    };

    this.salvandoCabecalho.set(true);
    this.sst.atualizarCabecalhoInspecao(insp.id, request).subscribe({
      next: (res) => {
        this.salvandoCabecalho.set(false);
        this.dialogEdicaoCabecalho = false;
        if (res) this.inspecao.set(res);
        this.timelineValue.set('timeline');
        this.historicoComp?.recarregar();
        this.messageService.add({
          severity: 'success',
          summary: 'Cabeçalho Atualizado',
          detail: 'As alterações foram salvas e registradas no histórico auditado.'
        });
      },
      error: (err) => {
        this.salvandoCabecalho.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro ao atualizar',
          detail: getErrorMessage(err)
        });
      }
    });
  }
}

