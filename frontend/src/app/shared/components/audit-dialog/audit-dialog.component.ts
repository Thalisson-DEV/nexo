import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { TimelineModule } from 'primeng/timeline';
import { AuditService } from '../../../core/services/audit.service';
import { AuditLogResponse } from '../../../core/models/shared.models';
import { finalize } from 'rxjs/operators';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-audit-dialog',
  standalone: true,
  imports: [
    CommonModule,
    DialogModule,
    TimelineModule,
    ButtonModule,
    TooltipModule
  ],
  template: `
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      [header]="title()"
      [style]="{ width: '95vw', maxWidth: '620px' }"
      [draggable]="false"
      [resizable]="false"
      styleClass="audit-dialog-custom"
      appendTo="body"
    >
      <div class="min-h-[300px] py-4">
        @if (loading()) {
          <div class="flex flex-col items-center justify-center gap-4 py-20">
            <i class="pi pi-spin pi-spinner text-4xl text-primary-600"></i>
            <span class="text-[10px] font-black text-[var(--ces-foreground-subtle)] uppercase tracking-widest">Buscando histórico...</span>
          </div>
        } @else if (error()) {
          <div class="flex flex-col items-center justify-center gap-4 py-20 text-center">
            <div class="w-16 h-16 bg-red-500/10 rounded-full flex items-center justify-center">
              <i class="pi pi-exclamation-circle text-2xl text-red-500"></i>
            </div>
            <div class="flex flex-col gap-1">
              <span class="text-sm font-bold text-[var(--ces-foreground)]">Não foi possível carregar os logs</span>
              <span class="text-[11px] text-[var(--ces-foreground-subtle)] px-10">Ocorreu um problema ao consultar o servidor de auditoria.</span>
            </div>
            <p-button label="Tentar Novamente" (onClick)="loadLogs()" [text]="true" styleClass="!text-primary-600 !font-bold !text-[10px]"></p-button>
          </div>
        } @else if (!logs()?.alteracoes?.length) {
          <div class="flex flex-col items-center justify-center gap-4 py-20 text-center">
            <div class="w-16 h-16 bg-[var(--ces-surface-subtle)] rounded-full flex items-center justify-center border border-[var(--ces-border)]">
              <i class="pi pi-history text-2xl text-[var(--ces-foreground-subtle)]"></i>
            </div>
            <span class="text-sm font-bold text-[var(--ces-foreground)]">Sem histórico disponível</span>
            <span class="text-[11px] text-[var(--ces-foreground-subtle)]">Este registro ainda não sofreu alterações registradas.</span>
          </div>
        } @else {
          <p-timeline [value]="logs()!.alteracoes" align="alternate" class="custom-timeline">
            <!-- Marcador Central (Ícone) -->
            <ng-template pTemplate="marker" let-log>
              <span
                class="flex w-9 h-9 items-center justify-center rounded-xl text-white z-10 transition-transform hover:scale-105"
                [ngClass]="getOperationClass(log)"
              >
                <i [class]="getOperationIcon(log) + ' text-[13px]'"></i>
              </span>
            </ng-template>

            <!-- Lado Oposto: Cabeçalho (Quem/Quando) -->
            <ng-template pTemplate="opposite" let-log>
               <div class="flex flex-col py-2">
                  <span class="text-[11px] font-medium text-[var(--ces-foreground)] uppercase tracking-tight">
                    {{ getFriendlyOperationName(log) }} por <span class="text-[var(--ces-foreground)] font-bold">{{ log.usuario }}</span>
                  </span>
                  <span class="text-[10px] text-[var(--ces-foreground-subtle)] font-medium">
                    {{ log.dataAlteracao | date: "EEEE, dd 'de' MMMM 'às' HH:mm" }}
                  </span>
                </div>
            </ng-template>

            <!-- Lado Principal: Card de Conteúdo -->
            <ng-template pTemplate="content" let-log>
              <div class="audit-log-card bg-[var(--ces-surface)] text-[var(--ces-foreground)] border border-[var(--ces-border)] rounded-xl p-4 shadow-sm flex flex-col gap-4 mb-12">
                @if (log.operacao === 'INSERT') {
                  <div class="flex items-center gap-3">
                    <div class="w-1 h-8 bg-green-500 rounded-full shrink-0"></div>
                    <span class="text-[11px] text-[var(--ces-foreground-muted)] font-medium text-left">Registro criado com sucesso no sistema.</span>
                  </div>
                } @else if (log.operacao === 'DELETE' || isDeactivation(log)) {
                  <div class="flex items-center gap-3 text-left">
                    <div class="w-1 h-8 bg-red-500 rounded-full shrink-0"></div>
                    <span class="text-[11px] text-[var(--ces-foreground-muted)] font-medium">
                      {{ isDeactivation(log) ? 'O registro foi desativado e não aparecerá mais em buscas ativas.' : 'O registro foi removido permanentemente.' }}
                    </span>
                  </div>
                } @else if (isReactivation(log)) {
                  <div class="flex items-center gap-3 text-left">
                    <div class="w-1 h-8 bg-green-500 rounded-full shrink-0"></div>
                    <span class="text-[11px] text-[var(--ces-foreground-muted)] font-medium">O registro foi reativado e está novamente disponível para buscas.</span>
                  </div>
                }

                @if (log.operacao === 'UPDATE') {
                  <div class="flex flex-col gap-2">
                    @for (campo of log.camposAlterados; track campo.campo) {
                      <div class="flex flex-col gap-1.5 p-2 rounded-xl hover:bg-[var(--ces-surface-subtle)] transition-colors border border-transparent hover:border-[var(--ces-border)] text-left">
                        <span class="text-[10px] font-medium text-[var(--ces-foreground-muted)] uppercase tracking-wider">
                           {{ getFriendlyFieldName(campo.campo) }}
                        </span>

                        <div class="flex items-center flex-wrap gap-2 text-[10px]">
                          @if (campo.valorAnterior !== undefined) {
                            <div class="bg-red-500/10 text-red-600 dark:text-red-400 px-2 py-1 rounded-md border border-red-500/20 line-through">
                              {{ formatValue(campo.valorAnterior) }}
                            </div>
                            <i class="pi pi-arrow-right text-[10px] text-[var(--ces-foreground-subtle)]"></i>
                          }
                          <div class="bg-green-500/10 text-green-700 dark:text-green-400 px-2 py-1 rounded-md border border-green-500/20 font-bold">
                            {{ formatValue(campo.valorNovo) }}
                          </div>
                        </div>
                      </div>
                    }
                  </div>
                }
              </div>
            </ng-template>
          </p-timeline>
        }
      </div>
    </p-dialog>
  `,
  styles: [`
    :host ::ng-deep {
      .audit-dialog-custom .p-dialog-header {
        padding: 1.5rem 1.5rem 0.5rem 1.5rem !important;
      }
      .audit-dialog-custom .p-dialog-content {
        padding: 0 1.5rem 1.5rem 1.5rem !important;
      }

      /* Ajustes de flexibilidade da timeline */
      .custom-timeline .p-timeline-event-content,
      .custom-timeline .p-timeline-event-opposite {
        padding: 0 1.5rem !important;
      }

      /* Estilo dos cards para garantir que o conteúdo seja SEMPRE à esquerda */
      .audit-log-card {
        text-align: left !important;
        direction: ltr !important;
      }

      .audit-log-card * {
        text-align: left !important;
      }
    }
  `]
})
export class AuditDialogComponent {
  private auditService = inject(AuditService);

