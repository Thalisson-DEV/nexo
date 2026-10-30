import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { CopilotoDashboardContext } from '../contexto/copiloto-contexto';
import { CopilotoPainelComponent } from './copiloto-painel.component';
import { CopilotoPainelService } from './copiloto-painel.service';

/** Largura mínima da viewport para o painel ficar lado a lado com o dashboard. */
const LARGURA_ACOPLADA = 1280;
const LARGURA_MINIMA = 340;
const LARGURA_MAXIMA = 720;

/**
 * Área de um dashboard com Copiloto contextual. Não altera o dashboard: projeta o conteúdo existente
 * numa região rolável (com o mesmo espaçamento do shell da aplicação) e acrescenta o painel lateral do
 * Copiloto. O gatilho (`app-copiloto-gatilho`) fica no cabeçalho do próprio dashboard.
 *
 * - Telas largas (≥ 1280px): painel lado a lado, redimensionável pela borda (mouse ou setas do teclado).
 *   A região do dashboard é um container `copiloto-area`, usado pelos grids para se reorganizar.
 * - Telas menores: drawer sobreposto (tela cheia no celular), com fundo clicável e Esc para fechar.
 * - Fechar o painel não descarta a conversa: o componente continua montado.
 *
 * Uso: `<app-copiloto-area [contexto]="contextoCopiloto()">…dashboard…</app-copiloto-area>` e
 * `data: { fullBleed: true }` na rota. Sem a permissão `assistente:chat:use`, só o dashboard é exibido.
 */
@Component({
  selector: 'app-copiloto-area',
  standalone: true,
  imports: [CopilotoPainelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'copiloto-area',
    '[attr.data-modo]': 'modo()',
    '[attr.data-aberto]': 'aberto()',
  },
  template: `
    <div class="area" [style.--sp-largura]="painel.largura() + 'px'">
      <div class="area__conteudo" [style.overflow-y]="aberto() && modo() === 'sobreposto' ? 'hidden' : null">
        <ng-content></ng-content>
      </div>
      @if (disponivel) {
        @if (aberto() && modo() === 'sobreposto') {
          <div class="area__fundo" aria-hidden="true" (click)="painel.fechar()"></div>
        }
        <aside
          id="copiloto-painel-contextual"
          class="area__painel"
          [class.area__painel--aberto]="aberto()"
          aria-label="Copiloto"
          [attr.aria-hidden]="!aberto()"
          [attr.inert]="aberto() ? null : ''"
          [attr.role]="modo() === 'sobreposto' ? 'dialog' : 'complementary'"
          [attr.aria-modal]="modo() === 'sobreposto' && aberto() ? 'true' : null"
          (keydown.escape)="modo() === 'sobreposto' && painel.fechar()"
        >
          @if (modo() === 'acoplado') {
            <div
              class="area__alca"
              role="separator"
              tabindex="0"
              aria-orientation="vertical"
              aria-label="Redimensionar o painel do Copiloto"
              [attr.aria-valuemin]="minimo"
              [attr.aria-valuemax]="maximo"
              [attr.aria-valuenow]="painel.largura()"
              (pointerdown)="iniciarArraste($event)"
              (keydown)="redimensionarPorTeclado($event)"
            ></div>
          }
          @if (montado()) {
            <app-copiloto-painel [contexto]="contexto()"></app-copiloto-painel>
          }
        </aside>
      }
    </div>
  `,
  styles: `
    :host { display: block; height: 100%; min-height: 0; }
    .area { position: relative; display: flex; height: 100%; min-height: 0; }
    .area__conteudo {
      flex: 1 1 auto;
      min-width: 0;
      min-height: 0;
      overflow-x: hidden;
      overflow-y: auto;
      padding: 1rem;
      container-type: inline-size;
      container-name: copiloto-area;
    }
    @media (min-width: 1024px) { .area__conteudo { padding: 1.5rem; } }
    .area__painel {
      position: relative;
      display: none;
      flex: 0 0 var(--sp-largura, 420px);
      width: var(--sp-largura, 420px);
      min-width: 0;
      min-height: 0;
      border-left: 1px solid var(--ces-border);
      background: var(--ces-surface);
    }
    .area__painel--aberto { display: flex; flex-direction: column; }
    .area__painel > app-copiloto-painel { flex: 1 1 auto; min-height: 0; }
    .area__alca {
      position: absolute;
      inset-block: 0;
      left: -4px;
      z-index: 2;
      width: 8px;
      cursor: col-resize;
      touch-action: none;
    }
    .area__alca::after {
      content: '';
      position: absolute;
      inset-block: 0;
      left: 3px;
      width: 2px;
      background: transparent;
      transition: background var(--ces-duration-fast) var(--ces-ease-standard);
    }
    .area__alca:hover::after, .area__alca:focus-visible::after { background: var(--ces-brand); }
    .area__alca:focus-visible { outline: none; }
    :host([data-modo='sobreposto']) .area__painel {
      position: fixed;
      inset-block: 0;
      right: 0;
      z-index: 60;
      flex: none;
      width: min(100vw, 440px);
      box-shadow: var(--ces-shadow-lg);
    }
    .area__fundo {
      position: fixed;
      inset: 0;
      z-index: 59;
      background: color-mix(in srgb, var(--ces-foreground) 38%, transparent);
    }
    @media (max-width: 640px) {
      :host([data-modo='sobreposto']) .area__painel { width: 100vw; border-left: 0; }
    }
    @media (prefers-reduced-motion: no-preference) {
      :host([data-modo='sobreposto']) .area__painel--aberto { animation: sp-entrar var(--ces-duration-normal) var(--ces-ease-standard); }
      @keyframes sp-entrar { from { transform: translateX(24px); opacity: 0; } to { transform: none; opacity: 1; } }
    }
    @media print {
      .area__painel, .area__fundo { display: none !important; }
      .area, .area__conteudo { height: auto; overflow: visible; }
    }
  `,
})
export class CopilotoAreaComponent {
  protected readonly painel = inject(CopilotoPainelService);
  protected readonly disponivel = this.painel.disponivelParaUsuario();
  protected readonly minimo = LARGURA_MINIMA;
  protected readonly maximo = LARGURA_MAXIMA;

