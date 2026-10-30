import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckboxModule } from 'primeng/checkbox';
import type { CheckboxChangeEvent, CheckboxPassThrough } from 'primeng/checkbox';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import type { ToggleSwitchChangeEvent, ToggleSwitchPassThrough } from 'primeng/toggleswitch';
import { CesFieldComponent } from '../field';
import { createControlId, getDescribedBy } from './form-control.helpers';
import { CesBooleanControlKind } from './form-control.types';

@Component({
  selector: 'ces-boolean-control',
  standalone: true,
  imports: [FormsModule, CheckboxModule, ToggleSwitchModule, CesFieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-field
      [class.ces-field--inline]="inline()"
      [label]="label()"
      [inputId]="controlId()"
      [description]="description()"
      [error]="error()"
      [required]="required()"
      [showOptional]="showOptional()"
      [disabled]="disabled()"
      [readonly]="readonly()"
    >
      @if (kind() === 'toggle') {
        <p-toggleswitch
          size="small"
          [inputId]="controlId()"
          [ngModel]="value()"
          [ngModelOptions]="{ standalone: true }"
          [required]="required()"
          [disabled]="disabled()"
          [readonly]="readonly()"
          [invalid]="invalid()"
          [ariaLabelledBy]="controlId() + '-label'"
          [pt]="togglePassThrough()"
          (onChange)="onToggleChange($event)"
        ></p-toggleswitch>
      } @else {
        <p-checkbox
          size="small"
          [inputId]="controlId()"
          [ngModel]="value()"
          [ngModelOptions]="{ standalone: true }"
          [binary]="true"
          [indeterminate]="indeterminate()"
          [required]="required()"
          [disabled]="disabled()"
          [readonly]="readonly()"
          [invalid]="invalid()"
          [ariaLabelledBy]="controlId() + '-label'"
          [pt]="checkboxPassThrough()"
          (onChange)="onCheckboxChange($event)"
        ></p-checkbox>
      }
    </ces-field>
  `,
})
export class CesBooleanControlComponent {
  private readonly generatedId = createControlId();

  readonly label = input<string>('');
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

  readonly value = model(false);
  readonly kind = input<CesBooleanControlKind>('checkbox');
  readonly indeterminate = input(false);
  readonly inline = input(false);

  protected checkboxPassThrough(): CheckboxPassThrough {
    return {
      input: {
        'aria-describedby': this.describedBy(),
        'aria-invalid': this.invalid() ? 'true' : 'false',
      },
    };
  }

  protected togglePassThrough(): ToggleSwitchPassThrough {
    return {
      input: {
        'aria-describedby': this.describedBy(),
        'aria-invalid': this.invalid() ? 'true' : 'false',
      },
    };
  }

  protected onCheckboxChange(event: CheckboxChangeEvent): void {
    if (!this.disabled() && !this.readonly()) this.value.set(Boolean(event.checked));
  }

  protected onToggleChange(event: ToggleSwitchChangeEvent): void {
    if (!this.disabled() && !this.readonly()) this.value.set(event.checked);
  }
}
