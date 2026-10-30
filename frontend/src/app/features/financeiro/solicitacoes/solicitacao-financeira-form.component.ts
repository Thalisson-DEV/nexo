import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MenuItem, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { DividerModule } from 'primeng/divider';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { SelectModule } from 'primeng/select';
import { StepperModule } from 'primeng/stepper';
import { distinctUntilChanged, filter } from 'rxjs';
import {
  CampoFormulario,
  CompraVinculada,
  FinanceiroProblemDetails,
  FormularioResumo,
  FormularioSchema,
  SolicitacaoFinanceira,
  TipoSolicitacao,
  ValorSolicitacao
} from '../../../core/models/financeiro.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { FinanceiroFormComponent } from '../shared/financeiro-form.component';
import {
  agruparCampos,
  aplicarValidacaoCondicional,
  ehCampoDadosPagamento,
  ehFormaPagamentoBoleto,
  regrasCruzadas,
  serializarValores,
  textoDoValor,
  valorParaControle,
  GrupoDeCampos
} from '../shared/campos-financeiros';
import { SolicitacaoDocumentosComponent } from './solicitacao-documentos.component';
import { NavigationHistoryService, bindStepToUrl, createFormDraft } from '../../../core/navigation';

/** Rascunho do preenchimento (passos 1 a 3; depois de salvo, o servidor guarda). */
interface RascunhoSolicitacaoFinanceira {
  varianteId: string | null;
  valores: Record<string, unknown> | null;
  valoresExibicao: Record<string, ValorSolicitacao>;
  camposHerdados: string[];
  compraVinculada: CompraVinculada | null;
}

