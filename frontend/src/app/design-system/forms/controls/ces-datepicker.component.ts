import { DateMaskDirective } from '../../../shared/directives/date-mask.directive';
import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import type { DatePickerPassThrough } from 'primeng/datepicker';
import { CesFieldComponent } from '../field';
import { createControlId, getDescribedBy } from './form-control.helpers';
import { CesDateValue, CesOverlayAppendTarget } from './form-control.types';

@Component({
  selector: 'ces-datepicker',
  standalone: true,
  imports: [FormsModule, DatePickerModule, CesFieldComponent, DateMaskDirective],
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
      <p-datepicker
        [autofocus]="false"
        dateMask
        [dateMaskTime]="showTime()"
        class="ces-prime-control"
        size="small"
        [inputId]="controlId()"
        [ngModel]="valorEstavel()"
        [ngModelOptions]="{ standalone: true }"
        [placeholder]="placeholderEfetivo()"
        [dateFormat]="dateFormat()"
        [showTime]="showTime()"
        hourFormat="24"
        [showIcon]="showIcon()"
        [iconDisplay]="'input'"
        [showClear]="clearable()"
        [showButtonBar]="showButtonBar()"
        [minDate]="minDate()"
        [maxDate]="maxDate()"
        [disabledDates]="primeDisabledDates()"
        [required]="required()"
        [disabled]="disabled()"
        [readonlyInput]="readonly()"
        [invalid]="invalid()"
        [appendTo]="appendTo()"
        [ariaLabelledBy]="controlId() + '-label'"
        [iconAriaLabel]="'Abrir calendário de ' + label()"
        [pt]="passThrough()"
        (ngModelChange)="onValueChange($event)"
      ></p-datepicker>
    </ces-field>
  `,
})
export class CesDatepickerComponent {
  private readonly generatedId = createControlId();

  readonly label = input<string>('');
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

  readonly value = model<CesDateValue>(null);
  readonly placeholder = input<string | undefined>(undefined);
  protected readonly placeholderEfetivo = computed(() => this.placeholder() ?? (this.showTime() ? 'dd/mm/aaaa hh:mm' : 'dd/mm/aaaa'));
  readonly dateFormat = input('dd/mm/yy');
  readonly showIcon = input(true);
  /** Também escolhe a hora (`dd/mm/aaaa hh:mm`, 24h). */
  readonly showTime = input(false);
  readonly clearable = input(false);
  readonly showButtonBar = input(false);
  readonly minDate = input<Date | null | undefined>();
  readonly maxDate = input<Date | null | undefined>();
  readonly disabledDates = input<readonly Date[]>([]);
  readonly appendTo = input<CesOverlayAppendTarget>('body');

  protected readonly primeDisabledDates = computed(() => [...this.disabledDates()]);

  protected passThrough(): DatePickerPassThrough {
    return {
      pcInputText: {
        root: {
          'aria-describedby': this.describedBy(),
          'aria-invalid': this.invalid() ? 'true' : 'false',
        },
      },
    };
  }

  private ultimoValor: CesDateValue = null;
  /** Mesma instância enquanto o instante não muda: quem passa `dataLocal(...)` no template cria um Date novo a cada ciclo. */
  protected readonly valorEstavel = computed<CesDateValue>(() => {
    const atual = this.value();
    if (atual instanceof Date && this.ultimoValor instanceof Date && atual.getTime() === this.ultimoValor.getTime()) return this.ultimoValor;
    this.ultimoValor = atual;
    return atual;
  });

  protected onValueChange(value: Date | null): void {
    if (this.disabled() || this.readonly()) return;
    // O PrimeNG reemite o mesmo instante quando recebe um Date novo: não propaga (evita laço com quem recria o Date a cada leitura).
    const atual = this.value();
    if (value instanceof Date && atual instanceof Date && value.getTime() === atual.getTime()) return;
    this.value.set(value);
  }
}
