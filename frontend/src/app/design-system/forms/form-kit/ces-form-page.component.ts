import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterNextRender,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { NavigationHistoryService } from '../../../core/navigation/navigation-history.service';
import { CesFormRegistry } from './form-kit.registry';

export interface CesFormBreadcrumb {
  label: string;
  link?: string | readonly unknown[];
}

export type CesFormPageWidth = 'md' | 'lg' | 'xl';

/**
 * Shell de formulário em página (tipos B, C e D): breadcrumb, Voltar único, cabeçalho com ícone em
 * caixa + título + descrição + ajuda, corpo, painel lateral opcional e rodapé fixo de ações.
 *
 * ```html
 * <ces-form-page title="Nova solicitação de compra" icon="pi pi-shopping-cart"
 *   [breadcrumb]="[{ label: 'Financeiro' }, { label: 'Compras', link: '/financeiro/compras' }, { label: 'Nova' }]"
 *   backFallback="/financeiro/compras" [sectionNav]="true" (help)="ajuda.show()">
 *   <ces-form-section title="Dados">…</ces-form-section>
 *   <ces-form-summary form-aside [form]="form" />
 *   <ces-form-actions form-actions (save)="salvar()" (cancel)="cancelar()" />
 * </ces-form-page>
 * ```
 *
 * O Voltar usa `NavigationHistoryService.back()` (volta exatamente ao estado anterior; sem histórico,
 * vai para `backFallback`). Com `sectionNav`, as `ces-form-section` viram um índice de âncoras.
 */
@Component({
  selector: 'ces-form-page',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [CesFormRegistry],
  host: {
    class: 'ces-form-page',
    '[class.ces-form-page--md]': "width() === 'md'",
    '[class.ces-form-page--xl]': "width() === 'xl'",
    '[class.ces-form-page--nav]': 'showNav()',
  },
  template: `
    <div class="ces-form-page__top">
      @if (breadcrumb().length) {
        <nav class="ces-form-page__breadcrumb" aria-label="Você está em">
          <ol>
            <li><a routerLink="/home" aria-label="Início"><i class="pi pi-home" aria-hidden="true"></i></a></li>
            @for (item of breadcrumb(); track $index; let last = $last) {
              <li [attr.aria-current]="last ? 'page' : null">
                @if (item.link && !last) {
                  <a [routerLink]="item.link">{{ item.label }}</a>
                } @else {
                  <span>{{ item.label }}</span>
                }
              </li>
            }
          </ol>
        </nav>
      }
      @if (showBack()) {
        <button type="button" class="ces-form-page__back" (click)="voltar()">
          <i class="pi pi-arrow-left" aria-hidden="true"></i>
          <span>{{ backLabel() }}</span>
        </button>
      }
    </div>

    <div class="ces-form-card">
      <header class="ces-form-card__header">
        @if (icon()) {
          <span class="ces-form-card__icon" aria-hidden="true"><i [class]="icon()"></i></span>
        }
        <div class="ces-form-card__heading">
          @if (eyebrow()) {
            <span class="ces-form-card__eyebrow">{{ eyebrow() }}</span>
          }
          <h1 class="ces-form-card__title">{{ title() }}</h1>
          @if (description()) {
            <p class="ces-form-card__description">{{ description() }}</p>
          }
        </div>
        <div class="ces-form-card__header-actions">
          <ng-content select="[form-header-actions]"></ng-content>
          @if (helpAvailable()) {
            <button type="button" class="ces-form-card__help" aria-label="Ajuda deste formulário" title="Ajuda" (click)="help.emit()">
              <i class="pi pi-question-circle" aria-hidden="true"></i>
            </button>
          }
        </div>
      </header>

      <ng-content select="ces-form-stepper, [form-stepper]"></ng-content>

      <div class="ces-form-card__layout">
        @if (showNav()) {
          <nav class="ces-form-nav" aria-label="Seções do formulário">
            <ol>
              @for (secao of registry.sections(); track secao.id) {
                <li>
                  <a
                    [href]="'#' + secao.id"
                    [class.ces-form-nav__link--active]="ativa() === secao.id"
                    [attr.aria-current]="ativa() === secao.id ? 'location' : null"
                    (click)="irPara($event, secao.id)"
                  >
                    @if (secao.icon()) {
                      <i [class]="secao.icon()" aria-hidden="true"></i>
                    }
                    <span>{{ secao.title() }}</span>
                  </a>
                </li>
              }
            </ol>
          </nav>
        }

        <div class="ces-form-card__body" [attr.aria-busy]="loading()">
          @if (loading()) {
            <div class="ces-form-skeleton" role="status" aria-label="Carregando formulário">
              @for (linha of [1, 2, 3]; track linha) {
                <span class="ces-form-skeleton__title"></span>
                <span class="ces-form-skeleton__row"><span></span><span></span></span>
                <span class="ces-form-skeleton__row"><span></span><span></span></span>
              }
            </div>
          }
          <div [hidden]="loading()" class="ces-form-card__content">
            <ng-content></ng-content>
          </div>
        </div>

        <div class="ces-form-card__aside">
          <ng-content select="[form-aside]"></ng-content>
        </div>
      </div>

      <footer class="ces-form-card__footer">
        <ng-content select="[form-actions]"></ng-content>
      </footer>
    </div>
  `,
})
export class CesFormPageComponent {
  private readonly navegacao = inject(NavigationHistoryService);
  readonly registry = inject(CesFormRegistry);

