import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InputNumberModule } from 'primeng/inputnumber';
import { CesFieldComponent } from '../field';
import { createControlId, getDescribedBy } from './form-control.helpers';

@Component({
  selector: 'ces-number-input',
  standalone: true,
  imports: [FormsModule, InputNumberModule, CesFieldComponent],
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
      <p-inputnumber
        [autofocus]="false"
        class="ces-prime-control"
        size="small"
        [inputId]="controlId()"
        [ngModel]="value()"
        [ngModelOptions]="{ standalone: true }"
        [placeholder]="placeholder()"
        [mode]="mode()"
        [currency]="mode() === 'currency' ? currency() : undefined"
        [locale]="locale()"
        [useGrouping]="useGrouping()"
        [min]="min()"
        [max]="max()"
        [step]="step()"
        [minFractionDigits]="minFractionDigits()"
        [maxFractionDigits]="maxFractionDigits()"
        [prefix]="prefix()"
        [suffix]="suffix()"
        [showButtons]="showButtons()"
        [allowEmpty]="!required()"
        [required]="required()"
        [disabled]="disabled()"
        [readonly]="readonly()"
        [invalid]="invalid()"
        [ariaRequired]="required()"
        [ariaDescribedBy]="describedBy()"
        (ngModelChange)="onValueChange($event)"
      ></p-inputnumber>
    </ces-field>
  `,
})
export class CesNumberInputComponent {
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

  readonly value = model<number | null>(null);
  readonly placeholder = input('');
  readonly mode = input<'decimal' | 'currency'>('decimal');
  readonly currency = input('BRL');
  readonly locale = input('pt-BR');
  readonly useGrouping = input(true);
  readonly min = input<number | undefined>();
  readonly max = input<number | undefined>();
  readonly step = input(1);
  readonly minFractionDigits = input<number | undefined>();
  readonly maxFractionDigits = input<number | undefined>();
  readonly prefix = input<string | undefined>();
  readonly suffix = input<string | undefined>();
  readonly showButtons = input(false);

  protected onValueChange(value: number | null): void {
    if (!this.disabled() && !this.readonly()) this.value.set(value);
  }
}