@Component({
  selector: 'app-solicitacao-financeira-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    BreadcrumbModule,
    ButtonModule,
    DividerModule,
    ProgressSpinnerModule,
    SelectModule,
    StepperModule,
    FinanceiroFormComponent,
    SolicitacaoDocumentosComponent
  ],
  template: `
    <div class="flex flex-col gap-4 pb-28">
      <div class="flex justify-between items-center">
        <p-breadcrumb [model]="breadcrumbItems" styleClass="!bg-transparent !border-0 !p-0"></p-breadcrumb>
        <p-button icon="pi pi-arrow-left" label="Voltar" [text]="true" severity="secondary"
          (onClick)="voltar()" styleClass="!text-slate-400 dark:!text-slate-400 !text-[10px] !font-medium"></p-button>
      </div>
      @if (loading()) {
        <div class="form-shell max-w-4xl w-full mx-auto min-h-96 flex flex-col items-center justify-center gap-3">
          <p-progress-spinner strokeWidth="3" [style]="{ width: '36px', height: '36px' }" />
          <span class="text-[11px] text-slate-400 dark:text-slate-500">Preparando o formulário...</span>
        </div>
      } @else {
        <div class="form-shell max-w-4xl w-full mx-auto">
          <div class="form-shell__header">
            <div class="flex items-center gap-2 mb-1">
              <i [class]="(tipo === 'COMPRA' ? 'pi pi-shopping-cart' : 'pi pi-credit-card') + ' text-primary-700 dark:text-primary-400 text-xs'"></i>
              <span class="text-[10px] font-medium text-slate-500 dark:text-slate-400 uppercase tracking-widest">
                {{ activeStep() === 5 ? 'Solicitação concluída' : (editando() ? 'Editar' : 'Nova') + ' solicitação de ' + (tipo === 'COMPRA' ? 'compra' : 'pagamento') }}
              </span>
            </div>
            <h1 class="text-base md:text-lg font-bold text-slate-800 dark:text-slate-100">
              {{ activeStep() === 5 ? 'Solicitação enviada com sucesso!' : (schema()?.titulo || 'Selecione o formulário adequado') }}
            </h1>
            <p class="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
              {{ activeStep() === 5 ? 'A solicitação foi registrada no sistema e encaminhada para aprovação.' : (schema()?.descricao || 'Os campos serão carregados conforme o modelo escolhido.') }}
            </p>
          </div>

          <div class="form-shell__body">
            <p-stepper [(value)]="activeStep">
              <p-step-list class="flex justify-center items-center gap-4 md:gap-8 pb-4 border-b border-slate-100 dark:border-slate-800">
                <p-step [value]="1">
                  <ng-template #content let-activateCallback="activateCallback" let-value="value">
                    <button type="button" class="step-button"
                      (click)="!editando() && activeStep() < 5 && activateCallback()">
                      <span class="step-circle" [ngClass]="value <= activeStep() ? 'step-active' : 'step-inactive'">
                        <i class="pi pi-file-edit text-xs"></i>
                      </span>
                      <span class="step-label" [ngClass]="value <= activeStep() ? 'text-primary-600 dark:text-primary-400 font-bold' : 'text-slate-400 dark:text-slate-500 font-medium'">1. Modelo</span>
                    </button>
                  </ng-template>
                </p-step>
                <p-step [value]="2">
                  <ng-template #content let-activateCallback="activateCallback" let-value="value">
                    <button type="button" class="step-button"
                      (click)="activeStep() > 2 && activeStep() < 5 && activateCallback()">
                      <span class="step-circle" [ngClass]="value <= activeStep() ? 'step-active' : 'step-inactive'">
                        <i class="pi pi-pencil text-xs"></i>
                      </span>
                      <span class="step-label" [ngClass]="value <= activeStep() ? 'text-primary-600 dark:text-primary-400 font-bold' : 'text-slate-400 dark:text-slate-500 font-medium'">2. Dados</span>
                    </button>
                  </ng-template>
                </p-step>
                <p-step [value]="3">
                  <ng-template #content let-activateCallback="activateCallback" let-value="value">
                    <button type="button" class="step-button"
                      (click)="activeStep() > 3 && activeStep() < 5 && activateCallback()">
                      <span class="step-circle" [ngClass]="value <= activeStep() ? 'step-active' : 'step-inactive'">
                        <i class="pi pi-check text-xs"></i>
                      </span>
                      <span class="step-label" [ngClass]="value <= activeStep() ? 'text-primary-600 dark:text-primary-400 font-bold' : 'text-slate-400 dark:text-slate-500 font-medium'">3. Revisão</span>
                    </button>
                  </ng-template>
                </p-step>
                <p-step [value]="4">
                  <ng-template #content let-activateCallback="activateCallback" let-value="value">
                    <button type="button" class="step-button"
                      (click)="activeStep() > 4 && activeStep() < 5 && activateCallback()">
                      <span class="step-circle" [ngClass]="value <= activeStep() ? 'step-active' : 'step-inactive'">
                        <i class="pi pi-paperclip text-xs"></i>
                      </span>
                      <span class="step-label" [ngClass]="value <= activeStep() ? 'text-primary-600 dark:text-primary-400 font-bold' : 'text-slate-400 dark:text-slate-500 font-medium'">4. Documentos</span>
                    </button>
                  </ng-template>
                </p-step>
                <p-step [value]="5">
                  <ng-template #content let-value="value">
                    <button type="button" class="step-button" [disabled]="activeStep() < 5">
                      <span class="step-circle" [ngClass]="value <= activeStep() ? 'step-active' : 'step-inactive'">
                        <i class="pi pi-check-circle text-xs"></i>
                      </span>
                      <span class="step-label" [ngClass]="value <= activeStep() ? 'text-primary-600 dark:text-primary-400 font-bold' : 'text-slate-400 dark:text-slate-500 font-medium'">5. Conclusão</span>
                    </button>
                  </ng-template>
                </p-step>
              </p-step-list>

              <p-step-panels>
                <p-step-panel [value]="1">
                  <ng-template #content>
                    <div class="flex flex-col gap-5 mt-6">
                      <div class="section-marker">
                        <i class="pi pi-file-edit text-primary-600 dark:text-primary-400 text-sm"></i>
                        <span class="text-slate-800 dark:text-slate-100 font-bold text-xs md:text-sm tracking-tight">1. Modelo do Formulário</span>
                      </div>
                      <div class="flex flex-col gap-1.5 md:pl-5">
                        <label class="text-[11px] font-medium text-slate-700 dark:text-slate-200">Modelo do formulário <span class="text-red-500 font-bold">*</span></label>
                        <p-select [(ngModel)]="varianteSelecionada" [options]="variantes()"
                          optionLabel="titulo" optionValue="id" [filter]="true" filterBy="titulo,descricao"
                          placeholder="Selecione o tipo de solicitação" class="w-full" styleClass="w-full"
                          size="small" [fluid]="true" appendTo="body">
                          <ng-template pTemplate="item" let-variante>
                            <div class="flex flex-col py-1">
                              <span class="text-[11px] font-medium text-slate-700 dark:text-slate-200">
                                {{ variante.titulo }}
                                @if (variante.padrao) { <span class="ml-1" [ngClass]="tipo === 'COMPRA' ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'">(Padrão)</span> }
                              </span>
                              <span class="text-[9px] text-slate-400 dark:text-slate-500">{{ variante.descricao || variante.codigo }}</span>
                            </div>
                          </ng-template>
                        </p-select>
                        <span class="text-[9px] text-slate-400 dark:text-slate-500 italic">
                          Escolha o modelo que melhor representa a necessidade.
                        </span>
                      </div>
                      <div class="flex justify-end mt-4">
                        <p-button label="Próximo" icon="pi pi-arrow-right" iconPos="right"
                          [disabled]="!varianteSelecionada()" (onClick)="avancarModelo()"
                          styleClass="!rounded-lg !bg-primary-700 !border-primary-700 !py-2.5 !px-6 !font-bold !text-[11px]"></p-button>
                      </div>
                    </div>
                  </ng-template>
                </p-step-panel>

                <p-step-panel [value]="2">
                  <ng-template #content>
                    <div class="mt-6">
                      <div class="section-marker mb-4">
                        <i class="pi pi-pencil text-primary-600 dark:text-primary-400 text-sm"></i>
                        <span class="text-slate-800 dark:text-slate-100 font-bold text-xs md:text-sm tracking-tight">2. Preenchimento dos Dados</span>
                      </div>

                      @if (compraVinculada(); as compra) {
                        <div class="mt-2 mb-5 border border-slate-200 dark:border-slate-800 rounded-xl px-4 py-3 flex items-center justify-between gap-4 md:ml-5">
                          <div>
                            <span class="text-xs font-semibold text-slate-700 dark:text-slate-200">
                              Origem: compra #{{ compra.numero }}
                            </span>
                            <p class="text-[10px] text-slate-500 dark:text-slate-400 mt-1">
                              Os dados herdados vieram dela. O valor é apenas uma sugestão — ajuste se o
                              pagamento for diferente do que foi autorizado.
                            </p>
                          </div>
                          <div class="text-right shrink-0">
                            <span class="text-[9px] text-slate-400 uppercase">Autorizado na compra</span>
                            <p class="text-sm font-bold text-slate-800 dark:text-slate-100">{{ compra.valorAprovado | currency:'BRL' }}</p>
                          </div>
                        </div>
                      }
                      @if (schema()) {
                        <form [formGroup]="form" novalidate class="mt-4">
                          <app-financeiro-form [schema]="schema()!" [form]="form"
                            [errosServidor]="errosServidor()" [somenteLeitura]="camposHerdados()"
                            [valoresExibicao]="valoresExibicao()" (pronto)="formularioPronto()"
                            (exibicoesResolvidas)="registrarExibicoes($event)" />
                        </form>
                      }
                      <div class="flex justify-between mt-8 pt-4 border-t border-slate-100 dark:border-slate-800">
                        <p-button label="Voltar" icon="pi pi-arrow-left" [text]="true"
                          [disabled]="editando()" (onClick)="voltarModelo()"
                          styleClass="!text-primary-700 dark:!text-primary-400 !font-bold !text-[11px]"></p-button>
                        <p-button label="Revisar" icon="pi pi-arrow-right" iconPos="right"
                          (onClick)="irParaRevisao()"
                          styleClass="!rounded-lg !bg-primary-700 !border-primary-700 !py-2.5 !px-6 !font-bold !text-[11px]"></p-button>
                      </div>
                    </div>
                  </ng-template>
                </p-step-panel>

                <p-step-panel [value]="3">
                  <ng-template #content>
                    <div class="mt-6 flex flex-col gap-6">
                      <div class="section-marker">
                        <i class="pi pi-eye text-primary-600 dark:text-primary-400 text-sm"></i>
                        <span class="text-slate-800 dark:text-slate-100 font-bold text-xs md:text-sm tracking-tight">3. Revisão do Preenchimento</span>
                      </div>

                      @for (grupo of gruposParaRevisao(); track grupo.grupo) {
                        <div>
                          <div class="section-marker">
                            <span class="text-slate-700 dark:text-slate-200 font-bold text-xs">{{ grupo.rotulo }}</span>
                          </div>
                          <div class="grid grid-cols-1 md:grid-cols-2 gap-3 md:pl-5">
                            @for (campo of grupo.campos; track campo.id) {
                              @if (valorResumo(campo); as valor) {
                                <div class="review-card"
                                  [class.md:col-span-2]="campo.tipoCampo === 'TEXTO_LONGO'">
                                  <span class="text-[10px] font-medium text-slate-500 dark:text-slate-400 break-words leading-tight">{{ campo.rotulo }}</span>
                                  <p class="text-[11px] text-slate-700 dark:text-slate-200 font-medium whitespace-pre-wrap mt-1">{{ valor }}</p>
                                </div>
                              }
                            }
                          </div>
                        </div>
                      }
                      <div class="bg-slate-50 dark:bg-slate-800/50 p-3.5 rounded-xl border border-slate-100 dark:border-slate-800 flex items-start gap-2.5">
                        <i class="pi pi-info-circle text-primary-700 dark:text-primary-400 text-xs mt-0.5"></i>
                        <p class="text-[10px] text-slate-500 dark:text-slate-400 leading-relaxed">Primeiro criaremos o rascunho. Na próxima etapa você poderá incluir orçamentos, nota fiscal, boleto, fotos e outros documentos antes do envio.</p>
                      </div>
                      <div class="flex justify-between mt-4 pt-4 border-t border-slate-100 dark:border-slate-800">
                        <p-button label="Voltar" icon="pi pi-arrow-left" [text]="true"
                          (onClick)="activeStep.set(2)" styleClass="!text-primary-700 dark:!text-primary-400 !font-bold !text-[11px]"></p-button>
                        <p-button label="Salvar e ir para anexos" icon="pi pi-paperclip" [loading]="saving()"
                          (onClick)="salvar()" styleClass="!rounded-lg !bg-primary-700 !border-primary-700 !py-2.5 !px-8 !font-bold !text-[11px]"></p-button>
                      </div>
                    </div>
                  </ng-template>
                </p-step-panel>

                <p-step-panel [value]="4">
                  <ng-template #content>
                    @if (solicitacao; as rascunho) {
                      <div class="mt-6">
                        <div class="section-marker mb-4">
                          <i class="pi pi-paperclip text-primary-600 dark:text-primary-400 text-sm"></i>
                          <span class="text-slate-800 dark:text-slate-100 font-bold text-xs md:text-sm tracking-tight">4. Documentos e Comprovantes</span>
                        </div>

                        <div class="border border-slate-200 dark:border-slate-800 rounded-xl px-4 py-3 mb-6 flex items-start gap-3">
                          <i class="pi pi-check-circle text-emerald-600 dark:text-emerald-400 text-sm mt-0.5"></i>
                          <div>
                            <p class="text-xs font-bold text-slate-700 dark:text-slate-200">
                              Rascunho #{{ rascunho.numero }} salvo
                            </p>
                            <p class="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
                              Anexe o que já tiver em mãos. A nota fiscal e o boleto podem ser adicionados depois, mesmo após a aprovação.
                            </p>
                          </div>
                        </div>

                        <app-solicitacao-documentos [tipo]="tipo" [solicitacao]="rascunho"
                          (atualizada)="atualizarRascunho($event)" />

                        <div class="flex flex-col-reverse sm:flex-row sm:justify-between gap-3 mt-8 pt-5 border-t border-slate-100 dark:border-slate-800">
                          <p-button label="Continuar depois" icon="pi pi-save" [text]="true"
                            (onClick)="abrirDetalhe()"
                            styleClass="!text-slate-500 dark:!text-slate-400 !font-bold !text-[11px]"></p-button>
                          @if (rascunho.acoes.podeSubmeter) {
                            <p-button label="Enviar para aprovação" icon="pi pi-send"
                              [loading]="saving()" (onClick)="submeter()"
                              styleClass="!rounded-lg !bg-primary-700 !border-primary-700 !py-2.5 !px-8 !font-bold !text-[11px]"></p-button>
                          }
                        </div>
                      </div>
                    }
                  </ng-template>
                </p-step-panel>

                <p-step-panel [value]="5">
                  <ng-template #content>
                    @if (solicitacao; as sol) {
                      <div class="mt-6 flex flex-col gap-6">
                        <div
                          class="p-6 rounded-2xl border border-emerald-200 bg-emerald-50/50 dark:border-emerald-800/40 dark:bg-emerald-950/20 flex flex-col items-center text-center gap-3"
                        >
                          <div
                            class="w-12 h-12 rounded-full bg-emerald-100 text-emerald-600 dark:bg-emerald-900/50 dark:text-emerald-400 flex items-center justify-center text-xl"
                          >
                            <i class="pi pi-check"></i>
                          </div>
                          <div>
                            <h2 class="text-base font-semibold text-emerald-900 dark:text-emerald-200">
                              Solicitação #{{ sol.numero }} enviada com sucesso!
                            </h2>
                            <p class="text-xs text-emerald-700 dark:text-emerald-400 mt-1 max-w-md">
                              A solicitação de {{ tipo === 'COMPRA' ? 'compra' : 'pagamento' }} foi finalizada e encaminhada para o fluxo de aprovação.
                            </p>
                          </div>
                        </div>

                        <div class="flex flex-col gap-3">
                          <span class="text-xs font-semibold text-slate-700 dark:text-slate-200">Resumo da Solicitação</span>
                          <div class="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                            <div class="flex items-center gap-3">
                              <span
                                class="inline-flex items-center justify-center w-10 h-10 rounded-lg bg-primary-100 text-primary-700 dark:bg-primary-900/40 dark:text-primary-300 text-base"
                              >
                                <i [class]="tipo === 'COMPRA' ? 'pi pi-shopping-cart' : 'pi pi-credit-card'"></i>
                              </span>
                              <div>
                                <p class="text-xs font-semibold text-slate-800 dark:text-slate-100">
                                  {{ sol.formulario.titulo }} <span class="font-normal text-slate-500 dark:text-slate-400">(#{{ sol.numero }})</span>
                                </p>
                                <div class="flex items-center gap-2 mt-0.5 text-[11px] text-slate-400 dark:text-slate-500">
                                  <span>ID: {{ idCurto(sol.id) }}</span>
                                  @if (sol.valor) {
                                    <span>·</span>
                                    <span class="font-medium text-slate-700 dark:text-slate-300">{{ sol.valor | currency:'BRL' }}</span>
                                  }
                                </div>
                              </div>
                            </div>
                            <p-button
                              label="Abrir Detalhe"
                              icon="pi pi-arrow-right"
                              iconPos="right"
                              size="small"
                              severity="primary"
                              (onClick)="abrirDetalhe()"
                              styleClass="!rounded-lg !bg-primary-700 !border-primary-700 !py-2 !px-4 !font-bold !text-[11px]"
                            />
                          </div>
                        </div>

                        <div class="flex justify-between pt-4 border-t border-slate-100 dark:border-slate-800">
                          <p-button
                            label="Solicitar outra"
                            icon="pi pi-plus"
                            [text]="true"
                            severity="secondary"
                            size="small"
                            (onClick)="novaSolicitacao()"
                            styleClass="!text-primary-700 dark:!text-primary-400 !font-bold !text-[11px]"
                          />
                          <p-button
                            label="Voltar para a Lista"
                            icon="pi pi-list"
                            size="small"
                            severity="secondary"
                            [outlined]="true"
                            (onClick)="voltar()"
                            styleClass="!text-slate-600 dark:!text-slate-300 !font-bold !text-[11px]"
                          />
                        </div>
                      </div>
                    }
                  </ng-template>
                </p-step-panel>
              </p-step-panels>
            </p-stepper>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .form-shell { background: white; border-radius: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.05); border: 1px solid #f1f5f9; }
    :host-context(.app-dark) .form-shell, :host-context(.dark) .form-shell { background: #0f172a !important; border-color: #334155 !important; }
    .form-shell__header { padding: 1.25rem 1.5rem; border-bottom: 1px solid #f1f5f9; display: flex; flex-direction: column; align-items: flex-start; gap: 0.25rem; }
    :host-context(.app-dark) .form-shell__header, :host-context(.dark) .form-shell__header { border-bottom-color: #334155 !important; }
    .form-shell__body { padding: 1.5rem; }
    @media (min-width: 768px) { .form-shell__header, .form-shell__body { padding-left: 2rem; padding-right: 2rem; } }
    .step-button { background: transparent; border: 0; display: inline-flex; flex-direction: column; align-items: center; gap: 0.375rem; cursor: pointer; padding: 0.25rem 0.5rem; }
    .step-button:disabled { cursor: default; }
    .step-circle { border-radius: 9999px; border-width: 2px; width: 2rem; height: 2rem; display: inline-flex; align-items: center; justify-content: center; transition: all 0.3s; }
    .step-active { background: var(--p-primary-600); color: white; border-color: var(--p-primary-600); }
    :host-context(.app-dark) .step-active, :host-context(.dark) .step-active { background: var(--p-primary-700) !important; border-color: var(--p-primary-700) !important; color: #ffffff !important; }
    .step-inactive { border-color: #e2e8f0; color: #94a3b8; background: transparent; }
    :host-context(.app-dark) .step-inactive, :host-context(.dark) .step-inactive { border-color: #334155 !important; color: #64748b !important; background: #0f172a !important; }
    .step-label { font-size: 10px; font-weight: 600; text-transform: none; }
    .section-marker { display: flex; align-items: center; gap: 0.5rem; border-left: 2px solid var(--p-primary-500); padding-left: 0.75rem; margin-bottom: 0.75rem; }
    :host-context(.app-dark) .section-marker, :host-context(.dark) .section-marker { border-left-color: var(--p-primary-400) !important; }
    .section-marker span { color: #374151; font-size: 13px; font-weight: 700; letter-spacing: -0.01em; text-transform: none; }
    :host-context(.app-dark) .section-marker span, :host-context(.dark) .section-marker span { color: #f1f5f9 !important; }
    .review-card { border: 1px solid #f1f5f9; border-radius: 0.5rem; background: #f8fafc80; padding: 0.65rem 0.75rem; }
    :host-context(.app-dark) .review-card, :host-context(.dark) .review-card { border-color: #334155 !important; background: rgba(15, 23, 42, 0.8) !important; }
    .review-card > span { color: #94a3b8; font-size: 10px; font-weight: 600; text-transform: none; }
    :host-context(.app-dark) .review-card > span, :host-context(.dark) .review-card > span { color: #94a3b8 !important; }
    :host-context(.app-dark) .review-card p, :host-context(.dark) .review-card p { color: #ffffff !important; }
  `]
})
export class SolicitacaoFinanceiraFormComponent {
  private readonly service = inject(FinanceiroService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);
  private readonly messages = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  protected solicitacao?: SolicitacaoFinanceira;
  private observandoCompra = false;

