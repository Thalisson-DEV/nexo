import { Injectable, InjectionToken, computed, signal } from '@angular/core';
import { AbstractControl } from '@angular/forms';

/** Campo registrado por um `ces-form-field` (usado pelo resumo de pendências e pelo foco no erro). */
export interface CesFormFieldRef {
  readonly label: () => string;
  readonly control: () => AbstractControl | null;
  readonly section: () => string | null;
  readonly sectionIcon: () => string | null;
  readonly invalid: () => boolean;
  readonly errorText: () => string | null;
  focus(): void;
  /** Posição no documento: ordena pendências na ordem visual. */
  element(): HTMLElement;
}

/** Seção registrada por um `ces-form-section` (índice de âncoras do tipo C). */
export interface CesFormSectionRef {
  readonly id: string;
  readonly title: () => string;
  readonly icon: () => string | undefined;
  element(): HTMLElement;
}

/**
 * Registro de campos e seções de UM formulário. Provido por `ces-form-page` e `ces-form-drawer`;
 * os campos/seções projetados neles se registram sozinhos.
 */
@Injectable()
export class CesFormRegistry {
  private readonly fieldList = signal<CesFormFieldRef[]>([]);
  private readonly sectionList = signal<CesFormSectionRef[]>([]);
  /** Incrementado quando o status de algum controle muda (controles não são signals). */
  readonly version = signal(0);

  readonly sections = computed(() => sortByDocument(this.sectionList()));
  readonly fields = computed(() => sortByDocument(this.fieldList()));

  /** Campos inválidos e já tocados, na ordem visual. */
  readonly pending = computed(() => {
    this.version();
    return this.fields().filter((field) => field.invalid());
  });

  addField(field: CesFormFieldRef): () => void {
    this.fieldList.update((list) => [...list, field]);
    return () => this.fieldList.update((list) => list.filter((item) => item !== field));
  }

  addSection(section: CesFormSectionRef): () => void {
    this.sectionList.update((list) => [...list, section]);
    return () => this.sectionList.update((list) => list.filter((item) => item !== section));
  }

  touch(): void {
    this.version.update((value) => value + 1);
  }
}

function sortByDocument<T extends { element(): HTMLElement }>(items: T[]): T[] {
  return [...items].sort((a, b) => {
    const position = a.element().compareDocumentPosition(b.element());
    if (position & Node.DOCUMENT_POSITION_FOLLOWING) return -1;
    if (position & Node.DOCUMENT_POSITION_PRECEDING) return 1;
    return 0;
  });
}

/** Seção atual de um campo (provida por `ces-form-section`). */
export interface CesFormSectionContext {
  readonly title: () => string;
  readonly icon: () => string | undefined;
}
export const CES_FORM_SECTION = new InjectionToken<CesFormSectionContext>('CES_FORM_SECTION');
