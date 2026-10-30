import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, model, output, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';
import {
  CesButtonComponent, CesDialogShellComponent, CesIconButtonComponent, CesSelectComponent, CesSelectOption, CesTextInputComponent
} from '@ces/ui';

import { JsonValue, Params } from '../../../../core/models/automacao.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { AutomacoesService } from '../../../../core/services/automacoes.service';
import { texto } from '../lib/arvore';
import { descreverExpressao, segmentos } from '../lib/dinamico';
import { CampoExpressaoComponent } from './campo-expressao.component';
import { ContextoDinamico, SeletorDinamicoComponent } from './seletor-dinamico.component';

const PERMITIDO = {
  ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 'h2', 'h3', 'ul', 'ol', 'li', 'a', 'span', 'div', 'img'],
  ALLOWED_ATTR: ['href', 'src', 'alt', 'width', 'style', 'data-expr', 'data-block', 'data-source', 'data-label', 'contenteditable', 'class', 'title']
};

/** Colado de fora (Word, site, outro e-mail): só a estrutura, sem estilos nem classes. */
const COLAGEM = {
  ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 'h2', 'h3', 'ul', 'ol', 'li', 'a'],
  ALLOWED_ATTR: ['href']
};

/** Cores do texto: as da identidade da empresa e os tons de destaque do e-mail institucional. */
const CORES: { nome: string; valor: string }[] = [
  { nome: 'Azul da empresa', valor: '#1E3A8A' },
  { nome: 'Texto', valor: '#0f172a' },
  { nome: 'Cinza', valor: '#64748b' },
  { nome: 'Verde', valor: '#065f46' },
  { nome: 'Âmbar', valor: '#92400e' },
  { nome: 'Vermelho', valor: '#991b1b' }
];

const DESTAQUES: CesSelectOption<string>[] = [
  { label: 'Neutro', value: 'NEUTRO' },
  { label: 'Informativo', value: 'INFORMATIVO' },
  { label: 'Atenção', value: 'ATENCAO' },
  { label: 'Crítico', value: 'CRITICO' },
  { label: 'Positivo', value: 'POSITIVO' }
];

/** "Início › Semana passada: segunda (dd/mm/aaaa)" vira "Semana passada: segunda" no texto do e-mail. */
function nomeCurto(nome: string): string {
  const partes = nome.split('›').map((p) => p.trim()).filter(Boolean);
  const final = partes.length > 1 && partes[0] === 'Início' ? partes.slice(1).join(' › ') : partes.join(' › ');
  return final.replace(/\s*\([^)]*\)\s*$/, '').trim() || nome;
}

type Pedido = { tipo: 'link'; texto: string; url: string } | { tipo: 'imagem'; url: string; descricao: string } | { tipo: 'botao'; fonte: string; texto: string };

/** O que o editor altera no passo "Enviar e-mail". */
export type CamposEmail = Params;

/**
 * Editor do e-mail no padrão do TON: escreve-se dentro do cartão institucional da empresa
 * (logo, área, título, corpo, botão e rodapé) e, ao lado, a prévia montada pelo servidor
 * com o mesmo layout do envio. Dados do fluxo viram fichas; blocos (tabela, lista,
 * análise do Copiloto, botão) são montados na hora do envio, com os dados reais.
 */
