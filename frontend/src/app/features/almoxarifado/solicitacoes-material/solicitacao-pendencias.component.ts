import { Component, computed, inject, input, output, signal } from '@angular/core';
import { CesButtonComponent, CesStatusComponent, NotificationService } from '@ces/ui';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { MaterialResponse, MotivoPendenciaResponse } from '../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../core/models/shared.models';

export interface PendenciaDetalhe {
  id: string;
  status: string;
  motivoPendencia?: MotivoPendenciaResponse | null;
  justificativaPendencia?: string | null;
  possuiPendencia?: boolean;
  itens: Array<{
    id: string;
    material: MaterialResponse;
    motivoPendencia?: MotivoPendenciaResponse | null;
    justificativaPendencia?: string | null;
  }>;
}

@Component({
  selector: 'app-solicitacao-pendencias',
  standalone: true,
  imports: [CesButtonComponent, CesStatusComponent],
  template: `
    @if (temPendenciaGeral(); as current) {
      <section
        class="rounded-xl border border-amber-200 dark:border-amber-900/60 bg-amber-50/40 dark:bg-amber-950/20 p-4 space-y-3"
        aria-label="Pendência ativa da solicitação"
      >
        <div class="flex flex-wrap items-center justify-between gap-2 border-b border-amber-200/70 dark:border-amber-900/50 pb-2">
          <div class="flex items-center gap-2">
            <i class="pi pi-exclamation-triangle text-amber-600 dark:text-amber-400"></i>
            <h3 class="font-bold text-xs uppercase tracking-wider text-amber-900 dark:text-amber-200">Pendência Geral da Solicitação</h3>
          </div>
          <ces-status tone="warning" icon="pi pi-exclamation-triangle">Com pendência</ces-status>
        </div>

        @if (current.motivoPendencia; as motivo) {
          <div class="rounded-lg border border-amber-200/80 dark:border-amber-900/60 bg-white/80 dark:bg-slate-900/80 p-3 space-y-1">
            <div class="flex items-center justify-between gap-2">
              <span class="text-xs font-bold text-slate-800 dark:text-slate-200">
                Motivo: {{ motivo.nome }}
              </span>
              @if (podeEditar()) {
                <ces-button
                  label="Resolver pendência"
                  icon="pi pi-check"
                  variant="ghost"
                  size="sm"
                  [loading]="resolvendo()"
                  (pressed)="remover()"
                />
              }
            </div>
            @if (current.justificativaPendencia) {
              <p class="text-xs text-slate-600 dark:text-slate-300 italic">"{{ current.justificativaPendencia }}"</p>
            }
          </div>
        }
      </section>
    }
  `,
})
export class SolicitacaoPendenciasComponent {
  readonly detalhe = input<PendenciaDetalhe | null>(null);
  readonly podeEditar = input(false);
  readonly atualizado = output<void>();

  private readonly service = inject(AlmoxarifadoService);
  private readonly notifications = inject(NotificationService);

  readonly resolvendo = signal(false);

  readonly temPendenciaGeral = computed(() => {
    const current = this.detalhe();
    if (!current) return null;
    const itens = current.itens ?? [];
    if (itens.length > 0 && itens.every((item) => !item.motivoPendencia)) {
      return null;
    }
    return current.motivoPendencia ? current : null;
  });

  remover(): void {
    const detalhe = this.detalhe();
    if (!detalhe || !this.podeEditar()) return;
    this.resolvendo.set(true);
    this.service.removerPendencia(detalhe.id).subscribe({
      next: () => {
        this.resolvendo.set(false);
        this.atualizado.emit();
        this.notifications.success('Pendência da solicitação resolvida com sucesso.');
      },
      error: (error) => {
        this.resolvendo.set(false);
        this.notifications.error(getErrorMessage(error));
      },
    });
  }
}
