import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  input,
  model,
  viewChild,
} from '@angular/core';
import { TextareaModule } from 'primeng/textarea';
import { CesFieldComponent } from '../field';
import { createControlId, getDescribedBy, getTextareaValue } from './form-control.helpers';

@Component({
  selector: 'ces-textarea',
  standalone: true,
  imports: [TextareaModule, CesFieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-field
      [label]="label()"
      [labelVisible]="labelVisible()"
      [inputId]="controlId()"
      [description]="description()"
      [error]="error()"
      [counter]="counterText()"
      [required]="required()"
      [showOptional]="showOptional()"
      [disabled]="disabled()"
      [readonly]="readonly()"
    >
      <textarea
        #control
        pTextarea
        class="ces-prime-control"
        pSize="small"
        [id]="controlId()"
        [value]="value()"
        [rows]="rows()"
        [placeholder]="placeholder()"
        [style.max-height]="maxHeight()"
        [style.overflow-y]="maxHeight() ? 'auto' : null"
        [attr.maxlength]="maxlength()"
        [attr.aria-describedby]="describedBy()"
        [attr.aria-invalid]="invalid()"
        [attr.aria-required]="required()"
        [required]="required()"
        [disabled]="disabled()"
        [readOnly]="readonly()"
        [autoResize]="autoResize()"
        [invalid]="invalid()"
        (input)="onInput($event)"
      ></textarea>
    </ces-field>
  `,
})
export class CesTextareaComponent {
  private readonly generatedId = createControlId();
  private readonly control = viewChild<ElementRef<HTMLTextAreaElement>>('control');

  readonly label = input<string>('');
  /**
   * Quando falso, o rótulo continua associado ao controle para leitores de tela, mas não ocupa
   * espaço visual (ex.: compositor de mensagens com placeholder e contexto evidentes).
   */
  readonly labelVisible = input(true);
  readonly inputId = input<string | undefined>();
  readonly description = input<string | undefined>();
  readonly error = input<string | undefined>();
  readonly required = input(false);
  readonly showOptional = input(false);
  readonly disabled = input(false);
  readonly readonly = input(false);

  readonly controlId = computed(() => this.inputId() || this.generatedId);
  readonly invalid = computed(() => Boolean(this.error()));
  readonly describedBy = computed(() =>
    getDescribedBy(this.controlId(), this.description(), this.error()),
  );

  readonly value = model('');
  readonly placeholder = input('');
  readonly rows = input(4);
  readonly maxlength = input<number | undefined>();
  readonly autoResize = input(false);
  /** Altura máxima com rolagem interna (útil com `autoResize`), ex.: `'12rem'`. */
  readonly maxHeight = input<string | undefined>();

  /** Move o foco para o campo (ex.: após enviar uma mensagem). */
  focus(): void {
    this.control()?.nativeElement.focus();
  }

  protected counterText(): string | undefined {
    const limit = this.maxlength();
    return limit === undefined ? undefined : `${this.value().length}/${limit}`;
  }

  protected onInput(event: Event): void {
    if (!this.disabled() && !this.readonly()) this.value.set(getTextareaValue(event));
  }
}