  readonly title = input.required<string>();
  readonly description = input<string | undefined>();
  readonly eyebrow = input<string | undefined>();
  /** Classe do ícone (`pi pi-...`), exibido em caixa. */
  readonly icon = input<string | undefined>();
  readonly breadcrumb = input<readonly CesFormBreadcrumb[]>([]);
  readonly showBack = input(true);
  readonly backLabel = input('Voltar');
  /** Destino do Voltar quando não há histórico interno (deep link/F5). */
  readonly backFallback = input<string | readonly unknown[] | undefined>();
  readonly helpAvailable = input(false);
  /** Índice lateral de seções (tipo C). Só aparece com 2+ seções e em telas largas. */
  readonly sectionNav = input(false);
  readonly loading = input(false);
  readonly width = input<CesFormPageWidth>('lg');
  /** Foca o primeiro campo ao abrir (desligue em telas de visualização). */
  readonly autofocus = input(true);

  readonly help = output<void>();

  protected readonly ativa = signal<string | null>(null);
  protected readonly showNav = () => this.sectionNav() && this.registry.sections().length > 1;

  constructor() {
    this.observarSecoes();
    afterNextRender(() => this.focarPrimeiroCampo());
  }

  protected voltar(): void {
    this.navegacao.back(this.backFallback());
  }

  protected irPara(event: Event, id: string): void {
    event.preventDefault();
    const alvo = document.getElementById(id);
    if (!alvo) return;
    alvo.scrollIntoView({ behavior: 'smooth', block: 'start' });
    this.ativa.set(id);
    const titulo = alvo.querySelector<HTMLElement>('.ces-form-section__title');
    if (titulo) {
      titulo.tabIndex = -1;
      titulo.focus({ preventScroll: true });
    }
  }

  private focarPrimeiroCampo(): void {
    if (!this.autofocus()) return;
    // Espera os dados (loading) para não focar um campo que ainda vai ser preenchido.
    const tentar = (restantes: number) => {
      if (this.loading() && restantes > 0) {
        setTimeout(() => tentar(restantes - 1), 100);
        return;
      }
      const primeiro = this.registry.fields()[0];
      if (primeiro && !document.activeElement?.closest('.ces-form-card__body')) primeiro.focus();
    };
    tentar(40);
  }

  /** Marca no índice a seção visível (a primeira que cruza o terço superior da tela). */
  private observarSecoes(): void {
    if (typeof IntersectionObserver === 'undefined') return;
    const visiveis = new Set<string>();
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) visiveis.add(entry.target.id);
          else visiveis.delete(entry.target.id);
        }
        const primeira = this.registry.sections().find((secao) => visiveis.has(secao.id));
        if (primeira) this.ativa.set(primeira.id);
      },
      { rootMargin: '-15% 0px -60% 0px' },
    );
    effect(() => {
      observer.disconnect();
      if (!this.showNav()) return;
      for (const secao of this.registry.sections()) observer.observe(secao.element());
    });
    inject(DestroyRef).onDestroy(() => observer.disconnect());
  }
}
