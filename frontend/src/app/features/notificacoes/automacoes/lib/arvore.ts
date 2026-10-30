import { Definicao, FlowNode, JsonValue, NoEspec, Params } from '../../../../core/models/automacao.models';

/** Uma lista filha: a principal (parentId nulo) ou um slot de um contêiner. */
export type Slot = 'steps' | 'then' | 'else' | 'default' | `case:${string}` | `branch:${string}`;

export interface ListRef {
  parentId: string | null;
  slot: Slot;
}

export interface InsertTarget extends ListRef {
  index: number;
}

export const RAIZ: ListRef = { parentId: null, slot: 'steps' };

export function mesmaLista(a: ListRef, b: ListRef): boolean {
  return a.parentId === b.parentId && a.slot === b.slot;
}

export function chaveAlvo(t: InsertTarget): string {
  return `${t.parentId ?? 'raiz'}:${t.slot}:${t.index}`;
}

/** Listas filhas de um nó, na ordem de exibição. */
export function filhos(no: FlowNode): [Slot, FlowNode[]][] {
  const listas: [Slot, FlowNode[]][] = [];
  if (no.then) listas.push(['then', no.then]);
  if (no.else) listas.push(['else', no.else]);
  for (const c of no.cases ?? []) listas.push([`case:${c.id}`, c.steps]);
  if (no.default) listas.push(['default', no.default]);
  if (no.steps) listas.push(['steps', no.steps]);
  for (const b of no.branches ?? []) listas.push([`branch:${b.id}`, b.steps]);
  return listas;
}

function listaDe(no: FlowNode, slot: Slot): FlowNode[] | undefined {
  return filhos(no).find(([k]) => k === slot)?.[1];
}

function comLista(no: FlowNode, slot: Slot, lista: FlowNode[]): FlowNode {
  if (slot === 'then' || slot === 'else' || slot === 'default' || slot === 'steps') return { ...no, [slot]: lista };
  if (slot.startsWith('case:')) {
    const id = slot.slice(5);
    return { ...no, cases: (no.cases ?? []).map((c) => (c.id === id ? { ...c, steps: lista } : c)) };
  }
  const id = slot.slice(7);
  return { ...no, branches: (no.branches ?? []).map((b) => (b.id === id ? { ...b, steps: lista } : b)) };
}

export function* percorrer(nos: FlowNode[]): Generator<FlowNode> {
  for (const no of nos) {
    yield no;
    for (const [, lista] of filhos(no)) yield* percorrer(lista);
  }
}

export function todosIds(d: Definicao): Set<string> {
  const ids = new Set<string>(['trigger']);
  for (const no of percorrer(d.steps)) {
    ids.add(no.id);
    for (const c of no.cases ?? []) ids.add(c.id);
    for (const b of no.branches ?? []) ids.add(b.id);
  }
  return ids;
}

export function buscarNo(d: Definicao, id: string): FlowNode | undefined {
  for (const no of percorrer(d.steps)) if (no.id === id) return no;
  return undefined;
}

export interface Localizado {
  no: FlowNode;
  ref: ListRef;
  index: number;
}

export function localizar(d: Definicao, id: string): Localizado | undefined {
  function busca(nos: FlowNode[], ref: ListRef): Localizado | undefined {
    for (let i = 0; i < nos.length; i++) {
      const no = nos[i]!;
      if (no.id === id) return { no, ref, index: i };
      for (const [slot, lista] of filhos(no)) {
        const achado = busca(lista, { parentId: no.id, slot });
        if (achado) return achado;
      }
    }
    return undefined;
  }
  return busca(d.steps, RAIZ);
}

function mapear(nos: FlowNode[], fn: (no: FlowNode) => FlowNode): FlowNode[] {
  return nos.map((no) => {
    let prox = fn(no);
    for (const [slot, lista] of filhos(prox)) prox = comLista(prox, slot, mapear(lista, fn));
    return prox;
  });
}

