import { Component, input, output, ViewChild, signal, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DrawerModule } from 'primeng/drawer';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageModule } from 'primeng/message';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';

import { SstService } from '../../../core/services/sst.service';
import { DesvioDetalhadoResponse } from '../../../core/models/desvio.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import {
  GRAVIDADE_LABELS,
  GravidadeDesvio,
  STATUS_DESVIO_LABELS,
  StatusDesvio
} from '../../../core/models/sst.models';
import { DesvioAnexosComponent } from '../inspecoes/desvio-anexos.component';
import { TratamentoMeuDesvioDialogComponent } from '../meus-desvios/tratamento-meu-desvio-dialog.component';
import { TratarMeuDesvioRequest } from '../../../core/models/meu-desvio.models';

@Component({
  selector: 'app-desvio-detalhe-dialog',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    RouterModule,
    ButtonModule,
    DrawerModule,
    ProgressSpinnerModule,
    MessageModule,
    TooltipModule,
    DesvioAnexosComponent,
    TratamentoMeuDesvioDialogComponent
  ],
  providers: [MessageService],
  template: `
    <p-drawer
      [(visible)]="visible"
      [modal]="true"
      position="right"
      [closable]="false"
      styleClass="!w-full md:!w-[720px] !p-0 custom-drawer"
    >
      <ng-template #headless>
        @if (loading()) {
          <div class="flex items-center justify-center h-full bg-white dark:bg-slate-900">
            <p-progressSpinner strokeWidth="4" />
          </div>
        } @else if (error()) {
          <div class="p-6 h-full flex flex-col items-center justify-center gap-4 bg-white dark:bg-slate-900">
            <p-message severity="error" [text]="error() || ''" />
            <p-button label="Fechar" icon="pi pi-times" [text]="true" (onClick)="close()" />
          </div>
        } @else if (desvio(); as d) {
          <div class="flex flex-col h-full bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100">
            <!-- Cabeçalho (Padrão de Relatório da empresa / SST) -->
            <div class="p-4 md:px-6 md:py-4 bg-slate-50 dark:bg-slate-850 border-b border-slate-200 dark:border-slate-700/80 flex items-start justify-between gap-4 shrink-0">
              <div class="flex flex-col gap-2">
                <div class="flex items-center gap-2">
                  <span class="text-[9px] font-bold text-slate-500 dark:text-slate-300 uppercase tracking-widest">
                    Desvio de Segurança
                  </span>
                  <span class="text-slate-300 dark:text-slate-600">·</span>
                  <span class="text-[9px] text-slate-600 dark:text-slate-300 font-mono font-bold">Nº {{ d.id.substring(0, 8) | uppercase }}</span>
                </div>
                <h2 class="text-base md:text-lg font-bold text-slate-900 dark:text-white uppercase tracking-tight">
                  Relatório de Desvio
                </h2>
                <div class="flex flex-wrap gap-2 items-center">
                  <span
                    class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-bold border shadow-2xs whitespace-nowrap"
                    [ngClass]="gravidadeClass(d.gravidade)"
                  >
                    <i [class]="gravidadeIcon(d.gravidade)"></i>
                    {{ gravidadeLabel(d.gravidade) }}
                  </span>
                  <span
                    class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-bold border shadow-2xs whitespace-nowrap"
                    [ngClass]="statusClass(d.status)"
                  >
                    <i [class]="statusIcon(d.status)"></i>
                    {{ statusLabel(d.status) }}
                  </span>
                  @if (d.vencido) {
                    <span class="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[11px] font-bold uppercase tracking-wide bg-rose-600 text-white shadow-xs whitespace-nowrap">
                      <i class="pi pi-clock text-[10px]"></i> Vencido há {{ d.diasEmAtraso }} dias
                    </span>
                  }
                </div>
              </div>
              <p-button
                icon="pi pi-times"
                [rounded]="true"
                [text]="true"
                severity="secondary"
                pTooltip="Fechar"
                (onClick)="close()"
                styleClass="!w-8 !h-8 !text-slate-400 hover:!text-slate-800 dark:hover:!text-white"
              />
            </div>

            <!-- Conteúdo com Scroll -->
            <div class="p-4 md:p-6 flex-grow overflow-y-auto flex flex-col gap-5">
              
              <!-- Seção 1: O Desvio (Não Conformidade Apontada) -->
              <div class="bg-slate-50/70 dark:bg-slate-800/60 p-4 md:p-5 rounded-xl border border-slate-200 dark:border-slate-700/80 shadow-xs dark:shadow-md flex flex-col gap-3.5">
                <div class="flex items-center justify-between border-b border-slate-200 dark:border-slate-700/80 pb-2.5">
                  <div class="flex items-center gap-2">
                    <i class="pi pi-exclamation-triangle text-amber-600 dark:text-amber-400 text-xs"></i>
                    <span class="text-[10px] font-bold text-slate-800 dark:text-slate-100 uppercase tracking-widest">O Desvio</span>
                  </div>
                  @if (d.grupo) {
                    <span class="text-[9px] font-semibold text-slate-600 dark:text-slate-200 uppercase tracking-wider bg-slate-200/70 dark:bg-slate-700 px-2 py-0.5 rounded border border-slate-300/60 dark:border-slate-600">
                      {{ d.grupo }}
                    </span>
                  }
                </div>

                <div class="flex flex-col gap-3">
                  <div>
                    <span class="text-[9px] font-bold text-slate-500 dark:text-slate-300 uppercase tracking-widest block mb-1">Item / Pergunta</span>
                    <span class="text-xs md:text-sm font-bold text-slate-900 dark:text-white leading-snug">{{ d.pergunta }}</span>
                  </div>

                  <div>
                    <span class="text-[9px] font-bold text-slate-500 dark:text-slate-300 uppercase tracking-widest block mb-1">Opção Marcada</span>
                    <span class="inline-flex items-center gap-1.5 bg-red-50 dark:bg-red-950/70 border border-red-200 dark:border-red-800/80 text-red-700 dark:text-red-200 text-xs font-bold px-2.5 py-1 rounded-md">
                      <i class="pi pi-times-circle text-xs"></i> {{ d.opcaoResposta }}
                    </span>
                  </div>

                  @if (d.descricao) {
                    <div>
                      <span class="text-[9px] font-bold text-slate-500 dark:text-slate-300 uppercase tracking-widest block mb-1">Descrição Detalhada</span>
                      <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-3 text-xs md:text-sm text-slate-800 dark:text-slate-100 leading-relaxed whitespace-pre-wrap">
                        {{ d.descricao }}
                      </div>
                    </div>
                  }
                </div>
              </div>

              <!-- Seção 2: Tratamento & Ação Corretiva -->
              <div class="bg-slate-50/70 dark:bg-slate-800/60 p-4 md:p-5 rounded-xl border border-slate-200 dark:border-slate-700/80 shadow-xs dark:shadow-md flex flex-col gap-3.5">
                <div class="flex items-center justify-between border-b border-slate-200 dark:border-slate-700/80 pb-2.5">
                  <div class="flex items-center gap-2">
                    <i class="pi pi-wrench text-primary-600 dark:text-primary-400 text-xs"></i>
                    <span class="text-[10px] font-bold text-slate-800 dark:text-slate-100 uppercase tracking-widest">Tratamento</span>
                  </div>
                  <span
                    class="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-md text-[10px] font-bold border uppercase tracking-wider"
                    [ngClass]="statusClass(d.status)"
                  >
                    {{ statusLabel(d.status) }}
                  </span>
                </div>

                <div class="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-2">
                  <div class="flex justify-between items-center gap-2 py-1.5 border-b border-slate-200/60 dark:border-slate-700/60">
                    <span class="text-[10px] font-bold text-slate-500 dark:text-slate-300">Prazo</span>
                    <span class="text-[11px] font-semibold text-slate-900 dark:text-white">
                      {{ d.prazo ? (d.prazo | date:'dd/MM/yyyy') : 'Sem prazo' }}
                    </span>
                  </div>

                  <div class="flex justify-between items-center gap-2 py-1.5 border-b border-slate-200/60 dark:border-slate-700/60">
                    <span class="text-[10px] font-bold text-slate-500 dark:text-slate-300">Resolvido em</span>
                    <span class="text-[11px] font-semibold text-slate-900 dark:text-white">
                      {{ d.dataResolucao ? (d.dataResolucao | date:'dd/MM/yyyy HH:mm') : '—' }}
                    </span>
                  </div>

                  <div class="flex justify-between items-center gap-2 py-1.5 border-b border-slate-200/60 dark:border-slate-700/60 md:col-span-2">
                    <span class="text-[10px] font-bold text-slate-500 dark:text-slate-300">Última atualização</span>
                    <span class="text-[11px] font-semibold text-slate-900 dark:text-white">
                      {{ d.dataUltimaAlteracao ? (d.dataUltimaAlteracao | date:'dd/MM/yyyy HH:mm') : '—' }}
                    </span>
                  </div>
                </div>

                <div class="pt-1">
                  <span class="text-[9px] font-bold text-slate-500 dark:text-slate-300 uppercase tracking-widest block mb-1">Ação Corretiva</span>
                  <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-3 text-xs md:text-sm text-slate-800 dark:text-slate-100 leading-relaxed whitespace-pre-wrap">
                    {{ d.acaoCorretiva || 'Pendente de tratamento.' }}
                  </div>
                </div>
              </div>

              <!-- Seção 3: Identificação & Contexto Operacional (Onde aconteceu) -->
              <div class="bg-slate-50/70 dark:bg-slate-800/60 p-4 md:p-5 rounded-xl border border-slate-200 dark:border-slate-700/80 shadow-xs dark:shadow-md flex flex-col gap-3.5">
                <div class="flex items-center justify-between border-b border-slate-200 dark:border-slate-700/80 pb-2.5">
                  <div class="flex items-center gap-2">
                    <i class="pi pi-sitemap text-primary-600 dark:text-primary-400 text-xs"></i>
                    <span class="text-[10px] font-bold text-slate-800 dark:text-slate-100 uppercase tracking-widest">Contexto da Inspeção</span>
                  </div>
                  <a
                    [routerLink]="['/sst/inspecoes', d.idInspecao]"
                    (click)="close()"
                    class="text-[11px] text-primary-700 dark:text-primary-300 hover:text-primary-800 dark:hover:text-primary-200 font-bold hover:underline inline-flex items-center gap-1"
                  >
                    <span>Ver relatório completo</span>
                    <i class="pi pi-external-link text-[10px]"></i>
                  </a>
                </div>

                <div class="grid grid-cols-1 md:grid-cols-2 gap-x-6 gap-y-2">
                  <div class="flex justify-between items-center gap-2 py-1.5 border-b border-slate-200/60 dark:border-slate-700/60">
                    <span class="text-[10px] font-bold text-slate-500 dark:text-slate-300">Processo da Inspeção</span>
                    <span class="text-[11px] font-semibold text-slate-900 dark:text-white truncate">{{ d.processo?.nome || '—' }}</span>
                  </div>

                  <div class="flex justify-between items-center gap-2 py-1.5 border-b border-slate-200/60 dark:border-slate-700/60">
                    <span class="text-[10px] font-bold text-slate-500 dark:text-slate-300">Processo do Inspetor</span>
                    <span class="text-[11px] font-semibold text-slate-900 dark:text-white truncate">{{ d.processoInspetor?.nome || '—' }}</span>
                  </div>

                  <div class="flex justify-between items-center gap-2 py-1.5 border-b border-slate-200/60 dark:border-slate-700/60">
                    <span class="text-[10px] font-bold text-slate-500 dark:text-slate-300">Base Operacional</span>
                    <span class="text-[11px] font-semibold text-slate-900 dark:text-white truncate">{{ d.baseOperacional?.nome || '—' }}</span>
                  </div>

                  <div class="flex justify-between items-center gap-2 py-1.5 border-b border-slate-200/60 dark:border-slate-700/60">
                    <span class="text-[10px] font-bold text-slate-500 dark:text-slate-300">Equipe</span>
                    <span class="text-[11px] font-semibold text-slate-900 dark:text-white">{{ d.equipe ? 'Equipe ' + d.equipe.codigo : 'Individual' }}</span>
                  </div>

                  <div class="flex justify-between items-center gap-2 py-1.5 border-b border-slate-200/60 dark:border-slate-700/60">
                    <span class="text-[10px] font-bold text-slate-500 dark:text-slate-300">Veículo</span>
                    <span class="text-[11px] font-semibold text-slate-900 dark:text-white">{{ d.veiculo?.placa || '—' }}</span>
                  </div>

                  <div class="flex justify-between items-center gap-2 py-1.5 border-b border-slate-200/60 dark:border-slate-700/60">
                    <span class="text-[10px] font-bold text-slate-500 dark:text-slate-300">Inspetor</span>
                    <span class="text-[11px] font-semibold text-slate-900 dark:text-white truncate">{{ d.inspetor || '—' }}</span>
                  </div>

                  <div class="flex justify-between items-center gap-2 py-1.5 border-b border-slate-200/60 dark:border-slate-700/60">
                    <span class="text-[10px] font-bold text-slate-500 dark:text-slate-300">Data da Inspeção</span>
                    <span class="text-[11px] font-semibold text-slate-900 dark:text-white">{{ d.dataInspecao | date:'dd/MM/yyyy HH:mm' }}</span>
                  </div>
                </div>
              </div>

              <!-- Seção 4: Colaboradores Inspecionados / Atribuídos -->
              <div class="bg-slate-50/70 dark:bg-slate-800/60 p-4 md:p-5 rounded-xl border border-slate-200 dark:border-slate-700/80 shadow-xs dark:shadow-md flex flex-col gap-3.5">
                <div class="flex items-center gap-2 border-b border-slate-200 dark:border-slate-700/80 pb-2.5">
                  <i class="pi pi-users text-primary-600 dark:text-primary-400 text-xs"></i>
                  <span class="text-[10px] font-bold text-slate-800 dark:text-slate-100 uppercase tracking-widest">
                    Responsáveis Atribuídos ({{ d.colaboradores.length }})
                  </span>
                </div>

                @if (d.colaboradores.length) {
                  <div class="flex flex-wrap gap-2.5 pt-1">
                    @for (c of d.colaboradores; track c.id) {
                      <span class="inline-flex items-center gap-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-100 text-[11px] font-bold px-3 py-1.5 rounded-lg shadow-2xs">
                        <i class="pi pi-user text-[10px] text-primary-600 dark:text-primary-400"></i>
                        <span>{{ c.nomeCompleto }}</span>
                        @if (c.funcao) {
                          <span class="text-[10px] text-slate-500 dark:text-slate-300 font-medium">· {{ c.funcao }}</span>
                        }
                        @if (c.baseOperacional) {
                          <span class="text-[9px] text-slate-600 dark:text-slate-200 bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 px-1.5 py-0.5 rounded font-medium ml-1">
                            {{ c.baseOperacional }}
                          </span>
                        }
                        @if (!c.ativo) {
                          <span class="text-[8px] font-bold bg-slate-200 dark:bg-slate-700 text-slate-600 dark:text-slate-300 px-1.5 py-0.5 rounded uppercase">
                            Inativo
                          </span>
                        }
                      </span>
                    }
                  </div>
                } @else {
                  <span class="text-xs text-slate-400 dark:text-slate-400 italic">Nenhum colaborador atribuído a este desvio.</span>
                }
              </div>

              <!-- Seção 5: Evidências (Anexos) -->
              <div class="bg-slate-50/70 dark:bg-slate-800/60 p-4 md:p-5 rounded-xl border border-slate-200 dark:border-slate-700/80 shadow-xs dark:shadow-md flex flex-col gap-3.5">
                <div class="flex items-center gap-2 border-b border-slate-200 dark:border-slate-700/80 pb-2.5">
                  <i class="pi pi-paperclip text-primary-600 dark:text-primary-400 text-xs"></i>
                  <span class="text-[10px] font-bold text-slate-800 dark:text-slate-100 uppercase tracking-widest">Evidências (Anexos)</span>
                </div>

                <app-desvio-anexos
                  [idInspecao]="d.idInspecao"
                  [idDesvio]="d.id"
                  [anexos]="d.anexos || []"
                  [canWrite]="canWrite()"
                ></app-desvio-anexos>
              </div>

            </div>

            <!-- Rodapé -->
            @if (canWrite()) {
              <div class="p-4 bg-white dark:bg-slate-900 border-t border-slate-200 dark:border-slate-700/80 shrink-0 flex justify-end">
                <p-button
                  label="Tratar Desvio"
                  icon="pi pi-wrench"
                  (onClick)="abrirTratamento()"
                  styleClass="!rounded-lg !bg-primary-700 !border-primary-700 hover:!bg-primary-800 !py-2.5 !px-6 !font-bold !text-xs !text-white"
                />
              </div>
            }
          </div>
        }
      </ng-template>
    </p-drawer>

    <!-- Modal de Tratamento reaproveitado -->
    <app-tratamento-meu-desvio-dialog
      #tratamentoDialog
      [loading]="salvandoTratamento()"
      (salvar)="salvarTratamento($event)"
    />
  `
})
export class DesvioDetalheDialogComponent {
  readonly canWrite = input<boolean>(false);
  readonly alterado = output<string>();

