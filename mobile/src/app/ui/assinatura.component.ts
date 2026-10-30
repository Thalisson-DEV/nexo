import { ChangeDetectionStrategy, Component, ElementRef, effect, input, model, signal, untracked, viewChild } from '@angular/core';
import { FolhaComponent } from './folha.component';
import { IconeComponent } from './icone.component';

/**
 * Assinatura digital colhida no aparelho: um cartão que mostra a assinatura (ou "Toque
 * para assinar") e abre um quadro em folha inferior. O valor é um PNG em data URL, no
 * formato que o backend guarda. Assinar de novo substitui; "Refazer" limpa o quadro.
 */
@Component({
  selector: 'ces-assinatura',
  standalone: true,
  imports: [FolhaComponent, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="cartao" [class.feita]="!!valor()" [class.invalida]="invalida() && !valor()" (click)="abrir()"
      [attr.aria-label]="rotulo() + (valor() ? ': assinada. Toque para assinar de novo.' : ': não assinada. Toque para assinar.')">
      <span class="topo">
        <span class="rotulo">{{ rotulo() }}</span>
        @if (valor()) { <span class="ok"><ces-icone nome="check" [tamanho]="16" />Assinada</span> }
      </span>
      @if (quem()) { <span class="quem">{{ quem() }}</span> }
      @if (valor(); as img) {
        <img [src]="img" alt="">
      } @else {
        <span class="vazia"><ces-icone nome="assinatura" />Toque para assinar</span>
      }
    </button>

    <ces-folha [aberta]="aberto()" (fechar)="aberto.set(false)" [titulo]="rotulo()" [descricao]="quem() ? 'Assina: ' + quem() : 'Assine com o dedo no quadro.'">
      <div class="quadro">
        <canvas #quadro aria-label="Quadro de assinatura" (pointerdown)="iniciar($event)" (pointermove)="tracar($event)"
          (pointerup)="terminar()" (pointercancel)="terminar()" (pointerleave)="terminar()"></canvas>
        <span class="linha" aria-hidden="true"></span>
        @if (!desenhou()) { <span class="dica" aria-hidden="true">Assine aqui</span> }
      </div>
      <div rodape class="ces-folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="preparar()" [disabled]="!desenhou()">Refazer</button>
        <button type="button" class="ces-btn ces-btn--principal" (click)="confirmar()" [disabled]="!desenhou()">Confirmar assinatura</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: block; }
    .cartao { width: 100%; display: grid; gap: var(--ces-e2); padding: var(--ces-e3) var(--ces-e4); min-height: 148px; box-sizing: border-box;
      border: 1.5px dashed var(--ces-linha-forte); border-radius: var(--ces-r-grupo); background: var(--ces-superficie); text-align: left;
      color: var(--ces-texto); cursor: pointer; font: inherit; transition: border-color var(--ces-m-entrar); }
    .cartao:active { background: var(--ces-superficie-2); }
    .cartao.feita { border-style: solid; border-color: var(--ces-ok); }
    .cartao.invalida { border-color: var(--ces-erro); }
    .topo { display: flex; align-items: center; justify-content: space-between; gap: var(--ces-e2); }
    .rotulo { font: 600 .875rem/1.3 var(--ces-fonte); color: var(--ces-texto); }
    .quem { font: var(--ces-t-apoio); color: var(--ces-texto-2); margin-top: -6px; }
    .ok { display: inline-flex; align-items: center; gap: 4px; font: 600 .75rem/1 var(--ces-fonte); color: var(--ces-ok); }
    img { width: 100%; height: 88px; object-fit: contain; background: #fff; border-radius: 8px; }
    .vazia { display: flex; align-items: center; justify-content: center; gap: var(--ces-e2); min-height: 88px; color: var(--ces-destaque); font-weight: 600; }
    .quadro { position: relative; padding: 0 var(--ces-e4); }
    canvas { width: 100%; height: 260px; display: block; border-radius: var(--ces-r-grupo); background: #fff; border: 1px solid var(--ces-linha-forte); touch-action: none; }
    .linha { position: absolute; left: calc(var(--ces-e4) + 24px); right: calc(var(--ces-e4) + 24px); bottom: 56px; height: 1px; background: #c9d1dc; pointer-events: none; }
    .dica { position: absolute; left: calc(var(--ces-e4) + 24px); bottom: 64px; font: 500 .8125rem/1 var(--ces-fonte); color: #7c8899; pointer-events: none; }
  `],
})
export class AssinaturaComponent {
  readonly rotulo = input.required<string>();
  /** Nome de quem assina, quando já escolhido. */
  readonly quem = input<string | null>(null);
  readonly invalida = input(false);
  readonly valor = model<string | null>(null);
  readonly aberto = signal(false);
  readonly desenhou = signal(false);
  private readonly quadro = viewChild<ElementRef<HTMLCanvasElement>>('quadro');
  private ctx: CanvasRenderingContext2D | null = null;
  private desenhando = false;

  constructor() {
    effect(() => {
      const canvas = this.quadro();
      if (this.aberto() && canvas) untracked(() => requestAnimationFrame(() => requestAnimationFrame(() => this.preparar())));
    });
  }

  abrir(): void {
    this.desenhou.set(false);
    this.aberto.set(true);
  }

  preparar(): void {
    const canvas = this.quadro()?.nativeElement;
    if (!canvas) return;
    const r = canvas.getBoundingClientRect();
    if (!r.width) return;
    const escala = window.devicePixelRatio || 1;
    canvas.width = r.width * escala;
    canvas.height = r.height * escala;
    this.ctx = canvas.getContext('2d');
    if (!this.ctx) return;
    this.ctx.scale(escala, escala);
    this.ctx.fillStyle = '#ffffff';
    this.ctx.fillRect(0, 0, r.width, r.height);
    this.ctx.lineWidth = 2.6;
    this.ctx.lineCap = 'round';
    this.ctx.lineJoin = 'round';
    this.ctx.strokeStyle = '#0e1726';
    this.desenhou.set(false);
  }

  private ponto(e: PointerEvent): { x: number; y: number } | null {
    const canvas = this.quadro()?.nativeElement;
    if (!canvas) return null;
    const r = canvas.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  }

  iniciar(e: PointerEvent): void {
    e.preventDefault();
    (e.target as Element).setPointerCapture?.(e.pointerId);
    const p = this.ponto(e);
    if (!this.ctx || !p) return;
    this.desenhando = true;
    this.ctx.beginPath();
    this.ctx.moveTo(p.x, p.y);
    // Um toque sem arrastar também marca (ponto).
    this.ctx.lineTo(p.x + 0.1, p.y + 0.1);
    this.ctx.stroke();
  }

  tracar(e: PointerEvent): void {
    if (!this.desenhando || !this.ctx) return;
    const p = this.ponto(e);
    if (!p) return;
    this.ctx.lineTo(p.x, p.y);
    this.ctx.stroke();
    this.ctx.beginPath();
    this.ctx.moveTo(p.x, p.y);
    this.desenhou.set(true);
  }

  terminar(): void {
    this.desenhando = false;
    this.ctx?.beginPath();
  }

  confirmar(): void {
    const canvas = this.quadro()?.nativeElement;
    if (!canvas || !this.desenhou()) return;
    this.valor.set(canvas.toDataURL('image/png'));
    this.aberto.set(false);
  }
}
