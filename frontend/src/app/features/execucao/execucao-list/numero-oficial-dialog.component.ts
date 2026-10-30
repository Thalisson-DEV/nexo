import { ChangeDetectionStrategy, Component, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { CesButtonComponent, CesCalloutComponent, CesDialogShellComponent, CesTextInputComponent, NotificationService } from '@ces/ui';

import { ExecucaoComercialResponse } from '../../../core/models/execucao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ExecucaoService } from '../../../core/services/execucao.service';

/** Troca o número provisório da nota avulsa pelo número oficial. */
@Component({
  selector: 'app-numero-oficial-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesButtonComponent, CesCalloutComponent, CesDialogShellComponent, CesTextInputComponent],
  template: `
    <ces-dialog-shell
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      title="Informar número oficial"
      [description]="'Nota avulsa lançada com o número provisório ' + (execucao()?.referenciaProvisoria ?? execucao()?.referenciaNota ?? '') + '.'"
      size="sm"
    >
      <div class="num-form">
        <ces-callout tone="info" title="Só a identificação muda">Serviços, materiais, faturamento e turno continuam como foram lançados. O número provisório fica guardado na nota.</ces-callout>
        <ces-text-input
          label="Número oficial da nota"
          placeholder="Ex.: 123456789"
          [value]="numero()"
          (valueChange)="numero.set($event)"
          [required]="true"
          [error]="erro()"
        />
      </div>
      <div dialog-footer class="ces-actions-row">
        <ces-button label="Cancelar" variant="ghost" (pressed)="visible.set(false)" />
        <ces-button label="Salvar número" icon="pi pi-check" [loading]="salvando()" (pressed)="confirmar()" />
      </div>
    </ces-dialog-shell>
  `,
  styles: `
    .num-form { display: grid; gap: 1rem; }
  `
})
export class NumeroOficialDialogComponent {
  private readonly execucoes = inject(ExecucaoService);
  private readonly notificacoes = inject(NotificationService);

  readonly visible = model(false);
  readonly execucao = input<ExecucaoComercialResponse | null>(null);
  readonly informado = output<ExecucaoComercialResponse>();

  protected readonly numero = signal('');
  protected readonly tentou = signal(false);
  protected readonly salvando = signal(false);

  constructor() {
    effect(() => {
      if (!this.visible()) return;
      untracked(() => {
        const e = this.execucao();
        // Corrigindo um número já informado: começa pelo atual.
        this.numero.set(e && !e.aguardandoNumeroOficial ? e.referenciaNota : '');
        this.tentou.set(false);
      });
    });
  }

  protected erro(): string | undefined {
    if (!this.tentou()) return undefined;
    const numero = this.numero().trim();
    if (!numero) return 'Digite o número oficial.';
    const provisorio = this.execucao()?.referenciaProvisoria;
    if (provisorio && numero.toUpperCase() === provisorio.toUpperCase()) return 'Use o número oficial, não o provisório.';
    return undefined;
  }

  protected confirmar(): void {
    const e = this.execucao();
    this.tentou.set(true);
    if (!e || this.erro() || this.salvando()) return;
    this.salvando.set(true);
    this.execucoes.informarNumeroOficial(e.id, this.numero().trim()).subscribe({
      next: (atualizada) => {
        this.salvando.set(false);
        this.visible.set(false);
        this.notificacoes.success(`Nota ${atualizada.referenciaNota} registrada no lugar de ${atualizada.referenciaProvisoria ?? e.referenciaNota}.`);
        this.informado.emit(atualizada);
      },
      error: (err) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(err), { title: 'Não foi possível salvar o número' });
      }
    });
  }
}
