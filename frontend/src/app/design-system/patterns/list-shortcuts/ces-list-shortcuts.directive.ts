import { DOCUMENT } from '@angular/common';
import { Directive, HostListener, computed, contentChild, forwardRef, inject, input, output } from '@angular/core';
import { CES_LIST_SHORTCUTS, CesListShortcutsContext, CesShortcutsPreference } from '../../core/shortcuts';
import { CesDataTableComponent } from '../../data/data-table/ces-data-table.component';
import { CesFilterPanelComponent } from '../filter-panel/ces-filter-panel.component';

/** Campos e áreas em que uma tecla é digitação, não atalho. */
const EDITABLE = 'input, textarea, select, [contenteditable=""], [contenteditable="true"], [role="combobox"], [role="textbox"], [role="listbox"]';
/** Overlays abertos (diálogos, painéis laterais, listas e menus): atalhos ficam suspensos. */
const OPEN_OVERLAY = '[aria-modal="true"], .p-dialog-mask, .p-drawer-mask, .p-overlay-mask, .p-select-overlay, .p-multiselect-overlay, .p-datepicker-panel, .p-popover, .p-menu-overlay, .p-autocomplete-overlay';

/**
 * Atalhos de uma tecla de uma listagem, aplicados ao contêiner da página:
 * `/` foca a busca da tabela, `F` expande ou recolhe os filtros e `N` pede um novo registro
 * (só quando `newAction` é verdadeiro). Ignorados em campos, overlays abertos, com modificadores
 * e quando a pessoa desligou "Atalhos de uma tecla" (WCAG 2.1.4).
 *
 * ```html
 * <div class="ces-page" cesListShortcuts [newAction]="podeCriar()" (newRequested)="abrirNovo()">
 *   …<ces-button actions [keyShortcut]="atalhos.newShortcut()" …>
 * ```
 */
@Directive({
  selector: '[cesListShortcuts]',
  standalone: true,
  exportAs: 'cesListShortcuts',
  providers: [{ provide: CES_LIST_SHORTCUTS, useExisting: forwardRef(() => CesListShortcutsDirective) }],
})
export class CesListShortcutsDirective implements CesListShortcutsContext {
  private readonly document = inject(DOCUMENT);
  private readonly preference = inject(CesShortcutsPreference);

  /** Há permissão para criar: habilita `N`. */
  readonly newAction = input(false);
  readonly newRequested = output<void>();

  private readonly table = contentChild(CesDataTableComponent, { descendants: true });
  private readonly panel = contentChild(CesFilterPanelComponent, { descendants: true });

  readonly enabled = computed(() => this.preference.enabled());
  /** Para `[keyShortcut]` do botão "Novo …". */
  readonly newShortcut = computed(() => (this.enabled() && this.newAction() ? 'N' : undefined));

  @HostListener('document:keydown', ['$event'])
  protected onKeydown(event: KeyboardEvent): void {
    if (!this.enabled() || event.defaultPrevented || event.repeat) return;
    if (event.ctrlKey || event.metaKey || event.altKey) return;
    const alvo = event.target as HTMLElement | null;
    if (alvo?.closest?.(EDITABLE) || alvo?.isContentEditable) return;
    if (this.document.querySelector(OPEN_OVERLAY)) return;

    const tecla = event.key;
    if (tecla === '/') {
      const tabela = this.table();
      if (!tabela) return;
      event.preventDefault();
      tabela.focusSearch();
    } else if (tecla.toLowerCase() === 'f') {
      const painel = this.panel();
      if (!painel) return;
      event.preventDefault();
      if (painel.expanded()) painel.toggle();
      else painel.expandAndFocus();
    } else if (tecla.toLowerCase() === 'n' && this.newAction()) {
      event.preventDefault();
      this.newRequested.emit();
    }
  }
}