  protected readonly tipo = this.route.snapshot.data['tipo'] as TipoSolicitacao;
  protected readonly id = this.route.snapshot.paramMap.get('id');
  protected readonly editando = signal(!!this.id);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly activeStep = signal(1);
  protected readonly varianteSelecionada = signal<string | null>(null);
  protected readonly variantes = signal<FormularioResumo[]>([]);
  protected readonly schema = signal<FormularioSchema | null>(null);
  protected readonly errosServidor = signal<Record<string, string>>({});
  protected readonly camposHerdados = signal<Set<string>>(new Set());
  protected readonly valoresExibicao = signal<Record<string, ValorSolicitacao>>({});
  protected readonly compraVinculada = signal<CompraVinculada | null>(null);
  protected readonly form: FormGroup<any> = new FormGroup({});
  protected readonly breadcrumbItems: MenuItem[] = [
    { icon: 'pi pi-home', routerLink: '/home' },
    { label: 'Financeiro' },
    { label: this.tipo === 'COMPRA' ? 'Compras' : 'Pagamentos', routerLink: `/financeiro/${this.slug}` },
    { label: this.editando() ? 'Editar' : 'Nova solicitação' }
  ];

  private readonly passoUrl = bindStepToUrl(this.activeStep);
  /** Passo da URL, reaplicado quando o formulário restaurado fica pronto. */
  private passoRestaurado: number | null = null;
  private rascunhoPendente: RascunhoSolicitacaoFinanceira | null = null;
  // Só em memória: valores de pagamento (PIX, conta, documento do favorecido) são sensíveis.
  private readonly rascunho = createFormDraft<RascunhoSolicitacaoFinanceira>({
    id: this.id ?? 'nova',
    persist: false,
    // Sem alteração do usuário (form não sujo) não há rascunho: a edição recarrega do servidor.
    read: () =>
      this.rascunhoPendente ?? {
        varianteId: this.form.dirty ? (this.schema()?.id ?? null) : null,
        valores: this.form.dirty ? this.form.getRawValue() : null,
        valoresExibicao: this.form.dirty ? this.valoresExibicao() : {},
        camposHerdados: this.form.dirty ? [...this.camposHerdados()] : [],
        compraVinculada: this.form.dirty ? this.compraVinculada() : null,
      },
    write: (valor) => (this.rascunhoPendente = valor),
  });

