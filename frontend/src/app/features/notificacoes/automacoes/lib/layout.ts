import { Definicao, FlowNode } from '../../../../core/models/automacao.models';
import { InsertTarget, ListRef, Slot, contarNos, filhos } from './arvore';

/**
 * Layout do canvas: fluxo vertical como no Power Automate e no TON. Contêineres de ramos
 * (condição, escolher caso, paralelo) abrem colunas lado a lado; contêineres de bloco (para
 * cada, repetir até, escopo) envolvem os passos internos numa moldura. Cartões movidos à mão
 * guardam o deslocamento em `definicao.layout` (só visual).
 */
export const CARD_W = 320;
export const CARD_H = 72;
const GAP = 64;
const PAD = 28;
const COL_GAP = 56;
const LABEL_H = 26;
const LABEL_GAP = 30;
const SLOT_H = 48;
const RECOLHIDO_EXTRA = 24;
const RAMOS = new Set(['control.condition', 'control.switch', 'control.parallel']);
const MOLDURAS = new Set(['control.foreach', 'control.until', 'control.scope']);

export type Tom = 'yes' | 'no' | 'case' | 'default' | 'branch';

export type Item =
  | { tipo: 'gatilho'; id: 'trigger'; x: number; y: number; w: number; h: number }
  | { tipo: 'passo'; id: string; x: number; y: number; w: number; h: number; no: FlowNode; ref: ListRef; index: number; recolhido: boolean; filhos: number }
  | { tipo: 'moldura'; id: string; x: number; y: number; w: number; h: number; noId: string; tom: 'branch' | 'loop' }
  | { tipo: 'rotulo'; id: string; x: number; y: number; w: number; h: number; texto: string; tom: Tom; noId: string; slot: Slot }
  | { tipo: 'vaga'; id: string; x: number; y: number; w: number; h: number; alvo: InsertTarget; interna: boolean; pequena: boolean }
  | { tipo: 'ponto'; id: string; x: number; y: number; w: number; h: number }
  | { tipo: 'fim'; id: string; x: number; y: number; w: number; h: number; alvo: InsertTarget };

export interface Conexao {
  id: string;
  de: string;
  para: string;
  alvo?: InsertTarget;
  dobra: boolean;
  /** Preenchidos por `tracar`. */
  caminho?: string;
  maisX?: number;
  maisY?: number;
}

export interface Layout {
  itens: Item[];
  conexoes: Conexao[];
  /** Alvos de inserção com o ponto no canvas (para soltar cartões arrastados). */
  alvos: { alvo: InsertTarget; x: number; y: number }[];
  largura: { min: number; max: number };
  altura: number;
}

export interface Rotulos {
  sim: string;
  nao: string;
  senao: string;
}

interface Caixa {
  w: number;
  h: number;
}

export function colunasDe(no: FlowNode, r: Rotulos): { slot: Slot; lista: FlowNode[]; texto: string; tom: Tom }[] {
  if (no.type === 'control.condition') {
    return [
      { slot: 'then', lista: no.then ?? [], texto: r.sim, tom: 'yes' },
      { slot: 'else', lista: no.else ?? [], texto: r.nao, tom: 'no' }
    ];
  }
  if (no.type === 'control.switch') {
    return [
      ...(no.cases ?? []).map((c) => ({ slot: `case:${c.id}` as Slot, lista: c.steps, texto: c.value || '…', tom: 'case' as Tom })),
      { slot: 'default' as Slot, lista: no.default ?? [], texto: r.senao, tom: 'default' as Tom }
    ];
  }
  return (no.branches ?? []).map((b) => ({ slot: `branch:${b.id}` as Slot, lista: b.steps, texto: b.label || 'Ramo', tom: 'branch' as Tom }));
}

class Construtor {
  itens: Item[] = [];
  conexoes: Conexao[] = [];
  alvos: Layout['alvos'] = [];
  private desloc = { x: 0, y: 0 };

  constructor(private recolhidos: Set<string>, private r: Rotulos, private offsets: Record<string, { x: number; y: number }>) {}

  add(item: Item): void {
    this.itens.push({ ...item, x: item.x + this.desloc.x, y: item.y + this.desloc.y } as Item);
  }

  medirLista(lista: FlowNode[]): Caixa {
    if (!lista.length) return { w: CARD_W, h: SLOT_H };
    let w = CARD_W;
    let h = 0;
    lista.forEach((no, i) => {
      const c = this.medir(no);
      w = Math.max(w, c.w);
      h += c.h + (i ? GAP : 0);
    });
    return { w, h };
  }

