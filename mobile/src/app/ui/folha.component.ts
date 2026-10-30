import {
  ChangeDetectionStrategy, Component, ElementRef, OnDestroy, effect, input, output, signal, untracked, viewChild,
} from '@angular/core';

const DURACAO_MS = 280;
const ARRASTE_FECHA_PX = 90;

/**
 * Folha inferior (bottom sheet) — substitui modais centrais e dropdowns.
 *
 * - Fecha por: véu, Esc, alça arrastada para baixo e o **voltar do Android**
 *   (abre empurrando um estado no histórico; o voltar consome esse estado).
 * - Foco vai para a folha ao abrir e volta para quem a abriu ao fechar.
 * - Conteúdo rola por dentro sem arrastar a página (overscroll contido).
 * Quem usa decide se a folha aberta entra no estado de tela persistido.
 */
@Component({
  selector: 'ces-folha',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown.escape)': 'aberta() && pedirFechar()' },
  template: `
    @if (montada()) {
      <div class="veu" [class.visivel]="visivel()" (click)="pedirFechar()" aria-hidden="true"></div>
      <section #painel class="painel" [class.visivel]="visivel()" role="dialog" aria-modal="true"
        [attr.aria-labelledby]="idTitulo" tabindex="-1" [style.transform]="arraste() ? 'translateY(' + arraste() + 'px)' : null">
        <div class="alca-area" (pointerdown)="iniciarArraste($event)" aria-hidden="true"><span class="alca"></span></div>
        @if (titulo()) {
          <header class="cab">
            <h2 [id]="idTitulo">{{ titulo() }}</h2>
            @if (descricao()) { <p>{{ descricao() }}</p> }
          </header>
        }
        <div class="corpo"><ng-content /></div>
        <ng-content select="[rodape]" />
      </section>
    }
  `,
  styles: [`
    :host { position: fixed; inset: 0; z-index: 60; pointer-events: none; }
    .veu { position: absolute; inset: 0; background: var(--ces-veu); opacity: 0; pointer-events: auto;
      transition: opacity var(--ces-m-folha) var(--ces-curva); }
    .veu.visivel { opacity: 1; }
    .painel { position: absolute; left: 0; right: 0; bottom: 0; max-height: 88dvh; display: flex; flex-direction: column;
      background: var(--ces-superficie); border-radius: var(--ces-r-folha) var(--ces-r-folha) 0 0; box-shadow: var(--ces-sombra-folha);
      padding-bottom: calc(var(--ces-e4) + var(--ces-safe-base)); pointer-events: auto; outline: none;
      transform: translateY(102%); transition: transform var(--ces-m-folha) var(--ces-curva); }
    /* Aberta sem transform: assim o leitor em tela cheia (position: fixed) cobre a tela, não só o painel. */
    .painel.visivel { transform: none; }
    .alca-area { display: grid; place-items: center; height: 24px; flex: none; touch-action: none; cursor: grab; }
    .alca { width: 36px; height: 4px; border-radius: 99px; background: var(--ces-linha-forte); }
    .cab { padding: 0 var(--ces-e5) var(--ces-e3); flex: none; }
    h2 { margin: 0; font: 600 1.1875rem/1.3 var(--ces-fonte); }
    .cab p { margin: 4px 0 0; font: var(--ces-t-apoio); font-size: .875rem; color: var(--ces-texto-2); }
    .corpo { overflow-y: auto; overscroll-behavior: contain; min-height: 0; flex: 1; }
  `],
})
export class FolhaComponent implements OnDestroy {
  private static seq = 0;
  /**
   * Folhas abertas, da de baixo para a de cima. O voltar do Android fecha só a de cima;
   * fechar pela tela consome o próprio estado do histórico sem fechar a de baixo
   * (antes, escolher num seletor dentro de uma folha fechava as duas).
   */
  private static pilha: FolhaComponent[] = [];
  private static voltasIgnoradas = 0;
  private static ouvindo = false;
  private static readonly aoVoltarGlobal = () => {
    if (FolhaComponent.voltasIgnoradas > 0) { FolhaComponent.voltasIgnoradas--; return; }
    const topo = FolhaComponent.pilha.pop();
    if (!topo) return;
    topo.empurrouHistorico = false;
    topo.fechar.emit();
  };
  readonly idTitulo = `ces-folha-titulo-${++FolhaComponent.seq}`;

  readonly aberta = input(false);
  readonly titulo = input<string | null>(null);
  readonly descricao = input<string | null>(null);
  readonly fechar = output<void>();

  readonly montada = signal(false);
  readonly visivel = signal(false);
  readonly arraste = signal(0);
  private readonly painel = viewChild<ElementRef<HTMLElement>>('painel');

  private focoAnterior: HTMLElement | null = null;
  private empurrouHistorico = false;
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      const aberta = this.aberta();
      untracked(() => (aberta ? this.abrir() : this.ocultar()));
    });
  }

  pedirFechar(): void {
    this.fechar.emit();
  }

  iniciarArraste(ev: PointerEvent): void {
    const inicioY = ev.clientY;
    const alvo = ev.currentTarget as HTMLElement;
    alvo.setPointerCapture(ev.pointerId);
    const mover = (e: PointerEvent) => this.arraste.set(Math.max(0, e.clientY - inicioY));
    const soltar = () => {
      alvo.removeEventListener('pointermove', mover);
      alvo.removeEventListener('pointerup', soltar);
      alvo.removeEventListener('pointercancel', soltar);
      const fecha = this.arraste() > ARRASTE_FECHA_PX;
      this.arraste.set(0);
      if (fecha) this.pedirFechar();
    };
    alvo.addEventListener('pointermove', mover);
    alvo.addEventListener('pointerup', soltar);
    alvo.addEventListener('pointercancel', soltar);
  }

  private abrir(): void {
    if (this.timer) clearTimeout(this.timer);
    this.focoAnterior = document.activeElement as HTMLElement | null;
    this.montada.set(true);
    // Dois quadros: monta fora da tela, depois anima para dentro.
    requestAnimationFrame(() => requestAnimationFrame(() => {
      this.visivel.set(true);
      this.painel()?.nativeElement.focus({ preventScroll: true });
    }));
    if (!this.empurrouHistorico) {
      history.pushState({ ...(history.state ?? {}), cesFolha: true }, '');
      this.empurrouHistorico = true;
      FolhaComponent.pilha.push(this);
      if (!FolhaComponent.ouvindo) {
        FolhaComponent.ouvindo = true;
        window.addEventListener('popstate', FolhaComponent.aoVoltarGlobal);
      }
    }
  }

  private ocultar(): void {
    if (!this.montada()) return;
    this.visivel.set(false);
    this.timer = setTimeout(() => this.montada.set(false), DURACAO_MS);
    this.focoAnterior?.focus?.({ preventScroll: true });
    this.liberarHistorico();
  }

  private liberarHistorico(): void {
    FolhaComponent.pilha = FolhaComponent.pilha.filter(f => f !== this);
    if (this.empurrouHistorico) {
      this.empurrouHistorico = false;
      // Fechou pela UI: consome o estado que a folha empurrou, sem que isso feche outra folha.
      if (history.state?.cesFolha) {
        FolhaComponent.voltasIgnoradas++;
        history.back();
      }
    }
  }

  ngOnDestroy(): void {
    if (this.timer) clearTimeout(this.timer);
    // Saiu da tela com a folha aberta: só sai da pilha (a navegação já mudou o histórico).
    FolhaComponent.pilha = FolhaComponent.pilha.filter(f => f !== this);
  }
}
