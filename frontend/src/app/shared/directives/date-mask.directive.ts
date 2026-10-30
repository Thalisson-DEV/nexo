import { Directive, ElementRef, HostListener, inject, input } from '@angular/core';
import { NgControl } from '@angular/forms';

@Directive({
  selector: 'p-datepicker, [dateMask]',
  standalone: true,
})
export class DateMaskDirective {
  private isProcessing = false;
  private el = inject(ElementRef, { optional: true });
  private ngControl = inject(NgControl, { optional: true, self: true });
  /** `true` aceita também a hora: `dd/mm/aaaa hh:mm`. */
  readonly dateMaskTime = input(false);

  @HostListener('input', ['$event'])
  onInput(event: Event) {
    if (this.isProcessing) return;

    const input = event.target as HTMLInputElement;
    if (!input || input.tagName !== 'INPUT') return;

    let rawValue = input.value.replace(/\D/g, '');
    const limite = this.dateMaskTime() ? 12 : 8;
    if (rawValue.length > limite) {
      rawValue = rawValue.substring(0, limite);
    }

    let formattedValue = rawValue;
    if (rawValue.length > 10) {
      formattedValue = rawValue.replace(/(\d{2})(\d{2})(\d{4})(\d{2})(\d{1,2})/, '$1/$2/$3 $4:$5');
    } else if (rawValue.length > 8) {
      formattedValue = rawValue.replace(/(\d{2})(\d{2})(\d{4})(\d{1,2})/, '$1/$2/$3 $4');
    } else if (rawValue.length > 4) {
      formattedValue = rawValue.replace(/(\d{2})(\d{2})(\d{1,4})/, '$1/$2/$3');
    } else if (rawValue.length > 2) {
      formattedValue = rawValue.replace(/(\d{2})(\d{1,2})/, '$1/$2');
    }

    if (input.value !== formattedValue) {
      this.isProcessing = true;
      input.value = formattedValue;

      // Dispatch input event so that PrimeNG DatePicker detects the new value
      input.dispatchEvent(new Event('input', { bubbles: true }));

      this.isProcessing = false;
    }
  }
}