export function atualizarNo(d: Definicao, id: string, fn: (no: FlowNode) => FlowNode): Definicao {
  return { ...d, steps: mapear(d.steps, (no) => (no.id === id ? fn(no) : no)) };
}

function atualizarLista(d: Definicao, ref: ListRef, fn: (lista: FlowNode[]) => FlowNode[]): Definicao {
  if (ref.parentId === null) return { ...d, steps: fn(d.steps) };
  return atualizarNo(d, ref.parentId, (no) => comLista(no, ref.slot, fn(listaDe(no, ref.slot) ?? [])));
}

export function inserirNo(d: Definicao, alvo: InsertTarget, no: FlowNode): Definicao {
  return atualizarLista(d, alvo, (lista) => [...lista.slice(0, alvo.index), no, ...lista.slice(alvo.index)]);
}

export function removerNo(d: Definicao, id: string): Definicao {
  const achado = localizar(d, id);
  if (!achado) return d;
  const layout = { ...(d.layout ?? {}) };
  delete layout[id];
  return { ...atualizarLista(d, achado.ref, (lista) => lista.filter((n) => n.id !== id)), layout };
}

export function contem(no: FlowNode, id: string): boolean {
  for (const n of percorrer([no])) if (n.id === id) return true;
  return false;
}

/** Move um nó (com os filhos) para um slot. Mover para dentro de si mesmo é ignorado. */
export function moverNo(d: Definicao, id: string, alvo: InsertTarget): Definicao {
  const achado = localizar(d, id);
  if (!achado) return d;
  if (alvo.parentId !== null && contem(achado.no, alvo.parentId)) return d;
  let index = alvo.index;
  if (mesmaLista(achado.ref, alvo)) {
    if (index === achado.index || index === achado.index + 1) return d;
    if (index > achado.index) index -= 1;
  }
  const sem = atualizarLista(d, achado.ref, (lista) => lista.filter((n) => n.id !== id));
  return inserirNo(sem, { ...alvo, index }, achado.no);
}

export function slug(texto: string): string {
  const base = texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 34);
  if (!base) return 'passo';
  return /^[a-z]/.test(base) ? base : `p_${base}`.slice(0, 34);
}

export function idUnico(base: string, usados: Set<string>): string {
  const raiz = slug(base);
  let candidato = raiz;
  let i = 2;
  while (usados.has(candidato)) candidato = `${raiz}_${i++}`;
  usados.add(candidato);
  return candidato;
}

function clonar<T>(valor: T): T {
  return valor === undefined ? valor : (JSON.parse(JSON.stringify(valor)) as T);
}

function padroes(espec: NoEspec): Params {
  const params: Params = {};
  for (const p of espec.params) if (p.padrao !== null && p.padrao !== undefined) params[p.chave] = clonar(p.padrao);
  return params;
}

export function novoNo(espec: NoEspec, usados: Set<string>): FlowNode {
  const no: FlowNode = { id: idUnico(espec.rotulo, usados), type: espec.tipo, label: espec.rotulo, params: padroes(espec) };
  switch (espec.conteiner) {
    case 'condition':
      return { ...no, then: [], else: [] };
    case 'switch':
      return { ...no, cases: [{ id: idUnico('caso', usados), value: '', steps: [] }], default: [] };
    case 'loop':
    case 'until':
    case 'scope':
      return { ...no, steps: [] };
    case 'parallel':
      return {
        ...no,
        branches: [
          { id: idUnico('ramo', usados), label: 'Ramo 1', steps: [] },
          { id: idUnico('ramo', usados), label: 'Ramo 2', steps: [] }
        ]
      };
    default:
      return no;
  }
}

export function parametrosPadrao(espec: NoEspec): Params {
  return padroes(espec);
}

