import { DateMaskDirective } from '../../../shared/directives/date-mask.directive';
import { CommonModule } from '@angular/common';
import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';

import { MeuDesvioResponse, TratarMeuDesvioRequest } from '../../../core/models/meu-desvio.models';
import { GravidadeDesvio, StatusDesvio } from '../../../core/models/sst.models';
import { formatarDataLocal, parsearDataLocal } from './meus-desvios.utils';

@Component({
  selector: 'app-tratamento-meu-desvio-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    DatePickerModule,
    DialogModule,
    TextareaModule,
    TooltipModule,
    DateMaskDirective
  ],
  template: `
    <p-dialog
      [header]="modoLote ? 'Tratar desvios selecionados em lote' : 'Tratamento do desvio'"
      [(visible)]="visible"
      [modal]="true"
      [style]="{ width: '540px' }"
      [breakpoints]="{ '640px': '96vw' }"
      [draggable]="false"
      [resizable]="false"
      styleClass="custom-dialog dialogo-tratamento"
      appendTo="body"
    >
      @if (modoLote) {
        <div class="mb-4 rounded-xl border border-primary-100 dark:border-primary-800/60 bg-primary-50/70 dark:bg-primary-950/30 p-3 text-[11px] text-primary-800 dark:text-primary-300 flex items-center gap-2">
          <i class="pi pi-check-square text-base text-primary-700 dark:text-primary-400"></i>
          <span>A mesma tratativa será aplicada atomicamente a <strong>{{ quantidade }}</strong> desvio(s) selecionado(s).</span>
        </div>
      } @else if (item; as desvio) {
        <div class="mb-4 rounded-xl bg-slate-50 dark:bg-slate-800/70 border border-slate-100 dark:border-slate-800 p-3.5">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <div class="text-[11px] font-semibold text-slate-800 dark:text-slate-100">
                {{ desvio.pergunta || 'Pergunta não identificada' }}
              </div>
              @if (desvio.descricao) {
                <div class="mt-1 text-[10px] text-slate-500 dark:text-slate-400 line-clamp-3">
                  {{ desvio.descricao }}
                </div>
              }
            </div>
            @if (desvio.gravidade) {
              <div class="shrink-0">
                <span
                  class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-bold border shadow-2xs whitespace-nowrap"
                  [ngClass]="gravidadeClass(desvio.gravidade)"
                >
                  <i [class]="gravidadeIcon(desvio.gravidade)"></i>
                  {{ desvio.gravidade }}
                </span>
              </div>
            }
          </div>
          @if (desvio.prazo) {
            <div class="mt-2 pt-2 border-t border-slate-200/60 dark:border-slate-700/60 flex items-center gap-2 text-[10px] text-slate-500 dark:text-slate-400">
              <i class="pi pi-calendar text-[10px]"></i>
              <span>Prazo atual: <strong>{{ desvio.prazo | date: 'dd/MM/yyyy' }}</strong></span>
              @if (desvio.vencido) {
                <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wide bg-rose-600 text-white shadow-xs">
                  <i class="pi pi-clock text-[9px]"></i> Vencido
                </span>
              }
            </div>
          }
        </div>
      }

      <div class="flex flex-col gap-4">
        <!-- Seletor visual de situação (Cards de escolha rápida) -->
        <div class="flex flex-col gap-1.5">
          <label class="filter-label !text-slate-700 dark:!text-slate-300 !text-[11px] !font-semibold">
            Qual é a situação deste desvio? <span class="text-red-500" aria-label="obrigatório">*</span>
          </label>
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-2">
            <!-- Opção: Já corrigido -->
            <button
              type="button"
              (click)="selecionarStatus('RESOLVIDO')"
              [class]="status === 'RESOLVIDO'
                ? 'border-emerald-600 bg-emerald-50 dark:bg-emerald-950/40 text-emerald-900 dark:text-emerald-200 ring-2 ring-emerald-500/30'
                : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 hover:border-emerald-300 dark:hover:border-emerald-700'"
              class="p-2.5 rounded-xl border flex flex-col items-start gap-1 text-left transition-all cursor-pointer"
            >
              <div class="flex items-center gap-1.5 w-full">
                <i class="pi pi-check-circle text-sm text-emerald-600 dark:text-emerald-400"></i>
                <span class="text-[11px] font-bold">Já corrigido</span>
              </div>
              <span class="text-[9px] text-slate-500 dark:text-slate-400 leading-tight">Solucionado e finalizado</span>
            </button>

            <!-- Opção: Em tratamento -->
            <button
              type="button"
              (click)="selecionarStatus('EM_TRATAMENTO')"
              [class]="status === 'EM_TRATAMENTO'
                ? 'border-amber-500 bg-amber-50 dark:bg-amber-950/40 text-amber-900 dark:text-amber-200 ring-2 ring-amber-500/30'
                : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 hover:border-amber-300 dark:hover:border-amber-700'"
              class="p-2.5 rounded-xl border flex flex-col items-start gap-1 text-left transition-all cursor-pointer"
            >
              <div class="flex items-center gap-1.5 w-full">
                <i class="pi pi-wrench text-sm text-amber-600 dark:text-amber-400"></i>
                <span class="text-[11px] font-bold">Em tratamento</span>
              </div>
              <span class="text-[9px] text-slate-500 dark:text-slate-400 leading-tight">Definir plano e prazo</span>
            </button>

            <!-- Opção: Em aberto -->
            <button
              type="button"
              (click)="selecionarStatus('ABERTO')"
              [class]="status === 'ABERTO'
                ? 'border-rose-500 bg-rose-50 dark:bg-rose-950/40 text-rose-900 dark:text-rose-200 ring-2 ring-rose-500/30'
                : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 hover:border-rose-300 dark:hover:border-rose-700'"
              class="p-2.5 rounded-xl border flex flex-col items-start gap-1 text-left transition-all cursor-pointer"
            >
              <div class="flex items-center gap-1.5 w-full">
                <i class="pi pi-exclamation-circle text-sm text-rose-500 dark:text-rose-400"></i>
                <span class="text-[11px] font-bold">Em aberto</span>
              </div>
              <span class="text-[9px] text-slate-500 dark:text-slate-400 leading-tight">Ainda não iniciado</span>
            </button>
          </div>
        </div>

        <!-- Formulários dinâmicos conforme o status selecionado -->
        @if (status === 'RESOLVIDO') {
          <div class="flex flex-col gap-3 animate-in fade-in duration-300">
            <div class="flex items-center gap-2 p-2.5 rounded-lg bg-emerald-50/70 dark:bg-emerald-950/30 border border-emerald-100 dark:border-emerald-800/40 text-emerald-800 dark:text-emerald-300 text-[10px]">
              <i class="pi pi-info-circle shrink-0"></i>
              <span>Ao marcar como corrigido, o desvio é encerrado com a data e hora de resolução atuais.</span>
            </div>

            <div class="flex flex-col gap-1">
              <label class="filter-label !text-slate-600 dark:!text-slate-300 !text-[11px]">
                O que foi realizado para sanar o desvio? <span class="text-red-500" aria-label="obrigatório">*</span>
              </label>
              <textarea
                pTextarea
                [(ngModel)]="acao"
                rows="3"
                class="w-full custom-input !text-[11px]"
                placeholder="Descreva a ação corretiva que solucionou a irregularidade..."
              ></textarea>
            </div>
          </div>
        }

        @if (status === 'EM_TRATAMENTO') {
          <div class="flex flex-col gap-3 animate-in fade-in duration-300">
            <div class="flex items-center gap-2 p-2.5 rounded-lg bg-amber-50/70 dark:bg-amber-950/30 border border-amber-100 dark:border-amber-800/40 text-amber-800 dark:text-amber-300 text-[10px]">
              <i class="pi pi-info-circle shrink-0"></i>
              <span>Defina a data limite para a conclusão do tratamento e o plano de ação correspondente.</span>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div class="flex flex-col gap-1 sm:col-span-1">
                <label class="filter-label !text-slate-600 dark:!text-slate-300 !text-[11px]">
                  Prazo para conclusão <span class="text-red-500" aria-label="obrigatório">*</span>
                </label>
                <p-datepicker
                  [(ngModel)]="prazo"
                  dateFormat="dd/mm/yy"
                  dateMask
                  [showIcon]="true"
                  iconDisplay="input"
                  [showOnFocus]="true"
                  [showButtonBar]="true"
                  placeholder="dd/mm/aaaa"
                  appendTo="body"
                  class="custom-date w-full"
                  size="small"
                />
              </div>
            </div>

            <div class="flex flex-col gap-1">
              <label class="filter-label !text-slate-600 dark:!text-slate-300 !text-[11px]">
                Ação corretiva planejada
              </label>
              <textarea
                pTextarea
                [(ngModel)]="acao"
                rows="3"
                class="w-full custom-input !text-[11px]"
                placeholder="Descreva as medidas que serão adotadas para corrigir a irregularidade..."
              ></textarea>
            </div>
          </div>
        }

        @if (status === 'ABERTO') {
          <div class="flex flex-col gap-3 animate-in fade-in duration-300">
            <div class="flex items-center gap-2 p-2.5 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 text-[10px]">
              <i class="pi pi-info-circle shrink-0"></i>
              <span>O desvio permanecerá em aberto na fila de pendências até que o tratamento seja iniciado.</span>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div class="flex flex-col gap-1 sm:col-span-1">
                <label class="filter-label !text-slate-600 dark:!text-slate-300 !text-[11px]">
                  Prazo estimado (opcional)
                </label>
                <p-datepicker
                  [(ngModel)]="prazo"
                  dateFormat="dd/mm/yy"
                  dateMask
                  [showIcon]="true"
                  iconDisplay="input"
                  [showOnFocus]="true"
                  [showButtonBar]="true"
                  placeholder="dd/mm/aaaa"
                  appendTo="body"
                  class="custom-date w-full"
                  size="small"
                />
              </div>
            </div>

            <div class="flex flex-col gap-1">
              <label class="filter-label !text-slate-600 dark:!text-slate-300 !text-[11px]">
                Observações / anotações (opcional)
              </label>
              <textarea
                pTextarea
                [(ngModel)]="acao"
                rows="2"
                class="w-full custom-input !text-[11px]"
                placeholder="Anotações adicionais sobre o desvio..."
              ></textarea>
            </div>
          </div>
        }
      </div>

      <ng-template pTemplate="footer">
        <div class="flex items-center justify-end gap-2 w-full pt-2">
          <p-button
            label="CANCELAR"
            severity="secondary"
            [text]="true"
            (onClick)="visible = false"
            styleClass="!font-bold !text-[10px]"
          />
          <p-button
            label="SALVAR TRATAMENTO"
            icon="pi pi-check"
            [loading]="loading()"
            [disabled]="botaoSalvarDesabilitado()"
            (onClick)="confirmar()"
            styleClass="!rounded-lg !bg-primary-700 !border-primary-700 !py-2.5 !px-6 !font-bold !text-[10px]"
          />
        </div>
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    :host ::ng-deep {
      .dialogo-tratamento .p-dialog-content { padding: 1.25rem !important; }
      .p-datepicker, .p-datepicker-input { border-radius: 8px !important; }
      .p-datepicker-input { height: 30px !important; font-size: 10px !important; }
      .p-datepicker-button-icon { font-size: 10px !important; }
      textarea.p-inputtext { border-radius: 8px !important; padding: 0.5rem 0.75rem !important; }
    }
  `]
})
export class TratamentoMeuDesvioDialogComponent {
  readonly loading = input(false);
  readonly salvar = output<TratarMeuDesvioRequest>();

