import { Catalogo, Definicao, FlowNode, NoEspec, SaidaEspec } from '../../../../core/models/automacao.models';
import { buscarNo, texto, visivelPara } from './arvore';

export interface ItemDinamico {
  rotulo: string;
  expressao: string;
  tipo: string;
  descricao?: string;
  /** Profundidade visual (campos de um objeto ficam recuados). */
  nivel: number;
}

export interface GrupoDinamico {
  chave: string;
  titulo: string;
  icone: string;
  grupo: string;
  itens: ItemDinamico[];
}

export function mapaEspecs(catalogo: Catalogo): Map<string, NoEspec> {
  return new Map(catalogo.nos.map((n) => [n.tipo, n]));
}

/** Saídas de um nó, incluindo as que dependem dos parâmetros (campos da IA, perguntas do gatilho). */
export function saidasDinamicas(no: { params: FlowNode['params'] }, espec: NoEspec): SaidaEspec[] {
  const saidas = [...espec.saidas];
  if (!espec.saidasDinamicas) return saidas;
  const origem = espec.saidasDinamicas === 'inputs' ? 'inputs' : 'fields';
  const campos = no.params[origem];
  if (Array.isArray(campos)) {
    for (const campo of campos) {
      if (campo && typeof campo === 'object' && !Array.isArray(campo) && campo['name']) {
        const t = texto(campo['type']);
        saidas.push({
          chave: `${origem}.${texto(campo['name'])}`,
          rotulo: texto(campo['label'] || campo['name']),
          tipo: t === 'number' ? 'number' : t === 'boolean' ? 'boolean' : t === 'list' ? 'array' : 'string',
          descricao: texto(campo['description'] ?? ''),
          campos: []
        });
      }
    }
  }
  return saidas;
}

function itensDe(saidas: SaidaEspec[], prefixo: string, nivel = 0): ItemDinamico[] {
  const itens: ItemDinamico[] = [];
  for (const s of saidas) {
    itens.push({ rotulo: s.rotulo, expressao: `${prefixo}.${s.chave}`, tipo: s.tipo, descricao: s.descricao, nivel });
    // Campos de um objeto (ex.: Colaborador › Base) também são escolhíveis diretamente.
    if (s.tipo === 'object' && s.campos.length && nivel < 2) {
      for (const item of itensDe(s.campos, `${prefixo}.${s.chave}`, nivel + 1)) {
        itens.push({ ...item, rotulo: `${s.rotulo} › ${item.rotulo}` });
      }
    }
  }
  return itens;
}

/** Campos de cada item de uma lista, para expressões como "{{ steps.x.outputs.rows }}". */
export function camposDoItem(d: Definicao, expressao: string, especs: Map<string, NoEspec>): SaidaEspec[] {
  const m = /\{\{\s*(trigger|steps\.([a-z0-9_]+))\.outputs\.([a-z0-9_.]+)\s*\}\}/i.exec(expressao ?? '');
  if (!m) return [];
  const [, raiz, passoId, chave] = m;
  let saidas: SaidaEspec[] = [];
  if (raiz === 'trigger') {
    const espec = especs.get(d.trigger.type);
    saidas = espec ? saidasDinamicas(d.trigger, espec) : [];
  } else if (passoId) {
    const no = buscarNo(d, passoId);
    const espec = no ? especs.get(no.type) : undefined;
    if (no && espec) {
      if (no.type === 'data.filter' || no.type === 'data.sort') return camposDoItem(d, texto(no.params['items']), especs);
      saidas = saidasDinamicas(no, espec);
    }
  }
  let atual: SaidaEspec | undefined;
  let nivel = saidas;
  for (const parte of (chave ?? '').split('.')) {
    atual = nivel.find((s) => s.chave === parte);
    if (!atual) return [];
    nivel = atual.campos;
  }
  return atual?.campos ?? [];
}