@Component({
  selector: 'aut-editor-email',
  standalone: true,
  imports: [CesButtonComponent, CesIconButtonComponent, CesDialogShellComponent, CesSelectComponent, CesTextInputComponent,
    CampoExpressaoComponent, SeletorDinamicoComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-dialog-shell [(visible)]="aberto" title="E-mail" size="full"
                      description="Escreva no e-mail da empresa; ao lado está a prévia de como ele chega, com o layout real do envio.">
      <div class="aut aut-mail">
        <section class="aut-mail-compose" aria-label="Escrever o e-mail">
          <div class="aut-mail-fields">
            <div class="aut-mail-field-wide">
              <span class="ces-field__label">Assunto <span class="ces-field__required" aria-hidden="true">*</span></span>
              <aut-campo-expressao [valor]="assunto()" [contexto]="contexto()" rotulo="Assunto" placeholder="Ex.: Desconto validado aguardando baixa"
                                   (mudou)="assunto.set($event); mexeu()" />
            </div>
            <div>
              <span class="ces-field__label">Área (linha acima do título)</span>
              <aut-campo-expressao [valor]="area()" [contexto]="contexto()" rotulo="Área" placeholder="Ex.: Recursos Humanos"
                                   (mudou)="area.set($event); mexeu()" />
            </div>
            <ces-select label="Destaque" [options]="destaques" [value]="tom()" (valueChange)="tom.set($event ?? 'NEUTRO'); mexeu()"></ces-select>
            <ces-text-input label="Texto do botão" placeholder="Abrir no Nexo" [value]="botaoTexto()" (valueChange)="botaoTexto.set($event); mexeu()"></ces-text-input>
            <div>
              <span class="ces-field__label">Link do botão</span>
              <aut-campo-expressao [valor]="botaoLink()" [contexto]="contexto()" rotulo="Link do botão" placeholder="Ex.: /dp/descontos (vazio: sem botão)"
                                   (mudou)="botaoLink.set($event); mexeu()" />
            </div>
          </div>

          <div class="aut-email-toolbar" role="toolbar" aria-label="Formatação">
            <button type="button" class="aut-mail-tool" title="Negrito" aria-label="Negrito" (mousedown)="$event.preventDefault()" (click)="cmd('bold')"><strong>N</strong></button>
            <button type="button" class="aut-mail-tool" title="Itálico" aria-label="Itálico" (mousedown)="$event.preventDefault()" (click)="cmd('italic')"><em>I</em></button>
            <button type="button" class="aut-mail-tool" title="Sublinhado" aria-label="Sublinhado" (mousedown)="$event.preventDefault()" (click)="cmd('underline')"><u>S</u></button>
            <span class="aut-topbar-sep" aria-hidden="true"></span>
            <ces-button label="Título" variant="ghost" size="sm" (mousedown)="$event.preventDefault()" (pressed)="cmd('formatBlock', 'h2')"></ces-button>
            <ces-button label="Subtítulo" variant="ghost" size="sm" (mousedown)="$event.preventDefault()" (pressed)="cmd('formatBlock', 'h3')"></ces-button>
            <ces-button label="Texto" variant="ghost" size="sm" (mousedown)="$event.preventDefault()" (pressed)="cmd('formatBlock', 'p')"></ces-button>
            <span class="aut-topbar-sep" aria-hidden="true"></span>
            <ces-icon-button icon="pi pi-list" label="Lista com marcadores" variant="ghost" size="icon-sm" (mousedown)="$event.preventDefault()" (pressed)="cmd('insertUnorderedList')"></ces-icon-button>
            <ces-icon-button icon="pi pi-sort-numeric-down" label="Lista numerada" variant="ghost" size="icon-sm" (mousedown)="$event.preventDefault()" (pressed)="cmd('insertOrderedList')"></ces-icon-button>
            <ces-icon-button icon="pi pi-align-left" label="Alinhar à esquerda" variant="ghost" size="icon-sm" (mousedown)="$event.preventDefault()" (pressed)="cmd('justifyLeft')"></ces-icon-button>
            <ces-icon-button icon="pi pi-align-center" label="Centralizar" variant="ghost" size="icon-sm" (mousedown)="$event.preventDefault()" (pressed)="cmd('justifyCenter')"></ces-icon-button>
            <ces-icon-button icon="pi pi-align-right" label="Alinhar à direita" variant="ghost" size="icon-sm" (mousedown)="$event.preventDefault()" (pressed)="cmd('justifyRight')"></ces-icon-button>
            <span class="aut-topbar-sep" aria-hidden="true"></span>
            <span class="aut-mail-colors" role="group" aria-label="Cor do texto">
              @for (c of cores; track c.valor) {
                <button type="button" class="aut-mail-swatch" [style.background-color]="c.valor" [attr.aria-label]="'Cor do texto: ' + c.nome" [title]="c.nome"
                        (mousedown)="$event.preventDefault()" (click)="cmd('foreColor', c.valor)"></button>
              }
            </span>
            <span class="aut-topbar-sep" aria-hidden="true"></span>
            <ces-icon-button icon="pi pi-link" label="Link" variant="ghost" size="icon-sm" (mousedown)="lembrar()" (pressed)="pedirLink()"></ces-icon-button>
            <ces-icon-button icon="pi pi-image" label="Imagem" variant="ghost" size="icon-sm" (mousedown)="lembrar()" (pressed)="pedirImagem()"></ces-icon-button>
            <ces-icon-button icon="pi pi-eraser" label="Limpar formatação" variant="ghost" size="icon-sm" (mousedown)="$event.preventDefault()" (pressed)="limparFormatacao()"></ces-icon-button>
            <span style="flex:1"></span>
            <span style="position:relative">
              <ces-button icon="pi pi-th-large" label="Bloco" variant="secondary" size="sm" data-dyn-trigger (mousedown)="lembrar()" (pressed)="alternarMenuBlocos()"></ces-button>
              @if (menuBlocos()) {
                <div class="aut-dyn aut-mail-blocks" role="menu">
                  @for (b of blocos(); track b[0]) {
                    <button type="button" class="aut-dyn-item" role="menuitem" (click)="escolherBloco(b[0])">
                      <span style="display:flex;flex-direction:column;text-align:start"><strong style="font-weight:600">{{ b[1] }}</strong><small class="aut-muted">{{ ajudaBloco(b[0]) }}</small></span>
                    </button>
                  }
                </div>
              }
              @if (seletor() && seletor() !== 'dado') {
                <aut-seletor-dinamico [contexto]="contexto()" (escolher)="escolheuFonte(seletor()!, $event)" (fechar)="seletor.set(null)" />
              }
            </span>
            <span style="position:relative">
              <ces-button icon="pi pi-bolt" label="Inserir dado" variant="secondary" size="sm" data-dyn-trigger (mousedown)="lembrar()" (pressed)="seletor.set(seletor() === 'dado' ? null : 'dado')"></ces-button>
              @if (seletor() === 'dado') {
                <aut-seletor-dinamico [contexto]="contexto()" (escolher)="inserirDado($event)" (fechar)="seletor.set(null)" />
              }
            </span>
          </div>

          @if (pedido(); as p) {
            <form class="aut-email-ask" (submit)="$event.preventDefault(); confirmarPedido()">
              @switch (p.tipo) {
                @case ('link') {
                  <ces-text-input label="Endereço do link" placeholder="https://…" [value]="p.url" (valueChange)="pedido.set({ tipo: 'link', texto: p.texto, url: $event })"
                                  [error]="erroEndereco(p.url) ? 'Use um endereço que comece com https://' : undefined"></ces-text-input>
                  <ces-text-input label="Texto do link" description="Em branco: usa o texto selecionado ou o próprio endereço." [value]="p.texto"
                                  (valueChange)="pedido.set({ tipo: 'link', texto: $event, url: p.url })"></ces-text-input>
                }
                @case ('imagem') {
                  <ces-text-input label="Endereço da imagem" placeholder="https://…/imagem.png" [value]="p.url" (valueChange)="pedido.set({ tipo: 'imagem', url: $event, descricao: p.descricao })"
                                  [error]="erroEndereco(p.url) ? 'Use um endereço público que comece com https://' : undefined"></ces-text-input>
                  <ces-text-input label="Descrição (para quem não carrega imagens)" [value]="p.descricao"
                                  (valueChange)="pedido.set({ tipo: 'imagem', url: p.url, descricao: $event })"></ces-text-input>
                }
                @case ('botao') {
                  <ces-text-input label="Texto do botão" [value]="p.texto" (valueChange)="pedido.set({ tipo: 'botao', fonte: p.fonte, texto: $event })"></ces-text-input>
                }
              }
              <div class="aut-email-ask-actions">
                <ces-button label="Cancelar" variant="ghost" size="sm" (pressed)="pedido.set(null)"></ces-button>
                <ces-button label="Inserir" variant="primary" size="sm" type="submit" [disabled]="!pedidoValido()"></ces-button>
              </div>
            </form>
          }

          <div class="aut-mail-paper">
            <div class="aut-mail-head">
              <img class="aut-mail-logo-claro" src="assets/logo-email.png" alt="Nexo" width="46" height="36" />
              <img class="aut-mail-logo-escuro" src="assets/logo-email-escuro.png" alt="" width="46" height="36" />
              <span><strong>Nexo</strong><small>Gestão Operacional</small></span>
            </div>
            <div class="aut-mail-content">
              @if (areaLegivel()) { <div class="aut-mail-area" [attr.data-tone]="tom()">{{ areaLegivel() }}</div> }
              <h1 class="aut-mail-title" [class.aut-muted]="!assuntoLegivel()">{{ assuntoLegivel() || 'O assunto aparece aqui como título' }}</h1>
              <div #editor class="aut-email-editor aut-mail-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="Mensagem"
                   data-placeholder="Escreva a mensagem. Use Inserir dado para incluir informações do fluxo e Bloco para tabelas, listas, a análise do Copiloto ou um botão."
                   (keyup)="lembrar()" (mouseup)="lembrar()" (input)="mexeu()" (paste)="colar($event)" (click)="clicou($event)"></div>
              @if (botaoLink().trim()) {
                <span class="aut-mail-button">{{ botaoTexto().trim() || 'Abrir no Nexo' }}</span>
              }
            </div>
            <div class="aut-mail-foot">Mensagem automática do sistema Nexo. Não responda a este e-mail.</div>
          </div>
        </section>

        <aside class="aut-mail-preview" aria-label="Prévia do e-mail">
          <div class="aut-mail-preview-bar">
            <strong>Prévia do envio</strong>
            <span style="flex:1"></span>
            <ces-button icon="pi pi-desktop" label="Computador" size="sm" [variant]="largura() === 'desktop' ? 'secondary' : 'ghost'" (pressed)="largura.set('desktop')"></ces-button>
            <ces-button icon="pi pi-mobile" label="Celular" size="sm" [variant]="largura() === 'mobile' ? 'secondary' : 'ghost'" (pressed)="largura.set('mobile')"></ces-button>
            <ces-icon-button icon="pi pi-refresh" label="Atualizar prévia" variant="ghost" size="icon-sm" [loading]="carregandoPrevia()" (pressed)="atualizarPrevia()"></ces-icon-button>
          </div>
          <div class="aut-mail-subject"><span class="aut-muted">Assunto:</span> {{ previa()?.assunto || '—' }}</div>
          @if (erroPrevia()) { <p class="aut-muted" role="alert" style="margin:0">{{ erroPrevia() }}</p> }
          <div class="aut-mail-frame-wrap" [attr.data-width]="largura()">
            @if (htmlPrevia(); as h) {
              <iframe [srcdoc]="h" title="Prévia do e-mail" sandbox="allow-same-origin" class="aut-mail-frame"></iframe>
            }
          </div>
          <p class="aut-muted" style="margin:0">Os dados do fluxo aparecem marcados entre colchetes e os blocos, com um exemplo. No envio, entram os valores reais.</p>
        </aside>
      </div>
      <div dialog-footer>
        <ces-button label="Cancelar" variant="ghost" size="sm" (pressed)="aberto.set(false)"></ces-button>
        <ces-button label="Usar este e-mail" variant="primary" size="sm" (pressed)="salvar()"></ces-button>
      </div>
    </ces-dialog-shell>
  `
})
export class EditorEmailComponent {
  readonly aberto = model(false);
  readonly params = input.required<Params>();
  readonly contexto = input.required<ContextoDinamico>();
  readonly mudou = output<CamposEmail>();

  private readonly service = inject(AutomacoesService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly editor = viewChild<ElementRef<HTMLDivElement>>('editor');
  protected readonly cores = CORES;
  protected readonly destaques = DESTAQUES;

  protected readonly assunto = signal('');
  protected readonly area = signal('');
  protected readonly tom = signal('NEUTRO');
  protected readonly botaoTexto = signal('');
  protected readonly botaoLink = signal('');
  protected readonly seletor = signal<string | null>(null);
  protected readonly menuBlocos = signal(false);
  protected readonly pedido = signal<Pedido | null>(null);
  protected readonly largura = signal<'desktop' | 'mobile'>('desktop');
  protected readonly previa = signal<{ assunto: string; html: string } | null>(null);
  protected readonly carregandoPrevia = signal(false);
  protected readonly erroPrevia = signal<string | null>(null);
  private intervalo: Range | null = null;
  private readonly pedidos$ = new Subject<void>();

  protected readonly blocos = computed(() => Object.entries(this.contexto().catalogo.blocos));
  protected readonly assuntoLegivel = computed(() => this.legivel(this.assunto()));
  protected readonly areaLegivel = computed(() => this.legivel(this.area()));
  protected readonly htmlPrevia = computed<SafeHtml | null>(() => {
    const p = this.previa();
    // HTML montado pelo nosso servidor, exibido num iframe sem scripts.
    return p ? this.sanitizer.bypassSecurityTrustHtml(p.html) : null;
  });
  protected readonly pedidoValido = computed(() => {
    const p = this.pedido();
    if (!p) return false;
    if (p.tipo === 'botao') return !!p.texto.trim();
    return this.enderecoValido(p.url);
  });

  constructor() {
    effect(() => {
      if (!this.aberto()) return;
      const el = this.editor()?.nativeElement;
      if (!el) return;
      const p = this.params();
      this.assunto.set(texto(p['subject']));
      this.area.set(texto(p['label']));
      this.tom.set(texto(p['tone']) || 'NEUTRO');
      this.botaoTexto.set(texto(p['button_label']));
      this.botaoLink.set(texto(p['button_link']));
      el.innerHTML = this.paraEdicao(texto(p['body']));
      this.pedido.set(null);
      this.seletor.set(null);
      this.atualizarPrevia();
    });
    this.pedidos$.pipe(
      debounceTime(600),
      switchMap(() => {
        this.carregandoPrevia.set(true);
        return this.service.previaEmail(this.camposAtuais(), this.rotulos()).pipe(catchError((e) => {
          this.erroPrevia.set(getErrorMessage(e));
          return of(null);
        }));
      }),
      takeUntilDestroyed(inject(DestroyRef))
    ).subscribe((p) => {
      this.carregandoPrevia.set(false);
      if (p) {
        this.erroPrevia.set(null);
        this.previa.set(p);
      }
    });
  }

  protected ajudaBloco(tipo: string): string {
    switch (tipo) {
      case 'markdown': return 'Texto já formatado, ex.: a análise do Copiloto';
      case 'table': return 'Uma tabela com os itens de uma lista';
      case 'list': return 'Os itens de uma lista, um por linha';
      case 'html': return 'Uma tabela pronta de "Criar tabela"';
      default: return 'Um botão que abre um link';
    }
  }

  /** Só reclama depois que a pessoa começou a digitar o endereço. */
  protected erroEndereco(url: string): boolean {
    const t = url.trim();
    return t !== '' && t !== 'https://' && t !== 'http://' && !this.enderecoValido(t);
  }

  protected enderecoValido(url: string): boolean {
    return /^https?:\/\/\S+\.\S+/.test(url.trim());
  }

  /** "Comercial {{ item }}" vira "Comercial [Para cada › Item]" para o cartão. */
  private legivel(valor: string): string {
    const c = this.contexto();
    return segmentos(valor ?? '')
      .map((s) => (s.expressao !== undefined ? `[${nomeCurto(descreverExpressao(s.expressao, c.definicao, c.catalogo))}]` : s.texto))
      .join('')
      .trim();
  }

  /** As fichas e os blocos ganham o nome amigável para a edição. */
  private paraEdicao(html: string): string {
    const limpo = DOMPurify.sanitize(html || '<p></p>', PERMITIDO);
    const div = document.createElement('div');
    div.innerHTML = limpo;
    const c = this.contexto();
    div.querySelectorAll<HTMLElement>('span[data-expr]').forEach((s) => {
      s.className = 'aut-token';
      s.setAttribute('contenteditable', 'false');
      s.textContent = nomeCurto(descreverExpressao(s.dataset['expr'] ?? '', c.definicao, c.catalogo));
    });
    div.querySelectorAll<HTMLElement>('[data-block]').forEach((b) => this.vestirBloco(b));
    return div.innerHTML;
  }

  /** O bloco no editor: o que ele monta, de onde vêm os dados e o ícone para tirar. */
  private vestirBloco(b: HTMLElement): void {
    b.setAttribute('contenteditable', 'false');
    const tipo = b.dataset['block'] ?? '';
    b.textContent = this.rotuloBloco(tipo, b.dataset['source'] ?? '') + (tipo === 'button' ? ` · "${b.dataset['label'] || 'Abrir'}"` : '');
    const x = document.createElement('span');
    x.className = 'pi pi-times aut-email-block-remove';
    x.title = 'Tirar este bloco';
    x.setAttribute('role', 'button');
    x.setAttribute('aria-label', 'Tirar este bloco');
    b.appendChild(x);
  }

  private rotuloBloco(tipo: string, fonte: string): string {
    const c = this.contexto();
    const nome = c.catalogo.blocos[tipo] ?? tipo;
    const origem = fonte ? descreverExpressao(fonte, c.definicao, c.catalogo) : '';
    return `Bloco: ${nome}${origem ? ` · ${origem}` : ''}`;
  }

  protected lembrar(): void {
    const sel = window.getSelection();
    const el = this.editor()?.nativeElement;
    if (el && sel && sel.rangeCount && el.contains(sel.anchorNode)) this.intervalo = sel.getRangeAt(0).cloneRange();
  }

  protected mexeu(): void {
    this.pedidos$.next();
  }

  protected atualizarPrevia(): void {
    this.pedidos$.next();
  }

  protected cmd(comando: string, valor?: string): void {
    this.editor()?.nativeElement.focus();
    // Cor e alinhamento como estilo (o sanitizador do e-mail descarta <font>).
    document.execCommand('styleWithCSS', false, 'true');
    document.execCommand(comando, false, valor);
    this.mexeu();
  }

  protected limparFormatacao(): void {
    this.cmd('removeFormat');
    this.cmd('formatBlock', 'p');
  }

  protected alternarMenuBlocos(): void {
    this.seletor.set(null);
    this.menuBlocos.set(!this.menuBlocos());
  }

  protected escolherBloco(tipo: string): void {
    this.menuBlocos.set(false);
    this.seletor.set(tipo);
  }

  protected pedirLink(): void {
    this.seletor.set(null);
    this.pedido.set({ tipo: 'link', texto: this.intervalo?.toString().trim() ?? '', url: 'https://' });
  }

  protected pedirImagem(): void {
    this.seletor.set(null);
    this.pedido.set({ tipo: 'imagem', url: 'https://', descricao: '' });
  }

  protected escolheuFonte(tipo: string, fonte: string): void {
    this.seletor.set(null);
    if (tipo === 'button') {
      this.pedido.set({ tipo: 'botao', fonte, texto: 'Abrir no Nexo' });
      return;
    }
    this.inserirBloco(tipo, fonte, null);
  }

  protected confirmarPedido(): void {
    const p = this.pedido();
    if (!p || !this.pedidoValido()) return;
    this.pedido.set(null);
    if (p.tipo === 'botao') {
      this.inserirBloco('button', p.fonte, p.texto.trim());
      return;
    }
    if (p.tipo === 'imagem') {
      const img = document.createElement('img');
      img.src = p.url.trim();
      img.alt = p.descricao.trim() || 'Imagem';
      img.setAttribute('style', 'max-width:100%;height:auto;');
      this.inserirNo(img);
      return;
    }
    const a = document.createElement('a');
    a.href = p.url.trim();
    a.textContent = p.texto.trim() || p.url.trim();
    this.inserirNo(a);
  }

  protected colar(e: ClipboardEvent): void {
    e.preventDefault();
    const html = e.clipboardData?.getData('text/html');
    if (html) {
      document.execCommand('insertHTML', false, DOMPurify.sanitize(html, COLAGEM));
    } else {
      document.execCommand('insertText', false, e.clipboardData?.getData('text/plain') ?? '');
    }
    this.mexeu();
  }

  protected clicou(e: MouseEvent): void {
    const alvo = e.target as HTMLElement | null;
    if (!alvo?.classList.contains('aut-email-block-remove')) return;
    alvo.closest('[data-block]')?.remove();
    this.mexeu();
  }

  private inserirNo(no: Node): void {
    const el = this.editor()?.nativeElement;
    if (!el) return;
    const r = this.intervalo && el.contains(this.intervalo.startContainer) ? this.intervalo : null;
    if (r) {
      r.deleteContents();
      r.insertNode(no);
    } else {
      el.appendChild(no);
    }
    const sel = window.getSelection();
    const depois = document.createRange();
    depois.setStartAfter(no);
    depois.collapse(true);
    sel?.removeAllRanges();
    sel?.addRange(depois);
    this.intervalo = depois;
    this.mexeu();
  }

  protected inserirDado(expr: string): void {
    this.seletor.set(null);
    const c = this.contexto();
    const s = document.createElement('span');
    s.className = 'aut-token';
    s.setAttribute('contenteditable', 'false');
    s.dataset['expr'] = expr;
    s.textContent = nomeCurto(descreverExpressao(expr, c.definicao, c.catalogo));
    this.inserirNo(s);
    this.inserirNo(document.createTextNode(' '));
  }

  private inserirBloco(tipo: string, fonte: string, rotuloBotao: string | null): void {
    const d = document.createElement('div');
    d.dataset['block'] = tipo;
    d.dataset['source'] = fonte;
    if (rotuloBotao) d.dataset['label'] = rotuloBotao;
    this.vestirBloco(d);
    this.inserirNo(d);
    const p = document.createElement('p');
    p.innerHTML = '<br>';
    this.inserirNo(p);
  }

  /** O corpo como é guardado: fichas e blocos vazios, só com os atributos. */
  private corpoAtual(): string {
    const el = this.editor()?.nativeElement;
    if (!el) return texto(this.params()['body']);
    const copia = el.cloneNode(true) as HTMLElement;
    copia.querySelectorAll<HTMLElement>('span[data-expr]').forEach((s) => {
      s.removeAttribute('class');
      s.removeAttribute('contenteditable');
      s.textContent = '';
    });
    copia.querySelectorAll<HTMLElement>('[data-block]').forEach((b) => {
      b.removeAttribute('contenteditable');
      b.textContent = '';
    });
    return DOMPurify.sanitize(copia.innerHTML, PERMITIDO).replace(/<div><br><\/div>/g, '<p><br></p>');
  }

  private camposAtuais(): CamposEmail {
    return {
      ...this.params(),
      subject: this.assunto(),
      label: this.area(),
      tone: this.tom(),
      button_label: this.botaoTexto(),
      button_link: this.botaoLink(),
      body: this.corpoAtual()
    };
  }

  /** Nome amigável de cada dado usado no e-mail, para a prévia marcar. */
  private rotulos(): Record<string, string> {
    const c = this.contexto();
    const campos = this.camposAtuais();
    const exprs = new Set<string>();
    for (const v of [campos['subject'], campos['label'], campos['button_link'], campos['attachments']]) {
      for (const s of segmentos(texto(v as JsonValue))) if (s.expressao !== undefined) exprs.add(s.expressao.trim());
    }
    const corpo = texto(campos['body']);
    for (const m of corpo.matchAll(/data-(?:expr|source)="([^"]+)"/g)) exprs.add(m[1].replace(/&amp;/g, '&').replace(/&quot;/g, '"').trim());
    for (const m of corpo.matchAll(/\{\{\s*([^}]+?)\s*\}\}/g)) exprs.add(m[1].trim());
    const mapa: Record<string, string> = {};
    for (const e of exprs) mapa[e] = nomeCurto(descreverExpressao(e, c.definicao, c.catalogo));
    return mapa;
  }

  protected salvar(): void {
    const c = this.camposAtuais();
    this.mudou.emit({
      subject: c['subject'], label: c['label'], tone: c['tone'], button_label: c['button_label'], button_link: c['button_link'], body: c['body']
    });
    this.aberto.set(false);
  }
}