  constructor() {
    const restaurado = this.rascunho.restore();
    this.passoUrl.restore((passo) => {
      if (restaurado && this.rascunhoPendente?.varianteId && (passo === 2 || passo === 3)) {
        this.passoRestaurado = passo;
      }
      // O passo só é aplicado com o formulário montado (formularioPronto).
      return false;
    });
    if (this.id) {
      this.carregarEdicao(this.id);
    } else {
      this.service.listarVariantes(this.tipo).subscribe({
        next: (variantes) => {
          this.variantes.set(variantes.filter((item) => item.ativo));
          this.varianteSelecionada.set(variantes.find((item) => item.ativo && item.padrao)?.id ?? null);
          this.loading.set(false);
          const variante = this.variantes().find((item) => item.id === this.rascunhoPendente?.varianteId);
          if (variante && this.passoRestaurado) {
            this.varianteSelecionada.set(variante.id);
            this.selecionarVariante(variante);
          } else {
            this.rascunhoPendente = null;
          }
        },
        error: (error) => this.falhaCarregamento(error)
      });
    }
  }

  protected selecionarVariante(variante: FormularioResumo): void {
    this.loading.set(true);
    this.service.buscarSchema(variante.id).subscribe({
      next: (schema) => {
        this.schema.set(schema);
        this.activeStep.set(2);
        this.loading.set(false);
      },
      error: (error) => this.falhaCarregamento(error)
    });
  }

