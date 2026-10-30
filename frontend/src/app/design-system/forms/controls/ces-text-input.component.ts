import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { InputTextModule } from 'primeng/inputtext';
import { CesFieldComponent } from '../field';
import { createControlId, getDescribedBy, getTextInputValue } from './form-control.helpers';

@Component({
  selector: 'ces-text-input',
  standalone: true,
  imports: [InputTextModule, CesFieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-field
      [label]="label()"
      [labelVisible]="labelVisible()"
      [inputId]="controlId()"
      [description]="description()"
      [error]="error()"
      [required]="required()"
      [showOptional]="showOptional()"
      [disabled]="disabled()"
      [readonly]="readonly()"
    >
      <input
        pInputText
        class="ces-prime-control"
        pSize="small"
        type="text"
        [id]="controlId()"
        [value]="value()"
        [placeholder]="placeholder()"
        [autocomplete]="autocomplete()"
        [attr.maxlength]="maxlength()"
        [attr.aria-describedby]="describedBy()"
        [attr.aria-invalid]="invalid()"
        [attr.aria-required]="required()"
        [required]="required()"
        [disabled]="disabled()"
        [readOnly]="readonly()"
        [invalid]="invalid()"
        (input)="onInput($event)"
      />
    </ces-field>
  `,
})
export class CesTextInputComponent {
  private readonly generatedId = createControlId();

  readonly label = input<string>('');
  /** `false` esconde o rótulo visualmente (fica para leitores de tela), ex.: edição em célula de tabela. */
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
  readonly autocomplete = input('off');
  readonly maxlength = input<number | undefined>();

  protected onInput(event: Event): void {
    if (!this.disabled() && !this.readonly()) this.value.set(getTextInputValue(event));
  }
}
