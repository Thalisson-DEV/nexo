import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injectable,
  OnDestroy,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import type { PDFDocumentProxy } from 'pdfjs-dist';
import {
  CES_PDF_ZOOM_MAXIMO,
  CES_PDF_ZOOM_MINIMO,
  CesPdfRegiao,
  CesPdfTrechoTexto,
  cesPdfOrdenarTexto,
  limitarZoom,
  normalizarRotacao
} from './ces-pdf-canvas.geometry';

/** Carrega o PDF.js só quando há um PDF para abrir; o worker vai junto da aplicação, sem CDN. */
@Injectable({ providedIn: 'root' })
export class CesPdfLoader {
  async abrir(dados: ArrayBuffer): Promise<PDFDocumentProxy> {
    const pdfjs = await import('pdfjs-dist');
    pdfjs.GlobalWorkerOptions.workerSrc = new URL('assets/pdfjs/pdf.worker.min.mjs', document.baseURI).href;
    // O worker fica com o buffer: passa uma cópia para o chamador poder reabrir o mesmo arquivo.
    return pdfjs.getDocument({ data: dados.slice(0) }).promise;
  }
}

const PASSO_ZOOM = 1.25;
const ATRASO_RENDER_MS = 120;
const MAXIMO_PIXELS_CANVAS = 16_000_000;

/**
 * Exibe um PDF página a página, com zoom ancorado, arraste, toque, rotação e extração de texto por região.
 * O conteúdo projetado em `[pdf-overlay]` fica sobre a folha, do tamanho exato da página na tela.
 */