  protected avancarModelo(): void {
    const variante = this.variantes().find((item) => item.id === this.varianteSelecionada());
    if (variante) this.selecionarVariante(variante);
  }

  protected voltarModelo(): void {
    if (this.editando()) return;
    this.trocarVariante();
    this.activeStep.set(1);
  }

  protected irParaRevisao(): void {
    if (!this.preenchimentoValido('Revise o preenchimento')) return;
    this.activeStep.set(3);
  }

  /** A revisão repete os mesmos blocos do preenchimento, para o olho reconhecer. */
  protected gruposParaRevisao(): GrupoDeCampos[] {
    const isBoleto = ehFormaPagamentoBoleto(this.form, this.todosCampos());
    const blocos = agruparCampos(this.todosCampos(), (chave) => this.form.get(chave)?.value);
    if (!isBoleto) return blocos;
    return blocos
      .map((bloco) => ({
        ...bloco,
        campos: bloco.campos.filter((c) => !ehCampoDadosPagamento(c))
      }))
      .filter((bloco) => bloco.campos.length > 0);
  }

  protected valorResumo(campo: CampoFormulario): string | null {
    return textoDoValor(campo, this.form.getRawValue()[campo.chave], this.valoresExibicao());
  }

  /**
   * Guarda os rótulos das referências que o usuário acabou de escolher. Sem isso a
   * revisão mostraria o UUID: o formulário só conhece o valor bruto.
   */
  protected registrarExibicoes(exibicoes: Record<string, ValorSolicitacao>): void {
    this.valoresExibicao.update((atual) => ({ ...atual, ...exibicoes }));
  }

