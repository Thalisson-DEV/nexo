import {
  AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, computed, effect, input, output, signal, viewChild
} from '@angular/core';
import { CesIconButtonComponent } from '@ces/ui';

import { Catalogo, Definicao, NoEspec, StatusEtapa } from '../../../../core/models/automacao.models';
import { InsertTarget, buscarNo, chaveAlvo, contem } from '../lib/arvore';
import { icone } from '../lib/dinamico';
import { CARD_H, CARD_W, Item, montarLayout } from '../lib/layout';
import { resumirNo } from '../lib/resumo';

export interface EstadoExecucaoNo {
  status: StatusEtapa;
  duracaoMs: number | null;
  iteracoes: number;
}

const DISTANCIA_SOLTAR = 120;
const DISTANCIA_REORDENAR = 48;

export function formatarDuracao(ms: number | null): string {
  if (ms === null || ms === undefined) return '';
  if (ms < 1000) return `${ms} ms`;
  const s = ms / 1000;
  if (s < 60) return `${s.toFixed(s < 10 ? 1 : 0).replace('.', ',')} s`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m} min ${Math.round(s % 60)} s`;
  return `${Math.floor(m / 60)} h ${m % 60} min`;
}

/**
 * O canvas do designer: fluxo vertical, como no Power Automate. Arraste o fundo para mover,
 * Ctrl + rolagem para zoom. Cartões podem ser arrastados: soltos sobre um "+" mudam de
 * lugar no fluxo; soltos em espaço livre só mudam de posição na tela.
 */
@Component({
  selector: 'aut-canvas',
  standalone: true,
  imports: [CesIconButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div #area class="aut-canvas" [attr.data-panning]="panning() || null" tabindex="-1"
         [style.background-size]="(20 * zoom()) + 'px ' + (20 * zoom()) + 'px'" [style.background-position]="pan().x + 'px ' + pan().y + 'px'"
         (pointerdown)="iniciarPan($event)" (wheel)="rolar($event)"
         (dragover)="sobrePaleta($event)" (drop)="soltarPaleta($event)" (dragleave)="hoverKey.set(null)">
      <div class="aut-canvas-stage" [style.transform]="'translate(' + pan().x + 'px,' + pan().y + 'px) scale(' + zoom() + ')'">
        <svg class="aut-canvas-svg" width="1" height="1" aria-hidden="true">
          <defs>
            <marker id="aut-seta" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" class="aut-arrow" />
            </marker>
          </defs>
          @for (c of layout().conexoes; track c.id) {
            @if (c.caminho && !(somenteLeitura() && c.para === 'fim')) {
              <path class="aut-edge" [attr.d]="c.caminho" [attr.marker-end]="pontaSeta(c.para) ? 'url(#aut-seta)' : null"
                    [attr.data-run]="arestaOk(c.de) ? 'ok' : null" />
            }
          }
        </svg>

        @for (i of layout().itens; track i.id) {
          @switch (i.tipo) {
            @case ('moldura') {
              <div class="aut-frame" [attr.data-tone]="i.tom" [attr.data-selected]="selecionado() === i.noId || null"
                   [attr.data-group]="grupoDe(i.noId)"
                   [style.left.px]="i.x" [style.top.px]="i.y" [style.width.px]="i.w" [style.height.px]="i.h"></div>
            }
            @case ('rotulo') {
              <div class="aut-label" [attr.data-tone]="i.tom" [style.left.px]="i.x" [style.top.px]="i.y" [style.width.px]="i.w" [style.height.px]="i.h">
                <span [title]="i.texto">{{ i.texto }}</span>
              </div>
            }
            @case ('vaga') {
              <button type="button" class="aut-slot" [attr.data-small]="i.pequena || null" [attr.data-readonly]="somenteLeitura() || null"
                      [attr.data-hover]="hoverKey() === chave(i.alvo) || null" [attr.data-dragging]="(arrastandoPaleta() || arrastado()) || null"
                      [disabled]="somenteLeitura()" [attr.aria-label]="'Adicionar ação aqui'"
                      [style.left.px]="i.x" [style.top.px]="i.y" [style.width.px]="i.w" [style.height.px]="i.h"
                      (pointerdown)="$event.stopPropagation()" (click)="inserirEm.emit(i.alvo)">
                @if (!somenteLeitura()) {
                  <i class="pi pi-plus" aria-hidden="true"></i>
                  @if (!i.pequena) { <span>{{ arrastandoPaleta() || arrastado() ? 'Soltar aqui' : 'Adicionar ação' }}</span> }
                }
              </button>
            }
            @case ('fim') {
              @if (!somenteLeitura()) {
                <button type="button" class="aut-end" [attr.data-hover]="hoverKey() === chave(i.alvo) || null"
                        [attr.data-dragging]="(arrastandoPaleta() || arrastado()) || null"
                        [style.left.px]="i.x" [style.top.px]="i.y" [style.width.px]="i.w" [style.height.px]="i.h"
                        (pointerdown)="$event.stopPropagation()" (click)="inserirEm.emit(i.alvo)">
                  <i class="pi pi-plus" aria-hidden="true"></i><span>{{ arrastandoPaleta() || arrastado() ? 'Soltar aqui' : 'Adicionar ação' }}</span>
                </button>
              }
            }
            @case ('ponto') {}
            @default {
              <div class="aut-node" role="button" tabindex="0" [attr.aria-label]="titulo(i)"
                   [attr.data-group]="i.tipo === 'gatilho' ? 'trigger' : grupoDe(i.id)"
                   [attr.data-selected]="selecionado() === i.id || null" [attr.data-error]="(problemas().get(i.id)?.erros ?? 0) > 0 || null"
                   [attr.data-run]="execucao()?.get(i.id)?.status ?? null" [attr.data-dragging]="arrastado() === i.id || null"
                   [style.left.px]="i.x + (arrastado() === i.id ? delta().x : 0)" [style.top.px]="i.y + (arrastado() === i.id ? delta().y : 0)"
                   [style.width.px]="i.w" [style.min-height.px]="i.h"
                   (pointerdown)="iniciarArraste($event, i)" (keydown.enter)="selecionar.emit(i.id)" (keydown.space)="$event.preventDefault(); selecionar.emit(i.id)">
                <span class="aut-node-icon"><i [class]="iconeDe(i)" aria-hidden="true"></i></span>
                <span class="aut-node-text">
                  @if (i.tipo === 'gatilho') { <span class="aut-node-eyebrow">QUANDO COMEÇA</span> }
                  <span class="aut-node-title">{{ titulo(i) }}</span>
                  @if (subtitulo(i); as s) { <span class="aut-node-sub" [title]="s">{{ s }}</span> }
                  @if (i.tipo === 'passo' && i.recolhido) { <span class="aut-node-sub aut-node-count">{{ i.filhos }} ação(ões) dentro</span> }
                </span>
                <span class="aut-node-badges">
                  @if (especDe(i)?.ia) { <span class="aut-tag">IA</span> }
                  @if (!execucao()) {
                    @if (problemas().get(i.id); as p) {
                      @if (p.erros || p.avisos) {
                        <span class="aut-issue" [attr.data-severity]="p.erros ? 'error' : 'warning'" [title]="p.erros ? p.erros + ' erro(s)' : p.avisos + ' aviso(s)'">
                          <i class="pi pi-exclamation-circle" aria-hidden="true"></i>{{ p.erros || p.avisos }}
                        </span>
                      }
                    }
                  }
                  @if (i.tipo === 'passo' && especDe(i)?.conteiner) {
                    <button type="button" class="aut-collapse" [attr.aria-label]="i.recolhido ? 'Expandir' : 'Recolher'"
                            (pointerdown)="$event.stopPropagation()" (click)="$event.stopPropagation(); alternarRecolhido(i.id)">
                      <i [class]="i.recolhido ? 'pi pi-chevron-down' : 'pi pi-chevron-up'" aria-hidden="true"></i>
                    </button>
                  }
                </span>
                @if (execucao()?.get(i.id); as r) {
                  <span class="aut-run-badge" [attr.data-status]="r.status">
                    <span>{{ r.iteracoes > 1 ? r.iteracoes + '×' : duracao(r.duracaoMs) }}</span>
                    <span class="aut-run-dot"><i [class]="iconeStatus(r.status)" aria-hidden="true"></i></span>
                  </span>
                }
              </div>
            }
          }
        }

        @if (!somenteLeitura()) {
          @for (c of layout().conexoes; track c.id) {
            @if (c.alvo && c.maisX !== undefined) {
              <button type="button" class="aut-insert" [style.left.px]="c.maisX" [style.top.px]="c.maisY"
                      [attr.data-hover]="hoverKey() === chave(c.alvo) || null" [attr.data-dragging]="(arrastandoPaleta() || arrastado()) || null"
                      aria-label="Adicionar ação aqui" title="Adicionar ação aqui"
                      (pointerdown)="$event.stopPropagation()" (click)="inserirEm.emit(c.alvo!)">
                <i class="pi pi-plus" style="font-size:0.65rem" aria-hidden="true"></i>
                @if (arrastandoPaleta() || arrastado()) { <span>Soltar</span> }
              </button>
            }
          }
        }
      </div>

      <div class="aut-controls" role="toolbar" aria-label="Visualização" (pointerdown)="$event.stopPropagation()">
        <ces-icon-button icon="pi pi-search-plus" label="Aproximar" variant="ghost" size="icon-sm" (pressed)="zoomEm(1.2)"></ces-icon-button>
        <ces-icon-button icon="pi pi-search-minus" label="Afastar" variant="ghost" size="icon-sm" (pressed)="zoomEm(1 / 1.2)"></ces-icon-button>
        <span class="aut-zoom-label">{{ (zoom() * 100).toFixed(0) }}%</span>
        <ces-icon-button icon="pi pi-window-maximize" label="Ver o fluxo inteiro" variant="ghost" size="icon-sm" (pressed)="ajustar()"></ces-icon-button>
        <ces-icon-button icon="pi pi-arrow-up" label="Voltar ao início" variant="ghost" size="icon-sm" (pressed)="topo()"></ces-icon-button>
        @if (!somenteLeitura() && movido()) {
          <ces-icon-button icon="pi pi-sitemap" label="Reorganizar" variant="ghost" size="icon-sm" (pressed)="reorganizar.emit()"></ces-icon-button>
        }
      </div>
      @if (!somenteLeitura()) {
        <div class="aut-canvas-hint" aria-hidden="true">
          {{ arrastado() ? 'Solte sobre um + para mudar a ordem, ou em espaço livre para só mover' : 'Arraste o fundo para mover · Ctrl + rolagem para zoom · arraste um bloco sobre um + para reordenar' }}
        </div>
      }
    </div>
  `
})
export class CanvasComponent implements AfterViewInit {
  readonly definicao = input.required<Definicao>();
  readonly catalogo = input.required<Catalogo>();
  readonly especs = input.required<Map<string, NoEspec>>();
  readonly selecionado = input<string | null>(null);
  readonly problemas = input<Map<string, { erros: number; avisos: number }>>(new Map());
  readonly somenteLeitura = input(false);
  readonly execucao = input<Map<string, EstadoExecucaoNo> | null>(null);
  readonly arrastandoPaleta = input(false);
  readonly foco = input<{ id: string; n: number } | null>(null);

