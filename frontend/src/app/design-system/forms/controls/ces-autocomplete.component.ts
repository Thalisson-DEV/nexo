import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  output,
  signal,
} from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { AutoCompleteModule } from 'primeng/autocomplete';
import type {
  AutoCompleteCompleteEvent,
  AutoCompleteLazyLoadEvent,
  AutoCompletePassThrough,
} from 'primeng/autocomplete';
import { CesFieldComponent } from '../field';
import type { LookupOption } from '../../../core/lookup/models/lookup-option';
import type { LookupStatus } from '../../../core/lookup/models/lookup-status';
import { CesButtonComponent } from '../../primitives';
import { createControlId, getDescribedBy } from './form-control.helpers';
import { CesOverlayAppendTarget } from './form-control.types';

@Component({
  selector: 'ces-autocomplete',
  standalone: true,
  imports: [FormsModule, AutoCompleteModule, CesFieldComponent, CesButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CesAutocompleteComponent),
      multi: true,
    },
  ],
  template: `
    <ces-field
      [label]="label()"
      [labelVisible]="labelVisible()"
      [inputId]="controlId()"
      [description]="effectiveDescription()"
      [error]="error()"
      [required]="required()"
      [showOptional]="showOptional()"
      [disabled]="effectiveDisabled()"
      [readonly]="readonly()"
    >
      <p-autocomplete
        [autofocus]="false"
        class="ces-prime-control"
        size="small"
        [inputId]="controlId()"
        [ngModel]="primeValue()"
        [ngModelOptions]="{ standalone: true }"
        [suggestions]="primeOptions()"
        optionLabel="label"
        [placeholder]="placeholder()"
        [minQueryLength]="minLength()"
        [delay]="delay()"
        [forceSelection]="forceSelection()"
        [virtualScroll]="virtualScroll()"
        [lazy]="virtualScroll()"
        [virtualScrollItemSize]="virtualScrollItemSize()"
        [showClear]="clearable()"
        [showEmptyMessage]="true"
        [emptyMessage]="emptyMessage()"
        [required]="required()"
        [disabled]="effectiveDisabled()"
        [readonly]="readonly()"
        [invalid]="invalid()"
        [appendTo]="appendTo()"
        [ariaLabelledBy]="controlId() + '-label'"
        [pt]="passThrough()"
        (ngModelChange)="onValueChange($event)"
        (completeMethod)="onComplete($event)"
        (onLazyLoad)="onLazyLoad($event)"
        (onBlur)="onBlur()"
      >
        <ng-template #empty>
          @if (effectiveStatus() === 'error') {
            <ces-button
              label="Tentar novamente"
              icon="pi pi-refresh"
              variant="link"
              size="sm"
              (pressed)="requestRetry()"
            ></ces-button>
          } @else {
            {{ emptyMessage() }}
          }
        </ng-template>
      </p-autocomplete>
    </ces-field>
    <span class="ces-sr-only" aria-live="polite">{{ statusMessage() }}</span>
  `,
})
export class CesAutocompleteComponent<T = unknown> implements ControlValueAccessor {
  private readonly generatedId = createControlId();
  private readonly internalValue = signal<T | null>(null);
  private readonly cvaDisabled = signal(false);
  private onChange: (value: T | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  readonly label = input<string>('');
  readonly labelVisible = input(true);
  readonly inputId = input<string | undefined>();
  readonly description = input<string | undefined>();
  /** Mantem a instrucao de busca visivel e associada ao campo remoto. */
  readonly showSearchInstruction = input(true);
  readonly error = input<string | undefined>();
  readonly required = input(false);
  readonly showOptional = input(false);
  readonly disabled = input(false);
  readonly readonly = input(false);
  readonly options = input<readonly LookupOption<T>[]>([]);
  readonly placeholder = input('Pesquisar');
  readonly minLength = input(2);
  readonly forceSelection = input(true);
  readonly virtualScroll = input(true);
  readonly virtualScrollItemSize = input(40);
  readonly appendTo = input<CesOverlayAppendTarget>('body');
  readonly clearable = input(false);
  readonly loading = input(false);
  readonly status = input<LookupStatus>('idle');
  /** Debounce belongs to the lookup facade; the wrapper forwards searches immediately. */
  readonly delay = input(0);

  readonly search = output<string>();
  readonly loadMore = output<void>();
  readonly retry = output<void>();

  readonly controlId = computed(() => this.inputId() || this.generatedId);
  readonly value = computed(() => this.internalValue());
  readonly effectiveDisabled = computed(() => this.disabled() || this.cvaDisabled());
  readonly searchInstruction = computed(() =>
    this.minLength() === 1
      ? 'Digite pelo menos 1 caractere para ver as opções.'
      : `Digite pelo menos ${this.minLength()} caracteres para ver as opções.`,
  );
  readonly effectiveDescription = computed(() => {
    const description = this.description()?.trim();
    const instruction = this.showSearchInstruction() ? this.searchInstruction() : '';
    return [instruction, description].filter(Boolean).join(' ') || undefined;
  });
  readonly effectiveStatus = computed<LookupStatus>(() =>
    this.loading() ? 'loading' : this.status(),
  );
  readonly invalid = computed(() => Boolean(this.error()) || this.effectiveStatus() === 'error');
  readonly describedBy = computed(() =>
    getDescribedBy(this.controlId(), this.effectiveDescription(), this.error()),
  );
  readonly emptyMessage = computed(() => {
    switch (this.effectiveStatus()) {
      case 'loading':
        return 'Carregando opções...';
      case 'empty':
        return 'Nenhum resultado encontrado.';
      case 'error':
        return 'Não foi possível carregar as opções.';
      case 'idle':
        return `Digite pelo menos ${this.minLength()} caracteres.`;
      default:
        return 'Nenhuma opção encontrada.';
    }
  });
  readonly statusMessage = computed(() => {
    if (this.effectiveStatus() === 'loading') return 'Carregando opções.';
    if (this.effectiveStatus() === 'error') return 'Não foi possível carregar as opções.';
    if (this.effectiveStatus() === 'empty') return 'Nenhum resultado encontrado.';
    return '';
  });

  protected readonly primeOptions = computed(() => [...this.options()]);
  protected readonly primeValue = computed(() => {
    const value = this.internalValue();
    if (value === null) return null;
    return this.options().find((option) => Object.is(option.value, value)) ?? null;
  });

  protected passThrough(): AutoCompletePassThrough {
    return {
      pcInputText: {
        root: {
          'aria-describedby': this.describedBy(),
          'aria-invalid': this.invalid() ? 'true' : 'false',
        },
      },
    };
  }

  writeValue(value: T | null): void {
    this.internalValue.set(value);
  }

  registerOnChange(fn: (value: T | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.cvaDisabled.set(disabled);
  }

  protected onValueChange(value: T | null): void {
    if (this.effectiveDisabled() || this.readonly()) return;
    // PrimeNG unwraps the `value` property from each suggestion before emitting
    // ngModelChange, even when optionValue is not configured.
    this.internalValue.set(value);
    this.onChange(value);
  }

  protected onComplete(event: AutoCompleteCompleteEvent): void {
    this.search.emit(event.query);
  }

  protected onLazyLoad(_event: AutoCompleteLazyLoadEvent): void {
    this.loadMore.emit();
  }

  protected onBlur(): void {
    this.onTouched();
  }

  requestRetry(): void {
    this.retry.emit();
  }
}