  visible = false;
  loading = signal(false);
  error = signal(false);
  logs = signal<AuditLogResponse | null>(null);
  title = signal('Histórico do Registro');
  registroId = signal<string | null>(null);

  private fieldLabels: Record<string, string> = {};

  show(registroId: string, entityName: string, customLabels: Record<string, string> = {}) {
    this.registroId.set(registroId);
    this.title.set(`Histórico do Registro`);
    this.fieldLabels = customLabels;
    this.visible = true;
    this.loadLogs();
  }

  loadLogs() {
    const id = this.registroId();
    if (!id) return;

    this.loading.set(true);
    this.error.set(false);
    this.auditService.getLogs(id)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (res) => this.logs.set(res),
        error: () => this.error.set(true)
      });
  }

  getOperationIcon(log: any): string {
    if (log.operacao === 'INSERT') return 'pi pi-plus';
    if (log.operacao === 'DELETE' || this.isDeactivation(log)) return 'pi pi-trash';
    if (this.isReactivation(log)) return 'pi pi-refresh';
    return 'pi pi-pencil';
  }

  getOperationClass(log: any): string {
    if (log.operacao === 'INSERT' || this.isReactivation(log)) return 'bg-gradient-to-br from-green-400 to-green-600 shadow-green-100';
    if (log.operacao === 'DELETE' || this.isDeactivation(log)) return 'bg-gradient-to-br from-red-400 to-red-600 shadow-red-100';
    return 'bg-gradient-to-br from-primary-400 to-primary-600 shadow-primary-100';
  }

  getFriendlyOperationName(log: any): string {
    if (log.operacao === 'INSERT') return 'Criação';
    if (log.operacao === 'DELETE') return 'Remoção';
    if (this.isDeactivation(log)) return 'Desativação';
    if (this.isReactivation(log)) return 'Reativação';
    return 'Alteração';
  }

  isDeactivation(log: any): boolean {
    return log.operacao === 'UPDATE' &&
           log.camposAlterados.some((c: any) => c.campo === 'ativo' && c.valorAnterior === true && c.valorNovo === false);
  }

  isReactivation(log: any): boolean {
    return log.operacao === 'UPDATE' &&
           log.camposAlterados.some((c: any) => c.campo === 'ativo' && c.valorAnterior === false && c.valorNovo === true);
  }

  getFriendlyFieldName(field: string): string {
    if (this.fieldLabels[field]) return this.fieldLabels[field];
    const result = field.replace(/([A-Z])/g, " $1");
    return result.charAt(0).toUpperCase() + result.slice(1);
  }

  formatValue(value: any): string {
    if (value === null || value === undefined) return 'Vazio';
    if (typeof value === 'boolean') return value ? 'Sim' : 'Não';

    if (typeof value === 'string' && value.match(/^\d{4}-\d{2}-\d{2}T/)) {
      const date = new Date(value);
      if (!isNaN(date.getTime())) {
        return date.toLocaleString('pt-BR', {
          day: '2-digit',
          month: '2-digit',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit'
        });
      }
    }

    if (typeof value === 'object') {
      if (value.nome) return value.nome;
      if (value.descricao) return value.descricao;
      return 'Alteração de Dados';
    }

    return String(value);
  }
}
