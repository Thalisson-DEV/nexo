import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, input } from '@angular/core';
import { marked } from 'marked';
import DOMPurify from 'dompurify';

const renderizador = new marked.Renderer();

renderizador.checkbox = ({ checked }: { checked?: boolean }) => `
  <span
    class="task-checkbox${checked ? ' task-checkbox--checked' : ''}"
    role="checkbox"
    aria-checked="${checked ? 'true' : 'false'}"
    aria-disabled="true"
  ></span>`;

/** Tabelas ganham um contêiner rolável para não estourar a largura da conversa em telas estreitas. */
const tabelaPadrao = renderizador.table.bind(renderizador);
renderizador.table = (token) => `<div class="markdown-table" role="region" tabindex="0" aria-label="Tabela">${tabelaPadrao(token)}</div>`;

const PURIFY = {
  ADD_ATTR: ['target', 'rel', 'aria-label', 'aria-checked', 'aria-disabled', 'role', 'tabindex'],
  FORBID_TAGS: ['style', 'form', 'input', 'button', 'iframe'],
};

/**
 * Markdown das respostas do Copiloto (GFM via marked + DOMPurify). Links externos abrem em nova aba com
 * `rel="noopener noreferrer"`; citações de fontes externas ([1], [2]) viram marcadores sobrescritos.
 */
@Component({
  selector: 'app-copiloto-markdown',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="copiloto-markdown markdown-body" [innerHTML]="html()"></div>`,
  styleUrl: './copiloto-markdown.component.css',
  encapsulation: ViewEncapsulation.None,
})
export class CopilotoMarkdownComponent {
  readonly content = input<string | null | undefined>('');
  /** Fontes conhecidas: citações [n] com número existente viram links para a fonte. */
  readonly fontes = input<readonly { numero: number; url: string; titulo: string }[]>([]);

  readonly html = computed(() => {
    const bruto = this.content() || '';
    const html = marked.parse(bruto, { async: false, breaks: false, gfm: true, renderer: renderizador }) as string;
    const limpo = DOMPurify.sanitize(html, PURIFY) as string;
    return this.pos(limpo);
  });

  private pos(html: string): string {
    const container = document.createElement('div');
    container.innerHTML = html;
    container.querySelectorAll('a[href]').forEach((link) => {
      const href = link.getAttribute('href') ?? '';
      if (/^https?:\/\//i.test(href)) {
        link.setAttribute('target', '_blank');
        link.setAttribute('rel', 'noopener noreferrer');
      }
    });
    const fontes = new Map(this.fontes().map((fonte) => [fonte.numero, fonte]));
    const caminhante = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
    const nos: Text[] = [];
    while (caminhante.nextNode()) {
      const no = caminhante.currentNode as Text;
      if (/\[\d{1,3}\]/.test(no.data) && !no.parentElement?.closest('code, pre, a')) nos.push(no);
    }
    for (const no of nos) {
      const fragmento = document.createDocumentFragment();
      let ultimo = 0;
      no.data.replace(/\[(\d{1,3})\]/g, (trecho, numero: string, posicao: number) => {
        fragmento.append(no.data.slice(ultimo, posicao));
        const fonte = fontes.get(Number(numero));
        if (fonte && /^https?:\/\//i.test(fonte.url)) {
          const link = document.createElement('a');
          link.href = fonte.url;
          link.target = '_blank';
          link.rel = 'noopener noreferrer';
          link.className = 'markdown-citacao';
          link.setAttribute('aria-label', `Fonte ${numero}: ${fonte.titulo}`);
          link.textContent = numero;
          const sup = document.createElement('sup');
          sup.append(link);
          fragmento.append(sup);
        } else {
          fragmento.append(trecho);
        }
        ultimo = posicao + trecho.length;
        return trecho;
      });
      fragmento.append(no.data.slice(ultimo));
      no.replaceWith(fragmento);
    }
    return container.innerHTML;
  }
}