  /** Contexto estruturado do dashboard (um `computed()` que acompanha filtros e dados aplicados). */
  readonly contexto = input.required<CopilotoDashboardContext | null>();

  private readonly larga = signal(this.viewportLarga());
  protected readonly modo = computed<'acoplado' | 'sobreposto'>(() => (this.larga() ? 'acoplado' : 'sobreposto'));
  protected readonly aberto = computed(() => this.disponivel && this.painel.aberto());
  /**
   * O painel só é criado na primeira abertura (quem nunca abre não chama o Copiloto) e, depois disso,
   * continua montado enquanto o dashboard existir: fechar e reabrir preserva conversa e rascunho.
   */
  protected readonly montado = signal(false);

  constructor() {
    effect(() => {
      if (this.aberto()) this.montado.set(true);
    });
    // Torna o contexto da tela visível às mensagens (validação de "Aplicar filtro").
    effect(() => this.painel.contextoAtivo.set(this.contexto()));
    inject(DestroyRef).onDestroy(() => this.painel.contextoAtivo.set(null));
    const midia = typeof window !== 'undefined' ? window.matchMedia?.(`(min-width: ${LARGURA_ACOPLADA}px)`) : undefined;
    if (midia) {
      const aoMudar = (evento: MediaQueryListEvent) => this.larga.set(evento.matches);
      midia.addEventListener?.('change', aoMudar);
      inject(DestroyRef).onDestroy(() => midia.removeEventListener?.('change', aoMudar));
    }
  }

  protected iniciarArraste(evento: PointerEvent): void {
    evento.preventDefault();
    const alca = evento.currentTarget as HTMLElement;
    alca.setPointerCapture?.(evento.pointerId);
    const inicioX = evento.clientX;
    const larguraInicial = this.painel.largura();
    const mover = (movimento: PointerEvent) => this.definirLargura(larguraInicial + (inicioX - movimento.clientX));
    const parar = () => {
      alca.removeEventListener('pointermove', mover);
      alca.removeEventListener('pointerup', parar);
      alca.removeEventListener('pointercancel', parar);
    };
    alca.addEventListener('pointermove', mover);
    alca.addEventListener('pointerup', parar);
    alca.addEventListener('pointercancel', parar);
  }

  protected redimensionarPorTeclado(evento: KeyboardEvent): void {
    const passo = evento.shiftKey ? 64 : 24;
    const atual = this.painel.largura();
    if (evento.key === 'ArrowLeft') this.definirLargura(atual + passo);
    else if (evento.key === 'ArrowRight') this.definirLargura(atual - passo);
    else if (evento.key === 'Home') this.definirLargura(LARGURA_MINIMA);
    else if (evento.key === 'End') this.definirLargura(LARGURA_MAXIMA);
    else return;
    evento.preventDefault();
  }

  /** Nunca deixa o dashboard com menos de ~55% da largura da janela. */
  private definirLargura(largura: number): void {
    const janela = typeof window !== 'undefined' ? window.innerWidth : 1600;
    const maximo = Math.max(LARGURA_MINIMA, Math.min(LARGURA_MAXIMA, Math.round(janela * 0.45)));
    this.painel.largura.set(Math.round(Math.max(LARGURA_MINIMA, Math.min(maximo, largura))));
  }

  private viewportLarga(): boolean {
    return typeof window === 'undefined' || window.matchMedia?.(`(min-width: ${LARGURA_ACOPLADA}px)`).matches !== false;
  }
}