  protected formularioPronto(): void {
    if (this.solicitacao) {
      const valores = Object.fromEntries(
        Object.entries(this.solicitacao.valores).map(([chave, item]) => [
          chave,
          valorParaControle(this.campo(chave), item.valor)
        ])
      );
      this.form.patchValue(valores, { emitEvent: false });
    }
    const rascunho = this.rascunhoPendente;
    if (rascunho?.valores && rascunho.varianteId === this.schema()?.id) {
      this.form.patchValue(rascunho.valores, { emitEvent: false });
      this.form.markAsDirty();
      this.valoresExibicao.set(rascunho.valoresExibicao);
      this.camposHerdados.set(new Set(rascunho.camposHerdados));
      this.compraVinculada.set(rascunho.compraVinculada);
      if (this.passoRestaurado === 3) this.activeStep.set(3);
    }
    this.rascunhoPendente = null;
    this.passoRestaurado = null;
    this.observarCompraVinculada();
  }

  protected salvar(): void {
    if (!this.preenchimentoValido('Revise o formulário')) return;

    this.saving.set(true);
    const request = {
      formularioId: this.schema()!.id,
      valores: serializarValores(this.todosCampos(), this.form)
    };
    const operation = this.id
      ? this.service.atualizarSolicitacao(this.tipo, this.id, request)
      : this.service.criarSolicitacao(this.tipo, request);
    operation.subscribe({
      next: (solicitacao) => {
        this.saving.set(false);
        this.rascunho.clear();
        this.solicitacao = solicitacao;
        this.editando.set(true);
        this.activeStep.set(4);
        this.messages.add({
          severity: 'success',
          summary: 'Rascunho salvo',
          detail: 'Inclua os documentos necessários antes de enviar para aprovação.'
        });
      },
      error: (error) => this.tratarErroSalvar(error)
    });
  }

