import { Directive, TemplateRef, inject, input } from '@angular/core';

export interface CesCellContext<T> {
  readonly $implicit: T;
  readonly index: number;
}

/** Célula customizada: `<ng-template cesCell="percentual" let-row>…</ng-template>`. */
@Directive({ selector: 'ng-template[cesCell]', standalone: true })
export class CesCellDirective<T = unknown> {
  readonly cesCell = input.required<string>();
  readonly template = inject<TemplateRef<CesCellContext<T>>>(TemplateRef);

  // A linha chega sem tipo no template (as diretivas não conhecem o `T` da tabela).
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  static ngTemplateContextGuard(_dir: CesCellDirective<unknown>, ctx: unknown): ctx is CesCellContext<any> {
    return true;
  }
}

/** Conteúdo da linha expandida: `<ng-template cesRowDetail let-row>…</ng-template>`. */
@Directive({ selector: 'ng-template[cesRowDetail]', standalone: true })
export class CesRowDetailDirective<T = unknown> {
  readonly template = inject<TemplateRef<CesCellContext<T>>>(TemplateRef);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  static ngTemplateContextGuard(_dir: CesRowDetailDirective<unknown>, ctx: unknown): ctx is CesCellContext<any> {
    return true;
  }
}

/**
 * Ações secundárias da linha (até três `ces-icon-button`), na mesma célula do botão da ação principal:
 * `<ng-template cesRowActions let-row>…</ng-template>`. No mobile aparecem junto do botão do card.
 */
@Directive({ selector: 'ng-template[cesRowActions]', standalone: true })
export class CesRowActionsDirective<T = unknown> {
  readonly template = inject<TemplateRef<CesCellContext<T>>>(TemplateRef);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  static ngTemplateContextGuard(_dir: CesRowActionsDirective<unknown>, ctx: unknown): ctx is CesCellContext<any> {
    return true;
  }
}