  readonly selecionar = output<string>();
  readonly inserirEm = output<InsertTarget>();
  readonly soltarNovo = output<{ alvo: InsertTarget; tipo: string }>();
  readonly mover = output<{ id: string; alvo: InsertTarget }>();
  readonly posicionar = output<{ id: string; x: number; y: number }>();
  readonly reorganizar = output<void>();

  private readonly area = viewChild.required<ElementRef<HTMLDivElement>>('area');
  protected readonly zoom = signal(1);
  protected readonly pan = signal({ x: 600, y: 48 });
  protected readonly panning = signal(false);
  protected readonly recolhidos = signal(new Set<string>());
  protected readonly hoverKey = signal<string | null>(null);
  protected readonly arrastado = signal<string | null>(null);
  protected readonly delta = signal({ x: 0, y: 0 });
  private focado = -1;

  protected readonly layout = computed(() => montarLayout(this.definicao(), this.recolhidos(), { sim: 'Sim', nao: 'Não', senao: 'Senão' }));
  protected readonly movido = computed(() => Object.keys(this.definicao().layout ?? {}).length > 0);
  protected readonly chave = chaveAlvo;
  protected readonly duracao = formatarDuracao;

  constructor() {
    effect(() => {
      const f = this.foco();
      if (!f || f.n === this.focado) return;
      const item = this.layout().itens.find((i) => i.id === f.id);
      if (!item) return;
      this.focado = f.n;
      const el = this.area().nativeElement;
      const z = Math.max(this.zoom(), 0.85);
      this.zoom.set(z);
      this.pan.set({ x: el.clientWidth / 2 - (item.x + item.w / 2) * z, y: el.clientHeight / 3 - (item.y + item.h / 2) * z });
    });
  }