  protected trocarVariante(): void {
    this.schema.set(null);
    this.form.reset();
    for (const key of Object.keys(this.form.controls)) this.form.removeControl(key);
    // Outra variante tem outros campos: o que veio da compra deixa de valer.
    this.compraVinculada.set(null);
    this.camposHerdados.set(new Set());
    this.valoresExibicao.set({});
    this.errosServidor.set({});
  }

  protected voltar(): void {
    this.rascunho.clear();
    this.navegacao.back(['/financeiro', this.slug]);
  }

  protected atualizarRascunho(solicitacao: SolicitacaoFinanceira): void {
    this.solicitacao = solicitacao;
  }

  protected abrirDetalhe(): void {
    if (this.solicitacao) {
      this.router.navigate(['/financeiro', this.slug, this.solicitacao.id]);
    }
  }

  protected submeter(): void {
    if (!this.solicitacao) return;
    this.saving.set(true);
    this.service.submeter(this.tipo, this.solicitacao.id).subscribe({
      next: (solicitacao) => {
        this.saving.set(false);
        this.solicitacao = solicitacao;
        this.messages.add({
          severity: 'success',
          summary: 'Solicitação enviada para aprovação',
          detail: `Solicitação #${solicitacao.numero} encaminhada com sucesso.`
        });
        this.activeStep.set(5);
      },
      error: (error) => this.tratarErroSalvar(error)
    });
  }

  protected novaSolicitacao(): void {
    if (this.id) {
      void this.router.navigate(['/financeiro', this.slug, 'nova']).then(() => {
        this.reiniciarFormulario();
      });
      return;
    }
    this.reiniciarFormulario();
  }

