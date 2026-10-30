import { CommonModule } from '@angular/common';
import { Component, input, model } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';

@Component({
  selector: 'app-help-dialog',
  standalone: true,
  imports: [CommonModule, DialogModule, ButtonModule],
  template: `
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      [header]="title()"
      [style]="{ width: '90vw', maxWidth: '600px' }"
      [draggable]="false"
      [resizable]="false"
      styleClass="custom-dialog"
      appendTo="body"
    >
      <div class="flex flex-col gap-6 mt-4">
        <!-- Explicação do cabeçalho / Hero -->
        <div class="bg-[var(--ces-brand-soft)] p-6 rounded-2xl border border-primary-500/20 flex flex-col gap-2 text-center items-center">
            <div class="w-12 h-12 bg-[var(--ces-surface)] border border-[var(--ces-border)] rounded-full flex items-center justify-center text-[var(--ces-brand)] mb-2 shadow-sm">
                <i class="pi pi-question-circle text-2xl"></i>
            </div>
            <h3 class="text-sm font-black text-[var(--ces-foreground)] uppercase tracking-widest">Guia de Tela</h3>
            <p class="text-xs text-[var(--ces-foreground-muted)] leading-relaxed max-w-md">
              Aqui você encontra o passo a passo simplificado para usar as funções desta página. Leia as dicas abaixo para realizar buscas, criar registros e usar a tabela.
            </p>
        </div>

        <!-- Conteúdo Injetado -->
        <div class="flex flex-col gap-8 pb-4">
            <ng-content></ng-content>
        </div>
      </div>

      <ng-template pTemplate="footer">
        <p-button
          label="ENTENDI, FECHAR"
          icon="pi pi-check"
          (onClick)="hide()"
          size="small"
          styleClass="!rounded-lg !bg-primary-600 !border-primary-600 hover:!bg-primary-700 !font-bold !text-[10px] !tracking-wider !py-2.5 !px-6 w-full"
        ></p-button>
      </ng-template>
    </p-dialog>
  `
})
export class HelpDialogComponent {
  title = input<string>('Ajuda');
  visible = model(false);

  show() {
    this.visible.set(true);
  }

  hide() {
    this.visible.set(false);
  }
}