  ngAfterViewInit(): void {
    requestAnimationFrame(() => this.topo());
  }

  // ------------------------------------------------------------------------ dados do cartão

  protected especDe(i: Item): NoEspec | undefined {
    if (i.tipo === 'gatilho') return this.especs().get(this.definicao().trigger.type);
    if (i.tipo === 'passo') return this.especs().get(i.no.type);
    return undefined;
  }

  protected grupoDe(id: string): string {
    const no = buscarNo(this.definicao(), id);
    return no ? this.especs().get(no.type)?.grupo ?? 'control' : 'control';
  }

  protected iconeDe(i: Item): string {
    return icone(this.especDe(i)?.icone);
  }

  protected titulo(i: Item): string {
    const e = this.especDe(i);
    if (i.tipo === 'gatilho') return e?.rotulo ?? 'Escolha o gatilho';
    if (i.tipo === 'passo') return i.no.label || e?.rotulo || i.no.type;
    return '';
  }

  protected subtitulo(i: Item): string {
    const e = this.especDe(i);
    const no = i.tipo === 'passo' ? i.no : null;
    const s = resumirNo(no, e, this.definicao(), this.catalogo());
    return s === this.titulo(i) ? '' : s;
  }

  protected iconeStatus(s: StatusEtapa): string {
    switch (s) {
      case 'SUCESSO': return 'pi pi-check';
      case 'FALHA': case 'CANCELADA': return 'pi pi-times';
      case 'AGUARDANDO': case 'TEMPO_ESGOTADO': return 'pi pi-clock';
      case 'EXECUTANDO': return 'pi pi-spin pi-spinner';
      default: return 'pi pi-minus';
    }
  }