  medir(no: FlowNode): Caixa {
    if (this.recolhidos.has(no.id) && filhos(no).length) return { w: CARD_W, h: CARD_H + RECOLHIDO_EXTRA };
    if (MOLDURAS.has(no.type)) {
      const dentro = this.medirLista(no.steps ?? []);
      return { w: Math.max(CARD_W, dentro.w) + PAD * 2, h: CARD_H + GAP + dentro.h + GAP / 2 + PAD };
    }
    if (RAMOS.has(no.type)) {
      const cols = colunasDe(no, this.r).map((c) => this.medirLista(c.lista));
      const largura = cols.reduce((s, c) => s + c.w, 0) + COL_GAP * Math.max(0, cols.length - 1);
      const altura = Math.max(SLOT_H, ...cols.map((c) => c.h));
      return { w: Math.max(CARD_W, largura) + PAD * 2, h: CARD_H + GAP + LABEL_H + LABEL_GAP + altura + GAP };
    }
    return { w: CARD_W, h: CARD_H };
  }

  ligar(de: string, para: string, alvo?: InsertTarget, dobra = false): void {
    this.conexoes.push({ id: `${de}->${para}`, de, para, alvo, dobra });
  }

  ponto(id: string, x: number, y: number): void {
    this.add({ tipo: 'ponto', id, x: x - 1, y: y - 1, w: 2, h: 2 });
  }

  alvo(alvo: InsertTarget, x: number, y: number): void {
    this.alvos.push({ alvo, x: x + this.desloc.x, y: y + this.desloc.y });
  }

  posicionarLista(lista: FlowNode[], ref: ListRef, cx: number, y: number, origem: string, interna: boolean): { saida: string; base: number } {
    if (!lista.length) {
      const id = `vaga:${ref.parentId ?? 'raiz'}:${ref.slot}`;
      const alvo = { ...ref, index: 0 };
      this.add({ tipo: 'vaga', id, x: cx - CARD_W / 2, y, w: CARD_W, h: SLOT_H, alvo, interna, pequena: false });
      this.ligar(origem, id);
      this.alvo(alvo, cx, y + SLOT_H / 2);
      return { saida: id, base: y + SLOT_H };
    }
    let anterior = origem;
    let topo = y;
    lista.forEach((no, index) => {
      const p = this.posicionar(no, ref, index, cx, topo);
      const alvo = { ...ref, index };
      this.ligar(anterior, p.entrada, alvo);
      this.alvo(alvo, cx, topo - GAP / 2);
      anterior = p.saida;
      topo = p.base + GAP;
    });
    return { saida: anterior, base: topo - GAP };
  }

  posicionar(no: FlowNode, ref: ListRef, index: number, cx: number, y: number): { entrada: string; saida: string; base: number } {
    const pai = this.desloc;
    const proprio = this.offsets[no.id];
    if (proprio) this.desloc = { x: pai.x + proprio.x, y: pai.y + proprio.y };
    try {
      return this.posicionarDeslocado(no, ref, index, cx, y);
    } finally {
      this.desloc = pai;
    }
  }

  private posicionarDeslocado(no: FlowNode, ref: ListRef, index: number, cx: number, y: number): { entrada: string; saida: string; base: number } {
    const caixa = this.medir(no);
    const listas = filhos(no);
    const recolhido = this.recolhidos.has(no.id) && listas.length > 0;
    const cartao: Item = {
      tipo: 'passo', id: no.id, x: cx - CARD_W / 2, y, w: CARD_W, h: recolhido ? CARD_H + RECOLHIDO_EXTRA : CARD_H,
      no, ref, index, recolhido, filhos: listas.reduce((s, [, l]) => s + contarNos(l), 0)
    };
    if (recolhido || (!MOLDURAS.has(no.type) && !RAMOS.has(no.type))) {
      this.add(cartao);
      return { entrada: no.id, saida: no.id, base: y + caixa.h };
    }
    const esquerda = cx - caixa.w / 2;
    this.add({ tipo: 'moldura', id: `moldura:${no.id}`, x: esquerda, y: y + CARD_H / 2, w: caixa.w, h: caixa.h - CARD_H / 2, noId: no.id, tom: RAMOS.has(no.type) ? 'branch' : 'loop' });
    this.add(cartao);
    const saidaId = `saida:${no.id}`;
    this.ponto(saidaId, cx, y + caixa.h);
    if (MOLDURAS.has(no.type)) {
      const ref2: ListRef = { parentId: no.id, slot: 'steps' };
      const p = this.posicionarLista(no.steps ?? [], ref2, cx, y + CARD_H + GAP, no.id, true);
      if ((no.steps ?? []).length) {
        const fimId = `vaga:${no.id}:fim`;
        const alvo = { ...ref2, index: (no.steps ?? []).length };
        const fimY = p.base + GAP / 2 - 14;
        this.add({ tipo: 'vaga', id: fimId, x: cx - 14, y: fimY, w: 28, h: 28, alvo, interna: true, pequena: true });
        this.ligar(p.saida, fimId);
        this.alvo(alvo, cx, fimY + 14);
      }
      return { entrada: no.id, saida: saidaId, base: y + caixa.h };
    }
    const cols = colunasDe(no, this.r);
    const caixas = cols.map((c) => this.medirLista(c.lista));
    const total = caixas.reduce((s, c) => s + c.w, 0) + COL_GAP * Math.max(0, cols.length - 1);
    let x = cx - total / 2;
    const rotuloY = y + CARD_H + GAP;
    const juntaId = `junta:${no.id}`;
    this.ponto(juntaId, cx, y + caixa.h - GAP / 2);
    cols.forEach((col, i) => {
      const c = caixas[i]!;
      const ccx = x + c.w / 2;
      const rotuloId = `rotulo:${no.id}:${col.slot}`;
      this.add({ tipo: 'rotulo', id: rotuloId, x: ccx - 64, y: rotuloY, w: 128, h: LABEL_H, texto: col.texto, tom: col.tom, noId: no.id, slot: col.slot });
      this.ligar(no.id, rotuloId, undefined, true);
      const ref2: ListRef = { parentId: no.id, slot: col.slot };
      const p = this.posicionarLista(col.lista, ref2, ccx, rotuloY + LABEL_H + LABEL_GAP, rotuloId, true);
      const fim = { ...ref2, index: col.lista.length };
      if (col.lista.length) {
        this.ligar(p.saida, juntaId, fim, true);
        this.alvo(fim, ccx, p.base + GAP / 2);
      } else {
        this.ligar(p.saida, juntaId, undefined, true);
      }
      x += c.w + COL_GAP;
    });
    this.ligar(juntaId, saidaId);
    return { entrada: no.id, saida: saidaId, base: y + caixa.h };
  }
}