  private reiniciarFormulario(): void {
    this.solicitacao = undefined;
    this.editando.set(false);
    this.schema.set(null);
    this.form.reset();
    for (const key of Object.keys(this.form.controls)) {
      this.form.removeControl(key);
    }
    this.compraVinculada.set(null);
    this.camposHerdados.set(new Set());
    this.valoresExibicao.set({});
    this.errosServidor.set({});
    const padrao = this.variantes().find((item) => item.ativo && item.padrao)?.id ?? null;
    this.varianteSelecionada.set(padrao);
    this.activeStep.set(1);
  }

  private carregarEdicao(id: string): void {
    this.service.buscarSolicitacao(this.tipo, id).subscribe({
      next: (solicitacao) => {
        this.solicitacao = solicitacao;
        this.varianteSelecionada.set(solicitacao.formulario.id);
        this.activeStep.set(2);
        this.valoresExibicao.set(solicitacao.valores);
        if (!solicitacao.acoes.podeEditar) {
          this.router.navigate(['/financeiro', this.slug, id]);
          return;
        }
        this.service.buscarSchema(solicitacao.formulario.id).subscribe({
          next: (schema) => {
            this.schema.set(schema);
            this.loading.set(false);
          },
          error: (error) => this.falhaCarregamento(error)
        });
      },
      error: (error) => this.falhaCarregamento(error)
    });
  }

  private observarCompraVinculada(): void {
    if (this.tipo !== 'PAGAMENTO' || this.observandoCompra) return;
    const control = this.form.get('solicitacaoCompraId');
    if (!control) return;
    this.observandoCompra = true;
    control.valueChanges
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        distinctUntilChanged(),
        filter((value): value is string => typeof value === 'string' && !!value)
      )
      .subscribe((compraId) => {
        this.service.buscarPrePreenchimento(compraId, this.schema()!.id).subscribe({
          next: (response) => {
            const valores = Object.fromEntries(
              Object.entries(response.valores).map(([chave, item]) => [
                chave,
                valorParaControle(this.campo(chave), item.valor)
              ])
            );
            this.camposHerdados.set(new Set(response.camposHerdados));
            this.valoresExibicao.update((atual) => ({ ...atual, ...response.valores }));
            this.compraVinculada.set(response.compra);
            this.form.patchValue(valores, { emitEvent: false });
          },
          error: (error) => {
            control.setValue(null, { emitEvent: false });
            this.compraVinculada.set(null);
            this.camposHerdados.set(new Set());
            this.messages.add({
              severity: 'error',
              summary: 'Compra indisponível',
              detail: getErrorMessage(error)
            });
          }
        });
      });
  }

  /**
   * Confere o preenchimento antes de avançar. As regras vêm todas de
   * `campos-financeiros`; aqui só se decide o que fazer com o resultado.
   */
  private preenchimentoValido(resumo: string): boolean {
    this.errosServidor.set({});
    const campos = this.todosCampos();
    aplicarValidacaoCondicional(this.form, campos);
    this.form.markAllAsTouched();

    const cruzada = regrasCruzadas(campos, this.form);
    if (cruzada) {
      this.errosServidor.set(
        Object.fromEntries(cruzada.chaves.map((chave) => [chave, cruzada.mensagem]))
      );
    }
    if (this.form.invalid || cruzada) {
      this.messages.add({
        severity: 'warn',
        summary: resumo,
        detail: cruzada?.mensagem ?? 'Existem campos obrigatórios ou inválidos.'
      });
      return false;
    }
    return true;
  }

  private todosCampos(): CampoFormulario[] {
    return this.schema()?.campos ?? [];
  }

  private campo(chave: string): CampoFormulario | undefined {
    return this.todosCampos().find((campo) => campo.chave === chave);
  }

  private tratarErroSalvar(error: { error?: FinanceiroProblemDetails }): void {
    this.saving.set(false);
    const erros = error.error?.erros ?? {};
    this.errosServidor.set(erros);
    if (Object.keys(erros).length) {
      for (const key of Object.keys(erros)) this.form.get(key)?.markAsTouched();
    }
    this.messages.add({
      severity: 'error',
      summary: 'Não foi possível salvar',
      detail: error.error?.detail || getErrorMessage(error)
    });
  }

  private falhaCarregamento(error: unknown): void {
    this.loading.set(false);
    this.messages.add({
      severity: 'error',
      summary: 'Não foi possível abrir o formulário',
      detail: getErrorMessage(error)
    });
  }

  protected idCurto(id?: string | null): string {
    if (!id) return '';
    return id.length > 8 ? id.slice(0, 8) : id;
  }

  private get slug(): string {
    return this.tipo === 'COMPRA' ? 'compras' : 'pagamentos';
  }
}