  protected visible = false;
  protected modoLote = false;
  protected quantidade = 0;
  protected item: any = null;
  protected status: StatusDesvio = 'EM_TRATAMENTO';
  protected acao = '';
  protected prazo: Date | null = null;

  open(item: any | null, quantidade = 0) {
    this.item = item;
    this.modoLote = item === null;
    this.quantidade = quantidade;
    this.status = item?.status ?? 'EM_TRATAMENTO';
    this.acao = item?.acaoCorretiva ?? '';
    this.prazo = parsearDataLocal(item?.prazo ?? null);
    this.visible = true;
  }

  close() {
    this.visible = false;
  }

  protected selecionarStatus(novoStatus: StatusDesvio) {
    this.status = novoStatus;
  }

  protected botaoSalvarDesabilitado(): boolean {
    if (this.modoLote && (this.quantidade < 1 || this.quantidade > 100)) {
      return true;
    }
    if (this.status === 'RESOLVIDO' && !this.acao.trim()) {
      return true;
    }
    return false;
  }

  protected confirmar() {
    const request: TratarMeuDesvioRequest = { status: this.status };
    const acao = this.acao.trim();
    const prazo = formatarDataLocal(this.prazo);

    if (this.status === 'RESOLVIDO') {
      if (acao) request.acaoCorretiva = acao;
      // Ao marcar como resolvido, o desvio está sanado; não envia prazo futuro
    } else {
      if (acao) request.acaoCorretiva = acao;
      if (prazo) request.prazo = prazo;
    }

    this.salvar.emit(request);
  }