/** Tudo que um parâmetro do nó pode ler (o gatilho não lê nada). */
export function gruposDinamicos(d: Definicao, noId: string, catalogo: Catalogo, fonteItem?: string): GrupoDinamico[] {
  if (noId === 'trigger') return [];
  const especs = mapaEspecs(catalogo);
  const grupos: GrupoDinamico[] = [];
  const visivel = visivelPara(d, noId);
  if (fonteItem !== undefined) {
    grupos.push({
      chave: 'item',
      titulo: 'Item da lista',
      icone: 'pi pi-list',
      grupo: 'control',
      itens: [{ rotulo: 'Item inteiro', expressao: 'item', tipo: 'object', nivel: 0 }, ...itensDe(camposDoItem(d, fonteItem, especs), 'item')]
    });
  }
  for (const loopId of [...visivel.loops].reverse()) {
    const loop = buscarNo(d, loopId);
    if (!loop) continue;
    grupos.push({
      chave: `loop:${loopId}`,
      titulo: `Item atual · ${loop.label || 'Para cada'}`,
      icone: 'pi pi-replay',
      grupo: 'control',
      itens: [
        { rotulo: 'Item inteiro', expressao: 'item', tipo: 'object', nivel: 0 },
        ...itensDe(camposDoItem(d, texto(loop.params['items']), especs), 'item'),
        { rotulo: 'Posição (0, 1, 2…)', expressao: `loop.${loopId}.index`, tipo: 'number', nivel: 0 }
      ]
    });
  }
  const gatilho = especs.get(d.trigger.type);
  if (gatilho) {
    grupos.push({
      chave: 'trigger',
      titulo: `Quando começa · ${gatilho.rotulo}`,
      icone: icone(gatilho.icone),
      grupo: 'trigger',
      itens: itensDe(saidasDinamicas(d.trigger, gatilho), 'trigger.outputs')
    });
  }
  for (const passoId of visivel.passos) {
    const no = buscarNo(d, passoId);
    const espec = no ? especs.get(no.type) : undefined;
    if (!no || !espec || espec.conteiner === 'scope' || espec.conteiner === 'parallel') continue;
    const itens = itensDe(saidasDinamicas(no, espec), `steps.${no.id}.outputs`);
    itens.push(
      { rotulo: 'Deu certo ou falhou', expressao: `steps.${no.id}.status`, tipo: 'string', nivel: 0 },
      { rotulo: 'Mensagem de erro', expressao: `steps.${no.id}.error`, tipo: 'string', nivel: 0 }
    );
    grupos.push({ chave: no.id, titulo: no.label || espec.rotulo, icone: icone(espec.icone), grupo: espec.grupo, itens });
  }
  if (d.variables.length) {
    grupos.push({
      chave: 'vars',
      titulo: 'Valores guardados',
      icone: 'pi pi-box',
      grupo: 'variables',
      itens: d.variables.map((v) => ({ rotulo: v.description || nomeBonito(v.name), expressao: `vars.${v.name}`, tipo: v.type, nivel: 0 }))
    });
  }
  grupos.push({
    chave: 'run',
    titulo: 'Sobre esta execução',
    icone: 'pi pi-play',
    grupo: 'trigger',
    itens: [
      { rotulo: 'Link da execução', expressao: 'run.link', tipo: 'string', nivel: 0 },
      { rotulo: 'Nome da automação', expressao: 'automation.name', tipo: 'string', nivel: 0 },
      { rotulo: 'Hoje (dd/mm/aaaa)', expressao: 'today()', tipo: 'string', nivel: 0 },
      { rotulo: 'Agora', expressao: 'now()', tipo: 'string', nivel: 0 }
    ]
  });
  return grupos.filter((g) => g.itens.length);
}

