import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

let fieldSequence = 0;

@Component({
  selector: 'ces-field',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ces-field',
    '[class.ces-field--invalid]': 'invalid()',
    '[class.ces-field--disabled]': 'disabled()',
    '[class.ces-field--readonly]': 'readonly()',
  },
  template: `
    @if (labelVisible()) {
      <div class="ces-field__heading">
        <label class="ces-field__label" [id]="labelId()" [for]="controlId()">
          {{ label() }}
          @if (required()) {
            <span class="ces-field__required" aria-hidden="true">*</span>
            <span class="ces-sr-only">obrigatório</span>
          } @else if (showOptional()) {
            <span class="ces-field__optional">Opcional</span>
          }
        </label>
        @if (counter()) {
          <span class="ces-field__counter">{{ counter() }}</span>
        }
      </div>
    } @else {
      <label class="ces-sr-only" [id]="labelId()" [for]="controlId()">{{ label() }}</label>
    }

    <div class="ces-field__control">
      <ng-content></ng-content>
    </div>

    @if (error()) {
      <p class="ces-field__message ces-field__message--error" [id]="errorId()" role="alert">
        <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
        {{ error() }}
      </p>
    } @else if (description()) {
      <p class="ces-field__message" [id]="descriptionId()">{{ description() }}</p>
    }
  `,
})
export class CesFieldComponent {
  private readonly generatedId = `ces-field-${++fieldSequence}`;

  readonly label = input<string>('');
  readonly labelVisible = input(true);
  readonly inputId = input<string | undefined>();
  readonly description = input<string | undefined>();
  readonly error = input<string | undefined>();
  readonly counter = input<string | undefined>();
  readonly required = input(false);
  readonly showOptional = input(false);
  readonly disabled = input(false);
  readonly readonly = input(false);

  readonly controlId = computed(() => this.inputId() || this.generatedId);
  readonly invalid = computed(() => Boolean(this.error()));
  readonly labelId = computed(() => `${this.controlId()}-label`);
  readonly descriptionId = computed(() => `${this.controlId()}-description`);
  readonly errorId = computed(() => `${this.controlId()}-error`);
  readonly describedBy = computed(() => {
    if (this.error()) return this.errorId();
    if (this.description()) return this.descriptionId();
    return undefined;
  });
}