  protected pontaSeta(para: string): boolean {
    return !para.startsWith('junta:') && !para.startsWith('saida:') && !para.startsWith('rotulo:');
  }

  protected arestaOk(de: string): boolean {
    const r = this.execucao()?.get(de === 'trigger' ? '' : de);
    return !!r && r.status === 'SUCESSO';
  }

  protected alternarRecolhido(id: string): void {
    const s = new Set(this.recolhidos());
    if (s.has(id)) s.delete(id);
    else s.add(id);
    this.recolhidos.set(s);
  }

  // ------------------------------------------------------------------------ visão

  private pontoCanvas(clientX: number, clientY: number): { x: number; y: number } {
    const r = this.area().nativeElement.getBoundingClientRect();
    return { x: (clientX - r.left - this.pan().x) / this.zoom(), y: (clientY - r.top - this.pan().y) / this.zoom() };
  }

  protected topo(): void {
    const el = this.area().nativeElement;
    this.zoom.set(1);
    this.pan.set({ x: el.clientWidth / 2, y: 48 });
  }

  protected ajustar(): void {
    const el = this.area().nativeElement;
    const l = this.layout();
    const largura = l.largura.max - l.largura.min + 80;
    const altura = l.altura + 80;
    const z = Math.max(0.25, Math.min(1, el.clientWidth / largura, el.clientHeight / altura));
    this.zoom.set(z);
    this.pan.set({ x: el.clientWidth / 2 - ((l.largura.min + l.largura.max) / 2) * z, y: 32 });
  }

  protected zoomEm(fator: number, cx?: number, cy?: number): void {
    const el = this.area().nativeElement;
    const z0 = this.zoom();
    const z = Math.max(0.2, Math.min(1.6, z0 * fator));
    const px = cx ?? el.clientWidth / 2;
    const py = cy ?? el.clientHeight / 2;
    const p = this.pan();
    this.pan.set({ x: px - ((px - p.x) / z0) * z, y: py - ((py - p.y) / z0) * z });
    this.zoom.set(z);
  }

  protected rolar(e: WheelEvent): void {
    e.preventDefault();
    if (e.ctrlKey || e.metaKey) {
      const r = this.area().nativeElement.getBoundingClientRect();
      this.zoomEm(e.deltaY < 0 ? 1.1 : 1 / 1.1, e.clientX - r.left, e.clientY - r.top);
    } else {
      const p = this.pan();
      this.pan.set({ x: p.x - (e.shiftKey ? e.deltaY : e.deltaX), y: p.y - (e.shiftKey ? 0 : e.deltaY) });
    }
  }

