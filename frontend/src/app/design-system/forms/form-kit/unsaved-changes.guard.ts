import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { CesConfirmationService } from '../../overlays/confirmation/confirmation.service';

/** Tela de formulário que pode ter alterações não salvas. */
export interface CesUnsavedChanges {
  /** `true` quando sair agora perderia dados (sem rascunho automático que os guarde). */
  hasUnsavedChanges(): boolean;
}

/**
 * `canDeactivate` para formulários em página **sem** rascunho automático: pede confirmação antes de
 * sair com alterações. Formulários com rascunho (`createFormDraft`) não precisam dele — sair guarda o
 * rascunho, que volta ao reabrir.
 *
 * ```ts
 * { path: 'nova', loadComponent: …, canDeactivate: [cesUnsavedChangesGuard] }
 * ```
 */
export const cesUnsavedChangesGuard: CanDeactivateFn<CesUnsavedChanges> = (component) => {
  if (!component?.hasUnsavedChanges?.()) return true;
  return inject(CesConfirmationService).confirm({
    title: 'Sair sem salvar?',
    message: 'As alterações feitas neste formulário serão perdidas.',
    confirmLabel: 'Sair sem salvar',
    cancelLabel: 'Continuar editando',
    variant: 'destructive',
  });
};