@Component({
  selector: 'ces-pdf-canvas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      #area
      class="ces-pdf-area"
      tabindex="0"
      role="application"
      [attr.aria-label]="ariaLabel()"
      [class.ces-pdf-area--mao]="panMode()"
      [class.ces-pdf-area--arrastando]="arrastando()"
      (wheel)="aoRolar($event)"
      (pointerdown)="aoApertar($event)"
      (pointermove)="aoMover($event)"
      (pointerup)="aoSoltar($event)"
      (pointercancel)="aoSoltar($event)"
      (auxclick)="$event.preventDefault()"
    >
      <div
        class="ces-pdf-folha"
        [style.width.px]="larguraTela()"
        [style.height.px]="alturaTela()"
        [style.visibility]="larguraTela() ? 'visible' : 'hidden'"
      >
        <canvas #canvas class="ces-pdf-canvas" [style.opacity]="1 - dim()"></canvas>
        <div class="ces-pdf-overlay"><ng-content select="[pdf-overlay]"></ng-content></div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      height: 100%;
      min-height: 0;
    }

    .ces-pdf-area {
      position: relative;
      width: 100%;
      height: 100%;
      overflow: auto;
      padding: 1rem;
      background: var(--ces-surface-muted);
      overscroll-behavior: contain;
      outline: none;
    }

    .ces-pdf-area:focus-visible {
      box-shadow: inset 0 0 0 2px var(--ces-focus-ring, var(--ces-brand));
    }

    .ces-pdf-area--mao {
      cursor: grab;
      touch-action: none;
    }

    .ces-pdf-area--arrastando {
      cursor: grabbing;
      user-select: none;
    }

    .ces-pdf-folha {
      position: relative;
      margin: 0 auto;
      background: #fff;
      box-shadow: 0 1px 6px rgb(0 0 0 / 0.25);
    }

    .ces-pdf-canvas {
      display: block;
      width: 100%;
      height: 100%;
    }

    .ces-pdf-overlay {
      position: absolute;
      inset: 0;
    }
  `]
})
export class CesPdfCanvasComponent implements OnDestroy {
  private readonly loader = inject(CesPdfLoader);

  /** Conteúdo do PDF. */
  readonly source = input<ArrayBuffer | null>(null);
  /** Página atual, a partir de 1. */
  readonly page = model(1);
  /** 1 = 100%. Limitado de 25% a 400%. */
  readonly zoom = model(1);
  /** Rotação salva da página atual, somada à do próprio PDF. */
  readonly rotation = input(0);
  /** Com a mão ativa, o botão esquerdo e um dedo arrastam a folha. O botão do meio sempre arrasta. */
  readonly panMode = input(false);
  /** Esmaecido do desenho, de 0 a 0,9. Não afeta o conteúdo sobreposto. */
  readonly dim = input(0);
  readonly ariaLabel = input('Visualizador de PDF');

  readonly pageCount = output<number>();
  readonly loadError = output<unknown>();
  /** A página atual terminou de desenhar. */
  readonly rendered = output<void>();

  private readonly area = viewChild.required<ElementRef<HTMLElement>>('area');
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  private readonly documento = signal<PDFDocumentProxy | null>(null);
  private readonly tamanhoBase = signal<{ largura: number; altura: number } | null>(null);
  protected readonly arrastando = signal(false);

  protected readonly larguraTela = computed(() => (this.tamanhoBase()?.largura ?? 0) * this.zoom());
  protected readonly alturaTela = computed(() => (this.tamanhoBase()?.altura ?? 0) * this.zoom());

  private renderAtual: { cancel(): void } | null = null;
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private ajustarAoAbrir = true;
  private destruido = false;

  private arraste: { x: number; y: number; scrollLeft: number; scrollTop: number } | null = null;
  private readonly toques = new Map<number, { x: number; y: number }>();
  private pinca: { distancia: number; zoom: number } | null = null;

  constructor() {
    effect(() => {
      const dados = this.source();
      untracked(() => void this.abrir(dados));
    });

    effect(() => {
      const documento = this.documento();
      const pagina = this.page();
      const rotacao = this.rotation();
      if (documento) untracked(() => void this.medir(documento, pagina, rotacao));
    });

    effect(() => {
      this.zoom();
      if (this.documento()) untracked(() => this.agendarRender());
    });
  }

  ngOnDestroy(): void {
    this.destruido = true;
    if (this.temporizador) clearTimeout(this.temporizador);
    this.renderAtual?.cancel();
    void this.documento()?.destroy();
  }

  zoomIn(): void {
    this.aplicarZoom(this.zoom() * PASSO_ZOOM);
  }

  zoomOut(): void {
    this.aplicarZoom(this.zoom() / PASSO_ZOOM);
  }

  fitWidth(): void {
    const base = this.tamanhoBase();
    if (!base) return;
    const estilo = getComputedStyle(this.area().nativeElement);
    const folga = parseFloat(estilo.paddingLeft) + parseFloat(estilo.paddingRight);
    this.zoom.set(limitarZoom((this.area().nativeElement.clientWidth - folga) / base.largura));
  }

  /** Mostra uma posição da tela da folha, em [0,1], no centro da área visível. */
  centralizar(x: number, y: number): void {
    const area = this.area().nativeElement;
    area.scrollTo({
      left: x * this.larguraTela() - area.clientWidth / 2,
      top: y * this.alturaTela() - area.clientHeight / 2,
      behavior: 'smooth'
    });
  }

  /** Texto do PDF numa região em [0,1] da página sem a rotação salva. Vazio quando não há camada de texto. */
  async extractText(regiao: CesPdfRegiao): Promise<string> {
    const documento = this.documento();
    if (!documento) return '';
    const pagina = await documento.getPage(this.page());
    const viewport = pagina.getViewport({ scale: 1 });
    const conteudo = await pagina.getTextContent();
    const trechos: CesPdfTrechoTexto[] = [];
    for (const item of conteudo.items) {
      if (!('str' in item) || !item.str.trim()) continue;
      const [px, py] = viewport.convertToViewportPoint(item.transform[4], item.transform[5]);
      const x = px / viewport.width;
      const y = py / viewport.height;
      if (x >= regiao.x0 && x <= regiao.x1 && y >= regiao.y0 && y <= regiao.y1) {
        trechos.push({ texto: item.str, x, y, altura: (item.height || 10) / viewport.height });
      }
    }
    return cesPdfOrdenarTexto(trechos);
  }

  protected aoRolar(evento: WheelEvent): void {
    if (!evento.ctrlKey && !evento.metaKey) return;
    evento.preventDefault();
    this.aplicarZoom(this.zoom() * (evento.deltaY < 0 ? PASSO_ZOOM : 1 / PASSO_ZOOM), evento);
  }

  protected aoApertar(evento: PointerEvent): void {
    if (evento.pointerType === 'touch') {
      this.toques.set(evento.pointerId, { x: evento.clientX, y: evento.clientY });
      if (this.toques.size === 2) {
        this.arraste = null;
        this.pinca = { distancia: this.distanciaToques(), zoom: this.zoom() };
        return;
      }
    }
    const botaoDoMeio = evento.button === 1;
    if (!botaoDoMeio && !(this.panMode() && evento.button === 0)) return;
    evento.preventDefault();
    const area = this.area().nativeElement;
    area.setPointerCapture?.(evento.pointerId);
    this.arraste = { x: evento.clientX, y: evento.clientY, scrollLeft: area.scrollLeft, scrollTop: area.scrollTop };
    this.arrastando.set(true);
  }

  protected aoMover(evento: PointerEvent): void {
    if (this.toques.has(evento.pointerId)) {
      this.toques.set(evento.pointerId, { x: evento.clientX, y: evento.clientY });
      if (this.pinca && this.toques.size === 2) {
        this.aplicarZoom((this.pinca.zoom * this.distanciaToques()) / this.pinca.distancia);
        return;
      }
    }
    if (!this.arraste) return;
    const area = this.area().nativeElement;
    area.scrollLeft = this.arraste.scrollLeft - (evento.clientX - this.arraste.x);
    area.scrollTop = this.arraste.scrollTop - (evento.clientY - this.arraste.y);
  }

  protected aoSoltar(evento: PointerEvent): void {
    this.toques.delete(evento.pointerId);
    if (this.toques.size < 2) this.pinca = null;
    this.arraste = null;
    this.arrastando.set(false);
  }

  /** Muda o zoom mantendo sob o cursor o mesmo ponto do desenho. */
  private aplicarZoom(novo: number, ancora?: { clientX: number; clientY: number }): void {
    const area = this.area().nativeElement;
    const atual = this.zoom();
    const zoom = limitarZoom(novo);
    if (zoom === atual || zoom < CES_PDF_ZOOM_MINIMO || zoom > CES_PDF_ZOOM_MAXIMO) return;
    const retangulo = area.getBoundingClientRect();
    const cursorX = ancora ? ancora.clientX - retangulo.left : area.clientWidth / 2;
    const cursorY = ancora ? ancora.clientY - retangulo.top : area.clientHeight / 2;
    const folha = area.firstElementChild as HTMLElement;
    const inicioX = folha.offsetLeft;
    const inicioY = folha.offsetTop;
    const noDesenhoX = (area.scrollLeft + cursorX - inicioX) / atual;
    const noDesenhoY = (area.scrollTop + cursorY - inicioY) / atual;
    this.zoom.set(zoom);
    queueMicrotask(() => {
      // A largura da folha muda a margem automática: a posição é lida de novo depois de aplicar o zoom.
      folha.style.width = `${(this.tamanhoBase()?.largura ?? 0) * zoom}px`;
      folha.style.height = `${(this.tamanhoBase()?.altura ?? 0) * zoom}px`;
      area.scrollLeft = noDesenhoX * zoom + folha.offsetLeft - cursorX;
      area.scrollTop = noDesenhoY * zoom + folha.offsetTop - cursorY;
    });
  }

  private distanciaToques(): number {
    const [a, b] = Array.from(this.toques.values());
    return Math.hypot(a.x - b.x, a.y - b.y) || 1;
  }

  private async abrir(dados: ArrayBuffer | null): Promise<void> {
    this.renderAtual?.cancel();
    const anterior = this.documento();
    this.documento.set(null);
    this.tamanhoBase.set(null);
    void anterior?.destroy();
    if (!dados) return;
    try {
      const documento = await this.loader.abrir(dados);
      if (this.destruido) {
        void documento.destroy();
        return;
      }
      this.ajustarAoAbrir = true;
      this.documento.set(documento);
      this.pageCount.emit(documento.numPages);
      if (this.page() > documento.numPages) this.page.set(1);
    } catch (erro) {
      this.loadError.emit(erro);
    }
  }

  private async medir(documento: PDFDocumentProxy, numero: number, rotacao: number): Promise<void> {
    const pagina = await documento.getPage(Math.min(numero, documento.numPages));
    const viewport = pagina.getViewport({ scale: 1, rotation: normalizarRotacao(pagina.rotate + rotacao) });
    this.tamanhoBase.set({ largura: viewport.width, altura: viewport.height });
    if (this.ajustarAoAbrir) {
      this.ajustarAoAbrir = false;
      this.fitWidth();
    }
    this.agendarRender(0);
  }

  private agendarRender(atraso = ATRASO_RENDER_MS): void {
    if (this.temporizador) clearTimeout(this.temporizador);
    this.temporizador = setTimeout(() => void this.renderizar(), atraso);
  }

  private async renderizar(): Promise<void> {
    const documento = this.documento();
    if (!documento || this.destruido) return;
    this.renderAtual?.cancel();
    const pagina = await documento.getPage(Math.min(this.page(), documento.numPages));
    const rotacao = normalizarRotacao(pagina.rotate + this.rotation());
    const base = pagina.getViewport({ scale: 1, rotation: rotacao });
    // Nitidez no zoom alto sem estourar a memória do navegador em folhas muito grandes.
    const desejada = this.zoom() * (window.devicePixelRatio || 1);
    const limite = Math.sqrt(MAXIMO_PIXELS_CANVAS / (base.width * base.height));
    const viewport = pagina.getViewport({ scale: Math.min(desejada, limite), rotation: rotacao });

    const canvas = this.canvas().nativeElement;
    const contexto = canvas.getContext('2d');
    if (!contexto) return;
    canvas.width = Math.floor(viewport.width);
    canvas.height = Math.floor(viewport.height);
    const tarefa = pagina.render({ canvasContext: contexto, viewport });
    this.renderAtual = tarefa;
    try {
      await tarefa.promise;
      this.rendered.emit();
    } catch (erro) {
      if ((erro as { name?: string })?.name !== 'RenderingCancelledException') this.loadError.emit(erro);
    }
  }
}
