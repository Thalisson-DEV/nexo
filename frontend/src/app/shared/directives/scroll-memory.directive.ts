import { DestroyRef, Directive, ElementRef, inject, input } from '@angular/core';
import { NavigationEnd, NavigationStart, Router } from '@angular/router';
import { NavigationHistoryService } from '../../core/navigation/navigation-history.service';
import { ViewStateStore } from '../../core/navigation/view-state.store';

const RESTORE_TIMEOUT_MS = 4000;
const TTL_MS = 12 * 60 * 60 * 1000;

/**
 * Guarda e restaura o `scrollTop` de um container com rolagem própria, por entrada do histórico.
 *
 * O shell rola dentro de `<main>` (não na janela), então `withInMemoryScrolling` não alcança
 * essa rolagem. Use também em áreas internas (tabelas com `scrollHeight`, painéis):
 *
 * ```html
 * <main appScrollMemory="main">…</main>
 * <p-table appScrollMemory="tabela" scrollMemoryTarget=".p-datatable-table-container">…</p-table>
 * ```
 *
 * Voltar/avançar e F5 restauram a posição (esperando os dados chegarem); navegação nova para
 * outro caminho começa do topo; troca só de queryParams (paginação, ordenação) mantém a posição.
 */
@Directive({
  selector: '[appScrollMemory]',
  standalone: true,
})
export class ScrollMemoryDirective {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly router = inject(Router);
  private readonly history = inject(NavigationHistoryService);
  private readonly store = inject(ViewStateStore);

  readonly appScrollMemory = input('main');
  /** Seletor do elemento que rola, dentro do host. Vazio = o próprio host. */
  readonly scrollMemoryTarget = input('');

  private cancelRestore: (() => void) | null = null;

  constructor() {
    const subscription = this.router.events.subscribe((event) => {
      if (event instanceof NavigationStart) {
        this.save();
      } else if (event instanceof NavigationEnd) {
        this.afterNavigation();
      }
    });
    const onPageHide = () => this.save();
    window.addEventListener('pagehide', onPageHide);
    inject(DestroyRef).onDestroy(() => {
      // Componente saindo de cena: a NavigationStart já salvou; aqui só liberamos recursos.
      subscription.unsubscribe();
      window.removeEventListener('pagehide', onPageHide);
      this.cancelRestore?.();
    });
  }

  private element(): HTMLElement | null {
    const selector = this.scrollMemoryTarget();
    return selector ? this.host.nativeElement.querySelector<HTMLElement>(selector) : this.host.nativeElement;
  }

  private storageKey(entryId: string): string {
    return `scroll:${entryId}:${this.appScrollMemory()}`;
  }

  private save(): void {
    const entryId = this.history.currentEntryId();
    const element = this.element();
    if (!entryId || !element) return;
    this.store.set(this.storageKey(entryId), element.scrollTop, { ttlMs: TTL_MS });
  }

  private afterNavigation(): void {
    this.cancelRestore?.();
    const entryId = this.history.currentEntryId();
    if (this.history.wasRestored() && entryId) {
      const saved = this.store.get<number>(this.storageKey(entryId));
      if (typeof saved === 'number' && saved > 0) this.restore(saved);
      return;
    }
    if (this.history.pathChanged()) {
      const element = this.element();
      if (element) element.scrollTop = 0;
    }
  }

  /** Tenta até o conteúdo ter altura suficiente (dados carregados) ou o usuário rolar por conta própria. */
  private restore(target: number): void {
    const started = Date.now();
    let frame = 0;
    const stop = () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('wheel', stop, true);
      window.removeEventListener('touchstart', stop, true);
      window.removeEventListener('keydown', stop, true);
      this.cancelRestore = null;
    };
    const attempt = () => {
      const element = this.element();
      if (element) {
        const max = element.scrollHeight - element.clientHeight;
        element.scrollTop = Math.min(target, Math.max(max, 0));
        if (max >= target || Date.now() - started > RESTORE_TIMEOUT_MS) {
          stop();
          return;
        }
      } else if (Date.now() - started > RESTORE_TIMEOUT_MS) {
        stop();
        return;
      }
      frame = requestAnimationFrame(attempt);
    };
    window.addEventListener('wheel', stop, true);
    window.addEventListener('touchstart', stop, true);
    window.addEventListener('keydown', stop, true);
    this.cancelRestore = stop;
    frame = requestAnimationFrame(attempt);
  }
}
