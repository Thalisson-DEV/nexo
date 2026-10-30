import {
  AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, computed, effect, input, output, signal, viewChild
} from '@angular/core';
import { CesIconButtonComponent } from '@ces/ui';

import { descreverExpressao, segmentos } from '../lib/dinamico';
import { ContextoDinamico, SeletorDinamicoComponent } from './seletor-dinamico.component';

function escapar(t: string): string {
  return t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function htmlFicha(expressao: string, rotulo: string): string {
  return `<span class="aut-token" contenteditable="false" data-expr="${escapar(expressao)}" title="${escapar(rotulo)}">${escapar(rotulo)}</span>`;
}

/** Converte o editor de volta em texto com {{ expressões }}. */
function serializar(raiz: Node): string {
  let saida = '';
  raiz.childNodes.forEach((filho, i) => {
    if (filho.nodeType === Node.TEXT_NODE) {
      saida += (filho.textContent ?? '').replace(/ /g, ' ');
      return;
    }
    if (!(filho instanceof HTMLElement)) return;
    if (filho.dataset['expr'] !== undefined) {
      saida += `{{ ${filho.dataset['expr']} }}`;
    } else if (filho.tagName === 'BR') {
      if (i < raiz.childNodes.length - 1) saida += '\n';
    } else {
      const bloco = filho.tagName === 'DIV' || filho.tagName === 'P';
      if (bloco && saida && !saida.endsWith('\n')) saida += '\n';
      saida += serializar(filho);
    }
  });
  return saida;
}

/**
 * Texto com dados do fluxo: cada {{ expressão }} aparece como uma ficha com nome amigável
 * ("Colaborador › Base operacional"). O botão "Inserir dado" insere uma ficha onde está o cursor.
 */
@Component({
  selector: 'aut-campo-expressao',
  standalone: true,
  imports: [CesIconButtonComponent, SeletorDinamicoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="aut-tokenfield" [attr.data-invalid]="invalido() || null" [attr.data-multiline]="multilinha() || null">
      <div #editor class="aut-tokenfield-input" contenteditable="true" role="textbox" tabindex="0"
           [attr.aria-label]="rotulo()" [attr.aria-multiline]="multilinha() || null" [attr.data-placeholder]="placeholder() ?? ''"
           (input)="emitir()" (keyup)="lembrar()" (mouseup)="lembrar()" (blur)="lembrar()"
           (keydown.enter)="enter($event)" (paste)="colar($event)"></div>
      @if (mostrarSeletor()) {
        <ces-icon-button icon="pi pi-bolt" label="Inserir dado de um passo anterior" variant="ghost" size="icon-sm" data-dyn-trigger (mousedown)="lembrar()" (pressed)="aberto.set(!aberto())"></ces-icon-button>
      }
      @if (aberto()) {
        <aut-seletor-dinamico [contexto]="contexto()" (escolher)="inserir($event)" (fechar)="aberto.set(false)" />
      }
    </div>
  `
})
export class CampoExpressaoComponent implements AfterViewInit {
  readonly valor = input<string>('');
  readonly contexto = input.required<ContextoDinamico>();
  readonly rotulo = input<string>('');
  readonly placeholder = input<string | null>(null);
  readonly multilinha = input(false);
  readonly dinamico = input(true);
  readonly invalido = input(false);
  readonly mudou = output<string>();

  private readonly editor = viewChild.required<ElementRef<HTMLDivElement>>('editor');
  protected readonly aberto = signal(false);
  private intervalo: Range | null = null;
  private ultimoEmitido: string | null = null;

  protected readonly mostrarSeletor = computed(() => this.dinamico() && this.contexto().noId !== 'trigger');

  private readonly html = computed(() => {
    const c = this.contexto();
    const v = this.valor() ?? '';
    return segmentos(v)
      .map((s) => (s.expressao !== undefined
        ? htmlFicha(s.expressao, descreverExpressao(s.expressao, c.definicao, c.catalogo))
        : escapar(s.texto).replace(/\n/g, '<br>')))
      .join('')
      .concat(v.endsWith('\n') ? '<br>' : '');
  });

  constructor() {
    effect(() => {
      const html = this.html();
      const valor = this.valor() ?? '';
      const el = this.editor()?.nativeElement;
      if (!el) return;
      const focado = document.activeElement === el;
      if (valor === this.ultimoEmitido && focado) return;
      if (serializar(el) !== valor || (!focado && el.innerHTML !== html)) el.innerHTML = html;
    });
  }

  ngAfterViewInit(): void {
    this.editor().nativeElement.innerHTML = this.html();
  }

  protected lembrar(): void {
    const sel = window.getSelection();
    const el = this.editor().nativeElement;
    if (sel && sel.rangeCount && el.contains(sel.anchorNode)) this.intervalo = sel.getRangeAt(0).cloneRange();
  }

  protected emitir(): void {
    const v = serializar(this.editor().nativeElement);
    this.ultimoEmitido = v;
    this.mudou.emit(v);
  }

  protected enter(e: Event): void {
    e.preventDefault();
    if (this.multilinha()) {
      document.execCommand('insertLineBreak');
      this.emitir();
    }
  }

  protected colar(e: ClipboardEvent): void {
    e.preventDefault();
    const t = e.clipboardData?.getData('text/plain') ?? '';
    document.execCommand('insertText', false, this.multilinha() ? t : t.replace(/\s*\n\s*/g, ' '));
  }

  protected inserir(expressao: string): void {
    const el = this.editor().nativeElement;
    this.aberto.set(false);
    const c = this.contexto();
    const caixa = document.createElement('span');
    caixa.innerHTML = htmlFicha(expressao, descreverExpressao(expressao, c.definicao, c.catalogo));
    const ficha = caixa.firstChild as HTMLElement;
    const espaco = document.createTextNode(' ');
    const r = this.intervalo && el.contains(this.intervalo.startContainer) ? this.intervalo : null;
    if (r) {
      r.deleteContents();
      r.insertNode(espaco);
      r.insertNode(ficha);
    } else {
      el.append(ficha, espaco);
    }
    const sel = window.getSelection();
    const depois = document.createRange();
    depois.setStartAfter(espaco);
    depois.collapse(true);
    sel?.removeAllRanges();
    sel?.addRange(depois);
    this.intervalo = depois.cloneRange();
    this.emitir();
  }
}
