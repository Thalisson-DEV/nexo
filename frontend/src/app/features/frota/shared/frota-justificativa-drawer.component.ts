import { ChangeDetectionStrategy, Component, effect, input, model, output, signal, untracked } from '@angular/core';
import { CesButtonComponent, CesFormDrawerComponent, CesTextareaComponent } from '@ces/ui';

/** Pede uma justificativa antes de uma ação auditada (anular leitura, confirmar inconsistência...). */
@Component({
  selector: 'app-frota-justificativa-drawer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesButtonComponent, CesFormDrawerComponent, CesTextareaComponent],
  template: `
    <ces-form-drawer [(visible)]="visivel" [title]="titulo()" [description]="descricao()" [dirty]="!!texto().trim()"
      [loading]="processando()" size="sm">
      <ces-textarea label="Justificativa" [required]="true" [(value)]="texto" [rows]="4" [maxlength]="400"
        description="Fica registrada com seu usuário e a data." [error]="erro()"></ces-textarea>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="visivel.set(false)"></ces-button>
        <ces-button [variant]="destrutivo() ? 'destructive' : 'primary'" icon="pi pi-check" [label]="rotuloConfirmar()" [loading]="processando()"
          (pressed)="confirmar()"></ces-button>
      </div>
    </ces-form-drawer>
  `
})
export class FrotaJustificativaDrawerComponent {
  readonly visivel = model(false);
  readonly titulo = input.required<string>();
  readonly descricao = input<string | undefined>();
  readonly rotuloConfirmar = input('Confirmar');
  readonly destrutivo = input(false);
  readonly processando = input(false);
  readonly confirmado = output<string>();

  protected readonly texto = signal('');
  protected readonly erro = signal<string | undefined>(undefined);

  constructor() {
    effect(() => {
      if (this.visivel()) {
        untracked(() => {
          this.texto.set('');
          this.erro.set(undefined);
        });
      }
    });
  }

  protected confirmar(): void {
    const valor = this.texto().trim();
    if (valor.length < 5) {
      this.erro.set('Escreva ao menos 5 caracteres.');
      return;
    }
    this.erro.set(undefined);
    this.confirmado.emit(valor);
  }
}