/** Nome amigável de uma ficha, ex.: "Quando começa › Colaborador › Base operacional". */
export function descreverExpressao(expressao: string, d: Definicao, catalogo: Catalogo): string {
  const t = expressao.trim();
  const especs = mapaEspecs(catalogo);
  const passo = /^steps\.([a-z0-9_]+)\.outputs\.([a-zA-Z0-9_.]+)$/.exec(t);
  if (passo) {
    const no = buscarNo(d, passo[1]!);
    const espec = no ? especs.get(no.type) : undefined;
    return `${no?.label || espec?.rotulo || passo[1]} › ${rotuloCaminho(espec && no ? saidasDinamicas(no, espec) : [], passo[2]!)}`;
  }
  const gatilho = /^trigger\.outputs\.([a-zA-Z0-9_.]+)$/.exec(t);
  if (gatilho) {
    const espec = especs.get(d.trigger.type);
    return `Início › ${rotuloCaminho(espec ? saidasDinamicas(d.trigger, espec) : [], gatilho[1]!)}`;
  }
  const variavel = /^vars\.([a-z0-9_]+)$/.exec(t);
  if (variavel) {
    const v = d.variables.find((x) => x.name === variavel[1]);
    return v?.description || nomeBonito(variavel[1]!);
  }
  const item = /^item(?:\.([a-zA-Z0-9_.]+))?$/.exec(t);
  if (item) return item[1] ? `Item › ${nomeBonito(item[1])}` : 'Item da lista';
  if (t === 'run.link') return 'Link da execução';
  if (t === 'automation.name') return 'Nome da automação';
  if (t === 'now()') return 'Agora';
  if (t === 'today()') return 'Hoje';
  const status = /^steps\.([a-z0-9_]+)\.(status|error)$/.exec(t);
  if (status) {
    const no = buscarNo(d, status[1]!);
    return `${no?.label || status[1]} › ${status[2] === 'status' ? 'Deu certo ou falhou' : 'Mensagem de erro'}`;
  }
  return `Expressão: ${t.length > 40 ? `${t.slice(0, 38)}…` : t}`;
}

function rotuloCaminho(saidas: SaidaEspec[], caminho: string): string {
  const partes = caminho.split('.');
  const rotulos: string[] = [];
  let nivel = saidas;
  for (const p of partes) {
    const s = nivel.find((x) => x.chave === p);
    rotulos.push(s ? s.rotulo : nomeBonito(p));
    nivel = s?.campos ?? [];
  }
  return rotulos.join(' › ');
}

export function nomeBonito(nome: string): string {
  const t = nome.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ').trim().toLowerCase();
  return t ? t[0]!.toUpperCase() + t.slice(1) : nome;
}

export interface Segmento {
  texto: string;
  expressao?: string;
}

export function segmentos(valor: string): Segmento[] {
  const partes: Segmento[] = [];
  const re = /\{\{([\s\S]*?)\}\}/g;
  let ultimo = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(valor)) !== null) {
    if (m.index > ultimo) partes.push({ texto: valor.slice(ultimo, m.index) });
    partes.push({ texto: m[0], expressao: m[1]!.trim() });
    ultimo = m.index + m[0].length;
  }
  if (ultimo < valor.length) partes.push({ texto: valor.slice(ultimo) });
  return partes;
}

const ICONES: Record<string, string> = {
  play: 'pi pi-play',
  calendar: 'pi pi-calendar-clock',
  bolt: 'pi pi-bolt',
  gauge: 'pi pi-chart-bar',
  alert: 'pi pi-exclamation-triangle',
  branch: 'pi pi-share-alt',
  switch: 'pi pi-sitemap',
  repeat: 'pi pi-replay',
  refresh: 'pi pi-refresh',
  box: 'pi pi-box',
  columns: 'pi pi-pause',
  clock: 'pi pi-clock',
  stop: 'pi pi-stop-circle',
  variable: 'pi pi-box',
  plus: 'pi pi-plus',
  list: 'pi pi-list',
  database: 'pi pi-database',
  compose: 'pi pi-pencil',
  braces: 'pi pi-code',
  filter: 'pi pi-filter',
  sort: 'pi pi-sort-alt',
  layers: 'pi pi-clone',
  sigma: 'pi pi-calculator',
  table: 'pi pi-table',
  link: 'pi pi-link',
  text: 'pi pi-align-left',
  sparkles: 'pi pi-sparkles',
  sparkle: 'pi pi-star',
  scan: 'pi pi-search',
  tag: 'pi pi-tag',
  mail: 'pi pi-envelope',
  bell: 'pi pi-bell',
  route: 'pi pi-directions',
  user: 'pi pi-user',
  users: 'pi pi-users',
  check: 'pi pi-check-square',
  globe: 'pi pi-globe'
};

export function icone(nome: string | null | undefined): string {
  return ICONES[nome ?? ''] ?? 'pi pi-circle';
}

export const TIPO_DADO: Record<string, string> = {
  string: 'Texto',
  number: 'Número',
  boolean: 'Sim/Não',
  array: 'Lista',
  object: 'Grupo de dados',
  any: 'Dado'
};