export function montarLayout(d: Definicao, recolhidos: Set<string>, r: Rotulos): Layout {
  const offsets = d.layout ?? {};
  const b = new Construtor(recolhidos, r, offsets);
  const movido = offsets['trigger'] ?? { x: 0, y: 0 };
  b.itens.push({ tipo: 'gatilho', id: 'trigger', x: -CARD_W / 2 + movido.x, y: movido.y, w: CARD_W, h: CARD_H });
  const p = b.posicionarLista(d.steps, { parentId: null, slot: 'steps' }, 0, CARD_H + GAP, 'trigger', false);
  if (d.steps.length) {
    const alvo: InsertTarget = { parentId: null, slot: 'steps', index: d.steps.length };
    const fimY = p.base + GAP;
    b.itens.push({ tipo: 'fim', id: 'fim', x: -100, y: fimY, w: 200, h: 44, alvo });
    b.ligar(p.saida, 'fim', alvo);
    b.alvos.push({ alvo, x: 0, y: p.base + GAP / 2 });
  }
  tracar(b.itens, b.conexoes);
  let min = 0;
  let max = 0;
  let altura = 0;
  for (const i of b.itens) {
    min = Math.min(min, i.x);
    max = Math.max(max, i.x + i.w);
    altura = Math.max(altura, i.y + i.h);
  }
  return { itens: b.itens, conexoes: b.conexoes, alvos: b.alvos, largura: { min, max }, altura };
}

/** Caminhos SVG: reto quando alinhado, em degrau com cantos arredondados quando não. */
function tracar(itens: Item[], conexoes: Conexao[]): void {
  const porId = new Map(itens.map((i) => [i.id, i]));
  for (const c of conexoes) {
    const a = porId.get(c.de);
    const b = porId.get(c.para);
    if (!a || !b) continue;
    const sx = a.x + a.w / 2;
    const sy = a.y + a.h;
    const tx = b.x + b.w / 2;
    const ty = b.y;
    if (Math.abs(sx - tx) < 1) {
      c.caminho = `M ${sx} ${sy} L ${tx} ${ty}`;
      c.maisX = sx;
      c.maisY = (sy + ty) / 2;
      continue;
    }
    const meio = c.dobra && b.tipo !== 'ponto' ? sy + 18 : (b.tipo === 'ponto' ? ty - 16 : sy + (ty - sy) / 2);
    const raio = Math.min(12, Math.abs(tx - sx) / 2, Math.abs(meio - sy), Math.abs(ty - meio));
    const dir = tx > sx ? 1 : -1;
    c.caminho = [
      `M ${sx} ${sy}`,
      `L ${sx} ${meio - raio}`,
      `Q ${sx} ${meio} ${sx + dir * raio} ${meio}`,
      `L ${tx - dir * raio} ${meio}`,
      `Q ${tx} ${meio} ${tx} ${meio + raio}`,
      `L ${tx} ${ty}`
    ].join(' ');
    c.maisX = b.tipo === 'ponto' ? sx : tx;
    c.maisY = b.tipo === 'ponto' ? sy + (meio - sy) / 2 : meio + (ty - meio) / 2;
  }
}
