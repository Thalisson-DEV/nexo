import { ChangeDetectionStrategy, Component, computed, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MultiSelectModule } from 'primeng/multiselect';
import type { MultiSelectPassThrough } from 'primeng/multiselect';
import { CesFieldComponent } from '../field';
import { createControlId, getDescribedBy } from './form-control.helpers';
import { CesOverlayAppendTarget, CesSelectOption } from './form-control.types';

type CesMultiSelectPassThrough = MultiSelectPassThrough & {
  hiddenInput?: {
    'aria-describedby'?: string;
    'aria-invalid'?: 'true' | 'false';
  };
};

@Component({
  selector: 'ces-multiselect',
  standalone: true,
  imports: [FormsModule, MultiSelectModule, CesFieldComponent],
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
      <p-multiselect
        [autofocus]="false"
        class="ces-prime-control"
        size="small"
        [inputId]="controlId()"
        [ngModel]="value()"
        [ngModelOptions]="{ standalone: true }"
        [options]="primeOptions()"
        optionLabel="label"
        optionValue="value"
        optionDisabled="disabled"
        [placeholder]="placeholder()"
        [filter]="filter()"
        [filterBy]="filter() ? 'label' : undefined"
        [filterPlaceHolder]="filterPlaceholder()"
        [virtualScroll]="isVirtualScrollActive()"
        [virtualScrollItemSize]="virtualScrollItemSize()"
        [display]="display()"
        [maxSelectedLabels]="maxSelectedLabels()"
        [selectedItemsLabel]="selectedItemsLabel()"
        [showClear]="clearable()"
        [loading]="loading()"
        [required]="required()"
        [disabled]="disabled()"
        [readonly]="readonly()"
        [invalid]="invalid()"
        [appendTo]="appendTo()"
        [ariaLabelledBy]="controlId() + '-label'"
        [pt]="passThrough()"
        (ngModelChange)="onValueChange($event)"
        (onFilter)="search.emit($event.filter ?? '')"
        (onPanelShow)="opened.emit()"
      ></p-multiselect>
    </ces-field>
  `,
})
export class CesMultiSelectComponent<T = unknown> {
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

  readonly value = model<T[]>([]);
  readonly options = input<readonly CesSelectOption<T>[]>([]);
  readonly placeholder = input('Selecione');
  readonly filter = input(true);
  readonly filterPlaceholder = input('Pesquisar');
  readonly virtualScroll = input<boolean | undefined>(undefined);
  readonly virtualScrollItemSize = input(34);
  readonly display = input<'comma' | 'chip'>('comma');
  readonly maxSelectedLabels = input(3);
  readonly selectedItemsLabel = input('{0} itens selecionados');
  readonly clearable = input(false);
  readonly loading = input(false);
  readonly appendTo = input<CesOverlayAppendTarget>('body');

  /** Texto digitado no filtro da lista (busca remota de opções, ex.: usuários). */
  readonly search = output<string>();
  /** A lista de opções abriu (carga preguiçosa das opções). */
  readonly opened = output<void>();

  protected readonly isVirtualScrollActive = computed(() => {
    const explicit = this.virtualScroll();
    if (explicit !== undefined) return explicit;
    return this.options().length > 40;
  });

  protected readonly primeOptions = computed(() => [...this.options()]);

  protected passThrough(): CesMultiSelectPassThrough {
    return {
      hiddenInput: {
        'aria-describedby': this.describedBy(),
        'aria-invalid': this.invalid() ? 'true' : 'false',
      },
    };
  }

  protected onValueChange(value: T[] | null): void {
    if (!this.disabled() && !this.readonly()) this.value.set(value ?? []);
  }
}