  protected iniciarPan(e: PointerEvent): void {
    if (e.button !== 0) return;
    const inicio = { x: e.clientX, y: e.clientY, pan: this.pan() };
    this.panning.set(true);
    const mover = (ev: PointerEvent) => this.pan.set({ x: inicio.pan.x + ev.clientX - inicio.x, y: inicio.pan.y + ev.clientY - inicio.y });
    const soltar = () => {
      this.panning.set(false);
      window.removeEventListener('pointermove', mover);
      window.removeEventListener('pointerup', soltar);
    };
    window.addEventListener('pointermove', mover);
    window.addEventListener('pointerup', soltar);
  }

  // ------------------------------------------------------------------------ arrastar cartões

  private maisProximo(x: number, y: number, excluir: string | null, limite: number): InsertTarget | null {
    let melhor: InsertTarget | null = null;
    let dist = limite;
    const movido = excluir ? buscarNo(this.definicao(), excluir) : undefined;
    const proprio = excluir ? this.layout().itens.find((i) => i.id === excluir) : undefined;
    for (const c of this.layout().alvos) {
      if (movido && c.alvo.parentId && contem(movido, c.alvo.parentId)) continue;
      if (proprio && proprio.tipo === 'passo' && c.alvo.parentId === proprio.ref.parentId && c.alvo.slot === proprio.ref.slot
          && (c.alvo.index === proprio.index || c.alvo.index === proprio.index + 1)) continue;
      const d = Math.hypot(c.x - x, c.y - y);
      if (d < dist) {
        dist = d;
        melhor = c.alvo;
      }
    }
    return melhor;
  }

  protected iniciarArraste(e: PointerEvent, i: Item): void {
    if (e.button !== 0) return;
    e.stopPropagation();
    const inicio = { x: e.clientX, y: e.clientY };
    let arrastando = false;
    const podeArrastar = !this.somenteLeitura();
    const mover = (ev: PointerEvent) => {
      if (!podeArrastar) return;
      const dx = (ev.clientX - inicio.x) / this.zoom();
      const dy = (ev.clientY - inicio.y) / this.zoom();
      if (!arrastando && Math.hypot(dx, dy) < 5) return;
      arrastando = true;
      this.arrastado.set(i.id);
      this.delta.set({ x: dx, y: dy });
      if (i.tipo === 'passo') {
        const alvo = this.maisProximo(i.x + dx + CARD_W / 2, i.y + dy + CARD_H / 2, i.id, DISTANCIA_REORDENAR);
        this.hoverKey.set(alvo ? chaveAlvo(alvo) : null);
      }
    };
    const soltar = () => {
      window.removeEventListener('pointermove', mover);
      window.removeEventListener('pointerup', soltar);
      if (!arrastando) {
        this.selecionar.emit(i.id);
        return;
      }
      const d = this.delta();
      this.arrastado.set(null);
      this.delta.set({ x: 0, y: 0 });
      this.hoverKey.set(null);
      if (i.tipo === 'passo') {
        const alvo = this.maisProximo(i.x + d.x + CARD_W / 2, i.y + d.y + CARD_H / 2, i.id, DISTANCIA_REORDENAR);
        if (alvo) {
          this.mover.emit({ id: i.id, alvo });
          return;
        }
      }
      const atual = this.definicao().layout?.[i.id] ?? { x: 0, y: 0 };
      this.posicionar.emit({ id: i.id, x: Math.round(atual.x + d.x), y: Math.round(atual.y + d.y) });
    };
    window.addEventListener('pointermove', mover);
    window.addEventListener('pointerup', soltar);
  }

  // ------------------------------------------------------------------------ soltar da paleta

  protected sobrePaleta(e: DragEvent): void {
    if (this.somenteLeitura() || !e.dataTransfer?.types.includes('application/ces-automacao')) return;
    e.preventDefault();
    const p = this.pontoCanvas(e.clientX, e.clientY);
    const alvo = this.maisProximo(p.x, p.y, null, DISTANCIA_SOLTAR);
    this.hoverKey.set(alvo ? chaveAlvo(alvo) : null);
  }

  protected soltarPaleta(e: DragEvent): void {
    if (this.somenteLeitura()) return;
    const dado = e.dataTransfer?.getData('application/ces-automacao');
    if (!dado) return;
    e.preventDefault();
    const p = this.pontoCanvas(e.clientX, e.clientY);
    const alvo = this.maisProximo(p.x, p.y, null, DISTANCIA_SOLTAR);
    this.hoverKey.set(null);
    if (alvo && dado.startsWith('novo:')) this.soltarNovo.emit({ alvo, tipo: dado.slice(5) });
  }
}
