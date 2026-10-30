import { map } from 'rxjs';
import { CesDataTableColumn } from '@ces/ui';
import { DesvioDetalhadoResponse, DesvioFilter } from '../../../../core/models/desvio.models';
import {
  GRAVIDADE_LABELS,
  GravidadeDesvio,
  InspecaoFilters,
  InspecaoSegurancaResponse,
  STATUS_DESVIO_LABELS,
  STATUS_INSPECAO_LABELS,
  StatusDesvio,
  StatusInspecao
} from '../../../../core/models/sst.models';
import { SstService } from '../../../../core/services/sst.service';
import {
  AcaoAnalise,
  DetalheRegistro,
  FiltroOrigem,
  GraficoAnalise,
  LinkAnalise,
  OrigemAnalise
} from '../../../../shared/components/dashboard/dashboard-analise.models';

/**
 * Peças comuns das análises (drill-down) dos dashboards de segurança: as origens "inspeções" e
 * "desvios" vindas das listagens (mesmas permissões e filtros do painel), o detalhe de cada registro
 * e os gráficos que só existem com os registros em mãos.
 */

export const LIMITE_SST = 200;
export const ORDEM_GRAVIDADE: GravidadeDesvio[] = ['BAIXA', 'MEDIA', 'ALTA', 'CRITICA'];
const FUSO = 'America/Sao_Paulo';
const DIA_HORA = new Intl.DateTimeFormat('pt-BR', { timeZone: FUSO, day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' });
const DIA = new Intl.DateTimeFormat('pt-BR', { timeZone: FUSO, day: '2-digit', month: '2-digit' });
const DIA_ISO = new Intl.DateTimeFormat('en-CA', { timeZone: FUSO, year: 'numeric', month: '2-digit', day: '2-digit' });

export function diaHora(iso: string | null | undefined): string {
  return iso ? DIA_HORA.format(new Date(iso)) : '—';
}

export function diaCurto(iso: string): string {
  return DIA.format(new Date(iso));
}

export function diaIso(iso: string): string {
  return DIA_ISO.format(new Date(iso));
}

/** Dia civil (`AAAA-MM-DD`) de um campo da API: data pura fica como está; instante vira o dia de São Paulo. */
export function diaCivil(valor: string): string {
  return /^\d{4}-\d{2}-\d{2}$/.test(valor) ? valor : diaIso(valor);
}

export function plural(n: number, um: string, varios: string): string {
  return `${n.toLocaleString('pt-BR')} ${n === 1 ? um : varios}`;
}

export function pct(valor: number | null | undefined, casas = 1): string {
  return valor == null || !Number.isFinite(valor) ? '—' : `${valor.toLocaleString('pt-BR', { maximumFractionDigits: casas })}%`;
}

export function nomeOu(nome: string | null | undefined, padrao = 'Sem nome'): string {
  return nome?.trim() || padrao;
}

export function alturaBarras(n: number): number {
  return Math.max(220, Math.min(n, 30) * 28 + 60);
}

/** Conta por chave e devolve do maior para o menor (top `limite`). */
export function contar<T>(linhas: readonly T[], chave: (l: T) => string | null | undefined, limite = 15): { rotulo: string; total: number }[] {
  const mapa = new Map<string, number>();
  for (const l of linhas) {
    const k = chave(l) || 'Não informado';
    mapa.set(k, (mapa.get(k) ?? 0) + 1);
  }
  return [...mapa.entries()].map(([rotulo, total]) => ({ rotulo, total })).sort((a, b) => b.total - a.total).slice(0, limite);
}

function filtrarPor<T>(chave: string, rotulo: string, aceita: (l: T) => boolean, origem?: string): AcaoAnalise {
  return { tipo: 'filtrar', filtro: { chave, rotulo, aceita } as FiltroOrigem, ...(origem ? { origem } : {}) };
}

// ------------------------------------------------------------------ inspeções

export interface LinhaInspecao {
  id: string;
  dataRealizacao: string;
  dia: string;
  formulario: string;
  tipo: string;
  inspetor: string;
  idInspetor: string;
  equipe: string;
  base: string;
  processo: string;
  status: StatusInspecao;
  colaboradores: string[];
  desvios: number;
  piorGravidade: GravidadeDesvio | null;
  gravidades: GravidadeDesvio[];
  desviosAbertos: number;
  evidencias: number;
  observacoes: string;
}

export function linhaInspecao(i: InspecaoSegurancaResponse): LinhaInspecao {
  const desvios = i.desvios ?? [];
  const gravidades = desvios.map((d) => d.gravidade);
  const pior = gravidades.reduce<GravidadeDesvio | null>((p, g) => (!p || ORDEM_GRAVIDADE.indexOf(g) > ORDEM_GRAVIDADE.indexOf(p) ? g : p), null);
  return {
    id: i.id,
    dataRealizacao: i.dataRealizacao,
    dia: diaIso(i.dataRealizacao),
    formulario: i.formulario?.titulo ?? 'Formulário',
    tipo: i.tipoInspecao?.nome ?? '—',
    inspetor: nomeOu(i.nomeInspetor),
    idInspetor: i.idInspetor,
    equipe: i.equipe?.codigo ?? '—',
    base: i.baseOperacional?.nome ?? '—',
    processo: i.processo?.nome ?? '—',
    status: i.status,
    colaboradores: (i.colaboradoresInspecionados ?? []).map((c) => nomeOu(c.nomeCompleto)),
    desvios: desvios.length,
    piorGravidade: pior,
    gravidades,
    desviosAbertos: desvios.filter((d) => d.status !== 'RESOLVIDO').length,
    evidencias: i.evidencias?.length ?? 0,
    observacoes: i.observacoes ?? ''
  };
}

export const COLUNAS_INSPECAO: CesDataTableColumn<LinhaInspecao>[] = [
  { key: 'dataRealizacao', header: 'Realizada', priority: 1, width: '8rem', value: (l) => l.dataRealizacao, display: (l) => diaHora(l.dataRealizacao), searchable: false },
  { key: 'formulario', header: 'Formulário', priority: 1 },
  { key: 'inspetor', header: 'Inspetor', priority: 1 },
  { key: 'equipe', header: 'Equipe', priority: 2 },
  { key: 'base', header: 'Base', priority: 3 },
  { key: 'status', header: 'Status', priority: 3, value: (l) => STATUS_INSPECAO_LABELS[l.status] ?? l.status },
  {
    key: 'desvios',
    header: 'Desvios',
    priority: 1,
    value: (l) => l.desvios * 10 + (l.piorGravidade ? ORDEM_GRAVIDADE.indexOf(l.piorGravidade) : 0),
    display: (l) => (l.desvios ? `${l.desvios} · pior ${GRAVIDADE_LABELS[l.piorGravidade!]}` : 'Nenhum')
  }
];

export function detalheInspecao(l: LinhaInspecao): DetalheRegistro {
  const porGravidade = ORDEM_GRAVIDADE.slice().reverse()
    .map((g) => ({ g, n: l.gravidades.filter((x) => x === g).length }))
    .filter((x) => x.n)
    .map((x) => `${x.n} ${GRAVIDADE_LABELS[x.g].toLowerCase()}`);
  return {
    titulo: l.formulario,
    subtitulo: `${l.inspetor} · ${diaHora(l.dataRealizacao)}`,
    campos: [
      { rotulo: 'Status', valor: STATUS_INSPECAO_LABELS[l.status] ?? l.status, icone: 'pi pi-check-square' },
      { rotulo: 'Tipo', valor: l.tipo, icone: 'pi pi-tag' },
      { rotulo: 'Equipe', valor: l.equipe, icone: 'pi pi-users' },
      { rotulo: 'Base operacional', valor: l.base, icone: 'pi pi-building' },
      { rotulo: 'Processo', valor: l.processo, icone: 'pi pi-sitemap' },
      { rotulo: 'Desvios', valor: l.desvios ? porGravidade.join(', ') : 'Nenhum', icone: 'pi pi-exclamation-triangle' },
      { rotulo: 'Desvios pendentes', valor: String(l.desviosAbertos), icone: 'pi pi-clock' },
      { rotulo: 'Evidências', valor: plural(l.evidencias, 'foto', 'fotos'), icone: 'pi pi-camera' }
    ],
    listas: [
      { rotulo: `Colaboradores inspecionados (${l.colaboradores.length})`, itens: l.colaboradores },
      ...(l.observacoes ? [{ rotulo: 'Observações', itens: [l.observacoes] }] : [])
    ],
    links: [{ rotulo: 'Abrir inspeção completa', icone: 'pi pi-file', rota: ['/sst/inspecoes', l.id] }]
  };
}

export interface OpcoesOrigemInspecoes {
  readonly sst: SstService;
  readonly filtros: InspecaoFilters;
  readonly titulo?: string;
  readonly descricao?: string;
  readonly quantidadeEsperada?: number;
  readonly filtroInicial?: FiltroOrigem<LinhaInspecao>;
  readonly derivados?: (linhas: readonly LinhaInspecao[]) => readonly GraficoAnalise[];
  readonly verTudo?: LinkAnalise;
  readonly nomeArquivo: string;
}

export function origemInspecoes(o: OpcoesOrigemInspecoes): OrigemAnalise<LinhaInspecao> {
  return {
    id: 'inspecoes',
    chave: `inspecoes:${JSON.stringify(o.filtros)}`,
    titulo: o.titulo ?? 'Inspeções que compõem este número',
    rotuloAba: 'Origem · inspeções',
    icone: 'pi pi-clipboard',
    descricao: o.descricao ?? 'As inspeções do recorte, com os mesmos filtros do painel. Clique numa inspeção para ver desvios, colaboradores e evidências.',
    colunas: COLUNAS_INSPECAO,
    carregar: () =>
      o.sst.listarInspecoes({ ...o.filtros, page: 0, size: LIMITE_SST, sort: 'dataRealizacao,desc' }).pipe(
        map((p) => ({ linhas: p.content.map(linhaInspecao), total: p.page.totalElements }))
      ),
    chaveLinha: (l) => l.id,
    rotuloLinha: (l) => `${l.formulario} de ${l.inspetor}`,
    filtroInicial: o.filtroInicial,
    conferencia: o.quantidadeEsperada != null ? { quantidadeEsperada: o.quantidadeEsperada, valorEsperado: null, somar: () => 0, formato: 'inteiro' } : undefined,
    derivados: o.derivados,
    detalhar: detalheInspecao,
    verTudo: o.verTudo,
    nomeArquivo: o.nomeArquivo,
    vazio: 'Nenhuma inspeção neste recorte'
  };
}

/** Gráficos a partir das inspeções carregadas: quem inspecionou, o quê, onde e com qual resultado. */
export function graficosDasInspecoes(linhas: readonly LinhaInspecao[], opcoes: { inspetor?: boolean; dia?: boolean } = {}): GraficoAnalise[] {
  if (!linhas.length) return [];
  const graficos: GraficoAnalise[] = [];
  const comDesvio = linhas.filter((l) => l.desvios > 0).length;
  graficos.push({
    id: 'resultado-inspecoes',
    titulo: 'Resultado das inspeções',
    descricao: 'Clique numa fatia para listar as inspeções.',
    tipo: 'doughnut',
    rotulos: ['Sem desvio', 'Com desvio'],
    series: [{ name: 'Inspeções', values: [linhas.length - comDesvio, comDesvio] }],
    formato: 'integer',
    altura: 220,
    aoSelecionar: (i) => filtrarPor<LinhaInspecao>(`resultado-${i}`, i ? 'Com desvio' : 'Sem desvio', (l) => (i ? l.desvios > 0 : l.desvios === 0), 'inspecoes'),
    rotuloSelecao: 'Ver inspeções'
  });
  if (opcoes.inspetor !== false) {
    const porInspetor = contar(linhas, (l) => l.inspetor);
    graficos.push({
      id: 'inspecoes-por-inspetor',
      titulo: 'Quem inspecionou',
      descricao: 'Clique num inspetor para filtrar as inspeções dele.',
      tipo: 'bar-horizontal',
      rotulos: porInspetor.map((x) => x.rotulo),
      series: [{ name: 'Inspeções', values: porInspetor.map((x) => x.total) }],
      formato: 'integer',
      altura: alturaBarras(porInspetor.length),
      aoSelecionar: (i) => filtrarPor<LinhaInspecao>(`inspetor-${i}`, porInspetor[i].rotulo, (l) => l.inspetor === porInspetor[i].rotulo, 'inspecoes'),
      rotuloSelecao: 'Ver inspeções'
    });
  }
  const porFormulario = contar(linhas, (l) => l.formulario, 10);
  graficos.push({
    id: 'inspecoes-por-formulario',
    titulo: 'Formulários aplicados',
    tipo: 'bar-horizontal',
    rotulos: porFormulario.map((x) => x.rotulo),
    series: [{ name: 'Inspeções', values: porFormulario.map((x) => x.total) }],
    formato: 'integer',
    altura: alturaBarras(porFormulario.length),
    aoSelecionar: (i) => filtrarPor<LinhaInspecao>(`formulario-${i}`, porFormulario[i].rotulo, (l) => l.formulario === porFormulario[i].rotulo, 'inspecoes'),
    rotuloSelecao: 'Ver inspeções'
  });
  if (opcoes.dia !== false) {
    const dias = contar(linhas, (l) => l.dia, 400).sort((a, b) => a.rotulo.localeCompare(b.rotulo));
    if (dias.length > 1) {
      graficos.push({
        id: 'inspecoes-por-dia',
        titulo: 'Inspeções por dia',
        tipo: 'bar',
        rotulos: dias.map((x) => x.rotulo.split('-').reverse().slice(0, 2).join('/')),
        series: [{ name: 'Inspeções', values: dias.map((x) => x.total) }],
        formato: 'integer',
        altura: 220,
        inteiro: true,
        aoSelecionar: (i) => filtrarPor<LinhaInspecao>(`dia-${dias[i].rotulo}`, `Em ${dias[i].rotulo.split('-').reverse().join('/')}`, (l) => l.dia === dias[i].rotulo, 'inspecoes'),
        rotuloSelecao: 'Ver inspeções do dia'
      });
    }
  }
  return graficos;
}

// ------------------------------------------------------------------ desvios

export interface LinhaDesvio {
  id: string;
  idInspecao: string;
  dataInspecao: string;
  gravidade: GravidadeDesvio;
  item: string;
  grupo: string;
  resposta: string;
  descricao: string;
  acaoCorretiva: string;
  inspetor: string;
  formulario: string;
  equipe: string;
  base: string;
  processo: string;
  status: StatusDesvio;
  prazo: string | null;
  vencido: boolean;
  diasEmAtraso: number | null;
  dataResolucao: string | null;
  colaboradores: string[];
  anexos: number;
  idadeDias: number;
}

export function linhaDesvio(d: DesvioDetalhadoResponse, agora = Date.now()): LinhaDesvio {
  return {
    id: d.id,
    idInspecao: d.idInspecao,
    dataInspecao: d.dataInspecao,
    gravidade: d.gravidade,
    item: d.pergunta ?? d.descricao ?? 'Desvio',
    grupo: d.grupo ?? '—',
    resposta: d.opcaoResposta ?? '—',
    descricao: d.descricao ?? '',
    acaoCorretiva: d.acaoCorretiva ?? '',
    inspetor: nomeOu(d.inspetor),
    formulario: d.formulario ?? '—',
    equipe: d.equipe?.codigo ?? '—',
    base: d.baseOperacional?.nome ?? '—',
    processo: d.processo?.nome ?? '—',
    status: d.status,
    prazo: d.prazo,
    vencido: d.vencido,
    diasEmAtraso: d.diasEmAtraso,
    dataResolucao: d.dataResolucao,
    colaboradores: (d.colaboradores ?? []).map((c) => nomeOu(c.nomeCompleto)),
    anexos: d.anexos?.length ?? 0,
    idadeDias: Math.max(0, Math.floor(((d.dataResolucao ? Date.parse(d.dataResolucao) : agora) - Date.parse(d.dataInspecao)) / 86_400_000))
  };
}

function rotuloPrazo(l: LinhaDesvio): string {
  if (!l.prazo) return 'Sem prazo';
  const data = new Date(`${l.prazo.slice(0, 10)}T12:00:00`).toLocaleDateString('pt-BR');
  return l.vencido ? `${data} · vencido há ${plural(l.diasEmAtraso ?? 0, 'dia', 'dias')}` : data;
}

export const COLUNAS_DESVIO: CesDataTableColumn<LinhaDesvio>[] = [
  { key: 'gravidade', header: 'Gravidade', priority: 1, width: '7rem', value: (l) => ORDEM_GRAVIDADE.indexOf(l.gravidade), display: (l) => GRAVIDADE_LABELS[l.gravidade] },
  { key: 'item', header: 'Item do checklist', priority: 1 },
  { key: 'equipe', header: 'Equipe', priority: 2 },
  { key: 'inspetor', header: 'Inspetor', priority: 3 },
  { key: 'status', header: 'Tratamento', priority: 1, value: (l) => STATUS_DESVIO_LABELS[l.status] ?? l.status },
  { key: 'prazo', header: 'Prazo', priority: 2, value: (l) => l.prazo ?? '9999', display: rotuloPrazo },
  { key: 'idadeDias', header: 'Idade (dias)', type: 'integer', priority: 3 },
  { key: 'dataInspecao', header: 'Inspeção', priority: 3, value: (l) => l.dataInspecao, display: (l) => diaHora(l.dataInspecao), searchable: false }
];

export function detalheDesvio(l: LinhaDesvio): DetalheRegistro {
  return {
    titulo: l.item,
    subtitulo: `${GRAVIDADE_LABELS[l.gravidade]} · ${l.formulario} · ${diaHora(l.dataInspecao)}`,
    campos: [
      { rotulo: 'Tratamento', valor: STATUS_DESVIO_LABELS[l.status] ?? l.status, icone: 'pi pi-sync' },
      { rotulo: 'Prazo', valor: rotuloPrazo(l), icone: 'pi pi-calendar' },
      { rotulo: l.dataResolucao ? 'Resolvido em' : 'Aberto há', valor: l.dataResolucao ? `${diaHora(l.dataResolucao)} (${plural(l.idadeDias, 'dia', 'dias')})` : plural(l.idadeDias, 'dia', 'dias'), icone: 'pi pi-history' },
      { rotulo: 'Resposta', valor: l.resposta, icone: 'pi pi-comment' },
      { rotulo: 'Grupo', valor: l.grupo, icone: 'pi pi-list' },
      { rotulo: 'Equipe', valor: l.equipe, icone: 'pi pi-users' },
      { rotulo: 'Base operacional', valor: l.base, icone: 'pi pi-building' },
      { rotulo: 'Inspetor', valor: l.inspetor, icone: 'pi pi-user' },
      { rotulo: 'Anexos', valor: plural(l.anexos, 'arquivo', 'arquivos'), icone: 'pi pi-paperclip' }
    ],
    listas: [
      { rotulo: `Colaboradores envolvidos (${l.colaboradores.length})`, itens: l.colaboradores },
      ...(l.descricao ? [{ rotulo: 'Descrição', itens: [l.descricao] }] : []),
      ...(l.acaoCorretiva ? [{ rotulo: 'Ação corretiva', itens: [l.acaoCorretiva] }] : [])
    ],
    links: [{ rotulo: 'Abrir a inspeção do desvio', icone: 'pi pi-file', rota: ['/sst/inspecoes', l.idInspecao] }]
  };
}

export interface OpcoesOrigemDesvios {
  readonly sst: SstService;
  readonly filtros: DesvioFilter;
  readonly id?: string;
  readonly titulo?: string;
  readonly rotuloAba?: string;
  readonly descricao?: string;
  readonly quantidadeEsperada?: number;
  readonly filtroInicial?: FiltroOrigem<LinhaDesvio>;
  readonly derivados?: (linhas: readonly LinhaDesvio[]) => readonly GraficoAnalise[];
  readonly verTudo?: LinkAnalise;
  readonly nomeArquivo: string;
}

export function origemDesvios(o: OpcoesOrigemDesvios): OrigemAnalise<LinhaDesvio> {
  return {
    id: o.id ?? 'desvios',
    chave: `desvios:${JSON.stringify(o.filtros)}`,
    titulo: o.titulo ?? 'Desvios que compõem este número',
    rotuloAba: o.rotuloAba ?? 'Origem · desvios',
    icone: 'pi pi-exclamation-triangle',
    descricao: o.descricao ?? 'Os desvios do recorte, mais graves primeiro. Clique num desvio para ver resposta, ação corretiva, prazo e envolvidos.',
    colunas: COLUNAS_DESVIO,
    carregar: () =>
      o.sst.listarDesvios({ ...o.filtros, page: 0, size: LIMITE_SST }).pipe(
        map((p) => ({
          linhas: p.content.map((d) => linhaDesvio(d)).sort((a, b) => ORDEM_GRAVIDADE.indexOf(b.gravidade) - ORDEM_GRAVIDADE.indexOf(a.gravidade)),
          total: p.page.totalElements
        }))
      ),
    chaveLinha: (l) => l.id,
    rotuloLinha: (l) => l.item,
    filtroInicial: o.filtroInicial,
    conferencia: o.quantidadeEsperada != null ? { quantidadeEsperada: o.quantidadeEsperada, valorEsperado: null, somar: () => 0, formato: 'inteiro' } : undefined,
    derivados: o.derivados,
    detalhar: detalheDesvio,
    verTudo: o.verTudo,
    nomeArquivo: o.nomeArquivo,
    vazio: 'Nenhum desvio neste recorte'
  };
}

/** Gráficos a partir dos desvios carregados: gravidade, tratamento, itens que mais falham e onde. */
export function graficosDosDesvios(linhas: readonly LinhaDesvio[], origem = 'desvios', opcoes: { equipe?: boolean; itens?: boolean } = {}): GraficoAnalise[] {
  if (!linhas.length) return [];
  const gravidades = ORDEM_GRAVIDADE.slice().reverse().filter((g) => linhas.some((l) => l.gravidade === g));
  const status = (['ABERTO', 'EM_TRATAMENTO', 'RESOLVIDO'] as StatusDesvio[]).filter((s) => linhas.some((l) => l.status === s));
  const graficos: GraficoAnalise[] = [
    {
      id: `${origem}-gravidade`,
      titulo: 'Desvios por gravidade',
      descricao: 'Clique numa fatia para listar os desvios.',
      tipo: 'doughnut',
      rotulos: gravidades.map((g) => GRAVIDADE_LABELS[g]),
      series: [{ name: 'Desvios', values: gravidades.map((g) => linhas.filter((l) => l.gravidade === g).length) }],
      formato: 'integer',
      altura: 220,
      aoSelecionar: (i) => filtrarPor<LinhaDesvio>(`gravidade-${gravidades[i]}`, `Gravidade ${GRAVIDADE_LABELS[gravidades[i]].toLowerCase()}`, (l) => l.gravidade === gravidades[i], origem),
      rotuloSelecao: 'Ver desvios'
    },
    {
      id: `${origem}-tratamento`,
      titulo: 'Situação do tratamento',
      descricao: 'Vencidos contam dentro de "Aberto" e "Em tratamento".',
      tipo: 'bar',
      rotulos: [...status.map((s) => STATUS_DESVIO_LABELS[s]), 'Vencidos'],
      series: [{ name: 'Desvios', values: [...status.map((s) => linhas.filter((l) => l.status === s).length), linhas.filter((l) => l.vencido).length] }],
      formato: 'integer',
      altura: 220,
      aoSelecionar: (i) => i < status.length
        ? filtrarPor<LinhaDesvio>(`status-${status[i]}`, STATUS_DESVIO_LABELS[status[i]], (l) => l.status === status[i], origem)
        : filtrarPor<LinhaDesvio>('vencidos', 'Vencidos', (l) => l.vencido, origem),
      rotuloSelecao: 'Ver desvios'
    }
  ];
  if (opcoes.itens !== false) {
    const itens = contar(linhas, (l) => l.item, 10);
    graficos.push({
      id: `${origem}-itens`,
      titulo: 'Itens que mais geraram desvio',
      descricao: 'Clique num item para ver onde e com quem aconteceu.',
      tipo: 'bar-horizontal',
      rotulos: itens.map((x) => (x.rotulo.length > 48 ? `${x.rotulo.slice(0, 46)}…` : x.rotulo)),
      series: [{ name: 'Desvios', values: itens.map((x) => x.total) }],
      formato: 'integer',
      altura: alturaBarras(itens.length),
      inteiro: true,
      aoSelecionar: (i) => filtrarPor<LinhaDesvio>(`item-${i}`, itens[i].rotulo.length > 40 ? `${itens[i].rotulo.slice(0, 38)}…` : itens[i].rotulo, (l) => l.item === itens[i].rotulo, origem),
      rotuloSelecao: 'Ver desvios do item'
    });
  }
  if (opcoes.equipe !== false) {
    const equipes = contar(linhas, (l) => l.equipe, 12);
    if (equipes.length > 1) {
      graficos.push({
        id: `${origem}-equipes`,
        titulo: 'Desvios por equipe',
        tipo: 'bar-horizontal',
        rotulos: equipes.map((x) => x.rotulo),
        series: [{ name: 'Desvios', values: equipes.map((x) => x.total) }],
        formato: 'integer',
        altura: alturaBarras(equipes.length),
        aoSelecionar: (i) => filtrarPor<LinhaDesvio>(`equipe-${i}`, `Equipe ${equipes[i].rotulo}`, (l) => l.equipe === equipes[i].rotulo, origem),
        rotuloSelecao: 'Ver desvios da equipe'
      });
    }
  }
  return graficos;
}