function clonarComIds(no: FlowNode, usados: Set<string>): FlowNode {
  const copia: FlowNode = { ...clonar(no), id: idUnico(no.id, usados) };
  if (copia.label) copia.label = `${copia.label} (cópia)`;
  for (const [slot, lista] of filhos(copia)) {
    Object.assign(copia, comLista(copia, slot, lista.map((f) => clonarComIds(f, usados))));
  }
  if (copia.cases) copia.cases = copia.cases.map((c) => ({ ...c, id: idUnico(c.id, usados) }));
  if (copia.branches) copia.branches = copia.branches.map((b) => ({ ...b, id: idUnico(b.id, usados) }));
  return copia;
}

export function duplicarNo(d: Definicao, id: string): { definicao: Definicao; id: string | null } {
  const achado = localizar(d, id);
  if (!achado) return { definicao: d, id: null };
  const copia = clonarComIds(achado.no, todosIds(d));
  return { definicao: inserirNo(d, { ...achado.ref, index: achado.index + 1 }, copia), id: copia.id };
}

export function contarNos(nos: FlowNode[]): number {
  let n = 0;
  for (const _ of percorrer(nos)) n++;
  return n;
}

export interface Visivel {
  /** Ids dos passos cujas saídas este nó pode ler, em ordem de execução. */
  passos: string[];
  /** "Para cada" que contêm o nó, do mais externo ao mais interno. */
  loops: string[];
}

/** O que um nó pode referenciar: mesma regra do verificador do servidor. */
export function visivelPara(d: Definicao, noId: string): Visivel {
  let resultado: Visivel = { passos: [], loops: [] };
  const conteineres = new Set(['control.condition', 'control.switch', 'control.scope', 'control.parallel']);
  function rodar(nos: FlowNode[], visiveis: string[], loops: string[]): { produzidos: string[]; achou: boolean } {
    const produzidos: string[] = [];
    for (const no of nos) {
      const aqui = [...visiveis, ...produzidos];
      if (no.id === noId) {
        resultado = { passos: aqui, loops };
        return { produzidos, achou: true };
      }
      produzidos.push(no.id);
      const listas = filhos(no);
      if (!listas.length) continue;
      const internos = no.type === 'control.foreach' ? [...loops, no.id] : loops;
      for (const [, lista] of listas) {
        const dentro = rodar(lista, aqui, internos);
        if (dentro.achou) return { produzidos, achou: true };
        if (conteineres.has(no.type)) produzidos.push(...dentro.produzidos);
      }
    }
    return { produzidos, achou: false };
  }
  rodar(d.steps, [], []);
  return resultado;
}

export function definicaoBranca(): Definicao {
  return {
    schema: 3,
    trigger: { type: 'trigger.manual', params: {} },
    variables: [],
    steps: [],
    settings: { notify_on_failure: [], timeout_hours: 168 },
    layout: {}
  };
}

export function normalizar(bruta: Definicao): Definicao {
  return {
    ...bruta,
    schema: 3,
    variables: bruta.variables ?? [],
    steps: bruta.steps ?? [],
    settings: bruta.settings ?? { notify_on_failure: [], timeout_hours: 168 },
    layout: bruta.layout ?? {}
  };
}

export function texto(valor: JsonValue | undefined): string {
  if (valor === null || valor === undefined) return '';
  if (typeof valor === 'string') return valor;
  if (typeof valor === 'number' || typeof valor === 'boolean') return String(valor);
  return JSON.stringify(valor);
}

export function lista(valor: JsonValue | undefined): string[] {
  if (Array.isArray(valor)) return valor.map((v) => texto(v));
  if (typeof valor === 'string' && valor.trim()) return [valor];
  return [];
}

export function objetos(valor: JsonValue | undefined): Record<string, JsonValue>[] {
  if (!Array.isArray(valor)) return [];
  return valor.filter((v): v is Record<string, JsonValue> => !!v && typeof v === 'object' && !Array.isArray(v));
}
