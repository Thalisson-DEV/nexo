import { AbstractControl, ValidationErrors, Validators } from '@angular/forms';

/** Mensagens padrão (pt-BR) por chave de erro. Um validador próprio pode devolver a mensagem como valor. */
export type CesErrorMessages = Record<string, string | ((error: any) => string)>;

export const CES_DEFAULT_ERROR_MESSAGES: CesErrorMessages = {
  required: 'Campo obrigatório.',
  requiredTrue: 'Confirme para continuar.',
  email: 'Informe um e-mail válido.',
  minlength: (e) => `Informe pelo menos ${e.requiredLength} caracteres.`,
  maxlength: (e) => `Use no máximo ${e.requiredLength} caracteres.`,
  min: (e) => `O valor mínimo é ${e.min}.`,
  max: (e) => `O valor máximo é ${e.max}.`,
  pattern: 'Formato inválido.',
};

/** Primeira mensagem de erro de um conjunto de erros de validação. */
export function cesErrorMessage(errors: ValidationErrors | null, custom: CesErrorMessages = {}): string | null {
  if (!errors) return null;
  const [key, value] = Object.entries(errors)[0] ?? [];
  if (!key) return null;
  const message = custom[key] ?? CES_DEFAULT_ERROR_MESSAGES[key];
  if (typeof message === 'function') return message(value);
  if (message) return message;
  // Validador próprio que já devolve o texto: `{ periodo: 'A data final deve ser posterior à inicial.' }`.
  if (typeof value === 'string' && value.trim()) return value;
  return 'Valor inválido.';
}

export function cesIsRequired(control: AbstractControl | null): boolean {
  return !!control && (control.hasValidator(Validators.required) || control.hasValidator(Validators.requiredTrue));
}

const FOCUSABLE =
  'input:not([type="hidden"]):not([disabled]), textarea:not([disabled]), select:not([disabled]), ' +
  '[role="combobox"]:not([aria-disabled="true"]), [tabindex]:not([tabindex="-1"]):not([aria-disabled="true"])';

/** Primeiro elemento focável de um campo (inclui os controles do PrimeNG). */
export function cesFocusableIn(root: ParentNode | null): HTMLElement | null {
  return (root?.querySelector(FOCUSABLE) as HTMLElement | null) ?? null;
}

/** Leva o foco (e a rolagem) ao primeiro campo com erro dentro de `root`. */
export function cesFocusFirstInvalid(root: ParentNode = document): boolean {
  const field = root.querySelector('.ces-form-field--invalid') as HTMLElement | null;
  const target = cesFocusableIn(field) ?? (root.querySelector('[aria-invalid="true"]') as HTMLElement | null);
  if (!target) return false;
  target.scrollIntoView?.({ block: 'center', behavior: 'smooth' });
  target.focus({ preventScroll: true });
  return true;
}

/**
 * Validação de envio: marca tudo como tocado, e se houver erro leva o foco ao primeiro campo
 * inválido (depois de a tela mostrar as mensagens). Retorna `true` quando pode salvar.
 *
 * ```ts
 * async salvar() {
 *   if (!(await cesValidateForm(this.form, this.host.nativeElement))) return;
 *   ...
 * }
 * ```
 */
export async function cesValidateForm(form: AbstractControl, root: ParentNode = document): Promise<boolean> {
  form.markAllAsTouched();
  form.updateValueAndValidity();
  if (form.valid || form.pending) return form.valid;
  await new Promise((resolve) => setTimeout(resolve));
  cesFocusFirstInvalid(root);
  return false;
}
