import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output } from '@angular/core';
import { CesButtonComponent } from '../../primitives/button/ces-button.component';

/**
 * Ações do formulário: Cancelar, "Salvar e novo" (cadastros) e Salvar, com estados de carregamento
 * e o atalho **Ctrl+S** (⌘S no Mac). Fica no rodapé fixo de `ces-form-page` (`[form-actions]`) ou de
 * `ces-form-drawer` (`[drawer-footer]`).
 *
 * ```html
 * <ces-form-actions form-actions [saving]="salvando()" [saveAndNew]="true"
 *   (save)="salvar()" (saveAndNewClick)="salvar(true)" (cancel)="cancelar()" />
 * ```
 *
 * Com vários formulários na tela (página + drawer aberto), o Ctrl+S vai para o que está por cima.
 */
@Component({
  selector: 'ces-form-actions',
  standalone: true,
  imports: [CesButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-form-actions',
    '(document:keydown)': 'onKeydown($event)',
  },
  template: `
    <div class="ces-form-actions__start"><ng-content select="[actions-start]"></ng-content></div>
    <div class="ces-form-actions__end">
      <ng-content></ng-content>
      @if (showCancel()) {
        <ces-button
          variant="ghost"
          [label]="cancelLabel()"
          [disabled]="saving()"
          (pressed)="cancel.emit()"
        ></ces-button>
      }
      @if (saveAndNew()) {
        <ces-button
          variant="outline"
          icon="pi pi-plus"
          label="Salvar e novo"
          [disabled]="disabled() || saving()"
          (pressed)="saveAndNewClick.emit()"
        ></ces-button>
      }
      <ces-button
        variant="primary"
        [icon]="submitIcon()"
        [label]="saving() ? savingLabel() : submitLabel()"
        [loading]="saving()"
        [disabled]="disabled()"
        [ariaLabel]="submitLabel() + ' (Ctrl+S)'"
        (pressed)="save.emit()"
      ></ces-button>
    </div>
  `,
})
export class CesFormActionsComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly submitLabel = input('Salvar');
  readonly submitIcon = input('pi pi-check');
  readonly savingLabel = input('Salvando…');
  readonly cancelLabel = input('Cancelar');
  readonly showCancel = input(true);
  /** Mostra "Salvar e novo" (cadastros em drawer). */
  readonly saveAndNew = input(false);
  readonly saving = input(false);
  /** Desabilita o envio. Prefira deixar habilitado e mostrar os erros no envio. */
  readonly disabled = input(false);
  readonly shortcut = input(true);

  readonly save = output<void>();
  readonly saveAndNewClick = output<void>();
  readonly cancel = output<void>();

  protected onKeydown(event: KeyboardEvent): void {
    if (!this.shortcut() || event.key.toLowerCase() !== 's' || !(event.ctrlKey || event.metaKey)) return;
    if (event.shiftKey || event.altKey || !this.isTopmost()) return;
    event.preventDefault();
    if (this.saving() || this.disabled()) return;
    // Confirma o valor do campo em edição (ex.: datepicker/autocomplete) antes de salvar.
    (document.activeElement as HTMLElement | null)?.blur?.();
    this.save.emit();
  }

  /** Visível e não coberto por um drawer/dialog que não o contém. */
  private isTopmost(): boolean {
    const element = this.host.nativeElement;
    if (!element.isConnected) return false;
    if (typeof element.checkVisibility === 'function' && !element.checkVisibility()) return false;
    // Painéis modais abertos (drawer/dialog), na ordem em que foram anexados ao body.
    const paineis = Array.from(document.querySelectorAll<HTMLElement>('.p-drawer, .p-dialog')).filter(
      (painel) => typeof painel.checkVisibility !== 'function' || painel.checkVisibility(),
    );
    const topo = paineis.at(-1);
    return !topo || topo.contains(element);
  }
}
