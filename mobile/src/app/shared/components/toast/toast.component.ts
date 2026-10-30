import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService, Toast } from '../../../core/ui/toast.service';
import { IconeComponent, NomeIcone } from '../../../ui';

const ICONES: Record<string, NomeIcone> = { success: 'check', error: 'alerta', warning: 'alerta', info: 'info' };

/**
 * Avisos curtos no rodapé, no estilo da snackbar do Android: superfície escura,
 * acima da barra de abas, um toque fecha. Erros são anunciados com prioridade.
 */
@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pilha">
      @for (t of toastService.toasts(); track t.id) {
        <div class="aviso" [class]="'aviso ' + t.type" [attr.role]="t.type === 'error' ? 'alert' : 'status'" (click)="toastService.remove(t.id)">
          <ces-icone class="icone" [nome]="icone(t)" [tamanho]="20" />
          <span class="msg">{{ t.message }}</span>
          @if (t.action) {
            <button type="button" class="acao" (click)="runAction($event, t)">{{ t.action.label }}</button>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: contents; }
    .pilha { position: fixed; left: 0; right: 0; z-index: 100; display: grid; justify-items: center; gap: var(--ces-e2); pointer-events: none;
      padding: 0 var(--ces-e3); bottom: calc(var(--ces-barra-abas) + var(--ces-safe-base) + var(--ces-e3)); }
    .aviso { pointer-events: auto; width: min(100%, 520px); display: flex; align-items: center; gap: var(--ces-e3); min-height: 52px;
      padding: var(--ces-e2) var(--ces-e2) var(--ces-e2) var(--ces-e4); border-radius: 14px; background: #1d2433; color: #f3f5f9;
      box-shadow: var(--ces-sombra-flutuante); font: 500 .9375rem/1.35 var(--ces-fonte); animation: subir 220ms var(--ces-curva); cursor: pointer; }
    .msg { flex: 1; min-width: 0; padding: 6px 0; overflow-wrap: anywhere; }
    .icone { flex: none; }
    .success .icone { color: #5bd69a; }
    .error .icone { color: #ff8a80; }
    .warning .icone { color: #ffc35c; }
    .info .icone { color: #8ab4ff; }
    .acao { flex: none; min-height: 40px; padding: 0 var(--ces-e3); border: 0; border-radius: 10px; background: transparent; color: #8ab4ff;
      font: 700 .9375rem/1 var(--ces-fonte); cursor: pointer; }
    .acao:active { background: rgba(255, 255, 255, .08); }
    @keyframes subir { from { opacity: 0; transform: translateY(12px); } }
  `],
})
export class ToastComponent {
  readonly toastService = inject(ToastService);

  icone(t: Toast): NomeIcone {
    return ICONES[t.type] ?? 'info';
  }

  runAction(event: Event, toast: Toast): void {
    event.stopPropagation();
    toast.action?.handler();
    this.toastService.remove(toast.id);
  }
}