  @ViewChild('tratamentoDialog') tratamentoDialog!: TratamentoMeuDesvioDialogComponent;

  private readonly sst = inject(SstService);
  private readonly messages = inject(MessageService);

  protected visible = false;
  protected loading = signal(false);
  protected error = signal<string | null>(null);
  protected desvio = signal<DesvioDetalhadoResponse | null>(null);
  
  protected salvandoTratamento = signal(false);

  open(idDesvio: string): void {
    this.visible = true;
    this.loading.set(true);
    this.error.set(null);
    this.desvio.set(null);

    this.sst.buscarDesvioPorId(idDesvio).subscribe({
      next: (res) => {
        this.desvio.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(getErrorMessage(err) || 'Erro ao carregar detalhes do desvio.');
        this.loading.set(false);
      }
    });
  }

  close(): void {
    this.visible = false;
  }

  protected gravidadeLabel(value: GravidadeDesvio): string {
    return GRAVIDADE_LABELS[value] ?? value;
  }

  protected gravidadeClass(value: GravidadeDesvio): string {
    switch (value) {
      case 'CRITICA':
        return 'bg-red-100 text-red-900 border-red-300 dark:bg-red-950/80 dark:text-red-100 dark:border-red-700';
      case 'ALTA':
        return 'bg-orange-100 text-orange-900 border-orange-300 dark:bg-orange-950/80 dark:text-orange-100 dark:border-orange-700';
      case 'MEDIA':
        return 'bg-amber-100 text-amber-900 border-amber-300 dark:bg-amber-950/80 dark:text-amber-100 dark:border-amber-700';
      case 'BAIXA':
        return 'bg-slate-100 text-slate-800 border-slate-300 dark:bg-slate-800 dark:text-slate-100 dark:border-slate-600';
      default:
        return 'bg-slate-100 text-slate-800 border-slate-300 dark:bg-slate-800 dark:text-slate-100 dark:border-slate-600';
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

  protected statusLabel(value: StatusDesvio): string {
    return STATUS_DESVIO_LABELS[value] ?? value;
  }

  protected statusClass(value: StatusDesvio): string {
    switch (value) {
      case 'ABERTO':
        return 'bg-rose-100 text-rose-900 border-rose-300 dark:bg-rose-950/80 dark:text-rose-100 dark:border-rose-700';
      case 'EM_TRATAMENTO':
        return 'bg-amber-100 text-amber-900 border-amber-300 dark:bg-amber-950/80 dark:text-amber-100 dark:border-amber-700';
      case 'RESOLVIDO':
        return 'bg-emerald-100 text-emerald-900 border-emerald-300 dark:bg-emerald-950/80 dark:text-emerald-100 dark:border-emerald-700';
      default:
        return 'bg-slate-100 text-slate-800 border-slate-300 dark:bg-slate-800 dark:text-slate-100 dark:border-slate-600';
    }
  }

  protected statusIcon(value: StatusDesvio): string {
    switch (value) {
      case 'ABERTO':
        return 'pi pi-exclamation-circle text-rose-600 dark:text-rose-400';
      case 'EM_TRATAMENTO':
        return 'pi pi-wrench text-amber-600 dark:text-amber-400';
      case 'RESOLVIDO':
        return 'pi pi-check-circle text-emerald-600 dark:text-emerald-400';
      default:
        return 'pi pi-circle-fill text-[8px] text-slate-500';
    }
  }

  protected abrirTratamento(): void {
    const d = this.desvio();
    if (d) {
      this.tratamentoDialog.open(d as any);
    }
  }

  protected salvarTratamento(request: TratarMeuDesvioRequest): void {
    const d = this.desvio();
    if (!d) return;

    this.salvandoTratamento.set(true);
    
    const payload = {
      status: request.status,
      acaoCorretiva: request.acaoCorretiva || undefined,
      prazo: request.prazo
    };

    this.sst.tratarDesvio(d.idInspecao, d.id, payload).subscribe({
      next: () => {
        this.salvandoTratamento.set(false);
        this.tratamentoDialog.close();
        
        // Emite o alterado para a tela atualizar
        this.alterado.emit(d.id);
        
        // Atualiza a view no dialog local também
        this.open(d.id);
        
        this.messages.add({ severity: 'success', summary: 'Sucesso', detail: 'Desvio tratado com sucesso.' });
      },
      error: (err) => {
        this.salvandoTratamento.set(false);
        this.tratamentoDialog.close();
        this.messages.add({ severity: 'error', summary: 'Erro ao tratar', detail: getErrorMessage(err) || 'Falha ao salvar.' });
      }
    });
  }
}
