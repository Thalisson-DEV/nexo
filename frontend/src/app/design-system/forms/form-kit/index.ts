export * from './form-kit.registry';
export * from './form-kit.utils';
export * from './ces-form-field.component';
export * from './ces-form-section.component';
export * from './ces-form-actions.component';
export * from './ces-form-stepper.component';
export * from './ces-form-summary.component';
export * from './ces-form-page.component';
export * from './ces-form-drawer.component';
export * from './unsaved-changes.guard';

import { CesFormActionsComponent } from './ces-form-actions.component';
import { CesFormDrawerComponent } from './ces-form-drawer.component';
import { CesFormFieldComponent } from './ces-form-field.component';
import { CesFormPageComponent } from './ces-form-page.component';
import { CesFormSectionComponent } from './ces-form-section.component';
import { CesFormStepperComponent } from './ces-form-stepper.component';
import { CesFormSummaryComponent } from './ces-form-summary.component';

/** Todos os componentes do kit de formulário, para o `imports` da tela. */
export const CES_FORM_KIT = [
  CesFormPageComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesFormFieldComponent,
  CesFormActionsComponent,
  CesFormSummaryComponent,
  CesFormStepperComponent,
] as const;