  protected gravidadeClass(value: GravidadeDesvio): string {
    switch (value) {
      case 'CRITICA':
        return 'bg-red-100 text-red-900 border-red-300 dark:bg-red-950/60 dark:text-red-200 dark:border-red-800/80';
      case 'ALTA':
        return 'bg-orange-100 text-orange-900 border-orange-300 dark:bg-orange-950/60 dark:text-orange-200 dark:border-orange-800/80';
      case 'MEDIA':
        return 'bg-amber-100 text-amber-900 border-amber-300 dark:bg-amber-950/60 dark:text-amber-200 dark:border-amber-800/80';
      case 'BAIXA':
        return 'bg-slate-100 text-slate-800 border-slate-300 dark:bg-slate-800 dark:text-slate-200 dark:border-slate-700';
      default:
        return 'bg-slate-100 text-slate-800 border-slate-300 dark:bg-slate-800 dark:text-slate-200 dark:border-slate-700';
    }
  }

  protected gravidadeIcon(value: GravidadeDesvio): string {
    switch (value) {
      case 'CRITICA':
        return 'pi pi-times-circle text-red-600 dark:text-red-400';
      case 'ALTA':
        return 'pi pi-exclamation-triangle text-orange-600 dark:text-orange-400';
      case 'MEDIA':
        return 'pi pi-exclamation-circle text-amber-600 dark:text-amber-400';
      case 'BAIXA':
        return 'pi pi-info-circle text-slate-500 dark:text-slate-400';
      default:
        return 'pi pi-circle-fill text-[8px] text-slate-500';
    }
  }
}

