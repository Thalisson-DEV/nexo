import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  viewChild,
} from '@angular/core';
import { DrawerModule } from 'primeng/drawer';
import { CES_COMPACT_VIEWPORT_QUERY, cesMediaQuery } from '../../core/media';
import { CesConfirmationService } from '../../overlays/confirmation/confirmation.service';
import { CesFormRegistry } from './form-kit.registry';

export type CesFormDrawerSize = 'sm' | 'md' | 'lg' | 'xl';

const WIDTHS: Record<CesFormDrawerSize, string> = {
  sm: '26rem',
  md: '34rem',
  lg: '42rem',
  xl: '56rem',
};

let drawerSequence = 0;

/**
 * Shell de cadastro em drawer (tipos A e B): título, corpo rolável, rodapé fixo de ações e larguras
 * sm/md/lg/xl. Fecha com Esc, botão Fechar ou Cancelar — **pedindo confirmação se houver alterações**
 * (`dirty`). Ao abrir, foca o primeiro campo; ao fechar, o foco volta para quem abriu.
 *
 * ```html
 * <ces-form-drawer [(visible)]="aberto" [title]="editando() ? 'Editar contrato' : 'Novo contrato'"
 *   [dirty]="form.dirty" (closed)="fechar()">
 *   <form [formGroup]="form">…ces-form-section / ces-form-field…</form>
 *   <ces-form-actions drawer-footer [saveAndNew]="!editando()" (save)="salvar()" (cancel)="drawer.requestClose()" />
 * </ces-form-drawer>
 * ```
 *
 * Para F5/voltar do navegador funcionarem, ligue o drawer à URL com `bindDrawerToUrl` (core/navigation).
 */
@Component({
  selector: 'ces-form-drawer',
  standalone: true,
  imports: [DrawerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [CesFormRegistry],
  host: { '(document:keydown.escape)': 'onEscape($event)' },
  template: `
    <p-drawer
      [visible]="visible()"
      (visibleChange)="!$event && requestClose()"
      [position]="compact() ? 'bottom' : 'right'"
      [modal]="true"
      [blockScroll]="true"
      [dismissible]="false"
      [closeOnEscape]="false"
      [showCloseIcon]="false"
      [style]="drawerStyle()"
      styleClass="ces-form-drawer"
      appendTo="body"
      (onShow)="onShow()"
      (onHide)="onHide()"
    >
      <ng-template #header>
        <div class="ces-form-drawer__header">
          @if (compact()) {
            <span class="ces-drawer__handle" aria-hidden="true"></span>
          }
          <div class="ces-form-drawer__heading">
            <h2 class="ces-form-drawer__title" [id]="titleId">{{ title() }}</h2>
            @if (description()) {
              <p class="ces-form-drawer__description">{{ description() }}</p>
            }
          </div>
          <button type="button" class="ces-form-drawer__close" aria-label="Fechar" title="Fechar (Esc)" (click)="requestClose()">
            <i class="pi pi-times" aria-hidden="true"></i>
          </button>
        </div>
      </ng-template>

      <section #body class="ces-form-drawer__body" role="form" [attr.aria-labelledby]="titleId" [attr.aria-busy]="loading()"
       >
        @if (loading()) {
          <div class="ces-form-skeleton" role="status" aria-label="Carregando">
            <span class="ces-form-skeleton__title"></span>
            <span class="ces-form-skeleton__row"><span></span><span></span></span>
            <span class="ces-form-skeleton__row"><span></span><span></span></span>
          </div>
        }
        <div [hidden]="loading()" class="ces-form-drawer__content">
          <ng-content></ng-content>
        </div>
      </section>

      <ng-template #footer>
        <div class="ces-form-drawer__footer">
          <ng-content select="[drawer-footer]"></ng-content>
        </div>
      </ng-template>
    </p-drawer>
  `,
})
export class CesFormDrawerComponent {
  private readonly confirmacao = inject(CesConfirmationService);
  readonly registry = inject(CesFormRegistry);

  readonly visible = model(false);
  readonly title = input.required<string>();
  readonly description = input<string | undefined>();
  readonly size = input<CesFormDrawerSize>('md');
  /** Há alterações não salvas: fechar pede confirmação. */
  readonly dirty = input(false);
  readonly loading = input(false);
  readonly discardMessage = input('As alterações feitas neste formulário serão perdidas.');

  /** Depois de fechar (por qualquer meio). */
  readonly closed = output<void>();

  private readonly body = viewChild<ElementRef<HTMLElement>>('body');
  protected readonly titleId = `ces-form-drawer-${++drawerSequence}`;
  protected readonly compact = cesMediaQuery(CES_COMPACT_VIEWPORT_QUERY);
  private confirmando = false;
  private origemFoco: HTMLElement | null = null;

  constructor() {
    // Guarda quem abriu o drawer antes de o foco entrar nele, para devolver ao fechar.
    effect(() => {
      if (this.visible() && !this.origemFoco) this.origemFoco = document.activeElement as HTMLElement | null;
    });
  }

  protected readonly drawerStyle = computed<Record<string, string>>((): Record<string, string> =>
    this.compact()
      ? {
          height: 'auto',
          maxHeight: '92dvh',
          borderTopLeftRadius: 'var(--ces-radius-lg)',
          borderTopRightRadius: 'var(--ces-radius-lg)',
        }
      : { width: `min(${WIDTHS[this.size()]}, 100vw)` },
  );

  /** Fecha; com alterações, pergunta antes. Use no Cancelar do rodapé. */
  async requestClose(): Promise<boolean> {
    if (!this.visible() || this.confirmando) return false;
    if (this.dirty()) {
      this.confirmando = true;
      const descartar = await this.confirmacao.confirm({
        title: 'Descartar alterações?',
        message: this.discardMessage(),
        confirmLabel: 'Descartar',
        cancelLabel: 'Continuar editando',
        variant: 'destructive',
      });
      this.confirmando = false;
      if (!descartar) return false;
    }
    this.visible.set(false);
    return true;
  }

  /** Foca o primeiro campo (útil depois de "Salvar e novo"). */
  focusFirstField(): void {
    const primeiro = this.registry.fields()[0];
    if (primeiro) {
      primeiro.focus();
      return;
    }
    this.body()?.nativeElement.querySelector<HTMLElement>('input, textarea, [role="combobox"]')?.focus();
  }

  protected onEscape(event: Event): void {
    if (!this.visible() || event.defaultPrevented) return;
    // Esc com um overlay aberto (select, datepicker, confirmação) fecha só o overlay.
    const alvo = event.target as HTMLElement | null;
    if (alvo?.closest?.('.p-overlay, .p-select-overlay, .p-datepicker-panel, .p-confirmdialog')) return;
    if (document.querySelector('.p-select-overlay, .p-datepicker-panel, .p-confirmdialog, .p-dialog-mask')) return;
    // Só o drawer de cima responde.
    const paineis = Array.from(document.querySelectorAll('.p-drawer'));
    const meu = this.body()?.nativeElement.closest('.p-drawer');
    if (meu && paineis.at(-1) !== meu) return;
    event.preventDefault();
    void this.requestClose();
  }

  protected onShow(): void {
    setTimeout(() => this.focusFirstField());
  }

  protected onHide(): void {
    if (this.origemFoco?.isConnected) this.origemFoco.focus();
    this.origemFoco = null;
    this.closed.emit();
  }
}
