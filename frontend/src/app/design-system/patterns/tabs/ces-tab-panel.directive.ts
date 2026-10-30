import { Directive, TemplateRef, inject, input } from '@angular/core';

export type CesTabValue = string | number;

@Directive({
  selector: 'ng-template[cesTabPanel]',
  standalone: true
})
export class CesTabPanelDirective {
  readonly value = input.required<CesTabValue>({ alias: 'cesTabPanel' });
  readonly template = inject(TemplateRef<unknown>);
}
